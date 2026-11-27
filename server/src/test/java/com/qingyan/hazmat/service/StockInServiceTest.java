package com.qingyan.hazmat.service;

import com.qingyan.hazmat.common.BizException;
import com.qingyan.hazmat.hazmat.entity.ChemBatch;
import com.qingyan.hazmat.hazmat.entity.PurchaseReq;
import com.qingyan.hazmat.hazmat.entity.ChemInfo;
import com.qingyan.hazmat.hazmat.entity.StockIn;
import com.qingyan.hazmat.hazmat.mapper.ChemInfoMapper;
import com.qingyan.hazmat.hazmat.mapper.PurchaseReqMapper;
import com.qingyan.hazmat.hazmat.mapper.StockInMapper;
import com.qingyan.hazmat.hazmat.service.CompatService;
import com.qingyan.hazmat.hazmat.service.StockInService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * R2 入库双签 + 落账单测：一步双签（普通/category≤3）、剧毒两步签、拒收终态、全部守卫。
 * 约定：收货人=100、复核人=201、第二签人=203（学院）；台账 id=5、入库单 id=9、申购单 id=7。
 */
class StockInServiceTest {

    private static final long RECEIVER = 100L;
    private static final long CHECKER = 201L;
    private static final long COLLEGE = 203L;

    private StockInMapper stockInMapper;
    private ChemInfoMapper chemMapper;
    private PurchaseReqMapper reqMapper;
    private CompatService compatService;
    private StockInService service;

    @BeforeEach
    void setUp() {
        stockInMapper = Mockito.mock(StockInMapper.class);
        chemMapper = Mockito.mock(ChemInfoMapper.class);
        reqMapper = Mockito.mock(PurchaseReqMapper.class);
        compatService = Mockito.mock(CompatService.class);
        service = new StockInService(stockInMapper, chemMapper, reqMapper, compatService);
    }

    private StockIn form() {
        StockIn f = new StockIn();
        f.setChemicalId(5L);
        f.setLotNo("LOT-2026-001");
        f.setQty(new BigDecimal("10"));
        f.setCabinetId(3L);
        f.setCheckerId(CHECKER);
        return f;
    }

    private ChemInfo chem(int category) {
        ChemInfo c = new ChemInfo();
        c.setId(5L);
        c.setName("丙酮");
        c.setCasNo("67-64-1");
        c.setCategory(category);
        c.setCompatGroup("FLAM");
        return c;
    }

    private PurchaseReq purchaseReq() {
        PurchaseReq r = new PurchaseReq();
        r.setId(7L);
        r.setChemicalName("丙酮");
        r.setCasNo("67-64-1");
        r.setCategory(1);
        return r;
    }

    private void permGranted() {
        when(reqMapper.countPerm(anyLong(), anyString())).thenReturn(1);
    }

    private void insertSetsId() {
        doAnswer(inv -> {
            inv.getArgument(0, StockIn.class).setId(9L);
            return 1;
        }).when(stockInMapper).insert(any(StockIn.class));
    }

    // ── submit：守卫 ──
    @Test
    void submit_数量非正拒绝() {
        StockIn f = form();
        f.setQty(BigDecimal.ZERO);
        assertEquals(4101, assertThrows(BizException.class,
                () -> service.submit(f, RECEIVER)).getCode());
    }

    @Test
    void submit_缺批号拒绝() {
        StockIn f = form();
        f.setLotNo(" ");
        assertEquals(4101, assertThrows(BizException.class,
                () -> service.submit(f, RECEIVER)).getCode());
    }

    @Test
    void submit_缺复核人拒绝() {
        StockIn f = form();
        f.setCheckerId(null);
        assertEquals(4101, assertThrows(BizException.class,
                () -> service.submit(f, RECEIVER)).getCode());
    }

    @Test
    void submit_双签同人拒绝() {
        permGranted();
        StockIn f = form();
        f.setCheckerId(RECEIVER); // 复核人 == 收货人
        assertEquals(4201, assertThrows(BizException.class,
                () -> service.submit(f, RECEIVER)).getCode());
    }

    @Test
    void submit_双签均无会签权限拒绝() {
        when(reqMapper.countPerm(anyLong(), anyString())).thenReturn(0);
        assertEquals(4202, assertThrows(BizException.class,
                () -> service.submit(form(), RECEIVER)).getCode());
    }

    @Test
    void submit_指定台账不存在拒绝() {
        permGranted();
        when(chemMapper.selectById(5L)).thenReturn(null);
        assertEquals(4101, assertThrows(BizException.class,
                () -> service.submit(form(), RECEIVER)).getCode());
    }

