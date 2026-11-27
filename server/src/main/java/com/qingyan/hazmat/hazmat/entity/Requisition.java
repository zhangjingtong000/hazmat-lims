package com.qingyan.hazmat.hazmat.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** requisition（schema.sql 同源生成）。 */
public class Requisition {

    private Long id;

    /** LY-年月日-3位序 */
    private String code;

    private Long chemicalId;

    private java.math.BigDecimal qty;

    private String purpose;

    private String experiment;

    private Long applicantId;

    /** 关联已批申购（免导师签） */
    private Long reqId;

    /** 导师签（非关联且 category≥1 必签） */
    private Long advisorId;

    @com.fasterxml.jackson.annotation.JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime advisorTime;

    /** 复核人（≠申请人，持 safety+ 角色） */
    private Long checkerId;

    /** 累计已还（条件更新对账 4602） */
    private java.math.BigDecimal returnQty;

    /** 归还时限（超期未还预警） */
    @com.fasterxml.jackson.annotation.JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate expectReturn;

    /** 0待导师/1待复核/2已出库/3驳回/4归还完毕关闭 */
    private Integer status;

    @com.fasterxml.jackson.annotation.JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @com.fasterxml.jackson.annotation.JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime outTime;

    /** 列表展示字段（LEFT JOIN chem_info，非表列） */
    private String chemicalName;

    /** 列表展示字段（LEFT JOIN sys_user，非表列） */
    private String applicantName;

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

    public Long getChemicalId() {
        return chemicalId;
    }

    public void setChemicalId(Long chemicalId) {
        this.chemicalId = chemicalId;
    }

    public java.math.BigDecimal getQty() {
        return qty;
    }

    public void setQty(java.math.BigDecimal qty) {
        this.qty = qty;
    }

    public String getPurpose() {
        return purpose;
    }

    public void setPurpose(String purpose) {
        this.purpose = purpose;
    }

    public String getExperiment() {
        return experiment;
    }

    public void setExperiment(String experiment) {
        this.experiment = experiment;
    }

    public Long getApplicantId() {
        return applicantId;
    }

    public void setApplicantId(Long applicantId) {
        this.applicantId = applicantId;
    }

    public Long getReqId() {
        return reqId;
    }

    public void setReqId(Long reqId) {
        this.reqId = reqId;
    }

    public Long getAdvisorId() {
        return advisorId;
    }

    public void setAdvisorId(Long advisorId) {
        this.advisorId = advisorId;
    }

    public java.time.LocalDateTime getAdvisorTime() {
        return advisorTime;
    }

    public void setAdvisorTime(java.time.LocalDateTime advisorTime) {
        this.advisorTime = advisorTime;
    }

    public Long getCheckerId() {
        return checkerId;
    }

    public void setCheckerId(Long checkerId) {
        this.checkerId = checkerId;
    }

    public java.math.BigDecimal getReturnQty() {
        return returnQty;
    }

    public void setReturnQty(java.math.BigDecimal returnQty) {
        this.returnQty = returnQty;
    }

    public java.time.LocalDate getExpectReturn() {
        return expectReturn;
    }

    public void setExpectReturn(java.time.LocalDate expectReturn) {
        this.expectReturn = expectReturn;
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

    public java.time.LocalDateTime getOutTime() {
        return outTime;
    }

    public void setOutTime(java.time.LocalDateTime outTime) {
        this.outTime = outTime;
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

}
