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

import java.util.List;
import java.util.Map;

/**
 * 操作日志：切面环绕全部业务 Controller 的写操作。
 * 成败以"是否抛出业务/运行时异常"判定——原来用拦截器读 HTTP status，
 * 但全局异常处理器统一返回 HTTP 200 + body code，导致失败全被记成"成功"。
 * 401/403 在拦截器层短路（不进入切面），由 LoginInterceptor.reject 单独留痕。
 */
@Aspect
@Component
public class OperLogAspect {

    /**
     * 路径前缀 → 模块名。顺序即优先级：更长的前缀写在前面，
     * 否则 {@code /chem/req} 会把 {@code /chem/req/return/...} 也吞掉。
     * 匹配规则是"相等"或"以 前缀 + / 开头"，不做裸 startsWith，避免误吃同名前缀路径。
     */
    private static final List<Map.Entry<String, String>> MODULES = List.of(
            Map.entry("/chem/purchase", "申购审批"),
            Map.entry("/chem/stockin", "入库登记"),
            Map.entry("/chem/req/return", "归还管理"),
            Map.entry("/chem/req", "领用管理"),
            Map.entry("/chem/waste", "废弃处置"),
            Map.entry("/chem/check", "盘点对账"),
            Map.entry("/chem/stats", "预警看板"),
            Map.entry("/system/user", "用户管理"),
            Map.entry("/system/role", "角色管理"),
            Map.entry("/system/menu", "菜单管理"),
            Map.entry("/system/operlog", "操作日志"),
            Map.entry("/biz/notice", "通知公告"),
            Map.entry("/auth/password", "个人设置"),
            Map.entry("/auth/logout", "登录"),
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
            log.setModule(moduleOf(path));
            String action = refine(method, path) + " · " + status;
            log.setAction(action.length() > 200 ? action.substring(0, 197) + "..." : action);
            log.setOperator(operator);
            log.setStatus(status.startsWith("成功") ? "成功" : "失败");
            operLogMapper.insert(log);
        } catch (Exception ignore) {
            // 日志写入失败不影响业务响应
        }
    }

    /** 路径 → 模块名；没有映射的路径保留原始路径，好过错标成别的模块。 */
    static String moduleOf(String path) {
        for (Map.Entry<String, String> e : MODULES) {
            if (path.equals(e.getKey()) || path.startsWith(e.getKey() + "/")) {
                return e.getValue();
            }
        }
        return path;
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