    @Test
    void submit_新化学品缺CAS拒绝() {
        permGranted();
        StockIn f = form();
        f.setChemicalId(null);
        ChemInfo draft = new ChemInfo();
        draft.setName("新化学品");
        draft.setCategory(1);
        assertEquals(4101, assertThrows(BizException.class,
                () -> service.submit(f, draft, RECEIVER)).getCode());
    }

    @Test
    void submit_新化学品未知分类拒绝() {
        permGranted();
        StockIn f = form();
        f.setChemicalId(null);
        ChemInfo draft = new ChemInfo();
        draft.setName("新化学品");
        draft.setCasNo("90-00-6");
        draft.setCategory(9);
        assertEquals(4001, assertThrows(BizException.class,
                () -> service.submit(f, draft, RECEIVER)).getCode());
    }

    @Test
    void submit_禁配冲突拒绝() {
        permGranted();
        when(chemMapper.selectById(5L)).thenReturn(chem(1));
        doThrow(new BizException(4301, "禁配冲突")).when(compatService).checkCabinet(eq(3L), any(ChemInfo.class));
        assertEquals(4301, assertThrows(BizException.class,
                () -> service.submit(form(), RECEIVER)).getCode());
        verify(stockInMapper, never()).insert(any(StockIn.class));
    }

    @Test
    void submit_关联申购累计超量拒绝() {
        permGranted();
        insertSetsId();
        when(chemMapper.selectById(5L)).thenReturn(chem(1));
        when(stockInMapper.complete(eq(9L), anyLong())).thenReturn(1);
        StockIn f = form();
        f.setReqId(7L);
        when(reqMapper.selectById(7L)).thenReturn(purchaseReq()); // CAS 与 chem(丙酮) 一致
        when(reqMapper.incrInQty(7L, new BigDecimal("10"))).thenReturn(0);
        assertEquals(4402, assertThrows(BizException.class,
                () -> service.submit(f, RECEIVER)).getCode());
    }

    @Test
    void submit_入库物质与申购CAS不符拒绝() {
        permGranted();
        insertSetsId();
        when(chemMapper.selectById(5L)).thenReturn(chem(1));
        PurchaseReq linked = purchaseReq();
        linked.setCasNo("7647-01-0"); // 盐酸，与丙酮不符
        when(reqMapper.selectById(7L)).thenReturn(linked);
        StockIn f = form();
        f.setReqId(7L);
        assertEquals(4405, assertThrows(BizException.class,
                () -> service.submit(f, RECEIVER)).getCode());
        verify(chemMapper, never()).insertBatch(any(ChemBatch.class)); // 未落任何账
    }

    @Test
    void submit_关联申购CAS一致正常核销() {
        permGranted();
        insertSetsId();
        when(chemMapper.selectById(5L)).thenReturn(chem(1));
        when(reqMapper.selectById(7L)).thenReturn(purchaseReq()); // CAS 一致
        when(reqMapper.incrInQty(7L, new BigDecimal("10"))).thenReturn(1);
        when(stockInMapper.complete(9L, CHECKER)).thenReturn(1);
        StockIn f = form();
        f.setReqId(7L);
        service.submit(f, RECEIVER);
        verify(chemMapper).insertBatch(any(ChemBatch.class));
        verify(stockInMapper).complete(9L, CHECKER);
    }

    @Test
    void submit_落账状态推进条件更新失败拒绝() {
        permGranted();
        insertSetsId();
        when(chemMapper.selectById(5L)).thenReturn(chem(1));
        when(stockInMapper.complete(9L, CHECKER)).thenReturn(0);
        assertEquals(4101, assertThrows(BizException.class,
                () -> service.submit(form(), RECEIVER)).getCode());
    }

    // ── submit：合法路径 ──
    @Test
    void submit_一步双签落账全链() {
        permGranted();
        insertSetsId();
        when(chemMapper.selectById(5L)).thenReturn(chem(1));
        when(stockInMapper.complete(9L, CHECKER)).thenReturn(1);

        service.submit(form(), RECEIVER);

        verify(compatService, Mockito.times(2)).checkCabinet(eq(3L), any(ChemInfo.class)); // 预检+落账复校
        ArgumentCaptor<ChemBatch> batch = ArgumentCaptor.forClass(ChemBatch.class);
        verify(chemMapper).insertBatch(batch.capture());
        assertEquals(5L, batch.getValue().getChemicalId());
        assertEquals(9L, batch.getValue().getStockInId());
        assertEquals(0, batch.getValue().getStatus());
        assertEquals(new BigDecimal("10"), batch.getValue().getQtyLeft());
        verify(chemMapper).changeStock(5L, new BigDecimal("10"));
        verify(reqMapper, never()).incrInQty(anyLong(), any()); // 未关联申购不回写
        verify(stockInMapper).complete(9L, CHECKER);
    }

