package com.qingyan.hazmat.service;

import com.qingyan.hazmat.common.BizException;
import com.qingyan.hazmat.hazmat.entity.ChemBatch;
import com.qingyan.hazmat.hazmat.entity.ChemInfo;
import com.qingyan.hazmat.hazmat.entity.PurchaseReq;
import com.qingyan.hazmat.hazmat.entity.Requisition;
import com.qingyan.hazmat.hazmat.entity.RequisitionBatch;
import com.qingyan.hazmat.hazmat.entity.StockReturn;
import com.qingyan.hazmat.hazmat.mapper.ChemInfoMapper;
import com.qingyan.hazmat.hazmat.mapper.PurchaseReqMapper;
import com.qingyan.hazmat.hazmat.mapper.RequisitionBatchMapper;
import com.qingyan.hazmat.hazmat.mapper.RequisitionMapper;
import com.qingyan.hazmat.hazmat.mapper.StockReturnMapper;
import com.qingyan.hazmat.hazmat.service.RequisitionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * FEFO 领用/归还单测：起点分支 + 导师签/复核双签 + FEFO 跨批次拆分 + 改派幂等 + 归还闭环 + 核销。
 * 约定：申请人=100、导师=201、双签安全员=202、学院=203；领用单 id=1、化学品台账 id=5、
 * 批次 11/12/13；关联申购 id=9（CAS 151-50-8，qty=5）。
 */
class RequisitionServiceTest {

    private static final long APPLICANT = 100L;
    private static final long ADVISOR = 201L;
    private static final long CHECKER = 202L;
    private static final long COLLEGE = 203L;
    private static final String CAS = "151-50-8";

    private ChemInfoMapper chemInfoMapper;
    private PurchaseReqMapper purchaseReqMapper;
    private RequisitionMapper requisitionMapper;
    private RequisitionBatchMapper requisitionBatchMapper;
    private StockReturnMapper stockReturnMapper;
    private RequisitionService service;

    @BeforeEach
    void setUp() {
        chemInfoMapper = Mockito.mock(ChemInfoMapper.class);
        purchaseReqMapper = Mockito.mock(PurchaseReqMapper.class);
        requisitionMapper = Mockito.mock(RequisitionMapper.class);
        requisitionBatchMapper = Mockito.mock(RequisitionBatchMapper.class);
        stockReturnMapper = Mockito.mock(StockReturnMapper.class);
        service = new RequisitionService(chemInfoMapper, purchaseReqMapper,
                requisitionMapper, requisitionBatchMapper, stockReturnMapper);
    }

    private ChemInfo chem(int category, int status) {
        ChemInfo c = new ChemInfo();
        c.setId(5L);
        c.setName("氰化钾");
        c.setCasNo(CAS);
        c.setCategory(category);
        c.setStockQty(new BigDecimal("10"));
        c.setStatus(status);
        return c;
    }

    private Requisition req(int status) {
        Requisition r = new Requisition();
        r.setId(1L);
        r.setChemicalId(5L);
        r.setQty(new BigDecimal("3"));
        r.setApplicantId(APPLICANT);
        r.setReturnQty(BigDecimal.ZERO);
        r.setStatus(status);
        return r;
    }

    private ChemBatch batch(long id, BigDecimal left) {
        ChemBatch b = new ChemBatch();
        b.setId(id);
        b.setChemicalId(5L);
        b.setLotNo("LOT-" + id);
        b.setQtyLeft(left);
        b.setExpiryDate(LocalDate.of(2027, 1, 1));
        b.setStatus(0);
        return b;
    }

    private PurchaseReq purchase(int status) {
        PurchaseReq p = new PurchaseReq();
        p.setId(9L);
        p.setCasNo(CAS);
        p.setQty(new BigDecimal("5"));
        p.setStatus(status);
        return p;
    }

    private void permGranted() {
        when(purchaseReqMapper.countPerm(anyLong(), anyString())).thenReturn(1);
    }

    // ── submit：守卫 ──
    @Test
    void submit_台账封存拒绝() {
        when(chemInfoMapper.selectById(5L)).thenReturn(chem(4, 1));
        assertEquals(4504, assertThrows(BizException.class,
                () -> service.submit(5L, new BigDecimal("1"), APPLICANT, null, null, null, null)).getCode());
    }

