package com.qingyan.hazmat.hazmat.entity;

/** storage_cabinet（schema.sql 同源生成）。 */
public class StorageCabinet {

    private Long id;

    private String code;

    private String name;

    /** 1酸/2碱/3有机/4氧化剂/5剧毒专柜/6通用 */
    private Integer type;

    private Integer dualLock;

    private String location;

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

    public Integer getType() {
        return type;
    }

    public void setType(Integer type) {
        this.type = type;
    }

    public Integer getDualLock() {
        return dualLock;
    }

    public void setDualLock(Integer dualLock) {
        this.dualLock = dualLock;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

}
