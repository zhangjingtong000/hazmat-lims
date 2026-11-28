package com.qingyan.hazmat.db;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 轻量迁移单测：脚本切分（忽略注释、按分号、过滤空语句）与校验和稳定性。
 * 迁移的正确性依赖"逐语句执行 + 已应用版本跳过"，故切分口径必须可验证。
 */
class SchemaMigrationRunnerTest {

    @Test
    void 按分号切分并剔除注释与空语句() {
        String sql = """
                -- V9 示例：加注释不影响执行
                CREATE TABLE IF NOT EXISTS demo (
                  id BIGINT PRIMARY KEY  -- 行内注释
                );
                INSERT INTO schema_version (version, script)
                SELECT '9', 'V9__demo.sql'
                WHERE NOT EXISTS (SELECT 1 FROM schema_version WHERE version = '9');
                """;
        List<String> statements = SchemaMigrationRunner.splitStatements(sql);
        assertEquals(2, statements.size(), "应切出 2 条语句：" + statements);
        assertTrue(statements.get(0).startsWith("CREATE TABLE IF NOT EXISTS demo"));
        assertTrue(statements.get(0).contains("id BIGINT PRIMARY KEY"), "行内注释应被剥离");
        assertTrue(statements.get(1).startsWith("INSERT INTO schema_version"));
        assertTrue(statements.stream().noneMatch(String::isBlank));
    }

    @Test
    void 注释行不产生语句() {
        List<String> statements = SchemaMigrationRunner.splitStatements("""
                -- 只有注释
                -- 再来一行
                """);
        assertTrue(statements.isEmpty(), "纯注释文件不应产生语句：" + statements);
    }

    @Test
    void 校验和对内容敏感且稳定() {
        String a = SchemaMigrationRunner.sha256("CREATE TABLE t (id INT);");
        String b = SchemaMigrationRunner.sha256("CREATE TABLE t (id INT);");
        String c = SchemaMigrationRunner.sha256("CREATE TABLE t (id BIGINT);");
        assertEquals(a, b, "同内容校验和必须一致（幂等判定的基础）");
        assertNotEquals(a, c, "内容变化校验和必须变化");
        assertEquals(64, a.length(), "SHA-256 十六进制长度应为 64");
    }

    @Test
    void 仅V1注释修正前的历史校验和允许一次性升级() {
        String oldV1 = "d33271cad45b4f6bf2f009cbea6c0d68f7a3ccc3230f0670685d907c2f413347";
        assertTrue(SchemaMigrationRunner.isLegacyChecksum("V1__baseline.sql", oldV1));
        assertTrue(SchemaMigrationRunner.isLegacyChecksum("V1__baseline.sql", oldV1.toUpperCase()));
        assertFalse(SchemaMigrationRunner.isLegacyChecksum("V2__notify_message.sql", oldV1),
                "历史豁免只针对 V1，不能扩散到其他脚本");
        assertFalse(SchemaMigrationRunner.isLegacyChecksum("V1__baseline.sql", "deadbeef"));
        assertFalse(SchemaMigrationRunner.isLegacyChecksum("V1__baseline.sql", null));
    }
}