    @Test
    void submit_数量非正拒绝() {
        when(chemInfoMapper.selectById(5L)).thenReturn(chem(4, 0));
        assertEquals(4101, assertThrows(BizException.class,
                () -> service.submit(5L, BigDecimal.ZERO, APPLICANT, null, null, null, null)).getCode());
    }

    @Test
    void submit_剧毒单次超限拒绝() {
        ChemInfo c = chem(4, 0);
        c.setMaxOnce(new BigDecimal("1"));
        when(chemInfoMapper.selectById(5L)).thenReturn(c);
        assertEquals(4701, assertThrows(BizException.class,
                () -> service.submit(5L, new BigDecimal("2"), APPLICANT, null, null, null, null)).getCode());
    }

    @Test
    void submit_关联申购非已批拒绝() {
        when(chemInfoMapper.selectById(5L)).thenReturn(chem(4, 0));
        when(purchaseReqMapper.selectById(9L)).thenReturn(purchase(1));
        assertEquals(4404, assertThrows(BizException.class,
                () -> service.submit(5L, new BigDecimal("1"), APPLICANT, 9L, null, null, null)).getCode());
    }

    @Test
    void submit_关联申购CAS不匹配拒绝() {
        when(chemInfoMapper.selectById(5L)).thenReturn(chem(4, 0));
        PurchaseReq p = purchase(2);
        p.setCasNo("67-64-1");
        when(purchaseReqMapper.selectById(9L)).thenReturn(p);
        assertEquals(4404, assertThrows(BizException.class,
                () -> service.submit(5L, new BigDecimal("1"), APPLICANT, 9L, null, null, null)).getCode());
    }

    @Test
    void submit_关联申购超领拒绝() {
        when(chemInfoMapper.selectById(5L)).thenReturn(chem(4, 0));
        when(purchaseReqMapper.selectById(9L)).thenReturn(purchase(2)); // qty=5
        when(requisitionMapper.sumQtyByReq(9L)).thenReturn(new BigDecimal("4"));
        // 4+2 > 5 → 4403
        assertEquals(4403, assertThrows(BizException.class,
                () -> service.submit(5L, new BigDecimal("2"), APPLICANT, 9L, null, null, null)).getCode());
    }

    // ── submit：起点分支 ──
    @Test
    void submit_关联已批申购落待复核() {
        when(chemInfoMapper.selectById(5L)).thenReturn(chem(4, 0));
        when(purchaseReqMapper.selectById(9L)).thenReturn(purchase(2));
        when(requisitionMapper.sumQtyByReq(9L)).thenReturn(BigDecimal.ZERO);
        Requisition r = service.submit(5L, new BigDecimal("2"), APPLICANT, 9L,
                LocalDate.of(2026, 10, 1), "合成实验", "EXP-1");
        assertEquals(1, r.getStatus()); // 关联申购免导师签 → 直接待复核
        verify(requisitionMapper).insert(r);
    }

    @Test
    void submit_非关联危化品落待导师() {
        when(chemInfoMapper.selectById(5L)).thenReturn(chem(1, 0));
        Requisition r = service.submit(5L, new BigDecimal("1"), APPLICANT, null, null, "有机实验", null);
        assertEquals(0, r.getStatus()); // category≥1 且无关联 → 待导师
    }

    @Test
    void submit_非关联普通化学品落待复核() {
        when(chemInfoMapper.selectById(5L)).thenReturn(chem(0, 0));
        Requisition r = service.submit(5L, new BigDecimal("1"), APPLICANT, null, null, null, null);
        assertEquals(1, r.getStatus()); // category=0 免导师签
    }

    // ── advisor：守卫 ──
    @Test
    void advisor_非待导师状态拒绝() {
        when(requisitionMapper.selectById(1L)).thenReturn(req(1));
        assertEquals(4101, assertThrows(BizException.class,
                () -> service.advisor(1L, ADVISOR, true, null)).getCode());
    }

    @Test
    void advisor_申请人自审拒绝() {
        when(requisitionMapper.selectById(1L)).thenReturn(req(0));
        permGranted();
        assertEquals(4104, assertThrows(BizException.class,
                () -> service.advisor(1L, APPLICANT, true, null)).getCode());
    }

    @Test
    void advisor_无导师权限拒绝() {
        when(requisitionMapper.selectById(1L)).thenReturn(req(0));
        when(purchaseReqMapper.countPerm(ADVISOR, "hzm:purchase:audit-advisor")).thenReturn(0);
        assertEquals(4105, assertThrows(BizException.class,
                () -> service.advisor(1L, ADVISOR, true, null)).getCode());
    }

