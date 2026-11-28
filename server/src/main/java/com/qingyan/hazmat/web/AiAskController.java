package com.qingyan.hazmat.web;

import com.qingyan.hazmat.common.Result;
import com.qingyan.hazmat.common.ai.AiAskService;
import com.qingyan.hazmat.security.RequirePerm;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 智能查询（防御性 AI 层）：POST /ai/ask {question}。
 * 响应 data：{intent, conditions, summary, rows, total, regs, source}——rows 与台账列表接口同构，
 * regs 为命中的法规条文；source=ai|cache|keyword（前端徽标：智能解析/缓存回放/关键词匹配）。
 * 权限键 hzm:ai:ask（25 键体系扩展第 26 键，授予全部演示角色——防御位人人可演示）。
 * 服务层"任何异常静默降级"承诺兜底：本控制器不产生新业务错误码。
 */
@RestController
@RequestMapping("/ai")
public class AiAskController {

    private final AiAskService aiAskService;

    public AiAskController(AiAskService aiAskService) {
        this.aiAskService = aiAskService;
    }

    @PostMapping("/ask")
    @RequirePerm("hzm:ai:ask")
    public Result<Map<String, Object>> ask(@RequestBody Map<String, String> body) {
        String question = body == null ? "" : body.getOrDefault("question", "");
        return Result.ok(aiAskService.ask(question));
    }
}
