package com.qingyan.hazmat.db;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 轻量 schema 版本化迁移（零新依赖，替代 Flyway）。
 *
 * <p>机制：启动时（早于 {@link com.qingyan.hazmat.DemoDataRunner} 种子注入）扫描
 * {@code classpath:db/migration/V*__*.sql}，按文件名序执行尚未应用（不在 {@code schema_version} 表）
 * 的脚本，执行成功后登记版本号 + 脚本名 + 内容校验和。首个基线由既有
 * {@code schema.sql}（spring.sql.init，全部 CREATE TABLE IF NOT EXISTS）承担，V1 仅登记版本。
 *
 * <p>已应用的脚本不可改写：启动时对已登记版本重算内容校验和，与库里的值不一致即阻断启动。
 * 需要变更结构时新增一个 V{n} 脚本，而不是修改历史脚本——历史脚本可能已在别的环境执行过，
 * 改动它会让不同环境的结构悄悄分叉。
 *
 * <p>幂等：已登记版本跳过；脚本自身使用 IF NOT EXISTS / 条件写入，重复执行无副作用。
 * 失败即抛出，阻止应用带病启动（升级不丢数据的前提是"迁移可见地成功"）。
 */
@Component
@Order(0)
public class SchemaMigrationRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SchemaMigrationRunner.class);

    private final JdbcTemplate jdbc;

    public SchemaMigrationRunner(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS schema_version (
                  version    VARCHAR(20)  NOT NULL PRIMARY KEY,
                  script     VARCHAR(120) NOT NULL,
                  checksum   VARCHAR(64)  DEFAULT NULL,
                  applied_at DATETIME     DEFAULT CURRENT_TIMESTAMP
                )
                """);
        Set<String> applied = new HashSet<>(jdbc.queryForList("SELECT version FROM schema_version", String.class));
        Map<String, String> appliedChecksums = new HashMap<>();
        jdbc.query("SELECT version, checksum FROM schema_version", rs -> {
            appliedChecksums.put(rs.getString("version"), rs.getString("checksum"));
        });
        List<Resource> scripts = new ArrayList<>();
        for (Resource r : new PathMatchingResourcePatternResolver().getResources("classpath:db/migration/*.sql")) {
            scripts.add(r);
        }
        scripts.sort(Comparator.comparing(r -> String.valueOf(r.getFilename())));
        if (scripts.isEmpty()) {
            log.info("[migration] 未发现 db/migration 脚本，跳过");
            return;
        }
        int executed = 0;
        for (Resource script : scripts) {
            String file = String.valueOf(script.getFilename());
            String version = versionOf(file);
            String sql = new String(script.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (applied.contains(version)) {
                verifyChecksum(file, version, appliedChecksums.get(version), sql);
                continue;
            }
            for (String stmt : splitStatements(sql)) {
                jdbc.execute(stmt);
            }
            // 防御：脚本自身若已写入同名版本（不推荐，见 V1 文件头纪律），此处只告警不重复登记
            Integer registered = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM schema_version WHERE version = ?", Integer.class, version);
            if (registered != null && registered > 0) {
                log.warn("[migration] {} 已在脚本内自行登记版本 {}，运行器跳过重复登记", file, version);
            } else {
                jdbc.update("INSERT INTO schema_version (version, script, checksum) VALUES (?, ?, ?)",
                        version, file, sha256(sql));
            }
            executed++;
            log.info("[migration] 已应用 {}（version={}）", file, version);
        }
        Integer total = jdbc.queryForObject("SELECT COUNT(*) FROM schema_version", Integer.class);
        log.info("[migration] schema 版本 {} 个（本次新应用 {} 个），当前版本 {}",
                total, executed, currentVersion());
    }

    /** V2__notify_message.sql → 2（版本号取文件名首个下划线分隔段去掉 V 前缀）。 */
    private String versionOf(String filename) {
        String head = filename.split("__", 2)[0];
        return head.replaceFirst("^[Vv]", "");
    }

    /**
     * 已应用脚本的内容校验：库里记录的校验和与当前文件不一致则阻断启动。
     * 历史库（早期版本登记时 checksum 为空）不校验，避免升级即失败。
     */
    private void verifyChecksum(String file, String version, String recorded, String sql) {
        String actual = sha256(sql);
        if (recorded == null || recorded.isBlank() || actual == null) {
            return;
        }
        if (!recorded.equalsIgnoreCase(actual)) {
            throw new IllegalStateException("[migration] 脚本 " + file + " 内容与已登记校验和不一致（version="
                    + version + "）。已应用的迁移脚本不可改写：如需变更结构，请新增 V{n}__ 脚本。");
        }
    }

    /** 当前（最大）版本号。CAST AS SIGNED 是 H2 与 MySQL 8 都接受的有符号整数转换。 */
    private String currentVersion() {
        try {
            return jdbc.queryForObject(
                    "SELECT version FROM schema_version ORDER BY CAST(version AS SIGNED) DESC LIMIT 1", String.class);
        } catch (Exception e) {
            return "unknown";
        }
    }

    /** 去除 -- 行注释后按分号切分，返回非空语句（迁移脚本约定不含存储过程/函数体）。 */
    static List<String> splitStatements(String sql) {
        StringBuilder cleaned = new StringBuilder();
        for (String line : sql.split("\n", -1)) {
            String stripped = line.stripLeading();
            if (stripped.startsWith("--")) {
                continue;
            }
            int comment = line.indexOf("--");
            cleaned.append(comment >= 0 ? line.substring(0, comment) : line).append('\n');
        }
        List<String> out = new ArrayList<>();
        for (String stmt : cleaned.toString().split(";")) {
            if (!stmt.isBlank()) {
                out.add(stmt.trim());
            }
        }
        return out;
    }

    static String sha256(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            return null;
        }
    }
}