    @Test
    void advisor_驳回缺意见拒绝() {
        when(requisitionMapper.selectById(1L)).thenReturn(req(0));
        permGranted();
        assertEquals(4101, assertThrows(BizException.class,
                () -> service.advisor(1L, ADVISOR, false, " ")).getCode());
    }

    @Test
    void advisor_通过条件更新0到1() {
        when(requisitionMapper.selectById(1L)).thenReturn(req(0));
        permGranted();
        when(requisitionMapper.approveAdvisor(1L, ADVISOR)).thenReturn(1);
        service.advisor(1L, ADVISOR, true, null);
        verify(requisitionMapper).approveAdvisor(1L, ADVISOR);
    }

    @Test
    void advisor_条件更新失败拒绝() {
        when(requisitionMapper.selectById(1L)).thenReturn(req(0));
        permGranted();
        when(requisitionMapper.approveAdvisor(1L, ADVISOR)).thenReturn(0);
        assertEquals(4101, assertThrows(BizException.class,
                () -> service.advisor(1L, ADVISOR, true, null)).getCode());
    }

    // ── check：守卫 ──
    @Test
    void check_复核与申请人同人拒绝() {
        when(requisitionMapper.selectById(1L)).thenReturn(req(1));
        permGranted();
        assertEquals(4201, assertThrows(BizException.class,
                () -> service.check(1L, APPLICANT, true, null)).getCode());
    }

    @Test
    void check_无双签权限拒绝() {
        when(requisitionMapper.selectById(1L)).thenReturn(req(1));
        assertEquals(4202, assertThrows(BizException.class,
                () -> service.check(1L, CHECKER, true, null)).getCode());
    }

    @Test
    void check_库存不足拒绝4501() {
        when(requisitionMapper.selectById(1L)).thenReturn(req(1));
        permGranted();
        when(chemInfoMapper.selectFefoBatches(5L)).thenReturn(List.of(batch(11L, new BigDecimal("1"))));
        // Σ余量 1 < 需求 3 → 4501，且不发生任何扣减
        assertEquals(4501, assertThrows(BizException.class,
                () -> service.check(1L, CHECKER, true, null)).getCode());
        verify(chemInfoMapper, never()).deductBatch(anyLong(), any());
    }

    @Test
    void check_驳回缺意见拒绝() {
        when(requisitionMapper.selectById(1L)).thenReturn(req(1));
        permGranted();
        assertEquals(4101, assertThrows(BizException.class,
                () -> service.check(1L, CHECKER, false, null)).getCode());
    }

    // ── check：FEFO 出库 ──
    @Test
    void check_FEFO跨批次拆分出库() {
        when(requisitionMapper.selectById(1L)).thenReturn(req(1)); // qty=3
        permGranted();
        when(requisitionBatchMapper.countByReq(1L)).thenReturn(0);
        when(chemInfoMapper.selectFefoBatches(5L))
                .thenReturn(List.of(batch(11L, new BigDecimal("1")), batch(12L, new BigDecimal("3"))));
        when(chemInfoMapper.deductBatch(anyLong(), any())).thenReturn(1);
        when(chemInfoMapper.changeStock(eq(5L), any())).thenReturn(1);
        when(requisitionMapper.markOut(1L, CHECKER)).thenReturn(1);

        service.check(1L, CHECKER, true, null);

        // 先扣尽最早效期批 11（余 1），再从批 12 补 2——跨批次拆分
        verify(chemInfoMapper).deductBatch(11L, new BigDecimal("1"));
        verify(chemInfoMapper).deductBatch(12L, new BigDecimal("2"));
        ArgumentCaptor<RequisitionBatch> captor = ArgumentCaptor.forClass(RequisitionBatch.class);
        verify(requisitionBatchMapper, times(2)).insert(captor.capture());
        assertEquals(11L, captor.getAllValues().get(0).getBatchId());
        assertEquals(1, captor.getAllValues().get(0).getSeqNo());
        assertEquals(12L, captor.getAllValues().get(1).getBatchId());
        assertEquals(2, captor.getAllValues().get(1).getSeqNo());
        // 台账一次扣减总领用量（守恒）
        verify(chemInfoMapper).changeStock(5L, new BigDecimal("-3"));
        verify(requisitionMapper).markOut(1L, CHECKER);
    }

