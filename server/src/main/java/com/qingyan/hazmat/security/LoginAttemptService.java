package com.qingyan.hazmat.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * E5 登录防爆破：内存滑动窗口计数（键 = IP + 用户名）。
 * 窗口内失败达阈值即锁定，锁定期间拒绝该键的一切登录尝试并给出稳定错误码 4291；
 * 任一成功登录立即清零（正常用户误输不会累积到锁定）。
 *
 * <p>单实例内存实现（当前部署形态为单 jar），多实例需换 Redis 计数——已在
 * docs/backup-recovery.md 与 README 的能力边界中说明。计数为有界清理，避免长期运行内存增长。
 */
@Component
public class LoginAttemptService {

    /** 登录失败达阈值 → 锁定（业务错误码，见错误码表 4291）。 */
    public static final int CODE_LOCKED = 4291;

    private static final Logger log = LoggerFactory.getLogger(LoginAttemptService.class);

    private final int maxFailures;
    private final Duration window;
    private final Duration lock;

    private final Map<String, Deque<Instant>> failures = new ConcurrentHashMap<>();
    private final Map<String, Instant> lockedUntil = new ConcurrentHashMap<>();

    public LoginAttemptService(@Value("${app.security.login-attempt.max-failures:5}") int maxFailures,
                               @Value("${app.security.login-attempt.window-minutes:10}") int windowMinutes,
                               @Value("${app.security.login-attempt.lock-minutes:10}") int lockMinutes) {
        this.maxFailures = maxFailures;
        this.window = Duration.ofMinutes(windowMinutes);
        this.lock = Duration.ofMinutes(lockMinutes);
    }

    /** 是否处于锁定期；返回剩余分钟数（0 表示未锁定）。 */
    public long lockedMinutes(String key) {
        Instant until = lockedUntil.get(key);
        if (until == null) {
            return 0;
        }
        if (until.isBefore(Instant.now())) {
            lockedUntil.remove(key);
            failures.remove(key);
            return 0;
        }
        return Math.max(1, Duration.between(Instant.now(), until).toMinutes() + 1);
    }

    /** 记录一次失败；达到阈值即锁定并返回 true。 */
    public boolean recordFailure(String key) {
        Instant now = Instant.now();
        Deque<Instant> queue = failures.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (queue) {
            queue.addLast(now);
            while (!queue.isEmpty() && Duration.between(queue.peekFirst(), now).compareTo(window) > 0) {
                queue.pollFirst();
            }
            if (queue.size() >= maxFailures) {
                lockedUntil.put(key, now.plus(lock));
                queue.clear();
                log.warn("[security] 登录失败达 {} 次，锁定 {} 分钟（key 已脱敏长度={}）",
                        maxFailures, lock.toMinutes(), key.length());
                return true;
            }
            return false;
        }
    }

    /** 成功登录：清零该键计数与锁定。 */
    public void reset(String key) {
        failures.remove(key);
        lockedUntil.remove(key);
    }

    /** 有界清理：仅保留最近活跃键（防长期运行内存增长）。 */
    public void sweep() {
        Instant now = Instant.now();
        lockedUntil.entrySet().removeIf(e -> e.getValue().isBefore(now));
        failures.entrySet().removeIf(e -> {
            Deque<Instant> q = e.getValue();
            synchronized (q) {
                while (!q.isEmpty() && Duration.between(q.peekFirst(), now).compareTo(window) > 0) {
                    q.pollFirst();
                }
                return q.isEmpty();
            }
        });
    }

    public int maxFailures() {
        return maxFailures;
    }

    public long lockMinutes() {
        return lock.toMinutes();
    }
}
