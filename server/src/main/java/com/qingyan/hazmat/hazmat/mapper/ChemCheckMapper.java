package com.qingyan.hazmat.hazmat.mapper;

import com.qingyan.hazmat.hazmat.entity.ChemCheck;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 账物核对单持久层（盘点/调账两段分离：finish 仅结单，调账在差异行侧）。 */
public interface ChemCheckMapper {

    int insert(ChemCheck check);

    ChemCheck selectById(@Param("id") Long id);

    /** 当日已有序号（编码 PD-年月日-NNN 生成用）。 */
    int countToday(@Param("dayPrefix") String dayPrefix);

    /** 结单：盘点中→平/不平（条件更新锁定 result=0，回写差异计数与盘点时间；不动库存）。 */
    int finish(@Param("id") Long id, @Param("diffCount") int diffCount, @Param("result") int result);

    // ── 列表分页（控制器层扩展，LEFT JOIN 补两名盘点人昵称）──

    /** 盘点单列表分页：result 可空。 */
    List<ChemCheck> selectPage(@Param("result") Integer result,
                               @Param("offset") int offset,
                               @Param("size") int size);

    long countPage(@Param("result") Integer result);
}