    @Test
    void check_并发扣减失败换下一批() {
        when(requisitionMapper.selectById(1L)).thenReturn(req(1)); // qty=3
        permGranted();
        when(requisitionBatchMapper.countByReq(1L)).thenReturn(0);
        when(chemInfoMapper.selectFefoBatches(5L))
                .thenReturn(List.of(batch(11L, new BigDecimal("1")), batch(12L, new BigDecimal("3"))));
        // 批 11 被并发抢走（affected=0），需求量整落到下一批 12 一次扣足
        when(chemInfoMapper.deductBatch(11L, new BigDecimal("1"))).thenReturn(0);
        when(chemInfoMapper.deductBatch(12L, new BigDecimal("3"))).thenReturn(1);
        when(chemInfoMapper.changeStock(eq(5L), any())).thenReturn(1);
        when(requisitionMapper.markOut(1L, CHECKER)).thenReturn(1);

        service.check(1L, CHECKER, true, null);

        verify(requisitionBatchMapper, times(1)).insert(any(RequisitionBatch.class));
        verify(chemInfoMapper).deductBatch(12L, new BigDecimal("3"));
        verify(chemInfoMapper).changeStock(5L, new BigDecimal("-3"));
    }

    @Test
    void check_出库条件更新失败拒绝() {
        when(requisitionMapper.selectById(1L)).thenReturn(req(1));
        permGranted();
        when(chemInfoMapper.selectFefoBatches(5L)).thenReturn(List.of(batch(11L, new BigDecimal("5"))));
        when(chemInfoMapper.deductBatch(anyLong(), any())).thenReturn(1);
        when(chemInfoMapper.changeStock(eq(5L), any())).thenReturn(1);
        when(requisitionMapper.markOut(1L, CHECKER)).thenReturn(0);
        assertEquals(4101, assertThrows(BizException.class,
                () -> service.check(1L, CHECKER, true, null)).getCode());
    }

    @Test
    void check_驳回进终态() {
        when(requisitionMapper.selectById(1L)).thenReturn(req(1));
        permGranted();
        when(requisitionMapper.reject(1L, 1)).thenReturn(1);
        service.check(1L, CHECKER, false, "领用依据不足");
        verify(requisitionMapper).reject(1L, 1);
        verify(chemInfoMapper, never()).selectFefoBatches(anyLong());
    }

    // ── dispatch：改派 ──
    @Test
    void dispatch_无双签权限拒绝() {
        when(requisitionMapper.selectById(1L)).thenReturn(req(2));
        assertEquals(4105, assertThrows(BizException.class,
                () -> service.dispatch(1L, 11L, 13L, 999L)).getCode());
    }

    @Test
    void dispatch_幂等条件更新失败拒绝() {
        Requisition r = req(2);
        when(requisitionMapper.selectById(1L)).thenReturn(r);
        permGranted();
        RequisitionBatch row = new RequisitionBatch();
        row.setReqId(1L);
        row.setBatchId(11L);
        row.setQty(new BigDecimal("3"));
        when(requisitionBatchMapper.selectRow(1L, 11L)).thenReturn(row);
        when(chemInfoMapper.selectBatch(13L)).thenReturn(batch(13L, new BigDecimal("5")));
        // affected=0 → 已被并发改派（防双回加幻影余量）
        when(requisitionBatchMapper.reassign(1L, 11L, 13L)).thenReturn(0);
        assertEquals(4101, assertThrows(BizException.class,
                () -> service.dispatch(1L, 11L, 13L, CHECKER)).getCode());
        verify(chemInfoMapper, never()).addBatch(anyLong(), any());
        verify(chemInfoMapper, never()).deductBatch(anyLong(), any());
    }

    @Test
    void dispatch_合法换批台账不动() {
        when(requisitionMapper.selectById(1L)).thenReturn(req(2));
        permGranted();
        RequisitionBatch row = new RequisitionBatch();
        row.setReqId(1L);
        row.setBatchId(11L);
        row.setQty(new BigDecimal("3"));
        when(requisitionBatchMapper.selectRow(1L, 11L)).thenReturn(row);
        when(chemInfoMapper.selectBatch(13L)).thenReturn(batch(13L, new BigDecimal("5")));
        when(requisitionBatchMapper.reassign(1L, 11L, 13L)).thenReturn(1);
        when(chemInfoMapper.addBatch(11L, new BigDecimal("3"))).thenReturn(1);
        when(chemInfoMapper.deductBatch(13L, new BigDecimal("3"))).thenReturn(1);

        service.dispatch(1L, 11L, 13L, CHECKER);

        verify(chemInfoMapper).addBatch(11L, new BigDecimal("3"));
        verify(chemInfoMapper).deductBatch(13L, new BigDecimal("3"));
        // 等量换批：台账 stock_qty 不动
        verify(chemInfoMapper, never()).changeStock(anyLong(), any());
    }

