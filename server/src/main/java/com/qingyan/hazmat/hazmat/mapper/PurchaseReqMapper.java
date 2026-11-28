package com.qingyan.hazmat.hazmat.mapper;

import com.qingyan.hazmat.hazmat.entity.PurchaseReq;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;

/** 申购单持久层（守卫一律条件更新，affected=0 即拒——防并发击穿）。 */
public interface PurchaseReqMapper {

    int insert(PurchaseReq req);

    PurchaseReq selectById(@Param("id") Long id);

    /** 当日已有序号（编码 AP-年月日-NNN 生成用）。 */
    int countToday(@Param("dayPrefix") String dayPrefix);

    /** 提交：草稿→审批中（条件更新，仅本人草稿）。 */
    int markSubmitted(@Param("id") Long id, @Param("applicantId") Long applicantId,
                      @Param("currentSeq") Integer currentSeq);

    /** 通过推进：审批中→下一级或已批（条件更新锁定状态与当前级，防并发审批）。 */
    int advance(@Param("id") Long id, @Param("fromSeq") Integer fromSeq,
                @Param("toSeq") Integer toSeq);

    /** 驳回：审批中→驳回终态（条件更新）。 */
    int reject(@Param("id") Long id, @Param("currentSeq") Integer currentSeq);

    /** 作废：草稿或已批且未入库，仅申请人本人（条件更新四守卫合一）。 */
    int voidReq(@Param("id") Long id, @Param("applicantId") Long applicantId,
                @Param("inQty") BigDecimal inQty);

    /** 关联申购累计已入（条件更新对账 4402：in_qty+:qty ≤ qty）。 */
    int incrInQty(@Param("id") Long id, @Param("qty") BigDecimal qty);

    /** 审批人是否持有指定权限标识（角色→菜单三级联查）。 */
    int countPerm(@Param("userId") Long userId, @Param("perm") String perm);

    // ── 列表分页 / 待办 / 看板（控制器层扩展，LEFT JOIN 补申请人/导师昵称）──

    /** 申购列表分页：keyword 匹配编码/化学品名；applicantId/status 可空（mine/todo 由控制器组合）。 */
    List<PurchaseReq> selectPage(@Param("keyword") String keyword,
                                 @Param("applicantId") Long applicantId,
                                 @Param("status") Integer status,
                                 @Param("offset") int offset,
                                 @Param("size") int size);

    long countPage(@Param("keyword") String keyword,
                   @Param("applicantId") Long applicantId,
                   @Param("status") Integer status);

    /** 待审申购（status=1，审批中心聚合）。 */
    long countPending();

    /** 待审申购列表（近 20 条，审批中心聚合）。 */
    List<PurchaseReq> selectPending();

    /** 看板第四类预警：已批（status=2）且 audit_time 距今超 days 天（排除已作废 5）。 */
    long countApprovedOverdue(@Param("days") int days);
}
