package com.qingyan.hazmat.service;

import com.qingyan.hazmat.common.BizException;
import com.qingyan.hazmat.hazmat.entity.ChemBatch;
import com.qingyan.hazmat.hazmat.entity.ChemCheck;
import com.qingyan.hazmat.hazmat.entity.ChemCheckDiff;
import com.qingyan.hazmat.hazmat.mapper.ChemCheckDiffMapper;
import com.qingyan.hazmat.hazmat.mapper.ChemCheckMapper;
import com.qingyan.hazmat.hazmat.mapper.ChemInfoMapper;
import com.qingyan.hazmat.hazmat.mapper.PurchaseReqMapper;
import com.qingyan.hazmat.hazmat.service.ApprovalChainService;
import com.qingyan.hazmat.hazmat.service.CheckService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 账物盘点单测：双人制单 4201 + 结单（盘点人身份 4105 / book 快照 / 平与不平判定 /
 * 不改库存）+ 调账（权限 4105 / 已调账 4101 / 批次封存"需重盘" ⑦-6 / 盘盈盘亏
 * 增量修正与台账同步 / 余量不足 4101）。
 * 约定：盘点人一=301、盘点人二=302、第三人=399、调账员=305；盘点单 id=1、差异行 id=11。
 */
class CheckServiceTest {

    private static final long ONE = 301L;
    private static final long TWO = 302L;
    private static final long OTHER = 399L;
    private static final long ADJUSTER = 305L;
    private static final long CHECK_ID = 1L;
    private static final long DIFF_ID = 11L;
    private static final long BATCH_ID = 20L;
    private static final long CHEM_ID = 10L;

    private ChemCheckMapper checkMapper;
    private ChemCheckDiffMapper diffMapper;
    private ChemInfoMapper chemMapper;
    private PurchaseReqMapper reqMapper;
    private CheckService service;

    @BeforeEach
    void setUp() {
        checkMapper = Mockito.mock(ChemCheckMapper.class);
        diffMapper = Mockito.mock(ChemCheckDiffMapper.class);
        chemMapper = Mockito.mock(ChemInfoMapper.class);
        reqMapper = Mockito.mock(PurchaseReqMapper.class);
        service = new CheckService(checkMapper, diffMapper, chemMapper, reqMapper, new ApprovalChainService());
    }

    private ChemCheck doing() {
        ChemCheck c = new ChemCheck();
        c.setId(CHECK_ID);
        c.setCheckerOneId(ONE);
        c.setCheckerTwoId(TWO);
        c.setDiffCount(0);
        c.setResult(0); // 盘点中
        return c;
    }

    private ChemBatch batch(int status, String qtyLeft) {
        ChemBatch b = new ChemBatch();
        b.setId(BATCH_ID);
        b.setChemicalId(CHEM_ID);
        b.setStatus(status);
        b.setQtyLeft(new BigDecimal(qtyLeft));
        return b;
    }

    /** 实盘行：盘点人只报 batchId + actualQty（book 由服务按批次余量快照回填）。 */
    private ChemCheckDiff diffRow(long batchId, String actual) {
        ChemCheckDiff d = new ChemCheckDiff();
        d.setBatchId(batchId);
        d.setActualQty(new BigDecimal(actual));
        return d;
    }

    // ── create ──
    @Test
    void create_双人同人拒绝() {
        assertEquals(4201, assertThrows(BizException.class,
                () -> service.create(null, ONE, ONE)).getCode());
    }

    @Test
    void create_未知分类拒绝() {
        assertEquals(4001, assertThrows(BizException.class,
                () -> service.create(9, ONE, TWO)).getCode());
    }

    @Test
    void create_合法落单() {
        ChemCheck c = service.create(null, ONE, TWO);
        assertTrue(c.getCode().startsWith("PD-"));
        assertEquals(0, c.getResult()); // 盘点中
        verify(checkMapper).insert(any(ChemCheck.class));
    }

