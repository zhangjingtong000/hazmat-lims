package com.qingyan.hazmat.system.mapper;

import com.qingyan.hazmat.system.entity.SysMenu;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface SysMenuMapper {

    List<SysMenu> selectAll();

    List<SysMenu> selectByUserId(@Param("uid") Long uid);

    List<String> selectAllPerms();

    int insert(SysMenu menu);

    int update(SysMenu menu);

    int deleteById(@Param("id") Long id);
}
