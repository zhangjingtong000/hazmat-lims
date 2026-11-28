package com.qingyan.hazmat.service;

import com.qingyan.hazmat.common.BizException;
import com.qingyan.hazmat.hazmat.entity.ApprovalRecord;
import com.qingyan.hazmat.hazmat.entity.ChemBatch;
import com.qingyan.hazmat.hazmat.entity.ChemInfo;
import com.qingyan.hazmat.hazmat.entity.WasteDisposal;
import com.qingyan.hazmat.hazmat.mapper.ApprovalRecordMapper;
import com.qingyan.hazmat.hazmat.mapper.ChemInfoMapper;
import com.qingyan.hazmat.hazmat.mapper.PurchaseReqMapper;
import com.qingyan.hazmat.hazmat.mapper.WasteDisposalMapper;
import com.qingyan.hazmat.hazmat.service.WasteService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 废弃物处置单测：错批守卫 4603+ type 分支状态守卫 + 单级审批
 * （回避 4104 / 层级权限 4105 / 重复 4103）+ 双路销账（type=1/2 批次+台账同扣、
 * type=3 仅销批次双扣守卫）。
 * 约定：处置人=100、学院审批人=203、校级审批人=204；化学品台账 id=10、批次 id=20。
 */
class WasteServiceTest {

    private static final long OPERATOR = 100L;
    private static final long COLLEGE = 203L;
    private static final long CAMPUS = 204L;
    private static final long CHEM_ID = 10L;
    private static final long BATCH_ID = 20L;

    private WasteDisposalMapper wasteMapper;
    private ChemInfoMapper chemMapper;
    private PurchaseReqMapper reqMapper;
    private ApprovalRecordMapper recordMapper;
    private WasteService service;

    @BeforeEach
    void setUp() {
        wasteMapper = Mockito.mock(WasteDisposalMapper.class);
        chemMapper = Mockito.mock(ChemInfoMapper.class);
        reqMapper = Mockito.mock(PurchaseReqMapper.class);
        recordMapper = Mockito.mock(ApprovalRecordMapper.class);
        service = new WasteService(wasteMapper, chemMapper, reqMapper, recordMapper);
    }

    /** 在库批次（status=0，余量 2）。 */
    private ChemBatch batch(int status, String qtyLeft) {
        ChemBatch b = new ChemBatch();
        b.setId(BATCH_ID);
        b.setChemicalId(CHEM_ID);
        b.setStatus(status);
        b.setQtyLeft(new BigDecimal(qtyLeft));
        return b;
    }

    private ChemInfo info(int category) {
        ChemInfo c = new ChemInfo();
        c.setId(CHEM_ID);
        c.setCategory(category);
        return c;
    }

    /** 已提交待审批的处置单（type=1 废液，量 2）。 */
    private WasteDisposal pending(int type, String qty) {
        WasteDisposal wd = new WasteDisposal();
        wd.setId(1L);
        wd.setChemicalId(CHEM_ID);
        wd.setBatchId(BATCH_ID);
        wd.setType(type);
        wd.setQty(new BigDecimal(qty));
        wd.setOperatorId(OPERATOR);
        wd.setStatus(0);
        return wd;
    }

    private void permGranted() {
        when(reqMapper.countPerm(anyLong(), any())).thenReturn(1);
    }

    // ── submit：守卫 ──
    @Test
    void submit_批次与化学品不匹配拒绝() {
        when(chemMapper.selectBatch(BATCH_ID)).thenReturn(batch(0, "2"));
        // 批次属于另一化学品（错批即销错台账）
        assertEquals(4603, assertThrows(BizException.class,
                () -> service.submit(99L, BATCH_ID, 1, new BigDecimal("1"), "焚烧", OPERATOR)).getCode());
    }

    @Test
    void submit_type3非封存批次拒绝() {
        when(chemMapper.selectBatch(BATCH_ID)).thenReturn(batch(0, "2")); // 在库而非过期封存
        assertEquals(4502, assertThrows(BizException.class,
                () -> service.submit(CHEM_ID, BATCH_ID, 3, null, "退回供应商", OPERATOR)).getCode());
    }

    @Test
    void submit_type1超批次余量拒绝() {
        when(chemMapper.selectBatch(BATCH_ID)).thenReturn(batch(0, "2"));
        assertEquals(4501, assertThrows(BizException.class,
                () -> service.submit(CHEM_ID, BATCH_ID, 1, new BigDecimal("5"), "废液暂存", OPERATOR)).getCode());
    }

    @Test
    void submit_type1非在库批次拒绝() {
        when(chemMapper.selectBatch(BATCH_ID)).thenReturn(batch(3, "2")); // 过期封存批次不能走废液销账
        assertEquals(4501, assertThrows(BizException.class,
                () -> service.submit(CHEM_ID, BATCH_ID, 1, new BigDecimal("1"), "废液暂存", OPERATOR)).getCode());
    }

    // ── submit：合法路径 ──
    @Test
    void submit_合法废液建单() {
        when(chemMapper.selectBatch(BATCH_ID)).thenReturn(batch(0, "2"));
        WasteDisposal wd = service.submit(CHEM_ID, BATCH_ID, 1, new BigDecimal("2"), "废液暂存", OPERATOR);
        assertTrue(wd.getCode().startsWith("FQ-"));
        assertEquals(0, wd.getStatus());
        verify(wasteMapper).insert(any(WasteDisposal.class));
    }

    @Test
    void submit_type3整批按封存余量建单() {
        when(chemMapper.selectBatch(BATCH_ID)).thenReturn(batch(3, "1.5"));
        WasteDisposal wd = service.submit(CHEM_ID, BATCH_ID, 3, null, "退回供应商", OPERATOR);
        assertEquals(new BigDecimal("1.5"), wd.getQty()); // 整批口径：量=封存余量
        verify(wasteMapper).insert(any(WasteDisposal.class));
    }

