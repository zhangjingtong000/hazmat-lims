package com.qingyan.hazmat.hazmat.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** chem_check_diff（schema.sql 同源生成）。 */
public class ChemCheckDiff {

    private Long id;

    private Long checkId;

    private Long batchId;

    private java.math.BigDecimal bookQty;

    private java.math.BigDecimal actualQty;

    private java.math.BigDecimal diffQty;

    private String remark;

    @com.fasterxml.jackson.annotation.JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime adjustTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getCheckId() {
        return checkId;
    }

    public void setCheckId(Long checkId) {
        this.checkId = checkId;
    }

    public Long getBatchId() {
        return batchId;
    }

    public void setBatchId(Long batchId) {
        this.batchId = batchId;
    }

    public java.math.BigDecimal getBookQty() {
        return bookQty;
    }

    public void setBookQty(java.math.BigDecimal bookQty) {
        this.bookQty = bookQty;
    }

    public java.math.BigDecimal getActualQty() {
        return actualQty;
    }

    public void setActualQty(java.math.BigDecimal actualQty) {
        this.actualQty = actualQty;
    }

    public java.math.BigDecimal getDiffQty() {
        return diffQty;
    }

    public void setDiffQty(java.math.BigDecimal diffQty) {
        this.diffQty = diffQty;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public java.time.LocalDateTime getAdjustTime() {
        return adjustTime;
    }

    public void setAdjustTime(java.time.LocalDateTime adjustTime) {
        this.adjustTime = adjustTime;
    }

}
