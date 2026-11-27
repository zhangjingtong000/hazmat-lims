package com.qingyan.hazmat.security;

import com.qingyan.hazmat.common.BizException;
import com.qingyan.hazmat.system.entity.OperLog;
import com.qingyan.hazmat.system.mapper.OperLogMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;

/**
 * G1 操作日志：切面环绕全部业务 Controller 的写操作。
 * 成败以"是否抛出业务/运行时异常"判定——原来用拦截器读 HTTP status，
 * 但全局异常处理器统一返回 HTTP 200 + body code，导致失败全被记成"成功"。
 * 401/403 在拦截器层短路（不进入切面），由 LoginInterceptor.reject 单独留痕。
 */
@Aspect
@Component
public class OperLogAspect {

    private static final Map<String, String> MODULES = Map.ofEntries(
            Map.entry("/asset/info", "资产台账"),
            Map.entry("/asset/borrow", "借用管理"),
            Map.entry("/asset/repair", "维修记录"),
            Map.entry("/asset/scrap", "报废管理"),
            Map.entry("/asset/check", "资产盘点"),
            Map.entry("/asset/dict", "基础数据"),
            Map.entry("/asset/attachment", "资产附件"),
            Map.entry("/system/user", "用户管理"),
            Map.entry("/system/role", "角色管理"),
            Map.entry("/system/menu", "菜单管理"),
            Map.entry("/system/operlog", "操作日志"),
            Map.entry("/biz/notice", "通知公告"),
            Map.entry("/auth/password", "个人设置"),
            Map.entry("/auth/login", "登录")
    );

    private final OperLogMapper operLogMapper;

    public OperLogAspect(OperLogMapper operLogMapper) {
        this.operLogMapper = operLogMapper;
    }

    @Around("execution(* com.qingyan.hazmat.web..*.*(..))")
    public Object logWrite(ProceedingJoinPoint pjp) throws Throwable {
        HttpServletRequest request = currentRequest();
        String method = request == null ? "" : request.getMethod();
        String path = request == null ? "" : request.getRequestURI();
        // 只留痕写操作；GET 是读行为不记
        if (request == null || "GET".equalsIgnoreCase(method)) {
            return pjp.proceed();
        }
        try {
            Object out = pjp.proceed();
            insert(path, method, operator(request), "成功");
            return out;
        } catch (BizException e) {
            insert(path, method, operator(request), "失败: " + e.getMessage());
            throw e;
        } catch (Throwable t) {
            insert(path, method, operator(request), "失败: 系统异常");
            throw t;
        }
    }

    private void insert(String path, String method, String operator, String status) {
        try {
            OperLog log = new OperLog();
            log.setModule(MODULES.getOrDefault(path, MODULES.entrySet().stream()
                    .filter(e -> path.startsWith(e.getKey()))
                    .map(Map.Entry::getValue)
                    .findFirst()
                    .orElse(path)));
            String action = refine(method, path) + " · " + status;
            log.setAction(action.length() > 200 ? action.substring(0, 197) + "..." : action);
            log.setOperator(operator);
            log.setStatus(status.startsWith("成功") ? "成功" : "失败");
            operLogMapper.insert(log);
        } catch (Exception ignore) {
            // 日志写入失败不影响业务响应
        }
    }

    private String refine(String method, String path) {
        String action = switch (method.toUpperCase()) {
            case "POST" -> "新增";
            case "PUT" -> "修改";
            case "DELETE" -> "删除";
            default -> method;
        };
        if (path.endsWith("/finish")) return "完成";
        if (path.endsWith("/return")) return "归还";
        if (path.endsWith("/approve")) return "审批";
        if (path.endsWith("/transfer")) return "调拨";
        if (path.endsWith("/dispatch")) return "派工";
        if (path.endsWith("/import")) return "导入";
        if (path.endsWith("/upload")) return "上传";
        if (path.endsWith("/complete")) return "完工";
        if (path.endsWith("/generate")) return "生成";
        return action + " " + path;
    }

    private String operator(HttpServletRequest request) {
        Object username = request.getAttribute("username");
        return username == null ? "anonymous" : String.valueOf(username);
    }

    private HttpServletRequest currentRequest() {
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attrs == null ? null : attrs.getRequest();
    }
}
