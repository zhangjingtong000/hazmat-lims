package com.qingyan.hazmat.common;

import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public Result<Void> handleBiz(BizException e) {
        return Result.err(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public Result<Void> handleUploadTooLarge(org.springframework.web.multipart.MaxUploadSizeExceededException e) {
        return Result.err(400, "文件超过大小限制（10MB）");
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleOther(Exception e) {
        return Result.err(500, "服务异常: " + e.getMessage());
    }
}
