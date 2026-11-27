package com.qingyan.hazmat.hazmat.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** chem_info（schema.sql 同源生成）。 */
public class ChemInfo {

    private Long id;

    /** HG-<类别码>-年-4位序 */
    private String code;

    private String name;

    private String alias;

    /** CAS 登记号（查重键之一） */
    private String casNo;

    /** 0普通化学品/1普通危化品/2易制毒/3易制爆/4剧毒 */
    private Integer category;

    /** 主相容组 OX/RD/AC/AK/ORG/FLAM/GEN */
    private String compatGroup;

    /** 次相容组（多属性化学品，可空） */
    private String compatGroup2;

    private String spec;

    private String unit;

    /** 现存总量=Σ批次余量（守恒） */
    private java.math.BigDecimal stockQty;

    /** 安全库存下限（看板预警） */
    private java.math.BigDecimal minStock;

    /** 剧毒单次上限（仅 category=4 非空） */
    private java.math.BigDecimal maxOnce;

    /** 默认柜位（展示；禁配以批次实际柜位为准） */
    private Long cabinetId;

    private Integer expiryWarnDays;

    /** GHS 象形图代码 */
    private String ghzPictogram;

    /** 应急处置要点 */
    private String firstAid;

    /** 0在管/1封存/2清空 */
    private Integer status;

    /** 逻辑删除（三条件守卫在服务层） */
    private Integer deleted;

    @com.fasterxml.jackson.annotation.JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @com.fasterxml.jackson.annotation.JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;

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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAlias() {
        return alias;
    }

    public void setAlias(String alias) {
        this.alias = alias;
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

    public String getCompatGroup() {
        return compatGroup;
    }

    public void setCompatGroup(String compatGroup) {
        this.compatGroup = compatGroup;
    }

    public String getCompatGroup2() {
        return compatGroup2;
    }

    public void setCompatGroup2(String compatGroup2) {
        this.compatGroup2 = compatGroup2;
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

    public java.math.BigDecimal getStockQty() {
        return stockQty;
    }

    public void setStockQty(java.math.BigDecimal stockQty) {
        this.stockQty = stockQty;
    }

    public java.math.BigDecimal getMinStock() {
        return minStock;
    }

    public void setMinStock(java.math.BigDecimal minStock) {
        this.minStock = minStock;
    }

    public java.math.BigDecimal getMaxOnce() {
        return maxOnce;
    }

    public void setMaxOnce(java.math.BigDecimal maxOnce) {
        this.maxOnce = maxOnce;
    }

    public Long getCabinetId() {
        return cabinetId;
    }

    public void setCabinetId(Long cabinetId) {
        this.cabinetId = cabinetId;
    }

    public Integer getExpiryWarnDays() {
        return expiryWarnDays;
    }

    public void setExpiryWarnDays(Integer expiryWarnDays) {
        this.expiryWarnDays = expiryWarnDays;
    }

    public String getGhzPictogram() {
        return ghzPictogram;
    }

    public void setGhzPictogram(String ghzPictogram) {
        this.ghzPictogram = ghzPictogram;
    }

    public String getFirstAid() {
        return firstAid;
    }

    public void setFirstAid(String firstAid) {
        this.firstAid = firstAid;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Integer getDeleted() {
        return deleted;
    }

    public void setDeleted(Integer deleted) {
        this.deleted = deleted;
    }

    public java.time.LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(java.time.LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public java.time.LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(java.time.LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }

}
