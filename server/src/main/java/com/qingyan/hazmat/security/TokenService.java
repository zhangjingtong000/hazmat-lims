package com.qingyan.hazmat.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import tools.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * HMAC-SHA256 签名令牌：payload(base64url) + "." + 签名。
 * 无状态，不依赖服务端会话存储。
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
        public long exp;
    }

    public String create(long uid, String username) {
        try {
            Payload p = new Payload();
            p.uid = uid;
            p.username = username;
            p.exp = System.currentTimeMillis() / 1000 + tokenHours * 3600L;
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
            if (p.exp < System.currentTimeMillis() / 1000) {
                return null;
            }
            return p;
        } catch (Exception e) {
            return null;
        }
    }

    private String sign(String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    }
}
