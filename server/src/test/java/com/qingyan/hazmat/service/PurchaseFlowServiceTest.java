package com.qingyan.hazmat.service;

import com.qingyan.hazmat.common.BizException;
import com.qingyan.hazmat.hazmat.entity.ApprovalRecord;
import com.qingyan.hazmat.hazmat.entity.PurchaseReq;
import com.qingyan.hazmat.hazmat.mapper.ApprovalRecordMapper;
import com.qingyan.hazmat.hazmat.mapper.PurchaseReqMapper;
import com.qingyan.hazmat.hazmat.service.ApprovalChainService;
import com.qingyan.hazmat.hazmat.service.PurchaseFlowService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 申购流转单测：分级审批链 + 混合审批人制（导师指定/持权自取）+ 全部守卫。
 * 约定：申请人=100、指定导师=201、安全员=202、学院=203、校级=204、另一位导师=205。
 * 剧毒单（category=4）走 4 级链；普通危化品（1）走 2 级。
 */
class PurchaseFlowServiceTest {

    private static final long APPLICANT = 100L;
    private static final long ADVISOR = 201L;
    private static final long SAFETY = 202L;
    private static final long COLLEGE = 203L;
    private static final long CAMPUS = 204L;

    private PurchaseReqMapper reqMapper;
    private ApprovalRecordMapper recordMapper;
    private PurchaseFlowService service;

    @BeforeEach
    void setUp() {
        reqMapper = Mockito.mock(PurchaseReqMapper.class);
        recordMapper = Mockito.mock(ApprovalRecordMapper.class);
        service = new PurchaseFlowService(reqMapper, recordMapper, new ApprovalChainService());
    }

    private PurchaseReq req(int category, int status, int currentSeq) {
        PurchaseReq r = new PurchaseReq();
        r.setId(1L);
        r.setApplicantId(APPLICANT);
        r.setAdvisorId(ADVISOR);
        r.setChemicalName("氰化钾");
        r.setCategory(category);
        r.setQty(new BigDecimal("2"));
        r.setStatus(status);
        r.setCurrentSeq(currentSeq);
        r.setInQty(BigDecimal.ZERO);
        return r;
    }

    private void permGranted() {
        when(reqMapper.countPerm(anyLong(), any())).thenReturn(1);
    }

    // ── save ──
    @Test
    void save_缺名称拒绝() {
        PurchaseReq r = req(4, 0, 1);
        r.setChemicalName(" ");
        assertEquals(4101, assertThrows(BizException.class, () -> service.save(r)).getCode());
    }

    @Test
    void save_数量非正拒绝() {
        PurchaseReq r = req(4, 0, 1);
        r.setQty(BigDecimal.ZERO);
        assertEquals(4101, assertThrows(BizException.class, () -> service.save(r)).getCode());
    }

    @Test
    void save_未指定导师拒绝() {
        PurchaseReq r = req(4, 0, 1);
        r.setAdvisorId(null);
        assertEquals(4101, assertThrows(BizException.class, () -> service.save(r)).getCode());
    }

    @Test
    void save_未知分类拒绝() {
        PurchaseReq r = req(9, 0, 1);
        assertEquals(4001, assertThrows(BizException.class, () -> service.save(r)).getCode());
    }

    @Test
    void save_合法落草稿() {
        PurchaseReq draft = req(4, 0, 1);
        draft.setStatus(null);
        assertTrue(service.save(draft).getId() > 0);
        verify(reqMapper).insert(any(PurchaseReq.class));
    }

    // ── submit ──
    @Test
    void submit_非草稿拒绝() {
        when(reqMapper.selectById(1L)).thenReturn(req(4, 2, 1));
        assertEquals(4101, assertThrows(BizException.class, () -> service.submit(1L, APPLICANT)).getCode());
    }

