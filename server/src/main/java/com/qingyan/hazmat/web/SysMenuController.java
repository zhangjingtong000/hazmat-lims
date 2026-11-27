package com.qingyan.hazmat.web;

import com.qingyan.hazmat.common.Result;
import com.qingyan.hazmat.security.RequirePerm;
import com.qingyan.hazmat.system.AuthService;
import com.qingyan.hazmat.system.mapper.SysMenuMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/system/menu")
public class SysMenuController {

    private final SysMenuMapper menuMapper;
    private final AuthService authService;

    public SysMenuController(SysMenuMapper menuMapper, AuthService authService) {
        this.menuMapper = menuMapper;
        this.authService = authService;
    }

    @GetMapping("/list")
    @RequirePerm("system:menu:list")
    public Result<List<Map<String, Object>>> list() {
        return Result.ok(authService.buildMenuTree(menuMapper.selectAll()));
    }
}
