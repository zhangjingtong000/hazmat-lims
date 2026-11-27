package com.qingyan.hazmat.system.mapper;

import com.qingyan.hazmat.system.entity.SysUser;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

public interface SysUserMapper {

    List<SysUser> selectPage(@Param("keyword") String keyword,
                             @Param("offset") int offset,
                             @Param("size") int size);

    long countPage(@Param("keyword") String keyword);

    SysUser selectByUsername(@Param("username") String username);

    SysUser selectById(@Param("id") Long id);

    long count();

    int insert(SysUser user);

    int update(SysUser user);

    int updatePassword(@Param("id") Long id, @Param("password") String password);

    int deleteByIds(@Param("ids") List<Long> ids);

    List<String> selectRoleKeys(@Param("uid") Long uid);

    List<String> selectPerms(@Param("uid") Long uid);

    /** 建单人员下拉：在用用户，可按权限键过滤（perm 为空返回全部）。 */
    List<Map<String, Object>> selectOptions(@Param("perm") String perm);
}
