package com.qingyan.hazmat.hazmat.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** waste_disposal（schema.sql 同源生成）。 */
public class WasteDisposal {

    private Long id;

    /** FQ-年月日-3位序 */
    private String code;

    private Long chemicalId;

    /** type=3 指过期封存批次；type=1/2 指在库批次 */
    private Long batchId;

    /** 1废液/2空瓶/3过期整批 */
    private Integer type;

    private java.math.BigDecimal qty;

    private String method;

    /** 处置去向（完成时必填） */
    private String disposalTarget;

    /** 转移凭证号（剧毒/易制毒/易制爆完成时必填） */
    private String voucherNo;

    private Long operatorId;

    private Long approverId;

    /** 0待审批/1已处置/2已驳回 */
    private Integer status;

    @com.fasterxml.jackson.annotation.JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @com.fasterxml.jackson.annotation.JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime finishTime;

    /** 列表展示字段（LEFT JOIN chem_info，非表列） */
    private String chemicalName;

    /** 列表展示字段（LEFT JOIN sys_user，非表列） */
    private String operatorName;

    /** 详情展示字段（LEFT JOIN chem_info.code，HG 台账编码，非表列） */
    private String chemicalCode;

    /** 详情展示字段（LEFT JOIN chem_info，非表列） */
    private String casNo;

    /** 详情展示字段（LEFT JOIN chem_info，非表列） */
    private String spec;

    /** 详情展示字段（LEFT JOIN chem_info，非表列） */
    private String unit;

    /** 详情展示字段（LEFT JOIN chem_info，非表列） */
    private Integer category;

    /** 详情展示字段（LEFT JOIN chem_batch.lot_no，非表列） */
    private String lotNo;

    /** 详情展示字段（LEFT JOIN sys_user（approver_id），非表列） */
    private String approverName;

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

    public Long getBatchId() {
        return batchId;
    }

    public void setBatchId(Long batchId) {
        this.batchId = batchId;
    }

    public Integer getType() {
        return type;
    }

    public void setType(Integer type) {
        this.type = type;
    }

    public java.math.BigDecimal getQty() {
        return qty;
    }

    public void setQty(java.math.BigDecimal qty) {
        this.qty = qty;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getDisposalTarget() {
        return disposalTarget;
    }

    public void setDisposalTarget(String disposalTarget) {
        this.disposalTarget = disposalTarget;
    }

    public String getVoucherNo() {
        return voucherNo;
    }

    public void setVoucherNo(String voucherNo) {
        this.voucherNo = voucherNo;
    }

    public Long getOperatorId() {
        return operatorId;
    }

    public void setOperatorId(Long operatorId) {
        this.operatorId = operatorId;
    }

    public Long getApproverId() {
        return approverId;
    }

    public void setApproverId(Long approverId) {
        this.approverId = approverId;
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

    public java.time.LocalDateTime getFinishTime() {
        return finishTime;
    }

    public void setFinishTime(java.time.LocalDateTime finishTime) {
        this.finishTime = finishTime;
    }

    public String getChemicalName() {
        return chemicalName;
    }

    public void setChemicalName(String chemicalName) {
        this.chemicalName = chemicalName;
    }

    public String getOperatorName() {
        return operatorName;
    }

    public void setOperatorName(String operatorName) {
        this.operatorName = operatorName;
    }

    public String getChemicalCode() {
        return chemicalCode;
    }

    public void setChemicalCode(String chemicalCode) {
        this.chemicalCode = chemicalCode;
    }

    public String getCasNo() {
        return casNo;
    }

    public void setCasNo(String casNo) {
        this.casNo = casNo;
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

    public Integer getCategory() {
        return category;
    }

    public void setCategory(Integer category) {
        this.category = category;
    }

    public String getLotNo() {
        return lotNo;
    }

    public void setLotNo(String lotNo) {
        this.lotNo = lotNo;
    }

    public String getApproverName() {
        return approverName;
    }

    public void setApproverName(String approverName) {
        this.approverName = approverName;
    }

}
