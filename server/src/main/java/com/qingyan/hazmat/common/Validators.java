package com.qingyan.hazmat.common;

import java.math.BigDecimal;
import java.util.regex.Pattern;

/**
 * 服务端参数校验工具（A4 后端侧 / E6）：写接口不信任前端，一律在此类做
 * 必填、长度、数值范围与格式校验。失败统一错误码 {@link #CODE_INVALID}（4001），
 * 与业务守卫码（41xx/42xx/43xx/44xx/45xx/46xx）区分：前者是"请求不合法"，
 * 后者是"业务规则不允许"。
 *
 * <p>校验口径与 DB 列宽一致（VARCHAR(100) 等），避免依赖截断行为；
 * 前端 el-form 的 rules 与此处一一对应（详见 docs/api.md 的表单校验矩阵）。
 */
public final class Validators {

    /** 参数校验失败（HTTP 200 + 业务码，前端按表单错误提示）。 */
    public static final int CODE_INVALID = 4001;

    /** CAS 登记号：2~7 位数字 - 2 位数字 - 1 位数字（如 67-64-1、7647-14-5）。 */
    private static final Pattern CAS = Pattern.compile("^\\d{2,7}-\\d{2}-\\d$");

    private Validators() {
    }

    public static void fail(String message) {
        throw new BizException(CODE_INVALID, message);
    }

    /** 必填字符串（去空白后非空），返回去空白结果。 */
    public static String required(String value, String label) {
        if (value == null || value.isBlank()) {
            fail(label + "不能为空");
        }
        return value.trim();
    }

    public static void notNull(Object value, String label) {
        if (value == null) {
            fail(label + "不能为空");
        }
    }

    /** 非空可选字符串的长度上限（null/空串放行）。 */
    public static void maxLen(String value, int max, String label) {
        if (value != null && value.length() > max) {
            fail(label + "长度不能超过 " + max + " 个字符");
        }
    }

    /** 必填 + 长度上限。 */
    public static String requiredLen(String value, int max, String label) {
        String v = required(value, label);
        if (v.length() > max) {
            fail(label + "长度不能超过 " + max + " 个字符");
        }
        return v;
    }

    /** 整数范围（含边界）。 */
    public static void range(Integer value, int min, int max, String label) {
        notNull(value, label);
        if (value < min || value > max) {
            fail(label + "取值须在 " + min + "~" + max + " 之间");
        }
    }

    /** 正数（>0）。 */
    public static void positive(BigDecimal value, String label) {
        notNull(value, label);
        if (value.compareTo(BigDecimal.ZERO) <= 0) {
            fail(label + "必须大于 0");
        }
    }

    /** 非负数（≥0，可空时放行）。 */
    public static void nonNegative(BigDecimal value, String label) {
        if (value != null && value.compareTo(BigDecimal.ZERO) < 0) {
            fail(label + "不能为负数");
        }
    }

    /** 非负整数（≥0，可空时放行）。 */
    public static void nonNegative(Integer value, String label) {
        if (value != null && value < 0) {
            fail(label + "不能为负数");
        }
    }

    /** CAS 号格式（可空放行——部分试剂无 CAS 登记号）。 */
    public static void casNo(String casNo) {
        if (casNo == null || casNo.isBlank()) {
            return;
        }
        if (!CAS.matcher(casNo.trim()).matches()) {
            fail("CAS 号格式不正确（示例：67-64-1）");
        }
    }

    /** 正则格式校验（可空放行）。 */
    public static void pattern(String value, Pattern pattern, String label) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (!pattern.matcher(value).matches()) {
            fail(label + "格式不正确");
        }
    }

    /**
     * E3 口令策略：长度 8~64，且须包含「小写字母 / 大写字母 / 数字 / 符号」中至少两类；
     * 不得与用户名相同。仅在设置/重置口令时校验（登录不校验，避免历史口令无法登录）。
     */
    public static void password(String value, String username) {
        String pwd = required(value, "密码");
        if (pwd.length() < 8 || pwd.length() > 64) {
            fail("密码长度须为 8~64 位");
        }
        int classes = 0;
        if (pwd.matches(".*[a-z].*")) {
            classes++;
        }
        if (pwd.matches(".*[A-Z].*")) {
            classes++;
        }
        if (pwd.matches(".*\\d.*")) {
            classes++;
        }
        if (pwd.matches(".*[^A-Za-z0-9].*")) {
            classes++;
        }
        if (classes < 2) {
            fail("密码须包含小写字母、大写字母、数字、符号中的至少两类");
        }
        if (username != null && !username.isBlank() && pwd.equalsIgnoreCase(username.trim())) {
            fail("密码不能与用户名相同");
        }
    }
}
