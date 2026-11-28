package com.qingyan.hazmat.web;

import com.qingyan.hazmat.common.BizException;
import com.qingyan.hazmat.common.Result;
import com.qingyan.hazmat.security.ClientIpResolver;
import com.qingyan.hazmat.security.LoginAttemptService;
import com.qingyan.hazmat.security.TokenService;
import com.qingyan.hazmat.system.AuthService;
import com.qingyan.hazmat.system.entity.SysUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
public class AuthController {

    private final AuthService authService;
    private final TokenService tokenService;
    private final LoginAttemptService loginAttempt;
    private final ClientIpResolver clientIp;

    public AuthController(AuthService authService, TokenService tokenService,
                          LoginAttemptService loginAttempt, ClientIpResolver clientIp) {
        this.authService = authService;
        this.tokenService = tokenService;
        this.loginAttempt = loginAttempt;
        this.clientIp = clientIp;
    }

    @PostMapping("/auth/login")
    public Result<Map<String, Object>> login(@RequestBody Map<String, String> body, HttpServletRequest request) {
        String username = body.get("username") == null ? "" : body.get("username").trim();
        String password = body.get("password");
        // 防爆破：IP + 用户名 维度滑动窗口，锁定期间直接拒绝（错误码 4291）。
        // IP 默认取 TCP 对端地址；只有显式开启 trust-proxy 且反代在受信清单内才读 XFF，
        // 否则伪造 X-Forwarded-For 即可无限重试。
        String attemptKey = clientIp.resolve(request) + '|' + username;
        long locked = loginAttempt.lockedMinutes(attemptKey);
        if (locked > 0) {
            throw new BizException(LoginAttemptService.CODE_LOCKED,
                    "登录失败次数过多，账号已临时锁定，请 " + locked + " 分钟后重试");
        }
        Map<String, Object> profile;
        try {
            profile = authService.login(username, password);
        } catch (BizException e) {
            if (e.getCode() == 401) {
                boolean nowLocked = loginAttempt.recordFailure(attemptKey);
                if (nowLocked) {
                    throw new BizException(LoginAttemptService.CODE_LOCKED,
                            "登录失败次数过多，账号已临时锁定 " + loginAttempt.lockMinutes() + " 分钟");
                }
            }
            throw e;
        }
        loginAttempt.reset(attemptKey);
        SysUser user = (SysUser) profile.get("user");

        Map<String, Object> data = new HashMap<>();
        data.put("token", tokenService.create(user.getId(), user.getUsername()));
        data.putAll(profile);
        return Result.ok(data);
    }

    @GetMapping("/auth/me")
    public Result<Map<String, Object>> me(HttpServletRequest request) {
        long uid = ((Number) request.getAttribute("uid")).longValue();
        return Result.ok(authService.profile(uid));
    }

    /** 自助修改密码（任何登录用户） */
    @PostMapping("/auth/password")
    public Result<Void> changePassword(@RequestBody Map<String, String> body, HttpServletRequest request) {
        long uid = ((Number) request.getAttribute("uid")).longValue();
        authService.changePassword(uid, body.get("oldPassword"), body.get("newPassword"));
        return Result.ok();
    }
}