    // ── submitReturn ──
    @Test
    void submitReturn_未出库拒绝() {
        when(requisitionMapper.selectById(1L)).thenReturn(req(1));
        assertEquals(4101, assertThrows(BizException.class,
                () -> service.submitReturn(1L, 11L, new BigDecimal("1"), APPLICANT)).getCode());
    }

    @Test
    void submitReturn_批次非在库拒绝() {
        when(requisitionMapper.selectById(1L)).thenReturn(req(2));
        ChemBatch sealed = batch(11L, BigDecimal.ZERO);
        sealed.setStatus(3); // 过期封存
        when(chemInfoMapper.selectBatch(11L)).thenReturn(sealed);
        assertEquals(4204, assertThrows(BizException.class,
                () -> service.submitReturn(1L, 11L, new BigDecimal("1"), APPLICANT)).getCode());
    }

    @Test
    void submitReturn_批次化学品不匹配拒绝() {
        when(requisitionMapper.selectById(1L)).thenReturn(req(2));
        ChemBatch wrong = batch(11L, new BigDecimal("1"));
        wrong.setChemicalId(6L);
        when(chemInfoMapper.selectBatch(11L)).thenReturn(wrong);
        assertEquals(4601, assertThrows(BizException.class,
                () -> service.submitReturn(1L, 11L, new BigDecimal("1"), APPLICANT)).getCode());
    }

    @Test
    void submitReturn_合法落待确认归还单() {
        when(requisitionMapper.selectById(1L)).thenReturn(req(2));
        when(chemInfoMapper.selectBatch(11L)).thenReturn(batch(11L, new BigDecimal("1")));
        StockReturn ret = service.submitReturn(1L, 11L, new BigDecimal("1"), APPLICANT);
        assertEquals(0, ret.getStatus());
        assertTrue(ret.getCode().startsWith("GH-"));
        verify(stockReturnMapper).insert(ret);
    }

    // ── confirmReturn ──
    private StockReturn pendingReturn() {
        StockReturn ret = new StockReturn();
        ret.setId(7L);
        ret.setReqId(1L);
        ret.setBatchId(11L);
        ret.setQty(new BigDecimal("2"));
        ret.setApplicantId(APPLICANT);
        ret.setStatus(0);
        return ret;
    }

    @Test
    void confirmReturn_确认人权限不足拒绝() {
        when(stockReturnMapper.selectById(7L)).thenReturn(pendingReturn());
        when(purchaseReqMapper.countPerm(CHECKER, "hzm:stockin:countersign")).thenReturn(0);
        when(purchaseReqMapper.countPerm(CHECKER, "hzm:purchase:audit-college")).thenReturn(0);
        assertEquals(4202, assertThrows(BizException.class,
                () -> service.confirmReturn(7L, CHECKER)).getCode());
    }

    @Test
    void confirmReturn_超还拒绝4602() {
        when(stockReturnMapper.selectById(7L)).thenReturn(pendingReturn());
        when(purchaseReqMapper.countPerm(COLLEGE, "hzm:purchase:audit-college")).thenReturn(1);
        when(requisitionMapper.selectById(1L)).thenReturn(req(2));
        // 条件更新 affected=0：return_qty+2 > qty
        when(requisitionMapper.incrReturnQty(1L, new BigDecimal("2"))).thenReturn(0);
        assertEquals(4602, assertThrows(BizException.class,
                () -> service.confirmReturn(7L, COLLEGE)).getCode());
        verify(chemInfoMapper, never()).addBatch(anyLong(), any());
    }

    @Test
    void confirmReturn_归还批次非在库拒绝() {
        when(stockReturnMapper.selectById(7L)).thenReturn(pendingReturn());
        when(purchaseReqMapper.countPerm(COLLEGE, "hzm:purchase:audit-college")).thenReturn(1);
        when(requisitionMapper.selectById(1L)).thenReturn(req(2));
        when(requisitionMapper.incrReturnQty(1L, new BigDecimal("2"))).thenReturn(1);
        when(chemInfoMapper.addBatch(11L, new BigDecimal("2"))).thenReturn(0);
        assertEquals(4204, assertThrows(BizException.class,
                () -> service.confirmReturn(7L, COLLEGE)).getCode());
        verify(chemInfoMapper, never()).changeStock(anyLong(), any());
    }

