package com.qingyan.hazmat.hazmat.mapper;

import com.qingyan.hazmat.hazmat.entity.ChemBatch;
import com.qingyan.hazmat.hazmat.entity.ChemInfo;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** 台账 + 批次持久层（全部条件原子更新，守恒口径见 README「账物守恒」）。 */
public interface ChemInfoMapper {

    int insert(ChemInfo info);

    ChemInfo selectById(@Param("id") Long id);

    ChemInfo selectByCas(@Param("casNo") String casNo);

    int update(ChemInfo info);

    /** 逻辑删除三条件（status=2+无在库批次+无未完成单据）在服务层判定后置 deleted=1。 */
    int markDeleted(@Param("id") Long id);

    /** 封存/解封（台账级，4504 源头）。 */
    int updateStatus(@Param("id") Long id, @Param("status") int status);

    /** 台账现存原子增减（delta 可负；负向必须满足 stock_qty+delta>=0）。 */
    int changeStock(@Param("id") Long id, @Param("delta") BigDecimal delta);

    // ── 批次 ──
    int insertBatch(ChemBatch batch);

    ChemBatch selectBatch(@Param("id") Long id);

    /** FEFO 候选：在库且有余量，按效期先后+批号排序。 */
    List<ChemBatch> selectFefoBatches(@Param("chemicalId") Long chemicalId);

    /** 批次余量条件扣减（affected=0 → 余量不足/状态非法，换批或拒）。 */
    int deductBatch(@Param("id") Long id, @Param("qty") BigDecimal qty);

    /** 批次余量条件回加（仅回在库批次）。 */
    int addBatch(@Param("id") Long id, @Param("qty") BigDecimal qty);

    /** 批次状态条件流转（封存/废弃/耗尽）。 */
    int updateBatchStatus(@Param("id") Long id, @Param("from") int from, @Param("to") int to);

    /** 批次改柜（移柜，禁配校验在服务层）。 */
    int moveBatch(@Param("id") Long id, @Param("cabinetId") Long cabinetId,
                  @Param("fromCabinetId") Long fromCabinetId);

    /** 柜内在库批次的化学品相容组（禁配判定数据源，JOIN 批次实际柜位——批次口径）。 */
    List<ChemInfo> selectCabinetMates(@Param("cabinetId") Long cabinetId);

    /** 某化学品 Σ 在库批次余量（守恒断言用）。 */
    BigDecimal sumBatchLeft(@Param("chemicalId") Long chemicalId);

    /** 过期封存批次销账（type=3 废弃：qty_left→0，status→2；封存时台账已扣，不再动台账）。 */
    int wipeBatch(@Param("id") Long id);

    /** 台账编码前缀计数（HG-<类别码>-年-% 生成序号）。 */
    int countByCodePrefix(@Param("prefix") String prefix);

    /** 在库批次计数（逻辑删除守卫用）。 */
    int countActiveBatches(@Param("chemicalId") Long chemicalId);

    // ── 列表分页 / 看板大屏聚合（控制器层扩展）──

    /** 台账列表分页：keyword 匹配编码/名称/CAS；category/status 可空（仅未删除）。 */
    List<ChemInfo> selectPage(@Param("keyword") String keyword,
                              @Param("category") Integer category,
                              @Param("status") Integer status,
                              @Param("offset") int offset,
                              @Param("size") int size);

    long countPage(@Param("keyword") String keyword,
                   @Param("category") Integer category,
                   @Param("status") Integer status);

    /** 批次列表分页：chemicalId 可空（LEFT JOIN 补化学品名/柜位名）。 */
    List<ChemBatch> selectBatchPage(@Param("chemicalId") Long chemicalId,
                                    @Param("offset") int offset,
                                    @Param("size") int size);

    long countBatchPage(@Param("chemicalId") Long chemicalId);

    /** 效期预警数：在库且未清空批次，expiry_date ≤ CURRENT_DATE + 各自台账 expiry_warn_days。 */
    long countExpiryWarn();

    /** 低库存预警数：未删除且 min_stock>0 的台账，stock_qty < min_stock。 */
    long countLowStock();

    /** 台账 ledger/大屏分类分布：按分类分组（台账数+Σ现存）。 */
    List<Map<String, Object>> ledgerByCategory();

    /** 大屏批次状态分布：按批次 status 分组计数。 */
    List<Map<String, Object>> batchStatusDist();
}
