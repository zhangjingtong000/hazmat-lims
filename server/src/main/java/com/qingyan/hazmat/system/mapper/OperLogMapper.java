package com.qingyan.hazmat.system.mapper;

import com.qingyan.hazmat.system.entity.OperLog;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface OperLogMapper {

    List<OperLog> selectPage(@Param("keyword") String keyword,
                             @Param("status") String status,
                             @Param("offset") int offset,
                             @Param("size") int size);

    long countPage(@Param("keyword") String keyword, @Param("status") String status);

    int insert(OperLog log);
}
