package com.qingyan.hazmat.hazmat.mapper;

import com.qingyan.hazmat.hazmat.entity.Requisition;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;

/** 领用单持久层（守卫一律条件更新，affected=0 即拒——防并发击穿）。 */
public interface RequisitionMapper {

    int insert(Requisition requisition);

    Requisition selectById(@Param("id") Long id);

    /** 当日已有序号（编码 LY-年月日-NNN 生成用）。 */
    int countToday(@Param("dayPrefix") String dayPrefix);

    /** 关联申购下已出库领用累计（超领 4403 对账口径，status 2 已出库/4 归还完毕或核销关闭）。 */
    BigDecimal sumQtyByReq(@Param("reqId") Long reqId);

    /** 导师通过：待导师 0→1 并落 advisor_id/advisor_time（条件更新）。 */
    int approveAdvisor(@Param("id") Long id, @Param("advisorId") Long advisorId);

    /** 驳回（导师签/复核签通用）：fromStatus→3 终态（条件更新）。 */
    int reject(@Param("id") Long id, @Param("fromStatus") int fromStatus);

    /** 复核通过出库：1→2 并落 checker_id/out_time（条件更新，FEFO 扣减之后调用）。 */
    int markOut(@Param("id") Long id, @Param("checkerId") Long checkerId);

    /** 累计归还对账（条件更新 4602：return_qty+:qty ≤ qty 且 status=2）。 */
    int incrReturnQty(@Param("id") Long id, @Param("qty") BigDecimal qty);

    /** 归还完毕自动关闭（条件更新：status=2 且 return_qty≥qty）。 */
    int complete(@Param("id") Long id);

    /** 核销关闭：status=2 且仅申请人本人（条件更新）。 */
    int close(@Param("id") Long id, @Param("applicantId") Long applicantId);

    // ── 列表分页 / 待办（控制器层扩展，LEFT JOIN 补化学品名/申请人昵称）──

    /** 领用列表分页：applicantId(mine)/status 可空；todo=true → status IN (0,1) 全量。 */
    List<Requisition> selectPage(@Param("applicantId") Long applicantId,
                                 @Param("status") Integer status,
                                 @Param("todo") boolean todo,
                                 @Param("offset") int offset,
                                 @Param("size") int size);

    long countPage(@Param("applicantId") Long applicantId,
                   @Param("status") Integer status,
                   @Param("todo") boolean todo);

    /** 待复核领用（status IN (0,1)，审批中心聚合）。 */
    long countPending();

    /** 待复核领用列表（近 20 条，审批中心聚合）。 */
    List<Requisition> selectPending();
}
