package com.qingyan.hazmat.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 请求追踪：短码生成口径（8 位十六进制、弱随机不重复即可）。 */
class TraceIdFilterTest {

    @Test
    void 短码为8位十六进制() {
        for (int i = 0; i < 200; i++) {
            String code = TraceIdFilter.shortCode();
            assertEquals(8, code.length(), "traceId 长度应为 8");
            assertTrue(code.matches("[0-9a-f]{8}"), "traceId 应为十六进制：" + code);
        }
    }

    @Test
    void 短码在多次生成中基本不重复() {
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (int i = 0; i < 500; i++) {
            seen.add(TraceIdFilter.shortCode());
        }
        // 4 字节熵在 500 次抽样下碰撞概率极低；留足余量断言
        assertTrue(seen.size() > 490, "短码重复过多：" + seen.size());
        assertFalse(seen.contains(null));
    }
}
