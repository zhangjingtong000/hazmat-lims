package com.qingyan.hazmat.security;

import com.qingyan.hazmat.system.entity.OperLog;
import com.qingyan.hazmat.system.mapper.SysUserMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;

@Component
public class LoginInterceptor implements HandlerInterceptor {

    private final TokenService tokenService;
    private final SysUserMapper userMapper;
    private final com.qingyan.hazmat.system.mapper.OperLogMapper operLogMapper;

    public LoginInterceptor(TokenService tokenService, SysUserMapper userMapper,
                            com.qingyan.hazmat.system.mapper.OperLogMapper operLogMapper) {
        this.tokenService = tokenService;
        this.userMapper = userMapper;
        this.operLogMapper = operLogMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if (!(handler instanceof HandlerMethod hm)) {
            return true;
        }

        String auth = request.getHeader("Authorization");
        String token = (auth != null && auth.startsWith("Bearer ")) ? auth.substring(7) : auth;
        TokenService.Payload payload = tokenService.verify(token);
        if (payload == null) {
            return reject(request, response, 401, "未登录或登录已过期");
        }
        // 令牌本身有效不代表账号仍可用：签名只证明"这枚令牌是我签发的"，
        // 停用/删除账号必须每次回到 sys_user 判定，否则旧令牌能一直用到过期。
        com.qingyan.hazmat.system.entity.SysUser user = userMapper.selectById(payload.uid);
        if (user == null) {
            return reject(request, response, 401, "用户不存在或已被删除");
        }
        if (!"0".equals(user.getStatus())) {
            return reject(request, response, 401, "账号已停用");
        }

        request.setAttribute("uid", payload.uid);
        request.setAttribute("username", payload.username);
        // 供 /auth/logout 注销当前这一枚令牌
        request.setAttribute("token", token);

        RequirePerm required = hm.getMethodAnnotation(RequirePerm.class);
        if (required != null) {
            List<String> roleKeys = userMapper.selectRoleKeys(payload.uid);
            if (!roleKeys.contains("admin")) {
                List<String> perms = userMapper.selectPerms(payload.uid);
                if (!perms.contains(required.value())) {
                    return reject(request, response, 403, "无操作权限: " + required.value());
                }
            }
        }
        return true;
    }

    private boolean reject(HttpServletRequest request, HttpServletResponse response, int code, String message)
            throws Exception {
        // 401/403 在拦截器层短路，不进入日志切面——在此单独留痕
        try {
            OperLog log = new OperLog();
            log.setModule("访问控制");
            log.setAction("拒绝 " + request.getMethod() + " " + request.getRequestURI());
            Object username = request.getAttribute("username");
            log.setOperator(username == null ? "anonymous" : String.valueOf(username));
            log.setStatus(code == 403 ? "失败" : "未登录");
            operLogMapper.insert(log);
        } catch (Exception ignore) {
            // 日志失败不阻断拒绝
        }
        response.setStatus(code);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":" + code + ",\"message\":\"" + message + "\"}");
        return false;
    }
}
