package com.qingyan.hazmat.web;

import com.qingyan.hazmat.common.ai.RegArticle;
import com.qingyan.hazmat.common.ai.RegCorpus;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * F5 健康检查单测：db 探活成功/失败决定整体 status；语料为空与 AI 未配置均降级但不报错。
 */
class HealthControllerTest {

    private RegCorpus corpus(int size) {
        List<RegArticle> fixture = new java.util.ArrayList<>();
        for (int i = 0; i < size; i++) {
            fixture.add(new RegArticle("条例", "第" + i + "条", "标题" + i, "正文" + i));
        }
        return new RegCorpus(fixture);
    }

    @Test
    void 数据库正常时整体UP且三通道齐备() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(eq("SELECT 1"), eq(Integer.class))).thenReturn(1);
        // 用必然存在的环境变量名（PATH）验证"已配置 key → up"；用不可能存在的名字验证降级
        HealthController controller = new HealthController(jdbc, corpus(3), "1.0.0", "PATH");

        Map<String, Object> body = controller.health();
        assertEquals("UP", body.get("status"));
        assertEquals("1.0.0", body.get("version"));
        assertNotNull(body.get("time"));
        @SuppressWarnings("unchecked")
        Map<String, Object> checks = (Map<String, Object>) body.get("checks");
        assertEquals("up", checks.get("db"));
        assertEquals("up(3)", checks.get("regs"));
        assertEquals("up", checks.get("ai"), "已配置 key 时应为 up");
    }

    @Test
    void 未配置AI密钥时显示降级而非异常() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(eq("SELECT 1"), eq(Integer.class))).thenReturn(1);
        HealthController controller = new HealthController(jdbc, corpus(1), "1.0.0",
                "HAZMAT_TEST_KEY_DEFINITELY_NOT_SET");
        @SuppressWarnings("unchecked")
        Map<String, Object> checks = (Map<String, Object>) controller.health().get("checks");
        assertEquals("degraded(no-key)", checks.get("ai"));
    }

    @Test
    void 数据库探活失败时整体DOWN且不抛异常() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Integer.class)))
                .thenThrow(new IllegalStateException("connection refused"));
        HealthController controller = new HealthController(jdbc, corpus(1), "1.0.0", "PATH");

        Map<String, Object> body = controller.health();
        assertEquals("DOWN", body.get("status"));
        @SuppressWarnings("unchecked")
        Map<String, Object> checks = (Map<String, Object>) body.get("checks");
        assertEquals("down", checks.get("db"));
    }

    @Test
    void 语料为空时显示降级而不是UP() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(eq("SELECT 1"), eq(Integer.class))).thenReturn(1);
        HealthController controller = new HealthController(jdbc, corpus(0), "1.0.0", "PATH");

        @SuppressWarnings("unchecked")
        Map<String, Object> checks = (Map<String, Object>) controller.health().get("checks");
        assertEquals("degraded(0)", checks.get("regs"), "语料为空时法规通道应显式降级");
    }
}
