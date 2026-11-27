package com.qingyan.hazmat.web;

import com.qingyan.hazmat.common.Result;
import com.qingyan.hazmat.security.RequirePerm;
import com.qingyan.hazmat.system.entity.SysRole;
import com.qingyan.hazmat.system.mapper.SysRoleMapper;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/system/role")
public class SysRoleController {

    private final SysRoleMapper roleMapper;

    public SysRoleController(SysRoleMapper roleMapper) {
        this.roleMapper = roleMapper;
    }

    @GetMapping("/list")
    @RequirePerm("system:role:list")
    public Result<List<SysRole>> list() {
        return Result.ok(roleMapper.selectAll());
    }

    @PostMapping
    @RequirePerm("system:role:add")
    public Result<Void> add(@RequestBody SysRole role) {
        if (role.getStatus() == null) {
            role.setStatus("0");
        }
        roleMapper.insert(role);
        return Result.ok();
    }

    @PutMapping
    @RequirePerm("system:role:edit")
    public Result<Void> edit(@RequestBody SysRole role) {
        roleMapper.update(role);
        return Result.ok();
    }

    @DeleteMapping("/{ids}")
    @RequirePerm("system:role:remove")
    public Result<Void> remove(@PathVariable String ids) {
        List<Long> idList = Arrays.stream(ids.split(",")).map(Long::valueOf).toList();
        roleMapper.deleteByIds(idList);
        return Result.ok();
    }
}
