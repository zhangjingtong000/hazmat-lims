package com.qingyan.hazmat.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import tools.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * HMAC-SHA256 签名令牌：payload(base64url) + "." + 签名。
 * 无状态，不依赖服务端会话存储。
 *
 * <p>无状态不等于"签发后无法撤回"：payload 里带签发时间 {@code iat}，服务端另存每个用户最近一次
 * 撤销时间点（改密、重置口令）与逐条登出的注销表。校验时 {@code iat <= 撤销时间点} 或命中注销表即拒绝，
 * 令牌真值仍由签名保证。撤销表只占内存、随过期时间清理；进程重启后按令牌自身的 {@code exp} 与
 * 签名密钥重新判定——未配置 {@code APP_TOKEN_SECRET} 时密钥本来就是每次运行现生成，重启即全部失效。
 */
@Component
public class TokenService {

    @Value("${app.token-secret:}")
    private String configuredSecret;

    /** 运行期签名密钥：未配置 APP_TOKEN_SECRET 时生成本次运行的临时随机密钥（不落盘、不入库）。 */
    private String secret;

    @jakarta.annotation.PostConstruct
    void initSecret() {
        String v = configuredSecret == null ? "" : configuredSecret.trim();
        String low = v.toLowerCase(java.util.Locale.ROOT);
        if (v.isEmpty() || low.contains("please-replace") || low.contains("change-me")) {
            byte[] buf = new byte[32];
            new java.security.SecureRandom().nextBytes(buf);
            StringBuilder sb = new StringBuilder();
            for (byte b : buf) {
                sb.append(String.format("%02x", b));
            }
            secret = sb.toString();
            org.slf4j.LoggerFactory.getLogger(TokenService.class).warn(
                    "未配置 APP_TOKEN_SECRET：已生成本次运行的临时令牌密钥（重启后此前签发的令牌将失效）。"
                            + "生产环境请设置环境变量 APP_TOKEN_SECRET=<64 位十六进制随机串>。");
        } else {
            secret = v;
        }
    }

    @Value("${app.token-hours}")
    private int tokenHours;

    private final ObjectMapper mapper = new ObjectMapper();

    public static class Payload {
        public long uid;
        public String username;
        /** 签发时间（毫秒）。旧格式令牌没有该字段，反序列化为 0。 */
        public long iat;
        public long exp;
    }

    /** 用户级撤销时间点（毫秒）：改密、重置口令后，签发时间不晚于该时刻的令牌一律失效。 */
    private final Map<Long, Long> userRevokedAt = new ConcurrentHashMap<>();

    /** 单条令牌注销表：token → 失效时刻（秒）。仅存到令牌自身过期，避免无界增长。 */
    private final Map<String, Long> revokedTokens = new ConcurrentHashMap<>();

    public String create(long uid, String username) {
        try {
            Payload p = new Payload();
            p.uid = uid;
            p.username = username;
            p.iat = System.currentTimeMillis();
            p.exp = p.iat / 1000 + tokenHours * 3600L;
            String body = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mapper.writeValueAsBytes(p));
            return body + "." + sign(body);
        } catch (Exception e) {
            throw new IllegalStateException("令牌生成失败", e);
        }
    }

    public Payload verify(String token) {
        try {
            if (token == null || token.isBlank()) {
                return null;
            }
            int dot = token.lastIndexOf('.');
            if (dot <= 0) {
                return null;
            }
            String body = token.substring(0, dot);
            boolean signatureOk = MessageDigest.isEqual(
                    sign(body).getBytes(StandardCharsets.UTF_8),
                    token.substring(dot + 1).getBytes(StandardCharsets.UTF_8));
            if (!signatureOk) {
                return null;
            }
            Payload p = mapper.readValue(Base64.getUrlDecoder().decode(body), Payload.class);
            long now = System.currentTimeMillis() / 1000;
            if (p.exp < now) {
                return null;
            }
            if (isRevoked(token, p, now)) {
                return null;
            }
            return p;
        } catch (Exception e) {
            return null;
        }
    }

    /** 该令牌是否已被撤销：逐条登出表命中，或签发时间不晚于用户级撤销时间点。 */
    private boolean isRevoked(String token, Payload p, long now) {
        Long tokenExp = revokedTokens.get(token);
        if (tokenExp != null) {
            if (tokenExp < now) {
                revokedTokens.remove(token);
            } else {
                return true;
            }
        }
        Long revokedAt = userRevokedAt.get(p.uid);
        // 旧格式令牌 iat=0：一旦该用户被撤销过，同样失效
        return revokedAt != null && p.iat <= revokedAt;
    }

    /** 登出单条令牌（只影响这一枚，其他设备/标签页的会话不动）。 */
    public void revoke(String token, Payload payload) {
        if (token == null || token.isBlank() || payload == null) {
            return;
        }
        long now = System.currentTimeMillis() / 1000;
        long exp = payload.exp > now ? payload.exp : now + 1;
        revokedTokens.put(token, exp);
        // 顺带清掉已过期的注销记录，避免长期运行后表无界增长
        revokedTokens.entrySet().removeIf(e -> e.getValue() < now);
    }

    /** 撤销某用户已签发的全部令牌（改密、重置口令后调用）。 */
    public void revokeUser(long uid) {
        userRevokedAt.put(uid, System.currentTimeMillis());
    }

    private String sign(String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    }
}
