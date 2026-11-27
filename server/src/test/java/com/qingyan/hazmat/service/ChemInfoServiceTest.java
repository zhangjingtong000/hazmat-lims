package com.qingyan.hazmat.service;

import com.qingyan.hazmat.common.BizException;
import com.qingyan.hazmat.hazmat.entity.ChemInfo;
import com.qingyan.hazmat.hazmat.mapper.ChemInfoMapper;
import com.qingyan.hazmat.hazmat.service.ApprovalChainService;
import com.qingyan.hazmat.hazmat.service.ChemInfoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.Year;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 台账管理单测：CAS 查重挂既有台账（不抛错）+ 编码生成（HG-<类别码>-年-4位序）+
 * 类别守卫 4107（在库批次/Σ余量）+ 主键性字段保护（stock_qty/cas_no 不经 update 变更）+
 * 逻辑删除三条件（简化口径）+ 封存/解封仅 0↔1。
 * 约定：管理员=900；台账 id=10。
 */
class ChemInfoServiceTest {

    private static final long ADMIN = 900L;
    private static final long ID = 10L;

    private ChemInfoMapper chemMapper;
    private ChemInfoService service;

    @BeforeEach
    void setUp() {
        chemMapper = Mockito.mock(ChemInfoMapper.class);
        service = new ChemInfoService(chemMapper, new ApprovalChainService());
    }

    private ChemInfo info(int category) {
        ChemInfo c = new ChemInfo();
        c.setId(ID);
        c.setName("氰化钾");
        c.setCasNo("151-50-8");
        c.setCategory(category);
        c.setStockQty(new BigDecimal("5"));
        c.setStatus(0);
        c.setDeleted(0);
        return c;
    }

    // ── create ──
    @Test
    void create_CAS命中挂既有台账不建新档() {
        ChemInfo existing = info(4);
        existing.setId(77L);
        when(chemMapper.selectByCas("151-50-8")).thenReturn(existing);
        assertSame(existing, service.create(info(4))); // 返回既有台账，提示不阻断
        verify(chemMapper, never()).insert(any(ChemInfo.class));
    }

    @Test
    void create_缺名称拒绝() {
        ChemInfo c = info(4);
        c.setName(" ");
        assertEquals(4101, assertThrows(BizException.class, () -> service.create(c)).getCode());
    }

    @Test
    void create_未知分类拒绝() {
        assertEquals(4001, assertThrows(BizException.class, () -> service.create(info(9))).getCode());
    }

    @Test
    void create_合法生成编码与初始态() {
        ChemInfo c = info(2); // 易制毒 → YZ
        c.setCasNo(null); // 无 CAS 亦建档（不查重）
        c.setStockQty(null);
        c.setStatus(null);
        when(chemMapper.countByCodePrefix("HG-YZ-" + Year.now().getValue() + "-")).thenReturn(2);
        service.create(c);
        assertEquals("HG-YZ-" + Year.now().getValue() + "-0003", c.getCode()); // count+1
        assertEquals(BigDecimal.ZERO, c.getStockQty());
        assertEquals(0, c.getStatus());
        verify(chemMapper).insert(any(ChemInfo.class));
    }

    // ── update：类别守卫 4107 ──
    @Test
    void update_有在库批次禁改类别() {
        ChemInfo existing = info(1);
        when(chemMapper.selectById(ID)).thenReturn(existing);
        when(chemMapper.countActiveBatches(ID)).thenReturn(3);
        ChemInfo req = info(4); // 试图把普通危化品改成剧毒
        assertEquals(4107, assertThrows(BizException.class, () -> service.update(req, ADMIN)).getCode());
        verify(chemMapper, never()).update(any(ChemInfo.class));
    }

    @Test
    void update_余量残留同样禁改类别() {
        ChemInfo existing = info(1);
        when(chemMapper.selectById(ID)).thenReturn(existing);
        when(chemMapper.countActiveBatches(ID)).thenReturn(0); // 无在库批次
        when(chemMapper.sumBatchLeft(ID)).thenReturn(new BigDecimal("0.5")); // 但余量残留（封存批）
        assertEquals(4107, assertThrows(BizException.class, () -> service.update(info(4), ADMIN)).getCode());
    }

