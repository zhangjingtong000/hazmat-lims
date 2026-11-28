package com.qingyan.hazmat.common;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 参数校验单测：必填/长度/数值范围/CAS 格式/口令策略。
 * 口径：失败一律 4001（前端按"请求不合法"提示），不与 41xx 业务守卫混用。
 */
class ValidatorsTest {

    private void expectInvalid(String messagePart, Runnable action) {
        BizException e = assertThrows(BizException.class, action::run);
        assertEquals(Validators.CODE_INVALID, e.getCode());
        assertTrue(e.getMessage().contains(messagePart), "实际文案：" + e.getMessage());
    }

    @Test
    void required_拒绝空白并返回去空白结果() {
        assertEquals("丙酮", Validators.required("  丙酮 ", "品名"));
        expectInvalid("品名不能为空", () -> Validators.required("   ", "品名"));
        expectInvalid("品名不能为空", () -> Validators.required(null, "品名"));
    }

    @Test
    void requiredLen_同时校验必填与长度上限() {
        assertEquals("氯化钠", Validators.requiredLen("氯化钠", 100, "品名"));
        expectInvalid("不能超过 3 个字符", () -> Validators.requiredLen("氯化钠x", 3, "品名"));
    }

    @Test
    void maxLen_空值放行非空超限拒绝() {
        assertDoesNotThrow(() -> Validators.maxLen(null, 5, "别名"));
        assertDoesNotThrow(() -> Validators.maxLen("", 5, "别名"));
        assertDoesNotThrow(() -> Validators.maxLen("12345", 5, "别名"));
        expectInvalid("别名长度不能超过 5", () -> Validators.maxLen("123456", 5, "别名"));
    }

    @Test
    void range_边界含端且拒绝越界() {
        assertDoesNotThrow(() -> Validators.range(0, 0, 4, "分类"));
        assertDoesNotThrow(() -> Validators.range(4, 0, 4, "分类"));
        expectInvalid("分类取值须在 0~4 之间", () -> Validators.range(5, 0, 4, "分类"));
        expectInvalid("分类不能为空", () -> Validators.range(null, 0, 4, "分类"));
    }

    @Test
    void positive_零与负数均拒绝() {
        assertDoesNotThrow(() -> Validators.positive(new BigDecimal("0.01"), "数量"));
        expectInvalid("数量必须大于 0", () -> Validators.positive(BigDecimal.ZERO, "数量"));
        expectInvalid("数量必须大于 0", () -> Validators.positive(new BigDecimal("-1"), "数量"));
    }

    @Test
    void nonNegative_可空但负数拒绝() {
        assertDoesNotThrow(() -> Validators.nonNegative((BigDecimal) null, "安全库存下限"));
        assertDoesNotThrow(() -> Validators.nonNegative(BigDecimal.ZERO, "安全库存下限"));
        expectInvalid("不能为负数", () -> Validators.nonNegative(new BigDecimal("-0.01"), "安全库存下限"));
        expectInvalid("不能为负数", () -> Validators.nonNegative(-1, "排序"));
    }

    @Test
    void casNo_可空但格式必须正确() {
        assertDoesNotThrow(() -> Validators.casNo(null));
        assertDoesNotThrow(() -> Validators.casNo(""));
        assertDoesNotThrow(() -> Validators.casNo("67-64-1"));
        assertDoesNotThrow(() -> Validators.casNo("7647-14-5"));
        expectInvalid("CAS 号格式不正确", () -> Validators.casNo("6764-1"));
        expectInvalid("CAS 号格式不正确", () -> Validators.casNo("ABC-64-1"));
    }

    @Test
    void password_长度与字符类别双约束() {
        assertDoesNotThrow(() -> Validators.password("Abcd1234", "admin"));
        assertDoesNotThrow(() -> Validators.password("abcd_1234", "admin"));
        // 仅小写一类 → 拒绝
        expectInvalid("至少两类", () -> Validators.password("abcdefgh", "admin"));
        // 仅数字一类 → 拒绝
        expectInvalid("至少两类", () -> Validators.password("12345678", "admin"));
        expectInvalid("长度须为 8~64 位", () -> Validators.password("Ab1", "admin"));
        // 与用户名相同（用户名本身需够长才能构造该场景）
        expectInvalid("密码不能与用户名相同", () -> Validators.password("Admin1234", "Admin1234"));
        expectInvalid("密码不能为空", () -> Validators.password(null, "admin"));
    }
}
