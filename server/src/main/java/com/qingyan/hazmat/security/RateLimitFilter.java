package com.qingyan.hazmat.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 接口限流：轻量进程内滑动窗口（零依赖，不引 Sentinel/Redis）。
 *
 * <p>覆盖两类高风险入口：① 登录 {@code POST /auth/login}；② 一切写接口（POST/PUT/DELETE）。
 * 只读 GET 不计数，避免看板轮询误伤。超限返回 HTTP 429 + 业务码 4290（语义码，前端按
 * "请求过于频繁" 提示，不视为登录态失效）。
 *
 * <p>阈值默认宽松（登录 120 次/分/IP，写 300 次/分/IP），既能挡住脚本爆破/刷单，
 * 又不影响人工操作与自动化验收（E2E 全量约 100 次登录/3 分钟）；可用配置覆盖
 * （app.security.rate-limit.*）。防爆破主防线是 {@link LoginAttemptService} 的失败计数锁定，
 * 本过滤器负责挡住"高频刷接口"这一层。单实例内存实现，多实例需前置网关或 Redis 计数。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
public class RateLimitFilter extends OncePerRequestFilter {

    /** 限流语义码：4290（HTTP 429）。 */
    public static final int CODE_RATE_LIMITED = 4290;

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private final int loginPerMinute;
    private final int writePerMinute;
    private final Map<String, Deque<Instant>> windows = new ConcurrentHashMap<>();

    public RateLimitFilter(@Value("${app.security.rate-limit.login-per-minute:120}") int loginPerMinute,
                           @Value("${app.security.rate-limit.write-per-minute:300}") int writePerMinute) {
        this.loginPerMinute = loginPerMinute;
        this.writePerMinute = writePerMinute;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String method = request.getMethod();
        boolean write = "POST".equals(method) || "PUT".equals(method) || "DELETE".equals(method);
        return !write;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String ip = ClientIp.of(request);
        String uri = request.getRequestURI();
        boolean login = "/auth/login".equals(uri);
        int limit = login ? loginPerMinute : writePerMinute;
        String key = (login ? "login:" : "write:") + ip;
        if (!allow(key, limit)) {
            log.warn("[security] 触发限流 {} {} limit={}/min ip={}", request.getMethod(), uri, limit, ip);
            response.setStatus(429);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":" + CODE_RATE_LIMITED
                    + ",\"message\":\"请求过于频繁，请稍后再试\"}");
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean allow(String key, int limit) {
        Instant now = Instant.now();
        Deque<Instant> queue = windows.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (queue) {
            while (!queue.isEmpty() && now.minusSeconds(60).isAfter(queue.peekFirst())) {
                queue.pollFirst();
            }
            if (queue.size() >= limit) {
                return false;
            }
            queue.addLast(now);
            return true;
        }
    }

    /** 有界清理：窗口内无请求的键释放（定时任务调用）。 */
    public void sweep() {
        Instant cutoff = Instant.now().minusSeconds(120);
        windows.entrySet().removeIf(e -> {
            Deque<Instant> q = e.getValue();
            synchronized (q) {
                while (!q.isEmpty() && cutoff.isAfter(q.peekFirst())) {
                    q.pollFirst();
                }
                return q.isEmpty();
            }
        });
    }

    public int loginPerMinute() {
        return loginPerMinute;
    }

    public int writePerMinute() {
        return writePerMinute;
    }
}