    // ── audit：守卫 ──
    @Test
    void audit_非待审批状态拒绝() {
        WasteDisposal wd = pending(1, "2");
        wd.setStatus(1);
        when(wasteMapper.selectById(1L)).thenReturn(wd);
        assertEquals(4101, assertThrows(BizException.class,
                () -> service.audit(1L, COLLEGE, true, null)).getCode());
    }

    @Test
    void audit_处置人自审拒绝() {
        when(wasteMapper.selectById(1L)).thenReturn(pending(1, "2"));
        when(chemMapper.selectById(CHEM_ID)).thenReturn(info(1));
        assertEquals(4104, assertThrows(BizException.class,
                () -> service.audit(1L, OPERATOR, true, null)).getCode());
    }

    @Test
    void audit_低类别未持学院权限拒绝() {
        when(wasteMapper.selectById(1L)).thenReturn(pending(1, "2"));
        when(chemMapper.selectById(CHEM_ID)).thenReturn(info(1)); // category≤1 → 学院单级
        when(reqMapper.countPerm(CAMPUS, "hzm:purchase:audit-college")).thenReturn(0);
        assertEquals(4105, assertThrows(BizException.class,
                () -> service.audit(1L, CAMPUS, true, null)).getCode());
    }

    @Test
    void audit_高类别未持校级权限拒绝() {
        when(wasteMapper.selectById(1L)).thenReturn(pending(1, "2"));
        when(chemMapper.selectById(CHEM_ID)).thenReturn(info(4)); // 剧毒 → 校级单级
        when(reqMapper.countPerm(COLLEGE, "hzm:purchase:audit-campus")).thenReturn(0);
        assertEquals(4105, assertThrows(BizException.class,
                () -> service.audit(1L, COLLEGE, true, null)).getCode());
    }

    @Test
    void audit_重复审批转4103() {
        when(wasteMapper.selectById(1L)).thenReturn(pending(1, "2"));
        when(chemMapper.selectById(CHEM_ID)).thenReturn(info(1));
        permGranted();
        when(recordMapper.insert(any(ApprovalRecord.class))).thenThrow(new DuplicateKeyException("uk"));
        assertEquals(4103, assertThrows(BizException.class,
                () -> service.audit(1L, COLLEGE, true, null)).getCode());
    }

    @Test
    void audit_驳回缺意见拒绝() {
        when(wasteMapper.selectById(1L)).thenReturn(pending(1, "2"));
        assertEquals(4101, assertThrows(BizException.class,
                () -> service.audit(1L, COLLEGE, false, " ")).getCode());
    }

    // ── audit：合法路径与双路销账 ──
    @Test
    void audit_废液通过_批次台账同事务销账() {
        when(wasteMapper.selectById(1L)).thenReturn(pending(1, "2"));
        when(chemMapper.selectById(CHEM_ID)).thenReturn(info(1));
        permGranted();
        when(wasteMapper.approve(1L, COLLEGE)).thenReturn(1);
        when(chemMapper.deductBatch(BATCH_ID, new BigDecimal("2"))).thenReturn(1);
        when(chemMapper.changeStock(CHEM_ID, new BigDecimal("-2"))).thenReturn(1);
        service.audit(1L, COLLEGE, true, null);
        verify(chemMapper).deductBatch(BATCH_ID, new BigDecimal("2"));
        verify(chemMapper).changeStock(CHEM_ID, new BigDecimal("-2"));
        verify(chemMapper, never()).wipeBatch(anyLong());
        verify(recordMapper).insert(any(ApprovalRecord.class));
    }

    @Test
    void audit_过期整批通过_仅销批次不动台账() {
        when(wasteMapper.selectById(1L)).thenReturn(pending(3, "1.5"));
        when(chemMapper.selectById(CHEM_ID)).thenReturn(info(4));
        permGranted();
        when(wasteMapper.approve(1L, CAMPUS)).thenReturn(1);
        when(chemMapper.wipeBatch(BATCH_ID)).thenReturn(1);
        service.audit(1L, CAMPUS, true, null);
        verify(chemMapper).wipeBatch(BATCH_ID); // qty_left→0、status 3→2
        verify(chemMapper, never()).deductBatch(anyLong(), any());
        verify(chemMapper, never()).changeStock(anyLong(), any()); // 封存时已扣，双扣守卫
    }

    @Test
    void audit_批次扣减失败回滚销账() {
        when(wasteMapper.selectById(1L)).thenReturn(pending(1, "2"));
        when(chemMapper.selectById(CHEM_ID)).thenReturn(info(1));
        permGranted();
        when(wasteMapper.approve(1L, COLLEGE)).thenReturn(1);
        when(chemMapper.deductBatch(BATCH_ID, new BigDecimal("2"))).thenReturn(0); // 条件更新未命中
        assertEquals(4501, assertThrows(BizException.class,
                () -> service.audit(1L, COLLEGE, true, null)).getCode());
    }

    @Test
    void audit_驳回置终态() {
        when(wasteMapper.selectById(1L)).thenReturn(pending(1, "2"));
        when(chemMapper.selectById(CHEM_ID)).thenReturn(info(1));
        permGranted();
        when(wasteMapper.reject(1L, COLLEGE)).thenReturn(1);
        service.audit(1L, COLLEGE, false, "处置去向不明");
        verify(wasteMapper).reject(1L, COLLEGE);
        verify(chemMapper, never()).deductBatch(anyLong(), any());
        verify(wasteMapper, never()).approve(anyLong(), anyLong());
    }
}
