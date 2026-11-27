package com.qingyan.hazmat.system.mapper;

import com.qingyan.hazmat.system.entity.SysRole;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface SysRoleMapper {

    List<SysRole> selectAll();

    long count();

    int insert(SysRole role);

    int update(SysRole role);

    int deleteByIds(@Param("ids") List<Long> ids);

    int insertUserRole(@Param("userId") long userId, @Param("roleId") long roleId);

    /** 角色指派：先清后插（全量覆盖语义）。 */
    int deleteUserRole(@Param("userId") long userId);

    List<Long> selectRoleIdsByUser(@Param("userId") long userId);

    int insertRoleMenu(@Param("roleId") long roleId, @Param("menuId") long menuId);
}
