package com.qingyan.hazmat.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * 接口限流单测：只读 GET 不计数；登录与写接口各自窗口；超限返回 HTTP 429 + 业务码 4290。
 */
class RateLimitFilterTest {

    @Test
    void 只读请求不参与限流() {
        RateLimitFilter filter = new RateLimitFilter(1, 1);
        MockHttpServletRequest get = new MockHttpServletRequest("GET", "/chem/info/list");
        MockHttpServletRequest post = new MockHttpServletRequest("POST", "/chem/info");
        MockHttpServletRequest put = new MockHttpServletRequest("PUT", "/chem/info");
        MockHttpServletRequest del = new MockHttpServletRequest("DELETE", "/chem/info/1");
        assertTrue(filter.shouldNotFilter(get), "GET 应跳过限流");
        assertFalse(filter.shouldNotFilter(post));
        assertFalse(filter.shouldNotFilter(put));
        assertFalse(filter.shouldNotFilter(del));
    }

    @Test
    void 登录限流按窗口上限拒绝并给出4290() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(1, 100);
        FilterChain chain = mock(FilterChain.class);

        MockHttpServletRequest first = new MockHttpServletRequest("POST", "/auth/login");
        first.setRemoteAddr("10.1.1.1");
        MockHttpServletResponse firstResp = new MockHttpServletResponse();
        filter.doFilterInternal(first, firstResp, chain);
        assertEquals(200, firstResp.getStatus(), "首次登录请求应放行");

        MockHttpServletRequest second = new MockHttpServletRequest("POST", "/auth/login");
        second.setRemoteAddr("10.1.1.1");
        MockHttpServletResponse secondResp = new MockHttpServletResponse();
        filter.doFilterInternal(second, secondResp, chain);
        assertEquals(429, secondResp.getStatus(), "超限应返回 HTTP 429");
        assertTrue(secondResp.getContentAsString().contains("\"code\":4290"),
                "响应体应带业务码 4290：" + secondResp.getContentAsString());
    }

    @Test
    void 登录与写接口窗口独立_不同IP互不影响() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(1, 1);
        FilterChain chain = mock(FilterChain.class);

        MockHttpServletRequest login = new MockHttpServletRequest("POST", "/auth/login");
        login.setRemoteAddr("10.2.2.2");
        filter.doFilterInternal(login, new MockHttpServletResponse(), chain);

        // 同 IP 再登录 → 拒绝；同 IP 写接口 → 独立额度仍放行
        MockHttpServletRequest login2 = new MockHttpServletRequest("POST", "/auth/login");
        login2.setRemoteAddr("10.2.2.2");
        MockHttpServletResponse login2Resp = new MockHttpServletResponse();
        filter.doFilterInternal(login2, login2Resp, chain);
        assertEquals(429, login2Resp.getStatus());

        MockHttpServletRequest write = new MockHttpServletRequest("POST", "/chem/info");
        write.setRemoteAddr("10.2.2.2");
        MockHttpServletResponse writeResp = new MockHttpServletResponse();
        filter.doFilterInternal(write, writeResp, chain);
        assertEquals(200, writeResp.getStatus(), "写接口额度与登录额度应相互独立");

        // 另一 IP 的登录不受影响
        MockHttpServletRequest other = new MockHttpServletRequest("POST", "/auth/login");
        other.setRemoteAddr("10.3.3.3");
        MockHttpServletResponse otherResp = new MockHttpServletResponse();
        filter.doFilterInternal(other, otherResp, chain);
        assertEquals(200, otherResp.getStatus());
    }

    @Test
    void 阈值暴露与清理不抛异常() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(7, 70);
        assertEquals(7, filter.loginPerMinute());
        assertEquals(70, filter.writePerMinute());
        MockHttpServletRequest post = new MockHttpServletRequest("POST", "/auth/login");
        post.setRemoteAddr("10.4.4.4");
        filter.doFilterInternal(post, new MockHttpServletResponse(), mock(FilterChain.class));
        filter.sweep();
        filter.sweep();
    }
}
