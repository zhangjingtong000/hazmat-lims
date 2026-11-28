package com.qingyan.hazmat.security;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 客户端 IP 解析：默认只认 {@code request.getRemoteAddr()}，不信任任何转发头。
 *
 * <p>只有同时满足两个条件才读 {@code X-Forwarded-For} / {@code X-Real-IP}：
 * ① {@code app.security.trust-proxy=true}；② 直连方（{@code getRemoteAddr()}）命中
 * {@code app.security.trusted-proxies} 的受信清单（精确 IP / IPv4 CIDR / {@code 10.0.0.*} 通配）。
 * 任一不满足就回落到 TCP 层地址——伪造的 XFF 不会改变登录锁定与限流的分键。
 *
 * <p>受信场景下从 XFF 最右侧往左取第一个非受信地址：最右侧是最近一跳反代追加的真实来源，
 * 客户端自己塞在左侧的伪造值会被跳过。
 */
@Component
public class ClientIpResolver {

    private static final Logger log = LoggerFactory.getLogger(ClientIpResolver.class);

    private final boolean trustProxy;
    private final List<String> trustedProxies;

    public ClientIpResolver(@Value("${app.security.trust-proxy:false}") boolean trustProxy,
                            @Value("${app.security.trusted-proxies:}") String trustedProxies) {
        this.trustProxy = trustProxy;
        this.trustedProxies = parseList(trustedProxies);
        if (trustProxy && this.trustedProxies.isEmpty()) {
            log.warn("== 安全提示：app.security.trust-proxy=true 但 trusted-proxies 为空，"
                    + "为避免伪造 X-Forwarded-For，仍只使用 getRemoteAddr() ==");
        }
    }

    /** 解析客户端 IP；永不为 null。 */
    public String resolve(HttpServletRequest request) {
        String remote = normalize(request.getRemoteAddr());
        if (!trustProxy || trustedProxies.isEmpty() || !isTrusted(remote)) {
            return remote;
        }
        // 从右往左取第一个非受信地址（右端是最近一跳反代写入的真实来源）
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String[] hops = forwarded.split(",");
            for (int i = hops.length - 1; i >= 0; i--) {
                String ip = normalize(hops[i]);
                if (ip != null && !isTrusted(ip)) {
                    return ip;
                }
            }
        }
        String realIp = normalize(request.getHeader("X-Real-IP"));
        if (realIp != null) {
            return realIp;
        }
        return remote;
    }

    public boolean trustProxy() {
        return trustProxy;
    }

    public List<String> trustedProxies() {
        return trustedProxies;
    }

    private boolean isTrusted(String ip) {
        if (ip == null) {
            return false;
        }
        for (String rule : trustedProxies) {
            if ("*".equals(rule)) {
                return true;
            }
            if (rule.contains("/") && cidrMatch(ip, rule)) {
                return true;
            }
            if (rule.endsWith(".*") && ip.startsWith(rule.substring(0, rule.length() - 1))) {
                return true;
            }
            if (rule.equalsIgnoreCase(ip)) {
                return true;
            }
        }
        return false;
    }

    /** 仅支持 IPv4 CIDR（反代地址几乎都是 IPv4；IPv6 用精确匹配）。 */
    private static boolean cidrMatch(String ip, String rule) {
        String[] parts = rule.split("/", 2);
        Integer mask = parseIpv4(parts[0]);
        Integer value = parseIpv4(ip);
        if (mask == null || value == null) {
            return false;
        }
        int bits;
        try {
            bits = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            return false;
        }
        if (bits < 0 || bits > 32) {
            return false;
        }
        if (bits == 0) {
            return true;
        }
        int shift = 32 - bits; // 1..32 → 31..0，避免 Java 中移 32 位等于不移位
        return (mask >>> shift) == (value >>> shift);
    }

    /** 校验并规范化 IP 字面量；非法或空返回 null（不做 DNS 解析）。 */
    static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.trim();
        if (s.isEmpty()) {
            return null;
        }
        if (s.startsWith("[")) { // [::1]:1234
            int end = s.indexOf(']');
            if (end < 0) {
                return null;
            }
            s = s.substring(1, end);
        } else {
            int colon = s.indexOf(':');
            if (colon > 0 && colon == s.lastIndexOf(':') && s.indexOf('.') > 0) {
                // 1.2.3.4:5678（IPv4 + 端口）
                String port = s.substring(colon + 1);
                if (port.chars().allMatch(Character::isDigit)) {
                    s = s.substring(0, colon);
                }
            }
        }
        if (parseIpv4(s) != null) {
            return s;
        }
        if (isIpv6Literal(s)) {
            String lower = s.toLowerCase();
            if ("0:0:0:0:0:0:0:1".equals(lower)) {
                return "::1";
            }
            return lower;
        }
        return null;
    }

    private static Integer parseIpv4(String s) {
        String[] parts = s.split("\\.", -1);
        if (parts.length != 4) {
            return null;
        }
        int value = 0;
        for (String part : parts) {
            if (part.isEmpty() || part.length() > 3 || !part.chars().allMatch(Character::isDigit)) {
                return null;
            }
            int octet = Integer.parseInt(part);
            if (octet > 255) {
                return null;
            }
            value = (value << 8) | octet;
        }
        return value;
    }

    private static boolean isIpv6Literal(String s) {
        if (s.indexOf(':') < 0 || s.length() > 45) {
            return false;
        }
        for (char c : s.toCharArray()) {
            boolean hex = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
            if (!hex && c != ':' && c != '.') {
                return false;
            }
        }
        return true;
    }

    private static List<String> parseList(String raw) {
        List<String> out = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            return out;
        }
        for (String item : raw.split(",")) {
            String value = item.trim();
            if (!value.isEmpty()) {
                out.add(value);
            }
        }
        return out;
    }
}
