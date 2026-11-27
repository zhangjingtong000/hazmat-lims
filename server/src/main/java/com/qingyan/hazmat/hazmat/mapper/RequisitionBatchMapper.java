package com.qingyan.hazmat.hazmat.mapper;

import com.qingyan.hazmat.hazmat.entity.RequisitionBatch;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 领用批次明细持久层（FEFO 分配结果；改派=条件更新行，幂等守卫见服务层 ⑦-4）。 */
public interface RequisitionBatchMapper {

    int insert(RequisitionBatch row);

    List<RequisitionBatch> selectByReq(@Param("reqId") Long reqId);

    RequisitionBatch selectRow(@Param("reqId") Long reqId, @Param("batchId") Long batchId);

    /** 明细行计数（FEFO 落明细 seq_no 递增用）。 */
    int countByReq(@Param("reqId") Long reqId);

    /** 改派（⑦-4）：条件更新 WHERE req_id+原批，affected=0 即已被并发改派（防双回加幻影余量）。 */
    int reassign(@Param("reqId") Long reqId, @Param("originalBatchId") Long originalBatchId,
                 @Param("newBatchId") Long newBatchId);
}
