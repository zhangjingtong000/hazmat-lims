package com.qingyan.hazmat.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 客户端 IP 解析单测：默认不信任转发头；只有 trust-proxy=true 且直连方在受信清单内，
 * 才从 XFF 最右侧取真实来源（左侧伪造值被跳过）。
 */
class ClientIpResolverTest {

    private static MockHttpServletRequest request(String remoteAddr, String xff) {
        MockHttpServletRequest r = new MockHttpServletRequest("POST", "/auth/login");
        r.setRemoteAddr(remoteAddr);
        if (xff != null) {
            r.addHeader("X-Forwarded-For", xff);
        }
        return r;
    }

    @Test
    void 默认不信任XFF_始终返回TCP对端地址() {
        ClientIpResolver resolver = new ClientIpResolver(false, "");
        for (int i = 1; i <= 3; i++) {
            assertEquals("10.0.0.5", resolver.resolve(request("10.0.0.5", "10.9.9." + i)),
                    "默认配置下伪造 XFF 不应改变客户端 IP");
        }
        assertFalse(resolver.trustProxy());
    }

    @Test
    void 开启但受信清单为空_仍不读XFF() {
        ClientIpResolver resolver = new ClientIpResolver(true, "  ");
        assertEquals("10.0.0.5", resolver.resolve(request("10.0.0.5", "1.2.3.4")));
        assertTrue(resolver.trustedProxies().isEmpty());
    }

    @Test
    void 直连方不在受信清单_忽略XFF() {
        ClientIpResolver resolver = new ClientIpResolver(true, "172.16.0.0/12");
        assertEquals("10.0.0.5", resolver.resolve(request("10.0.0.5", "1.2.3.4")));
    }

    @Test
    void 受信反代_取最右侧非受信地址_忽略客户端左侧伪造() {
        ClientIpResolver resolver = new ClientIpResolver(true, "10.0.0.0/8, 127.0.0.1");
        // 客户端伪造 "6.6.6.6"，最右侧 "203.0.113.9" 才是最近一跳反代写入的真实来源
        assertEquals("203.0.113.9",
                resolver.resolve(request("10.0.0.5", "6.6.6.6, 203.0.113.9")));
        assertEquals("203.0.113.9",
                resolver.resolve(request("10.0.0.5", "6.6.6.6,203.0.113.9, 10.0.0.7")));
    }

    @Test
    void 受信反代_仅XRealIP时使用之_且校验为IP字面量() {
        ClientIpResolver resolver = new ClientIpResolver(true, "10.0.0.1");
        // 没有 XFF / X-Real-IP 时仍回落到直连地址
        assertEquals("10.0.0.1", resolver.resolve(request("10.0.0.1", null)));
        MockHttpServletRequest withReal = request("10.0.0.1", null);
        withReal.addHeader("X-Real-IP", "198.51.100.7");
        assertEquals("198.51.100.7", resolver.resolve(withReal));

        MockHttpServletRequest bogus = request("10.0.0.1", null);
        bogus.addHeader("X-Real-IP", "not-an-ip");
        assertEquals("10.0.0.1", resolver.resolve(bogus), "非 IP 字面量不应进入锁定键");
    }

    @Test
    void 全链都是受信代理_回落到直连地址() {
        ClientIpResolver resolver = new ClientIpResolver(true, "10.0.0.0/8");
        assertEquals("10.0.0.5", resolver.resolve(request("10.0.0.5", "10.1.1.1, 10.2.2.2")));
    }

    @Test
    void 通配与精确规则_端口与IPv6规范化() {
        ClientIpResolver resolver = new ClientIpResolver(true, "192.168.1.*");
        assertEquals("203.0.113.9", resolver.resolve(request("192.168.1.20", "203.0.113.9:4711")));

        ClientIpResolver v6 = new ClientIpResolver(true, "::1");
        assertEquals("2001:db8::9", v6.resolve(request("0:0:0:0:0:0:0:1", "2001:db8::9")));
    }
}
