package com.qingyan.hazmat.security;

import com.qingyan.hazmat.common.BizException;
import com.qingyan.hazmat.system.AuthService;
import com.qingyan.hazmat.web.AuthController;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 登录防爆破回归测试（P0）：客户端每次伪造不同的 X-Forwarded-For，
 * 防护键必须仍按 TCP 对端地址归并——第 5 次失败照常锁定（4291），不能无限重试。
 */
class LoginBruteForceTest {

    private static AuthController controller(LoginAttemptService attempts, ClientIpResolver resolver) {
        AuthService auth = mock(AuthService.class);
        when(auth.login(anyString(), anyString()))
                .thenThrow(new BizException(401, "用户名或密码错误"));
        return new AuthController(auth, mock(TokenService.class), attempts, resolver);
    }

    private static Map<String, String> badLogin() {
        return Map.of("username", "admin", "password", "wrong-password");
    }

    private static MockHttpServletRequest request(String fakeXff) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/login");
        request.setRemoteAddr("10.0.0.1");
        request.addHeader("X-Forwarded-For", fakeXff);
        return request;
    }

    private static int codeOf(Throwable e) {
        return ((BizException) e).getCode();
    }

    @Test
    void 伪造不同XFF连发_第5次仍被锁定() {
        LoginAttemptService attempts = new LoginAttemptService(5, 10, 10);
        ClientIpResolver resolver = new ClientIpResolver(false, "");
        AuthController controller = controller(attempts, resolver);

        // 每次换一个伪造 XFF，模拟"绕过锁定"的爆破手法
        for (int i = 1; i <= 4; i++) {
            final int n = i;
            BizException e = assertThrows(BizException.class,
                    () -> controller.login(badLogin(), request("10.0.0." + n)));
            assertEquals(401, codeOf(e), "第 " + i + " 次失败应是普通认证失败");
        }

        BizException fifth = assertThrows(BizException.class,
                () -> controller.login(badLogin(), request("172.16.99.99")));
        assertEquals(LoginAttemptService.CODE_LOCKED, codeOf(fifth), "第 5 次失败必须触发锁定");

        BizException sixth = assertThrows(BizException.class,
                () -> controller.login(badLogin(), request("203.0.113.7")));
        assertEquals(LoginAttemptService.CODE_LOCKED, codeOf(sixth), "锁定后换 XFF 仍应被拒绝");
    }

    @Test
    void 受信反代场景_XFF分属不同真实客户端时各自计数() {
        LoginAttemptService attempts = new LoginAttemptService(5, 10, 10);
        ClientIpResolver resolver = new ClientIpResolver(true, "10.0.0.1");
        AuthController controller = controller(attempts, resolver);

        for (int i = 1; i <= 4; i++) {
            final int n = i;
            BizException e = assertThrows(BizException.class,
                    () -> controller.login(badLogin(), request("192.0.2." + n)));
            assertEquals(401, codeOf(e), "受信反代下每个真实客户端独立计数");
        }
        // 第 5 个真实客户端首次失败，不应被别人的 4 次失败连带锁定
        BizException fresh = assertThrows(BizException.class,
                () -> controller.login(badLogin(), request("192.0.2.50")));
        assertEquals(401, codeOf(fresh));
    }
}
