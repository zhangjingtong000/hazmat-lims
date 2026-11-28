package com.qingyan.hazmat.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 登录防爆破单测：达阈值锁定、锁定期间拒绝、成功清零、窗口过期释放。
 * 构造用 5 次/10 分钟窗口 + 10 分钟锁定（与 application.yml 默认一致）。
 */
class LoginAttemptServiceTest {

    private LoginAttemptService service() {
        return new LoginAttemptService(5, 10, 10);
    }

    @Test
    void 未达阈值不锁定_达阈值即锁定() {
        LoginAttemptService s = service();
        String key = "10.0.0.1|admin";
        for (int i = 0; i < 4; i++) {
            assertFalse(s.recordFailure(key), "第 " + (i + 1) + " 次失败不应锁定");
            assertEquals(0, s.lockedMinutes(key));
        }
        assertTrue(s.recordFailure(key), "第 5 次失败应锁定");
        assertTrue(s.lockedMinutes(key) > 0, "锁定期内 lockedMinutes 应大于 0");
        assertEquals(5, s.maxFailures());
        assertEquals(10, s.lockMinutes());
    }

    @Test
    void 锁定按IP加用户名分键_互不影响() {
        LoginAttemptService s = service();
        String a = "10.0.0.1|admin";
        String b = "10.0.0.2|admin";
        for (int i = 0; i < 5; i++) {
            s.recordFailure(a);
        }
        assertTrue(s.lockedMinutes(a) > 0);
        assertEquals(0, s.lockedMinutes(b), "另一 IP 不应被连带锁定");
        assertFalse(s.recordFailure(b), "另一 IP 的首次失败不应锁定");
    }

    @Test
    void 成功登录清零() {
        LoginAttemptService s = service();
        String key = "10.0.0.1|admin";
        for (int i = 0; i < 4; i++) {
            s.recordFailure(key);
        }
        s.reset(key);
        // 清零后需重新累计 5 次才锁定
        for (int i = 0; i < 4; i++) {
            assertFalse(s.recordFailure(key));
        }
        assertTrue(s.recordFailure(key), "清零后第 5 次才应锁定");
    }

    @Test
    void 锁定窗口到期后可重新累计失败不永久锁定() {
        // lock=0 为退化配置（锁定时长≈0）：用于验证"锁不持久"这一语义
        LoginAttemptService s = new LoginAttemptService(2, 10, 0);
        String key = "10.0.0.1|admin";
        assertFalse(s.recordFailure(key), "第 1 次失败不锁定");
        assertTrue(s.recordFailure(key), "第 2 次失败达阈值锁定");
        assertTrue(s.lockedMinutes(key) <= 1, "lock=0 时不应产生持久锁定");
        // 锁定到期后计数清零：重新从第一次开始累计
        assertFalse(s.recordFailure(key), "解锁后应重新计数而非延续旧计数");
    }

    @Test
    void sweep_清理过期锁与窗口且不影响后续计数() {
        LoginAttemptService s = new LoginAttemptService(5, 10, 0);
        String key = "10.0.0.1|admin";
        for (int i = 0; i < 3; i++) {
            s.recordFailure(key);
        }
        s.sweep();
        s.sweep();
        // 清理后状态自洽：继续累计失败，仍未达阈值
        assertFalse(s.recordFailure(key));
        assertEquals(0, s.lockedMinutes(key));
    }
}
