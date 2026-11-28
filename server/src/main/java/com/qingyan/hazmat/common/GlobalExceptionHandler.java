package com.qingyan.hazmat.common;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 统一异常出口：业务异常按业务码返回；资源缺失返回 404；其余异常只返回通用提示。
 *
 * <p>不把 {@code e.getMessage()} 回给客户端：异常消息可能带表名、SQL 片段、绝对路径或
 * 依赖版本等内部细节，既泄露实现也帮不上使用者。细节写日志（带 traceId）。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 缺少前端产物时的提示：干净 clone 后直接启动会走到这里，必须说清下一步做什么。 */
    static final String FRONTEND_MISSING =
            "前端未构建，请先 npm run build（cd web && npm ci && npm run build，"
                    + "再把 dist 内容拷到 server/src/main/resources/static），或直接在仓库根目录运行 ./start.sh";

    @ExceptionHandler(BizException.class)
    public Result<Void> handleBiz(BizException e) {
        return Result.err(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public Result<Void> handleUploadTooLarge(org.springframework.web.multipart.MaxUploadSizeExceededException e) {
        return Result.err(400, "文件超过大小限制（10MB）");
    }

    /** 静态资源不存在（含前端未构建时的 "/"）：返回 404，而不是 500。 */
    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Result<Void> handleNoResource(NoResourceFoundException e, HttpServletRequest request) {
        String path = request.getRequestURI();
        if ("/".equals(path) || "/index.html".equals(path)) {
            log.warn("[web] 前端入口缺失：{}（静态目录里没有 index.html）", path);
            return Result.err(404, FRONTEND_MISSING);
        }
        return Result.err(404, "资源不存在：" + path);
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleOther(Exception e) {
        log.error("[web] 未处理异常", e);
        return Result.err(500, "服务异常，请稍后重试");
    }
}
