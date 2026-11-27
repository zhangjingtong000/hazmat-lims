package com.qingyan.hazmat.system.mapper;

import com.qingyan.hazmat.system.entity.DictItem;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 基础字典（停用不删改）。 */
public interface DictItemMapper {

    List<DictItem> selectByType(@Param("dictType") String dictType);

    List<DictItem> selectPage(@Param("dictType") String dictType, @Param("keyword") String keyword,
                              @Param("offset") int offset, @Param("size") int size);

    long countPage(@Param("dictType") String dictType, @Param("keyword") String keyword);

    int insert(DictItem item);

    int update(DictItem item);

    int updateStatus(@Param("id") Long id, @Param("status") String status);

    int deleteByIds(@Param("ids") List<Long> ids);
}