    @Test
    void update_未改类别不触发守卫() {
        ChemInfo existing = info(4);
        when(chemMapper.selectById(ID)).thenReturn(existing);
        when(chemMapper.countActiveBatches(ID)).thenReturn(5); // 有批次但类别未变，放行
        ChemInfo req = info(4);
        req.setAlias("KCN");
        service.update(req, ADMIN);
        verify(chemMapper).update(any(ChemInfo.class));
    }

    @Test
    void update_主键性字段不被覆盖() {
        ChemInfo existing = info(4);
        existing.setCode("HG-JD-2026-0001");
        existing.setStockQty(new BigDecimal("5"));
        when(chemMapper.selectById(ID)).thenReturn(existing);
        ChemInfo req = info(4);
        req.setCode("HG-JD-1999-9999");       // 试图改编码
        req.setStockQty(BigDecimal.TEN);      // 试图改库存
        req.setCasNo("7777-77-7");            // 试图改 CAS
        req.setStatus(1);                     // 试图经 update 改状态
        service.update(req, ADMIN);
        assertEquals("HG-JD-2026-0001", req.getCode());
        assertEquals(new BigDecimal("5"), req.getStockQty());
        assertEquals("151-50-8", req.getCasNo());
        assertEquals(0, req.getStatus());
    }

    // ── remove：逻辑删除三条件（简化口径） ──
    @Test
    void remove_状态非清空拒绝() {
        when(chemMapper.selectById(ID)).thenReturn(info(4)); // status=0
        assertEquals(4101, assertThrows(BizException.class, () -> service.remove(ID, ADMIN)).getCode());
    }

    @Test
    void remove_仍有在库批次拒绝() {
        ChemInfo existing = info(4);
        existing.setStatus(2); // 已清空
        when(chemMapper.selectById(ID)).thenReturn(existing);
        when(chemMapper.countActiveBatches(ID)).thenReturn(1);
        assertEquals(4101, assertThrows(BizException.class, () -> service.remove(ID, ADMIN)).getCode());
        verify(chemMapper, never()).markDeleted(anyLong());
    }

    @Test
    void remove_三条件满足逻辑删除() {
        ChemInfo existing = info(4);
        existing.setStatus(2);
        when(chemMapper.selectById(ID)).thenReturn(existing);
        when(chemMapper.countActiveBatches(ID)).thenReturn(0);
        when(chemMapper.sumBatchLeft(ID)).thenReturn(BigDecimal.ZERO);
        service.remove(ID, ADMIN);
        verify(chemMapper).markDeleted(ID);
    }

    // ── setStatus：仅 0↔1 ──
    @Test
    void setStatus_非法目标状态拒绝() {
        when(chemMapper.selectById(ID)).thenReturn(info(4)); // status=0
        assertEquals(4101, assertThrows(BizException.class, () -> service.setStatus(ID, 2, ADMIN)).getCode());
    }

    @Test
    void setStatus_状态未变化拒绝() {
        when(chemMapper.selectById(ID)).thenReturn(info(4)); // status=0，再封存一次
        assertEquals(4101, assertThrows(BizException.class, () -> service.setStatus(ID, 0, ADMIN)).getCode());
    }

    @Test
    void setStatus_合法封存与解封() {
        when(chemMapper.selectById(ID)).thenReturn(info(4));
        service.setStatus(ID, 1, ADMIN); // 在管→封存
        verify(chemMapper).updateStatus(ID, 1);
        ChemInfo sealed = info(4);
        sealed.setStatus(1);
        when(chemMapper.selectById(ID)).thenReturn(sealed);
        service.setStatus(ID, 0, ADMIN); // 封存→解封
        verify(chemMapper).updateStatus(ID, 0);
    }
}