    @Test
    void submit_CAS命中挂既有台账() {
        permGranted();
        insertSetsId();
        StockIn f = form();
        f.setChemicalId(null);
        ChemInfo draft = new ChemInfo();
        draft.setName("丙酮");
        draft.setCasNo("67-64-1");
        draft.setCategory(1);
        when(chemMapper.selectByCas("67-64-1")).thenReturn(chem(1));
        when(stockInMapper.complete(9L, CHECKER)).thenReturn(1);

        service.submit(f, draft, RECEIVER);

        verify(chemMapper, never()).insert(any(ChemInfo.class)); // 命中不新建台账
        assertEquals(5L, f.getChemicalId());
    }

    @Test
    void submit_CAS未命中新建台账编码含类别码年序() {
        permGranted();
        insertSetsId();
        StockIn f = form();
        f.setChemicalId(null);
        ChemInfo draft = new ChemInfo();
        draft.setName("硝酸铵");
        draft.setCasNo("6484-52-2");
        draft.setCategory(3);
        draft.setCompatGroup("YB");
        when(chemMapper.selectByCas("6484-52-2")).thenReturn(null);
        when(chemMapper.countByCodePrefix("HG-YB-" + LocalDate.now().getYear() + "-")).thenReturn(7);
        doAnswer(inv -> {
            inv.getArgument(0, ChemInfo.class).setId(5L);
            return 1;
        }).when(chemMapper).insert(any(ChemInfo.class));
        when(stockInMapper.complete(9L, CHECKER)).thenReturn(1);

        service.submit(f, draft, RECEIVER);

        ArgumentCaptor<ChemInfo> created = ArgumentCaptor.forClass(ChemInfo.class);
        verify(chemMapper).insert(created.capture());
        assertEquals("HG-YB-" + LocalDate.now().getYear() + "-0008", created.getValue().getCode());
        assertEquals(new BigDecimal("0"), created.getValue().getStockQty());
        assertEquals("YB", created.getValue().getCompatGroup());
    }

    @Test
    void submit_新台账缺相容组默认GEN() {
        permGranted();
        insertSetsId();
        StockIn f = form();
        f.setChemicalId(null);
        ChemInfo draft = new ChemInfo();
        draft.setName("氯化钠");
        draft.setCasNo("7647-14-5");
        draft.setCategory(0);
        when(chemMapper.selectByCas("7647-14-5")).thenReturn(null);
        when(chemMapper.countByCodePrefix(anyString())).thenReturn(0);
        doAnswer(inv -> {
            inv.getArgument(0, ChemInfo.class).setId(5L);
            return 1;
        }).when(chemMapper).insert(any(ChemInfo.class));
        when(stockInMapper.complete(9L, CHECKER)).thenReturn(1);

        service.submit(f, draft, RECEIVER);

        ArgumentCaptor<ChemInfo> created = ArgumentCaptor.forClass(ChemInfo.class);
        verify(chemMapper).insert(created.capture());
        assertEquals("GEN", created.getValue().getCompatGroup());
    }

    @Test
    void submit_剧毒两步签仅首签不落账() {
        permGranted();
        insertSetsId();
        when(chemMapper.selectById(5L)).thenReturn(chem(4));

        StockIn f = service.submit(form(), RECEIVER);

        verify(chemMapper, never()).insertBatch(any(ChemBatch.class)); // 不落账
        verify(chemMapper, never()).changeStock(anyLong(), any());
        verify(reqMapper, never()).incrInQty(anyLong(), any());
        verify(stockInMapper, never()).complete(anyLong(), anyLong());
        assertEquals(0, f.getStatus());
        assertNull(f.getCheckerId()); // 第二签留待 countersign
    }

    // ── countersign ──
    private StockIn pendingPoison() {
        StockIn rec = new StockIn();
        rec.setId(9L);
        rec.setChemicalId(5L);
        rec.setLotNo("LOT-JD-001");
        rec.setQty(new BigDecimal("2"));
        rec.setCabinetId(6L);
        rec.setReceiverId(RECEIVER);
        rec.setStatus(0);
        return rec;
    }

