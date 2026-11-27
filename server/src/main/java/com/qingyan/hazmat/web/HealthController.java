package com.qingyan.hazmat.web;

import com.qingyan.hazmat.common.ai.RegCorpus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * F5 健康检查 / 就绪探针（零依赖自研，不引 actuator）。
 *
 * <p>{@code GET /health} 返回 {@code {status, version, time, checks:{db, regs, ai}}}：
 * <ul>
 *   <li>{@code db}：真实探活（SELECT 1），down 时整体 status=DOWN</li>
 *   <li>{@code regs}：法规语料条数（up 表示语料装载成功；空语料为 degraded，不影响主流程）</li>
 *   <li>{@code ai}：AI 通道状态（未配置 key 即 degraded —— 设计上 AI 失败不阻断业务）</li>
 * </ul>
 * 供负载均衡/容器编排探测；对外只暴露状态，不泄漏配置。需登录的接口不包含本路径。
 */
@RestController
public class HealthController {

    private static final Logger log = LoggerFactory.getLogger(HealthController.class);

    private final JdbcTemplate jdbc;
    private final RegCorpus regCorpus;
    private final String version;
    private final String aiKeyEnv;

    public HealthController(JdbcTemplate jdbc, RegCorpus regCorpus,
                            @Value("${app.version:1.0.0}") String version,
                            @Value("${app.ai.key-env:DEEPSEEK_API_KEY}") String aiKeyEnv) {
        this.jdbc = jdbc;
        this.regCorpus = regCorpus;
        this.version = version;
        this.aiKeyEnv = aiKeyEnv;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> checks = new LinkedHashMap<>();
        String db = "up";
        try {
            Integer one = jdbc.queryForObject("SELECT 1", Integer.class);
            db = one != null && one == 1 ? "up" : "down";
        } catch (Exception e) {
            db = "down";
            log.error("[health] 数据库探活失败", e);
        }
        int regCount = regCorpus.isEnabled() ? regCorpus.all().size() : 0;
        checks.put("db", db);
        checks.put("regs", regCount > 0 ? "up(" + regCount + ")" : "degraded(0)");
        boolean aiConfigured = System.getenv(aiKeyEnv) != null && !System.getenv(aiKeyEnv).isBlank();
        checks.put("ai", aiConfigured ? "up" : "degraded(no-key)");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "down".equals(db) ? "DOWN" : "UP");
        body.put("version", version);
        body.put("time", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        body.put("checks", checks);
        return body;
    }
}
