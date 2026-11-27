package com.qingyan.hazmat.hazmat.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** stock_in（schema.sql 同源生成）。 */
public class StockIn {

    private Long id;

    /** RK-年月日-3位序 */
    private String code;

    /** 关联申购单 */
    private Long reqId;

    /** 验收确认后指向台账（新品入库时创建） */
    private Long chemicalId;

    private String lotNo;

    private java.math.BigDecimal qty;

    @com.fasterxml.jackson.annotation.JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate expiryDate;

    private Long cabinetId;

    private String supplier;

    /** 易制毒/易制爆公安备案号 */
    private String permitNo;

    private Long receiverId;

    private Long checkerId;

    private String rejectReason;

    /** 0待验收/1已入库/2已拒收 */
    private Integer status;

    @com.fasterxml.jackson.annotation.JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime inTime;

    @com.fasterxml.jackson.annotation.JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    /** 列表展示字段（LEFT JOIN chem_info，非表列） */
    private String chemicalName;

    /** 列表展示字段（LEFT JOIN sys_user，非表列） */
    private String receiverName;

    /** 新化学品入库时的建档草稿（仅控制器入参承载；非表列，服务层落库时消费） */
    private ChemInfo draft;

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

    public Long getChemicalId() {
        return chemicalId;
    }

    public void setChemicalId(Long chemicalId) {
        this.chemicalId = chemicalId;
    }

    public String getLotNo() {
        return lotNo;
    }

    public void setLotNo(String lotNo) {
        this.lotNo = lotNo;
    }

    public java.math.BigDecimal getQty() {
        return qty;
    }

    public void setQty(java.math.BigDecimal qty) {
        this.qty = qty;
    }

    public java.time.LocalDate getExpiryDate() {
        return expiryDate;
    }

    public void setExpiryDate(java.time.LocalDate expiryDate) {
        this.expiryDate = expiryDate;
    }

    public Long getCabinetId() {
        return cabinetId;
    }

    public void setCabinetId(Long cabinetId) {
        this.cabinetId = cabinetId;
    }

    public String getSupplier() {
        return supplier;
    }

    public void setSupplier(String supplier) {
        this.supplier = supplier;
    }

    public String getPermitNo() {
        return permitNo;
    }

    public void setPermitNo(String permitNo) {
        this.permitNo = permitNo;
    }

    public Long getReceiverId() {
        return receiverId;
    }

    public void setReceiverId(Long receiverId) {
        this.receiverId = receiverId;
    }

    public Long getCheckerId() {
        return checkerId;
    }

    public void setCheckerId(Long checkerId) {
        this.checkerId = checkerId;
    }

    public String getRejectReason() {
        return rejectReason;
    }

    public void setRejectReason(String rejectReason) {
        this.rejectReason = rejectReason;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public java.time.LocalDateTime getInTime() {
        return inTime;
    }

    public void setInTime(java.time.LocalDateTime inTime) {
        this.inTime = inTime;
    }

    public java.time.LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(java.time.LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public String getChemicalName() {
        return chemicalName;
    }

    public void setChemicalName(String chemicalName) {
        this.chemicalName = chemicalName;
    }

    public String getReceiverName() {
        return receiverName;
    }

    public void setReceiverName(String receiverName) {
        this.receiverName = receiverName;
    }

    public ChemInfo getDraft() {
        return draft;
    }

    public void setDraft(ChemInfo draft) {
        this.draft = draft;
    }

}