    @Test
    void countersign_入库单不存在拒绝() {
        when(stockInMapper.selectById(9L)).thenReturn(null);
        assertEquals(4101, assertThrows(BizException.class,
                () -> service.countersign(9L, COLLEGE, COLLEGE)).getCode());
    }

    @Test
    void countersign_非待验收状态拒绝() {
        StockIn rec = pendingPoison();
        rec.setStatus(1);
        when(stockInMapper.selectById(9L)).thenReturn(rec);
        assertEquals(4101, assertThrows(BizException.class,
                () -> service.countersign(9L, COLLEGE, COLLEGE)).getCode());
    }

    @Test
    void countersign_与收货人同人拒绝() {
        when(stockInMapper.selectById(9L)).thenReturn(pendingPoison());
        permGranted();
        assertEquals(4201, assertThrows(BizException.class,
                () -> service.countersign(9L, RECEIVER, RECEIVER)).getCode());
    }

    @Test
    void countersign_第二签无学院权限拒绝() {
        when(stockInMapper.selectById(9L)).thenReturn(pendingPoison());
        when(reqMapper.countPerm(COLLEGE, StockInService.PERM_COLLEGE_AUDIT)).thenReturn(0);
        assertEquals(4202, assertThrows(BizException.class,
                () -> service.countersign(9L, COLLEGE, COLLEGE)).getCode());
        verify(chemMapper, never()).insertBatch(any(ChemBatch.class));
    }

    @Test
    void countersign_第二签落账全链() {
        StockIn rec = pendingPoison();
        when(stockInMapper.selectById(9L)).thenReturn(rec);
        permGranted();
        when(chemMapper.selectById(5L)).thenReturn(chem(4));
        when(stockInMapper.complete(9L, COLLEGE)).thenReturn(1);

        service.countersign(9L, COLLEGE, COLLEGE);

        verify(chemMapper).insertBatch(any(ChemBatch.class));
        verify(chemMapper).changeStock(5L, new BigDecimal("2"));
        verify(stockInMapper).complete(9L, COLLEGE);
    }

    @Test
    void countersign_累计超申购拒绝() {
        StockIn rec = pendingPoison();
        rec.setReqId(7L);
        when(stockInMapper.selectById(9L)).thenReturn(rec);
        permGranted();
        when(chemMapper.selectById(5L)).thenReturn(chem(4));
        when(reqMapper.selectById(7L)).thenReturn(purchaseReq()); // CAS 与 chem(丙酮) 一致
        when(reqMapper.incrInQty(7L, new BigDecimal("2"))).thenReturn(0);
        assertEquals(4402, assertThrows(BizException.class,
                () -> service.countersign(9L, COLLEGE, COLLEGE)).getCode());
        verify(stockInMapper, never()).complete(anyLong(), anyLong());
    }

    // ── reject ──
    @Test
    void reject_拒因必填() {
        assertEquals(4101, assertThrows(BizException.class,
                () -> service.reject(9L, " ", RECEIVER)).getCode());
        verify(stockInMapper, never()).reject(anyLong(), anyString());
    }

    @Test
    void reject_仅待验收可拒收() {
        when(stockInMapper.reject(9L, "包装破损")).thenReturn(0);
        StockIn done = pendingPoison();
        done.setStatus(1);
        when(stockInMapper.selectById(9L)).thenReturn(done);
        assertEquals(4101, assertThrows(BizException.class,
                () -> service.reject(9L, "包装破损", RECEIVER)).getCode());
    }

    @Test
    void reject_合法拒收不建批次不回写申购() {
        when(stockInMapper.reject(9L, "包装破损")).thenReturn(1);
        service.reject(9L, "包装破损", RECEIVER);
        verify(stockInMapper).reject(9L, "包装破损");
        verify(chemMapper, never()).insertBatch(any(ChemBatch.class));
        verify(reqMapper, never()).incrInQty(anyLong(), any());
    }

    // ── 编码 ──
    @Test
    void 编码_RK年月日序() {
        String day = LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
        when(stockInMapper.countToday("RK-" + day + "-")).thenReturn(2);
        assertEquals("RK-" + day + "-003", service.nextCode());
    }

    @Test
    void 编码_台账含类别码与四位序() {
        when(chemMapper.countByCodePrefix("HG-JD-2026-")).thenReturn(0);
        assertTrue(service.nextChemCode(4).endsWith("-0001"));
        assertEquals(4001, assertThrows(BizException.class, () -> service.nextChemCode(9)).getCode());
    }
}
