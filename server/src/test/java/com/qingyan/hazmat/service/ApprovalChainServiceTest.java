package com.qingyan.hazmat.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.qingyan.hazmat.common.BizException;
import com.qingyan.hazmat.hazmat.service.ApprovalChainService;

/**
 * 分级审批链规则的穷举验证：五类化学品 × 链长/链序/边界。
 * 非法路径（未知分类、级次越界）与合法路径同权重覆盖。
 */
class ApprovalChainServiceTest {

    private final ApprovalChainService svc = new ApprovalChainService();

    @Test
    void 普通化学品_单级导师() {
        assertEquals(1, svc.chainFor(ApprovalChainService.COMMON).size());
        assertEquals("导师审批", svc.firstNode(ApprovalChainService.COMMON).nodeName());
    }

    @Test
    void 普通危化品_两级到安全员() {
        assertEquals(2, svc.chainFor(ApprovalChainService.HAZCHEM).size());
        assertEquals("安全员审批", svc.chainFor(ApprovalChainService.HAZCHEM).get(1).nodeName());
    }

    @Test
    void 易制毒_三级到学院() {
        assertEquals(3, svc.chainFor(ApprovalChainService.PRECURSOR).size());
        assertEquals("学院审批", svc.chainFor(ApprovalChainService.PRECURSOR).get(2).nodeName());
    }

    @Test
    void 易制爆_与易制毒同级同构() {
        assertEquals(3, svc.chainFor(ApprovalChainService.EXPLO_PRE).size());
        assertEquals(svc.chainFor(ApprovalChainService.PRECURSOR), svc.chainFor(ApprovalChainService.EXPLO_PRE));
    }

    @Test
    void 剧毒_四级到校级() {
        List<ApprovalChainService.ApprovalNode> chain = svc.chainFor(ApprovalChainService.LETHAL);
        assertEquals(4, chain.size());
        assertEquals("校级审批", chain.get(3).nodeName());
    }

    @Test
    void 级次连续且从1开始() {
        for (int cat : new int[]{0, 1, 2, 3, 4}) {
            List<ApprovalChainService.ApprovalNode> chain = svc.chainFor(cat);
            for (int i = 0; i < chain.size(); i++) {
                assertEquals(i + 1, chain.get(i).seq(), "分类 " + cat + " 第 " + i + " 个节点级次断裂");
            }
        }
    }

    @Test
    void 审批深度随风险等级单调不减() {
        int prev = 0;
        for (int cat : new int[]{0, 1, 2, 4}) {
            assertTrue(svc.chainFor(cat).size() >= prev, "分类 " + cat + " 审批深度未随风险递增");
            prev = svc.chainFor(cat).size();
        }
    }

    @Test
    void 末级之后无下一节点() {
        assertNull(svc.nextAfter(ApprovalChainService.LETHAL, 4));
    }

    @Test
    void 中间级通过后指向下一级() {
        assertEquals("学院审批", svc.nextAfter(ApprovalChainService.LETHAL, 2).nodeName());
    }

    @Test
    void 未知分类被拒绝() {
        BizException e = assertThrows(BizException.class, () -> svc.chainFor(9));
        assertEquals(4001, e.getCode());
    }

    @Test
    void 级次越界被拒绝() {
        BizException e = assertThrows(BizException.class, () -> svc.nextAfter(ApprovalChainService.HAZCHEM, 5));
        assertEquals(4002, e.getCode());
        assertThrows(BizException.class, () -> svc.nextAfter(ApprovalChainService.HAZCHEM, 0));
    }

    @Test
    void 合法级次均在链内_越级不在() {
        assertTrue(svc.containsSeq(ApprovalChainService.LETHAL, 3));
        assertFalse(svc.containsSeq(ApprovalChainService.HAZCHEM, 3));
    }
}
