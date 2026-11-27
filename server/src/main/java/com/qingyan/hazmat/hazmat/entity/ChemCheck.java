package com.qingyan.hazmat.hazmat.entity;

import java.time.LocalDateTime;

/** chem_check（schema.sql 同源生成）。 */
public class ChemCheck {

    private Long id;

    /** PD-年月日-3位序 */
    private String code;

    /** NULL=全部 */
    private Integer scopeCategory;

    private Long checkerOneId;

    private Long checkerTwoId;

    private Integer diffCount;

    /** 0盘点中/1平/2不平 */
    private Integer result;

    private String remark;

    @com.fasterxml.jackson.annotation.JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime checkedTime;

    @com.fasterxml.jackson.annotation.JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    /** 列表展示字段（LEFT JOIN sys_user，非表列） */
    private String checkerOneName;

    /** 列表展示字段（LEFT JOIN sys_user，非表列） */
    private String checkerTwoName;

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

    public Integer getScopeCategory() {
        return scopeCategory;
    }

    public void setScopeCategory(Integer scopeCategory) {
        this.scopeCategory = scopeCategory;
    }

    public Long getCheckerOneId() {
        return checkerOneId;
    }

    public void setCheckerOneId(Long checkerOneId) {
        this.checkerOneId = checkerOneId;
    }

    public Long getCheckerTwoId() {
        return checkerTwoId;
    }

    public void setCheckerTwoId(Long checkerTwoId) {
        this.checkerTwoId = checkerTwoId;
    }

    public Integer getDiffCount() {
        return diffCount;
    }

    public void setDiffCount(Integer diffCount) {
        this.diffCount = diffCount;
    }

    public Integer getResult() {
        return result;
    }

    public void setResult(Integer result) {
        this.result = result;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public java.time.LocalDateTime getCheckedTime() {
        return checkedTime;
    }

    public void setCheckedTime(java.time.LocalDateTime checkedTime) {
        this.checkedTime = checkedTime;
    }

    public java.time.LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(java.time.LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public String getCheckerOneName() {
        return checkerOneName;
    }

    public void setCheckerOneName(String checkerOneName) {
        this.checkerOneName = checkerOneName;
    }

    public String getCheckerTwoName() {
        return checkerTwoName;
    }

    public void setCheckerTwoName(String checkerTwoName) {
        this.checkerTwoName = checkerTwoName;
    }

}
