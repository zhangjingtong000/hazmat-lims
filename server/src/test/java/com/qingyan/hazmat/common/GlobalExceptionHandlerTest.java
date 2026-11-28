package com.qingyan.hazmat.common;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 异常出口单测：前端未构建时 "/" 返回 404 + 人话提示；未预期异常不把内部消息回给客户端。
 */
class GlobalExceptionHandlerTest {

    @RestController
    static class BoomController {
        @GetMapping("/")
        public void index() throws NoResourceFoundException {
            throw new NoResourceFoundException(HttpMethod.GET, "/",
                    "No static resource for request '/'.");
        }

        @GetMapping("/boom")
        public void boom() {
            throw new IllegalStateException("jdbc:h2:file:C:/srv/secret-db;SQL 失败 at 内部类");
        }
    }

    private MockMvc mvc() {
        return MockMvcBuilders.standaloneSetup(new BoomController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void 前端产物缺失时根路径返回404与可操作提示() throws Exception {
        mvc().perform(get("/"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value(containsString("前端未构建")))
                .andExpect(jsonPath("$.message").value(containsString("npm run build")));
    }

    @Test
    void 未预期异常返回通用提示_不泄露异常消息() throws Exception {
        mvc().perform(get("/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value(not(containsString("jdbc"))))
                .andExpect(jsonPath("$.message").value(not(containsString("secret"))))
                .andExpect(jsonPath("$.message").value("服务异常，请稍后重试"));
    }
}
