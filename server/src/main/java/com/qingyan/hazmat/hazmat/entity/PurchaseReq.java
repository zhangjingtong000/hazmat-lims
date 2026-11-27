package com.qingyan.hazmat.hazmat.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** purchase_req（schema.sql 同源生成）。 */
public class PurchaseReq {

    private Long id;

    /** AP-年月日-3位序 */
    private String code;

    private Long applicantId;

    /** 提交时指定导师（导师节点指定制） */
    private Long advisorId;

    /** 申购为新品快照，入库时才建台账 */
    private String chemicalName;

    private String casNo;

    private Integer category;

    private String spec;

    private String unit;

    private java.math.BigDecimal qty;

    private String purpose;

    private String experiment;

    /** 0草稿/1审批中/2已批/3驳回/4已入库关闭/5已作废 */
    private Integer status;

    /** 当前待审批级 */
    private Integer currentSeq;

    /** 累计已入（≤qty 守卫 4402） */
    private java.math.BigDecimal inQty;

    @com.fasterxml.jackson.annotation.JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @com.fasterxml.jackson.annotation.JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime auditTime;

    /** 列表展示字段（LEFT JOIN sys_user，非表列） */
    private String applicantName;

    /** 列表展示字段（LEFT JOIN sys_user，非表列） */
    private String advisorName;

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

    public Long getApplicantId() {
        return applicantId;
    }

    public void setApplicantId(Long applicantId) {
        this.applicantId = applicantId;
    }

    public Long getAdvisorId() {
        return advisorId;
    }

    public void setAdvisorId(Long advisorId) {
        this.advisorId = advisorId;
    }

    public String getChemicalName() {
        return chemicalName;
    }

    public void setChemicalName(String chemicalName) {
        this.chemicalName = chemicalName;
    }

    public String getCasNo() {
        return casNo;
    }

    public void setCasNo(String casNo) {
        this.casNo = casNo;
    }

    public Integer getCategory() {
        return category;
    }

    public void setCategory(Integer category) {
        this.category = category;
    }

    public String getSpec() {
        return spec;
    }

    public void setSpec(String spec) {
        this.spec = spec;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
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

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Integer getCurrentSeq() {
        return currentSeq;
    }

    public void setCurrentSeq(Integer currentSeq) {
        this.currentSeq = currentSeq;
    }

    public java.math.BigDecimal getInQty() {
        return inQty;
    }

    public void setInQty(java.math.BigDecimal inQty) {
        this.inQty = inQty;
    }

    public java.time.LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(java.time.LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public java.time.LocalDateTime getAuditTime() {
        return auditTime;
    }

    public void setAuditTime(java.time.LocalDateTime auditTime) {
        this.auditTime = auditTime;
    }

    public String getApplicantName() {
        return applicantName;
    }

    public void setApplicantName(String applicantName) {
        this.applicantName = applicantName;
    }

    public String getAdvisorName() {
        return advisorName;
    }

    public void setAdvisorName(String advisorName) {
        this.advisorName = advisorName;
    }

}