    // ── finish：守卫与判定 ──
    @Test
    void finish_非本单盘点人拒绝() {
        when(checkMapper.selectById(CHECK_ID)).thenReturn(doing());
        assertEquals(4105, assertThrows(BizException.class,
                () -> service.finish(CHECK_ID, List.of(diffRow(BATCH_ID, "2")), OTHER)).getCode());
    }

    @Test
    void finish_已结单拒绝() {
        ChemCheck c = doing();
        c.setResult(1); // 已平
        when(checkMapper.selectById(CHECK_ID)).thenReturn(c);
        assertEquals(4101, assertThrows(BizException.class,
                () -> service.finish(CHECK_ID, List.of(diffRow(BATCH_ID, "2")), ONE)).getCode());
    }

    @Test
    void finish_有差异_判定不平时序插入() {
        when(checkMapper.selectById(CHECK_ID)).thenReturn(doing());
        when(chemMapper.selectBatch(BATCH_ID)).thenReturn(batch(0, "2"));
        when(checkMapper.finish(CHECK_ID, 1, 2)).thenReturn(1);
        // 一行全平（book=2 实盘 2）+ 一行盘亏（book=2 实盘 1 → diff=-1）
        service.finish(CHECK_ID, List.of(diffRow(BATCH_ID, "2"), diffRow(BATCH_ID, "1")), ONE);
        verify(diffMapper, org.mockito.Mockito.times(2)).insert(any(ChemCheckDiff.class));
        verify(checkMapper).finish(CHECK_ID, 1, 2); // diff_count=1，result=2 不平
        verify(chemMapper, never()).deductBatch(anyLong(), any()); // 不改库存
        verify(chemMapper, never()).addBatch(anyLong(), any());
    }

    @Test
    void finish_全部持平_判定平() {
        when(checkMapper.selectById(CHECK_ID)).thenReturn(doing());
        when(chemMapper.selectBatch(BATCH_ID)).thenReturn(batch(0, "2"));
        when(checkMapper.finish(CHECK_ID, 0, 1)).thenReturn(1);
        service.finish(CHECK_ID, List.of(diffRow(BATCH_ID, "2")), TWO);
        verify(checkMapper).finish(CHECK_ID, 0, 1);
    }