    @Test
    void submit_非本人拒绝() {
        when(reqMapper.selectById(1L)).thenReturn(req(4, 0, 1));
        assertEquals(4104, assertThrows(BizException.class, () -> service.submit(1L, 999L)).getCode());
    }

    @Test
    void submit_条件更新失败拒绝() {
        when(reqMapper.selectById(1L)).thenReturn(req(4, 0, 1));
        when(reqMapper.markSubmitted(1L, APPLICANT, 1)).thenReturn(0);
        assertEquals(4101, assertThrows(BizException.class, () -> service.submit(1L, APPLICANT)).getCode());
    }

    // ── audit：守卫 ──
    @Test
    void audit_驳回缺意见拒绝() {
        when(reqMapper.selectById(1L)).thenReturn(req(4, 1, 1));
        assertEquals(4101, assertThrows(BizException.class,
                () -> service.audit(1L, ADVISOR, false, " ")).getCode());
    }

    @Test
    void audit_申请人自审拒绝() {
        when(reqMapper.selectById(1L)).thenReturn(req(4, 1, 1));
        permGranted();
        assertEquals(4104, assertThrows(BizException.class,
                () -> service.audit(1L, APPLICANT, true, null)).getCode());
    }

    @Test
    void audit_第一级非指定导师拒绝() {
        when(reqMapper.selectById(1L)).thenReturn(req(4, 1, 1));
        permGranted();
        assertEquals(4105, assertThrows(BizException.class,
                () -> service.audit(1L, 205L, true, null)).getCode());
    }

    @Test
    void audit_第一级指定导师但越权仍拒绝() {
        // 指定制通过（201 是单据 advisor），但该用户不持导师审批权限
        when(reqMapper.selectById(1L)).thenReturn(req(4, 1, 1));
        when(reqMapper.countPerm(ADVISOR, "hzm:purchase:audit-advisor")).thenReturn(0);
        assertEquals(4105, assertThrows(BizException.class,
                () -> service.audit(1L, ADVISOR, true, null)).getCode());
    }

    @Test
    void audit_连续两级同人拒绝() {
        PurchaseReq r = req(4, 1, 2);
        when(reqMapper.selectById(1L)).thenReturn(r);
        permGranted();
        ApprovalRecord prev = new ApprovalRecord();
        prev.setSeq(1);
        prev.setApproverId(ADVISOR);
        when(recordMapper.selectBySeq(1, 1L, 1)).thenReturn(prev);
        assertEquals(4203, assertThrows(BizException.class,
                () -> service.audit(1L, ADVISOR, true, null)).getCode());
    }

    @Test
    void audit_重复审批转4103() {
        when(reqMapper.selectById(1L)).thenReturn(req(4, 1, 1));
        permGranted();
        when(recordMapper.insert(any(ApprovalRecord.class))).thenThrow(new DuplicateKeyException("uk"));
        assertEquals(4103, assertThrows(BizException.class,
                () -> service.audit(1L, ADVISOR, true, null)).getCode());
    }

    // ── audit：合法路径 ──
    @Test
    void audit_第一级通过推进到第二级() {
        when(reqMapper.selectById(1L)).thenReturn(req(4, 1, 1));
        permGranted();
        when(reqMapper.advance(anyLong(), anyInt(), anyInt())).thenReturn(1);
        service.audit(1L, ADVISOR, true, null);
        verify(reqMapper).advance(1L, 1, 2);
        verify(recordMapper).insert(any(ApprovalRecord.class));
    }

    @Test
    void audit_剧毒末级通过落已批() {
        PurchaseReq r = req(4, 1, 4);
        when(reqMapper.selectById(1L)).thenReturn(r);
        permGranted();
        ApprovalRecord prev = new ApprovalRecord();
        prev.setSeq(3);
        prev.setApproverId(COLLEGE);
        when(recordMapper.selectBySeq(1, 1L, 3)).thenReturn(prev);
        when(reqMapper.advance(anyLong(), anyInt(), anyInt())).thenReturn(1);
        service.audit(1L, CAMPUS, true, null);
        verify(reqMapper).advance(1L, 4, 0); // toSeq=0 → status=2 已批
    }

