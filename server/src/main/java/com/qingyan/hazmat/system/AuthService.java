package com.qingyan.hazmat.system;

import com.qingyan.hazmat.common.BizException;
import com.qingyan.hazmat.security.PasswordUtil;
import com.qingyan.hazmat.system.entity.SysMenu;
import com.qingyan.hazmat.system.entity.SysUser;
import com.qingyan.hazmat.system.mapper.SysMenuMapper;
import com.qingyan.hazmat.system.mapper.SysUserMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AuthService {

    private final SysUserMapper userMapper;
    private final SysMenuMapper menuMapper;

    public AuthService(SysUserMapper userMapper, SysMenuMapper menuMapper) {
        this.userMapper = userMapper;
        this.menuMapper = menuMapper;
    }

    public Map<String, Object> login(String username, String rawPassword) {
        SysUser user = userMapper.selectByUsername(username);
        if (user == null || !PasswordUtil.verify(rawPassword, user.getPassword())) {
            throw new BizException(401, "用户名或密码错误");
        }
        if (!"0".equals(user.getStatus())) {
            throw new BizException(403, "账号已停用");
        }
        return buildProfile(user);
    }

    public Map<String, Object> profile(long uid) {
        SysUser user = userMapper.selectById(uid);
        if (user == null) {
            throw new BizException(401, "用户不存在");
        }
        return buildProfile(user);
    }

    /** 自助修改密码：校验旧密码后落新散列；口令复杂度走统一策略 */
    public void changePassword(long uid, String oldPassword, String newPassword) {
        if (oldPassword == null || oldPassword.isBlank()) {
            throw new BizException(400, "旧密码不能为空");
        }
        SysUser user = userMapper.selectById(uid);
        if (user == null) {
            throw new BizException(401, "用户不存在");
        }
        com.qingyan.hazmat.common.Validators.password(newPassword, user.getUsername());
        if (!PasswordUtil.verify(oldPassword, user.getPassword())) {
            throw new BizException(400, "旧密码不正确");
        }
        if (PasswordUtil.verify(newPassword, user.getPassword())) {
            throw new BizException(400, "新密码不能与旧密码相同");
        }
        userMapper.updatePassword(uid, PasswordUtil.encode(newPassword));
    }

    private Map<String, Object> buildProfile(SysUser user) {
        List<String> roleKeys = userMapper.selectRoleKeys(user.getId());
        boolean admin = roleKeys.contains("admin");

        List<SysMenu> menus = admin ? menuMapper.selectAll() : menuMapper.selectByUserId(user.getId());
        List<String> perms = admin ? menuMapper.selectAllPerms() : userMapper.selectPerms(user.getId());

        user.setPassword(null);
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("user", user);
        profile.put("roles", roleKeys);
        profile.put("perms", perms);
        profile.put("menus", buildMenuTree(menus));
        return profile;
    }

    public List<Map<String, Object>> buildMenuTree(List<SysMenu> all) {
        Map<Long, List<SysMenu>> byParent = new LinkedHashMap<>();
        for (SysMenu m : all) {
            byParent.computeIfAbsent(m.getParentId() == null ? 0L : m.getParentId(), k -> new ArrayList<>()).add(m);
        }
        return childrenOf(0L, byParent);
    }

    private List<Map<String, Object>> childrenOf(long parentId, Map<Long, List<SysMenu>> byParent) {
        List<Map<String, Object>> nodes = new ArrayList<>();
        for (SysMenu m : byParent.getOrDefault(parentId, List.of())) {
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("id", m.getId());
            node.put("menuName", m.getMenuName());
            node.put("path", m.getPath());
            node.put("perms", m.getPerms());
            node.put("menuType", m.getMenuType());
            List<Map<String, Object>> children = childrenOf(m.getId(), byParent);
            if (!children.isEmpty()) {
                node.put("children", children);
            }
            nodes.add(node);
        }
        return nodes;
    }
}
