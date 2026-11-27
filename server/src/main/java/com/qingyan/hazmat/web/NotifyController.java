package com.qingyan.hazmat.web;

import com.qingyan.hazmat.common.Result;
import com.qingyan.hazmat.system.NotifyService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 站内信（G6）：我的消息分页 / 未读数 / 单条已读 / 全部已读。
 * 仅需登录——消息按 target_user_id 归属，天然隔离（含全员广播）。
 */
@RestController
@RequestMapping("/system/notify")
public class NotifyController {

    private final NotifyService notifyService;

    public NotifyController(NotifyService notifyService) {
        this.notifyService = notifyService;
    }

    @GetMapping("/list")
    public Result<Map<String, Object>> list(@RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "10") int size,
                                            HttpServletRequest request) {
        return Result.ok(notifyService.list(uid(request), page, size));
    }

    @GetMapping("/unread-count")
    public Result<Map<String, Object>> unread(HttpServletRequest request) {
        return Result.ok(Map.of("count", notifyService.unread(uid(request))));
    }

    @PostMapping("/{id}/read")
    public Result<Void> read(@PathVariable Long id, HttpServletRequest request) {
        notifyService.markRead(id, uid(request));
        return Result.ok();
    }

    @PostMapping("/read-all")
    public Result<Map<String, Object>> readAll(HttpServletRequest request) {
        return Result.ok(Map.of("updated", notifyService.markAllRead(uid(request))));
    }

    private long uid(HttpServletRequest request) {
        return ((Number) request.getAttribute("uid")).longValue();
    }
}
