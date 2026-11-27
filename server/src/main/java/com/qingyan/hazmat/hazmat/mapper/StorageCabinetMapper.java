package com.qingyan.hazmat.hazmat.mapper;

import com.qingyan.hazmat.hazmat.entity.StorageCabinet;

import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 存放柜持久层。 */
public interface StorageCabinetMapper {

    int insert(StorageCabinet cabinet);

    List<StorageCabinet> selectAll();

    StorageCabinet selectById(@Param("id") Long id);

    int update(StorageCabinet cabinet);
}
