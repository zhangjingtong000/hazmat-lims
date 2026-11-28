package com.qingyan.hazmat.hazmat.mapper;

import com.qingyan.hazmat.hazmat.entity.ApprovalRecord;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/** 审批记录持久层。 */
public interface ApprovalRecordMapper {

    /** 插入审批结论（DB 层 UNIQUE(biz_type,biz_id,seq) 防并发重复，冲突由服务层转 4103）。 */
    int insert(ApprovalRecord record);

    /** 某业务单的审批链（按级序）。 */
    List<ApprovalRecord> selectByBiz(@Param("bizType") int bizType, @Param("bizId") Long bizId);

    /** 指定级次是否已有结论（UNIQUE 的应用层前置查询）。 */
    ApprovalRecord selectBySeq(@Param("bizType") int bizType, @Param("bizId") Long bizId,
                               @Param("seq") int seq);

    /**
     * 我的已办：按签批人聚合最近审批结论——approval_record（biz_type 1申购/2废弃）
     * UNION requisition 导师/复核双签（领用双签落主单字段，非 approval_record），
     * 联表带单据编号与品名；按时间倒序取前 limit 条。
     */
    List<Map<String, Object>> selectDone(@Param("approverId") long approverId, @Param("limit") int limit);
}