    @Test
    void confirmReturn_足额归还自动关闭() {
        when(stockReturnMapper.selectById(7L)).thenReturn(pendingReturn()); // qty=2
        permGranted();
        Requisition r = req(2);
        r.setQty(new BigDecimal("2"));
        r.setReturnQty(new BigDecimal("2")); // 累计归还 == 领用量
        when(requisitionMapper.selectById(1L)).thenReturn(r);
        when(requisitionMapper.incrReturnQty(1L, new BigDecimal("2"))).thenReturn(1);
        when(chemInfoMapper.addBatch(11L, new BigDecimal("2"))).thenReturn(1);
        when(chemInfoMapper.changeStock(eq(5L), any())).thenReturn(1);
        when(stockReturnMapper.confirm(7L, CHECKER)).thenReturn(1);

        service.confirmReturn(7L, CHECKER);

        verify(chemInfoMapper).addBatch(11L, new BigDecimal("2"));
        verify(chemInfoMapper).changeStock(5L, new BigDecimal("2"));
        verify(stockReturnMapper).confirm(7L, CHECKER);
        // Σ归还 ≥ 领用量 → 领用单自动置 4 归还完毕
        verify(requisitionMapper).complete(1L);
    }

    @Test
    void confirmReturn_部分归还不自动关闭() {
        StockReturn ret = pendingReturn();
        ret.setQty(new BigDecimal("1")); // 部分归还
        when(stockReturnMapper.selectById(7L)).thenReturn(ret);
        permGranted();
        Requisition r = req(2);
        r.setReturnQty(new BigDecimal("1")); // 1 < 3
        when(requisitionMapper.selectById(1L)).thenReturn(r);
        when(requisitionMapper.incrReturnQty(1L, new BigDecimal("1"))).thenReturn(1);
        when(chemInfoMapper.addBatch(11L, new BigDecimal("1"))).thenReturn(1);
        when(chemInfoMapper.changeStock(eq(5L), any())).thenReturn(1);
        when(stockReturnMapper.confirm(7L, CHECKER)).thenReturn(1);

        service.confirmReturn(7L, CHECKER);

        verify(requisitionMapper, never()).complete(anyLong());
    }

    // ── rejectReturn ──
    @Test
    void rejectReturn_缺拒因拒绝() {
        StockReturn ret = pendingReturn();
        when(stockReturnMapper.selectById(7L)).thenReturn(ret);
        assertEquals(4101, assertThrows(BizException.class,
                () -> service.rejectReturn(7L, " ", CHECKER)).getCode());
    }

    @Test
    void rejectReturn_合法拒绝进终态() {
        when(stockReturnMapper.selectById(7L)).thenReturn(pendingReturn());
        when(stockReturnMapper.reject(7L, "包装破损拒收")).thenReturn(1);
        service.rejectReturn(7L, "包装破损拒收", CHECKER);
        verify(stockReturnMapper).reject(7L, "包装破损拒收");
    }

    // ── close：核销 ──
    @Test
    void close_缺说明拒绝() {
        assertEquals(4101, assertThrows(BizException.class,
                () -> service.close(1L, APPLICANT, " ")).getCode());
        verify(requisitionMapper, never()).close(anyLong(), anyLong());
    }

    @Test
    void close_非申请人拒绝() {
        when(requisitionMapper.close(1L, 999L)).thenReturn(0);
        when(requisitionMapper.selectById(1L)).thenReturn(req(2));
        assertEquals(4104, assertThrows(BizException.class,
                () -> service.close(1L, 999L, "实验中用尽")).getCode());
    }

    @Test
    void close_非已出库状态拒绝() {
        when(requisitionMapper.close(1L, APPLICANT)).thenReturn(0);
        when(requisitionMapper.selectById(1L)).thenReturn(req(3));
        assertEquals(4101, assertThrows(BizException.class,
                () -> service.close(1L, APPLICANT, "实验中用尽")).getCode());
    }

    @Test
    void close_合法核销() {
        when(requisitionMapper.close(1L, APPLICANT)).thenReturn(1);
        service.close(1L, APPLICANT, "实验中用尽，余量不可回收");
        verify(requisitionMapper).close(1L, APPLICANT);
    }
}
