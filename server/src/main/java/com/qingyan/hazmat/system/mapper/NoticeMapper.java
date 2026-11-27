package com.qingyan.hazmat.system.mapper;

import com.qingyan.hazmat.system.entity.Notice;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface NoticeMapper {

    List<Notice> selectPage(@Param("keyword") String keyword,
                            @Param("offset") int offset,
                            @Param("size") int size);

    long countPage(@Param("keyword") String keyword);

    Notice selectById(@Param("id") Long id);

    int insert(Notice notice);

    int update(Notice notice);

    int deleteByIds(@Param("ids") List<Long> ids);
}