    @Test
    void finish_book取批次当前余量快照() {
        when(checkMapper.selectById(CHECK_ID)).thenReturn(doing());
        when(chemMapper.selectBatch(BATCH_ID)).thenReturn(batch(0, "2"));
        when(checkMapper.finish(anyLong(), org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(1);
        service.finish(CHECK_ID, List.of(diffRow(BATCH_ID, "1")), ONE);
        org.mockito.ArgumentMatcher<ChemCheckDiff> bookSnapshotted =
                d -> new BigDecimal("2").compareTo(d.getBookQty()) == 0
                        && new BigDecimal("-1").compareTo(d.getDiffQty()) == 0;
        org.mockito.Mockito.verify(diffMapper).insert(org.mockito.Mockito.argThat(bookSnapshotted));
    }

    // ── adjust：守卫与增量修正 ──
    private ChemCheckDiff bookedDiff(String diffQty) {
        ChemCheckDiff d = new ChemCheckDiff();
        d.setId(DIFF_ID);
        d.setCheckId(CHECK_ID);
        d.setBatchId(BATCH_ID);
        d.setBookQty(new BigDecimal("2"));
        d.setActualQty(new BigDecimal("1"));
        d.setDiffQty(new BigDecimal(diffQty));
        return d;
    }

    @Test
    void adjust_未持调账权限拒绝() {
        when(reqMapper.countPerm(ADJUSTER, "hzm:check:adjust")).thenReturn(0);
        assertEquals(4105, assertThrows(BizException.class,
                () -> service.adjust(CHECK_ID, DIFF_ID, ADJUSTER)).getCode());
    }

    @Test
    void adjust_已调账拒绝() {
        when(reqMapper.countPerm(anyLong(), any())).thenReturn(1);
        ChemCheckDiff d = bookedDiff("-1");
        d.setAdjustTime(LocalDateTime.now());
        when(diffMapper.selectById(DIFF_ID)).thenReturn(d);
        assertEquals(4101, assertThrows(BizException.class,
                () -> service.adjust(CHECK_ID, DIFF_ID, ADJUSTER)).getCode());
    }

    @Test
    void adjust_批次已被封存_标记需重盘并拒绝() {
        when(reqMapper.countPerm(anyLong(), any())).thenReturn(1);
        when(diffMapper.selectById(DIFF_ID)).thenReturn(bookedDiff("-1"));
        when(chemMapper.selectBatch(BATCH_ID)).thenReturn(batch(3, "2")); // ⑦-6：finish 后被惰性封存
        assertEquals(4101, assertThrows(BizException.class,
                () -> service.adjust(CHECK_ID, DIFF_ID, ADJUSTER)).getCode());
        verify(diffMapper).setRemark(DIFF_ID, "需重盘");
        verify(chemMapper, never()).deductBatch(anyLong(), any());
    }

    @Test
    void adjust_盘亏_条件扣减并同步台账() {
        when(reqMapper.countPerm(anyLong(), any())).thenReturn(1);
        when(diffMapper.selectById(DIFF_ID)).thenReturn(bookedDiff("-1"));
        when(chemMapper.selectBatch(BATCH_ID)).thenReturn(batch(0, "2"));
        when(chemMapper.deductBatch(BATCH_ID, new BigDecimal("1"))).thenReturn(1);
        when(chemMapper.changeStock(CHEM_ID, new BigDecimal("-1"))).thenReturn(1);
        service.adjust(CHECK_ID, DIFF_ID, ADJUSTER);
        verify(chemMapper).deductBatch(BATCH_ID, new BigDecimal("1")); // |diff| 条件扣减
        verify(chemMapper).changeStock(CHEM_ID, new BigDecimal("-1"));
        verify(diffMapper).markAdjusted(DIFF_ID);
    }

    @Test
    void adjust_盘盈_既有批次回加() {
        when(reqMapper.countPerm(anyLong(), any())).thenReturn(1);
        ChemCheckDiff d = bookedDiff("+2");
        d.setDiffQty(new BigDecimal("2"));
        when(diffMapper.selectById(DIFF_ID)).thenReturn(d);
        when(chemMapper.selectBatch(BATCH_ID)).thenReturn(batch(0, "2"));
        when(chemMapper.addBatch(BATCH_ID, new BigDecimal("2"))).thenReturn(1);
        when(chemMapper.changeStock(CHEM_ID, new BigDecimal("2"))).thenReturn(1);
        service.adjust(CHECK_ID, DIFF_ID, ADJUSTER);
        verify(chemMapper).addBatch(BATCH_ID, new BigDecimal("2")); // 盘盈仅限既有批次增余量
        verify(chemMapper).changeStock(CHEM_ID, new BigDecimal("2"));
        verify(chemMapper, never()).deductBatch(anyLong(), any());
    }

    @Test
    void adjust_余量不足调减拒绝() {
        when(reqMapper.countPerm(anyLong(), any())).thenReturn(1);
        when(diffMapper.selectById(DIFF_ID)).thenReturn(bookedDiff("-5"));
        when(chemMapper.selectBatch(BATCH_ID)).thenReturn(batch(0, "2"));
        when(chemMapper.deductBatch(BATCH_ID, new BigDecimal("5"))).thenReturn(0); // 条件更新未命中
        assertEquals(4101, assertThrows(BizException.class,
                () -> service.adjust(CHECK_ID, DIFF_ID, ADJUSTER)).getCode());
        verify(diffMapper, never()).markAdjusted(anyLong());
    }
}
