package com.qingyan.hazmat.hazmat.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** chem_batch（schema.sql 同源生成）。 */
public class ChemBatch {

    private Long id;

    private Long chemicalId;

    /** 溯源：来源入库单 */
    private Long stockInId;

    private String lotNo;

    private java.math.BigDecimal qtyIn;

    /** ≥0（条件更新守卫） */
    private java.math.BigDecimal qtyLeft;

    @com.fasterxml.jackson.annotation.JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate expiryDate;

    /** 实际柜位（禁配/移柜对象） */
    private Long cabinetId;

    /** 0在库/1耗尽/2废弃/3过期封存 */
    private Integer status;

    @com.fasterxml.jackson.annotation.JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime sealedTime;

    @com.fasterxml.jackson.annotation.JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime inTime;

    @com.fasterxml.jackson.annotation.JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;

    /** 列表展示字段（LEFT JOIN chem_info，非表列） */
    private String chemicalName;

    /** 列表展示字段（LEFT JOIN storage_cabinet，非表列） */
    private String cabinetName;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getChemicalId() {
        return chemicalId;
    }

    public void setChemicalId(Long chemicalId) {
        this.chemicalId = chemicalId;
    }

    public Long getStockInId() {
        return stockInId;
    }

    public void setStockInId(Long stockInId) {
        this.stockInId = stockInId;
    }

    public String getLotNo() {
        return lotNo;
    }

    public void setLotNo(String lotNo) {
        this.lotNo = lotNo;
    }

    public java.math.BigDecimal getQtyIn() {
        return qtyIn;
    }

    public void setQtyIn(java.math.BigDecimal qtyIn) {
        this.qtyIn = qtyIn;
    }

    public java.math.BigDecimal getQtyLeft() {
        return qtyLeft;
    }

    public void setQtyLeft(java.math.BigDecimal qtyLeft) {
        this.qtyLeft = qtyLeft;
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

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public java.time.LocalDateTime getSealedTime() {
        return sealedTime;
    }

    public void setSealedTime(java.time.LocalDateTime sealedTime) {
        this.sealedTime = sealedTime;
    }

    public java.time.LocalDateTime getInTime() {
        return inTime;
    }

    public void setInTime(java.time.LocalDateTime inTime) {
        this.inTime = inTime;
    }

    public java.time.LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(java.time.LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }

    public String getChemicalName() {
        return chemicalName;
    }

    public void setChemicalName(String chemicalName) {
        this.chemicalName = chemicalName;
    }

    public String getCabinetName() {
        return cabinetName;
    }

    public void setCabinetName(String cabinetName) {
        this.cabinetName = cabinetName;
    }

}
