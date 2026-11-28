package com.qingyan.hazmat.hazmat.mapper;

import com.qingyan.hazmat.hazmat.entity.ChemCheckDiff;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 盘点差异明细持久层（差异记录与调账落账分离；book_qty 为批次余量快照）。 */
public interface ChemCheckDiffMapper {

    int insert(ChemCheckDiff diff);

    ChemCheckDiff selectById(@Param("id") Long id);

    /** 某盘点单的差异明细（按插入序，详情页数据源）。 */
    List<ChemCheckDiff> selectByCheck(@Param("checkId") Long checkId);

    /** 差异行备注（"需重盘"标记——adjust 与批次封存/销账竞态时留痕）。 */
    int setRemark(@Param("id") Long id, @Param("remark") String remark);

    /** 调账落账：写 adjust_time（一次性，防重复调账）。 */
    int markAdjusted(@Param("id") Long id);
}
