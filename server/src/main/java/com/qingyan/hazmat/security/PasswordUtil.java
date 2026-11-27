package com.qingyan.hazmat.security;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * PBKDF2 密码散列（JDK 内置，无第三方依赖）。
 * 存储格式：base64(salt):base64(hash)
 */
public class PasswordUtil {

    private static final int ITERATIONS = 120_000;
    private static final int KEY_BITS = 256;

    private PasswordUtil() {
    }

    public static String encode(String raw) {
        try {
            byte[] salt = new byte[16];
            new SecureRandom().nextBytes(salt);
            byte[] hash = pbkdf2(raw, salt);
            return Base64.getEncoder().encodeToString(salt) + ":"
                    + Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new IllegalStateException("密码散列失败", e);
        }
    }

    public static boolean verify(String raw, String stored) {
        try {
            String[] parts = stored.split(":");
            byte[] salt = Base64.getDecoder().decode(parts[0]);
            byte[] expected = Base64.getDecoder().decode(parts[1]);
            return MessageDigest.isEqual(expected, pbkdf2(raw, salt));
        } catch (Exception e) {
            return false;
        }
    }

    private static byte[] pbkdf2(String raw, byte[] salt) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(raw.toCharArray(), salt, ITERATIONS, KEY_BITS);
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
    }
}
