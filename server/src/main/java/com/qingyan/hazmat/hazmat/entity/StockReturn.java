package com.qingyan.hazmat.hazmat.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** stock_return（schema.sql 同源生成）。 */
public class StockReturn {

    private Long id;

    /** GH-年月日-3位序 */
    private String code;

    private Long reqId;

    /** 回原批次（status=0 守卫 4204） */
    private Long batchId;

    private java.math.BigDecimal qty;

    private Long applicantId;

    /** 拒绝原因（return:reject） */
    private String comment;

    private Long confirmerId;

    /** 0待确认/1已回库/2已拒绝 */
    private Integer status;

    @com.fasterxml.jackson.annotation.JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @com.fasterxml.jackson.annotation.JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime returnTime;

    @com.fasterxml.jackson.annotation.JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime confirmTime;

    /** 联表展示字段：化学品品名（selectPending）。 */
    private String chemicalName;

    /** 联表展示字段：申请人昵称（selectPending/selectByReq）。 */
    private String applicantName;

    /** 联表展示字段：确认人昵称（selectByReq）。 */
    private String confirmerName;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
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

    public Long getApplicantId() {
        return applicantId;
    }

    public void setApplicantId(Long applicantId) {
        this.applicantId = applicantId;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public Long getConfirmerId() {
        return confirmerId;
    }

    public void setConfirmerId(Long confirmerId) {
        this.confirmerId = confirmerId;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public java.time.LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(java.time.LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public java.time.LocalDateTime getReturnTime() {
        return returnTime;
    }

    public void setReturnTime(java.time.LocalDateTime returnTime) {
        this.returnTime = returnTime;
    }

    public java.time.LocalDateTime getConfirmTime() {
        return confirmTime;
    }

    public void setConfirmTime(java.time.LocalDateTime confirmTime) {
        this.confirmTime = confirmTime;
    }


    public String getChemicalName() {
        return chemicalName;
    }

    public void setChemicalName(String chemicalName) {
        this.chemicalName = chemicalName;
    }

    public String getApplicantName() {
        return applicantName;
    }

    public void setApplicantName(String applicantName) {
        this.applicantName = applicantName;
    }

    public String getConfirmerName() {
        return confirmerName;
    }

    public void setConfirmerName(String confirmerName) {
        this.confirmerName = confirmerName;
    }
}
