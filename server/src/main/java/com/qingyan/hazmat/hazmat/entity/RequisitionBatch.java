package com.qingyan.hazmat.hazmat.entity;

import java.math.BigDecimal;

/** requisition_batch（schema.sql 同源生成）。 */
public class RequisitionBatch {

    private Long id;

    private Long reqId;

    private Long batchId;

    private java.math.BigDecimal qty;

    private Integer seqNo;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getReqId() {
        return reqId;
    }

    public void setReqId(Long reqId) {
        this.reqId = reqId;
    }

    public Long getBatchId() {
        return batchId;
    }

    public void setBatchId(Long batchId) {
        this.batchId = batchId;
    }

    public java.math.BigDecimal getQty() {
        return qty;
    }

    public void setQty(java.math.BigDecimal qty) {
        this.qty = qty;
    }

    public Integer getSeqNo() {
        return seqNo;
    }

    public void setSeqNo(Integer seqNo) {
        this.seqNo = seqNo;
    }

}