    @Test
    void audit_普通危化品两级走完即已批() {
        PurchaseReq r = req(1, 1, 2); // 普通危化品 2 级链
        r.setAdvisorId(ADVISOR);
        when(reqMapper.selectById(1L)).thenReturn(r);
        permGranted();
        ApprovalRecord prev = new ApprovalRecord();
        prev.setSeq(1);
        prev.setApproverId(ADVISOR);
        when(recordMapper.selectBySeq(1, 1L, 1)).thenReturn(prev);
        when(reqMapper.advance(anyLong(), anyInt(), anyInt())).thenReturn(1);
        service.audit(1L, SAFETY, true, null);
        verify(reqMapper).advance(1L, 2, 0);
    }

    @Test
    void audit_驳回进终态且回写意见() {
        when(reqMapper.selectById(1L)).thenReturn(req(4, 1, 2));
        permGranted();
        ApprovalRecord prev = new ApprovalRecord();
        prev.setSeq(1);
        prev.setApproverId(ADVISOR);
        when(recordMapper.selectBySeq(1, 1L, 1)).thenReturn(prev);
        when(reqMapper.reject(1L, 2)).thenReturn(1);
        service.audit(1L, SAFETY, false, "实验依据不足");
        verify(reqMapper).reject(1L, 2);
        verify(recordMapper).insert(any(ApprovalRecord.class));
    }

    @Test
    void audit_推进条件更新失败拒绝() {
        when(reqMapper.selectById(1L)).thenReturn(req(4, 1, 1));
        permGranted();
        when(reqMapper.advance(1L, 1, 2)).thenReturn(0);
        assertEquals(4101, assertThrows(BizException.class,
                () -> service.audit(1L, ADVISOR, true, null)).getCode());
    }

    @Test
    void audit_未持权限的二级审批拒绝() {
        when(reqMapper.selectById(1L)).thenReturn(req(4, 1, 2));
        when(reqMapper.countPerm(SAFETY, "hzm:purchase:audit-safety")).thenReturn(0);
        assertEquals(4105, assertThrows(BizException.class,
                () -> service.audit(1L, SAFETY, true, null)).getCode());
        verify(recordMapper, never()).insert(any(ApprovalRecord.class));
    }

    // ── void ──
    @Test
    void void_条件更新命中即作废() {
        when(reqMapper.voidReq(1L, APPLICANT, BigDecimal.ZERO)).thenReturn(1);
        service.voidReq(1L, APPLICANT);
        verify(reqMapper).voidReq(1L, APPLICANT, BigDecimal.ZERO);
    }

    @Test
    void void_部分入库拒绝() {
        when(reqMapper.voidReq(1L, APPLICANT, BigDecimal.ZERO)).thenReturn(0);
        PurchaseReq r = req(4, 2, 1);
        r.setInQty(new BigDecimal("1"));
        when(reqMapper.selectById(1L)).thenReturn(r);
        assertEquals(4101, assertThrows(BizException.class,
                () -> service.voidReq(1L, APPLICANT)).getCode());
    }

    @Test
    void void_非本人拒绝() {
        when(reqMapper.voidReq(1L, 999L, BigDecimal.ZERO)).thenReturn(0);
        when(reqMapper.selectById(1L)).thenReturn(req(4, 2, 1));
        assertEquals(4104, assertThrows(BizException.class,
                () -> service.voidReq(1L, 999L)).getCode());
    }

    // ── 链配置与分类联动 ──
    @Test
    void 分类决定审批深度_守卫分类合法性() {
        assertEquals(4, new ApprovalChainService().chainFor(4).size());
        List<ApprovalChainService.ApprovalNode> chain = new ApprovalChainService().chainFor(0);
        assertEquals(1, chain.size());
    }
}
