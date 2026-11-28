package com.qingyan.hazmat.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 安全响应头：由应用统一注入，生产可再叠加网关策略。
 *
 * <ul>
 *   <li>{@code X-Content-Type-Options: nosniff}——禁 MIME 嗅探</li>
 *   <li>{@code X-Frame-Options: DENY}——禁被任意站点 iframe 套嵌（防点击劫持）</li>
 *   <li>{@code Referrer-Policy: no-referrer}——不向第三方泄漏来源</li>
 *   <li>{@code Content-Security-Policy}——宽松策略：同源脚本/样式，允许 style 内联
 *       （Element Plus 运行时注入 &lt;style&gt;）与 data:/blob: 图片（GHS 图标、canvas 导出），
 *       不引入外部域，保证现有前端零破坏</li>
 *   <li>{@code Strict-Transport-Security}——仅 HTTPS 请求下发（HTTP 下声明 HSTS 无效且易误伤调试）</li>
 * </ul>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class SecurityHeadersFilter extends OncePerRequestFilter {

    /** 宽松但默认拒绝外域的 CSP（前端资产全部同源打包，无 CDN 依赖）。 */
    static final String CSP = "default-src 'self'; "
            + "script-src 'self' 'unsafe-inline'; "
            + "style-src 'self' 'unsafe-inline'; "
            + "img-src 'self' data: blob:; "
            + "font-src 'self' data:; "
            + "connect-src 'self'; "
            + "frame-ancestors 'none'; "
            + "base-uri 'self'; "
            + "form-action 'self'";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("Referrer-Policy", "no-referrer");
        response.setHeader("Content-Security-Policy", CSP);
        if (request.isSecure()) {
            response.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
        }
        chain.doFilter(request, response);
    }
}
