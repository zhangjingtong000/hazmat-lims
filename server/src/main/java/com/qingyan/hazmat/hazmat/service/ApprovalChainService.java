package com.qingyan.hazmat.hazmat.service;

import java.util.List;
import java.util.Map;

import com.qingyan.hazmat.common.BizException;

/**
 * R1 分级多级审批链：申购单的审批节点序列由化学品分类决定，链由本表集中定义。
 * 审批深度随风险等级递增——普通化学品 1 级、普通危化品 2 级、易制毒/易制爆 3 级、
 * 剧毒 4 级（对标高校实务：剧毒类须经校级监管签批，见需求边界 R1）。
 * 规则阅读、修改与测试都只发生在这一处，控制器与持久层不承载审批链知识。
 */
@org.springframework.stereotype.Service
public class ApprovalChainService {

    /** 化学品分类（台账 category 字段口径）。 */
    public static final int COMMON = 0;        // 普通化学品
    public static final int HAZCHEM = 1;       // 普通危险化学品
    public static final int PRECURSOR = 2;     // 易制毒
    public static final int EXPLO_PRE = 3;     // 易制爆
    public static final int LETHAL = 4;        // 剧毒

    /** 审批节点：seq 从 1 连续递增；roleKey 对应角色表中审批角色的权限标识。 */
    public record ApprovalNode(int seq, String nodeKey, String nodeName, String roleKey) {}

    private static final ApprovalNode ADVISOR = new ApprovalNode(1, "advisor", "导师审批", "hzm:purchase:audit-advisor");
    private static final ApprovalNode SAFETY = new ApprovalNode(2, "safety", "安全员审批", "hzm:purchase:audit-safety");
    private static final ApprovalNode COLLEGE = new ApprovalNode(3, "college", "学院审批", "hzm:purchase:audit-college");
    private static final ApprovalNode CAMPUS = new ApprovalNode(4, "campus", "校级审批", "hzm:purchase:audit-campus");

    private static final Map<Integer, List<ApprovalNode>> CHAINS = Map.of(
            COMMON, List.of(ADVISOR),
            HAZCHEM, List.of(ADVISOR, SAFETY),
            PRECURSOR, List.of(ADVISOR, SAFETY, COLLEGE),
            EXPLO_PRE, List.of(ADVISOR, SAFETY, COLLEGE),
            LETHAL, List.of(ADVISOR, SAFETY, COLLEGE, CAMPUS)
    );

    /** 指定分类的完整审批链（不可变视图）。未知分类直接拒绝。 */
    public List<ApprovalNode> chainFor(int category) {
        List<ApprovalNode> chain = CHAINS.get(category);
        if (chain == null) {
            throw new BizException(4001, "未知的化学品分类：" + category);
        }
        return chain;
    }

    /** 首个待审批节点（申购单提交后从它开始）。 */
    public ApprovalNode firstNode(int category) {
        return chainFor(category).get(0);
    }

    /** 第 seq 级通过后的下一节点；已到末级返回 null（审批链走完）。 */
    public ApprovalNode nextAfter(int category, int seq) {
        List<ApprovalNode> chain = chainFor(category);
        if (seq < 1 || seq > chain.size()) {
            throw new BizException(4002, "审批级次越界：" + seq);
        }
        return seq < chain.size() ? chain.get(seq) : null;
    }

    /** 校验某级次是否属于该分类的链（越级审批的静态判定之一半，另一半是流转状态）。 */
    public boolean containsSeq(int category, int seq) {
        return chainFor(category).stream().anyMatch(n -> n.seq() == seq);
    }
}
