package com.qingyan.hazmat.hazmat.mapper;

import com.qingyan.hazmat.hazmat.entity.WasteDisposal;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/** 废弃物处置单持久层（状态推进一律条件更新，affected=0 即拒——防并发击穿）。 */
public interface WasteDisposalMapper {

    int insert(WasteDisposal disposal);

    WasteDisposal selectById(@Param("id") Long id);

    /** 处置单详情联表版（H2 打印闭环）：带化学品编码/CAS/分类/规格/单位、批次号、经办人/审批人姓名。 */
    WasteDisposal selectDetailById(@Param("id") Long id);

    /** 当日已有序号（编码 FQ-年月日-NNN 生成用）。 */
    int countToday(@Param("dayPrefix") String dayPrefix);

    /** 通过：待审批→已处置（条件更新锁定状态，回写审批人与完成时间；销账在服务层同事务）。 */
    int approve(@Param("id") Long id, @Param("approverId") Long approverId);

    /** 驳回：待审批→已驳回终态（条件更新）。 */
    int reject(@Param("id") Long id, @Param("approverId") Long approverId);

    // ── 列表分页 / 完成登记 / 待办 / 大屏（控制器层扩展）──

    /** 审批前置登记：落 disposal_target / voucher_no（完成合规信息，控制器校验后写）。 */
    int updateCompletion(@Param("id") Long id,
                         @Param("disposalTarget") String disposalTarget,
                         @Param("voucherNo") String voucherNo);

    /** 处置单列表分页：status 可空；keyword 匹配编码（LEFT JOIN 补化学品名/处置人昵称）。 */
    List<WasteDisposal> selectPage(@Param("keyword") String keyword,
                                   @Param("status") Integer status,
                                   @Param("offset") int offset,
                                   @Param("size") int size);

    long countPage(@Param("keyword") String keyword,
                   @Param("status") Integer status);

    /** 待审处置单（status=0，审批中心聚合）。 */
    long countPending();

    /** 待审处置单列表（近 20 条，审批中心聚合）。 */
    List<WasteDisposal> selectPending();

    /** 大屏处置统计：按处置类型分组（单数+Σ处置量）。 */
    List<Map<String, Object>> statsByType();
}
