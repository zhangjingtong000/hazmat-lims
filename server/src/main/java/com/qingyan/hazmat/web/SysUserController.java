package com.qingyan.hazmat.web;

import com.qingyan.hazmat.common.BizException;
import com.qingyan.hazmat.common.Result;
import com.qingyan.hazmat.common.TableData;
import com.qingyan.hazmat.security.PasswordUtil;
import com.qingyan.hazmat.security.RequirePerm;
import com.qingyan.hazmat.system.entity.SysUser;
import com.qingyan.hazmat.system.mapper.SysRoleMapper;
import com.qingyan.hazmat.system.mapper.SysUserMapper;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/system/user")
public class SysUserController {

    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;

    public SysUserController(SysUserMapper userMapper, SysRoleMapper roleMapper) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
    }

    @GetMapping("/list")
    @RequirePerm("system:user:list")
    public Result<TableData<SysUser>> list(@RequestParam(defaultValue = "") String keyword,
                                           @RequestParam(defaultValue = "1") int page,
                                           @RequestParam(defaultValue = "10") int size) {
        int offset = (page - 1) * size;
        List<SysUser> records = userMapper.selectPage(keyword, offset, size);
        records.forEach(u -> u.setPassword(null));
        return Result.ok(TableData.of(records, userMapper.countPage(keyword)));
    }

    @PostMapping
    @RequirePerm("system:user:add")
    public Result<Void> add(@RequestBody SysUser user) {
        if (user.getUsername() == null || user.getUsername().isBlank()) {
            throw new BizException(400, "用户名不能为空");
        }
        com.qingyan.hazmat.common.Validators.maxLen(user.getUsername(), 30, "用户名");
        com.qingyan.hazmat.common.Validators.maxLen(user.getNickname(), 50, "昵称");
        if (user.getUsername().length() < 2) {
            throw new BizException(com.qingyan.hazmat.common.Validators.CODE_INVALID, "用户名至少 2 位");
        }
        com.qingyan.hazmat.common.Validators.password(user.getPassword(), user.getUsername());
        if (userMapper.selectByUsername(user.getUsername()) != null) {
            throw new BizException(400, "用户名已存在");
        }
        user.setPassword(PasswordUtil.encode(user.getPassword()));
        if (user.getStatus() == null) {
            user.setStatus("0");
        }
        userMapper.insert(user);
        return Result.ok();
    }

    @PutMapping
    @RequirePerm("system:user:edit")
    public Result<Void> edit(@RequestBody SysUser user) {
        if (user.getId() == null) {
            throw new BizException(400, "缺少用户 ID");
        }
        com.qingyan.hazmat.common.Validators.maxLen(user.getNickname(), 50, "昵称");
        userMapper.update(user);
        return Result.ok();
    }

    @DeleteMapping("/{ids}")
    @RequirePerm("system:user:remove")
    public Result<Void> remove(@PathVariable String ids) {
        List<Long> idList = Arrays.stream(ids.split(",")).map(Long::valueOf).toList();
        userMapper.deleteByIds(idList);
        return Result.ok();
    }

    /** 建单人员下拉（登录即可）：可按权限键过滤（如 hzm:req:advisor）。 */
    @GetMapping("/options")
    public Result<List<java.util.Map<String, Object>>> options(@RequestParam(required = false) String perm) {
        return Result.ok(userMapper.selectOptions(perm));
    }

    /** 查询用户已指派角色 ID 集（指派对话框回显）。 */
    @GetMapping("/{id}/roles")
    @RequirePerm("system:user:list")
    public Result<List<Long>> roleIds(@PathVariable Long id) {
        return Result.ok(roleMapper.selectRoleIdsByUser(id));
    }

    /** 角色指派：全量覆盖（先清后插，同事务）。 */
    @PutMapping("/{id}/roles")
    @RequirePerm("system:user:edit")
    @org.springframework.transaction.annotation.Transactional
    public Result<Void> assignRoles(@PathVariable Long id, @RequestBody Map<String, List<Long>> body) {
        List<Long> roleIds = body.get("roleIds");
        if (roleIds == null) {
            throw new BizException(400, "roleIds 不能为空");
        }
        roleMapper.deleteUserRole(id);
        for (Long roleId : new java.util.LinkedHashSet<>(roleIds)) {
            roleMapper.insertUserRole(id, roleId);
        }
        return Result.ok();
    }

    @PutMapping("/{id}/password")
    @RequirePerm("system:user:resetPwd")
    public Result<Void> resetPwd(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String password = body.get("password");
        com.qingyan.hazmat.common.Validators.password(password, null);
        userMapper.updatePassword(id, PasswordUtil.encode(password));
        return Result.ok();
    }
}
