package com.qingyan.hazmat.common.ai;

/**
 * 防御性 AI 层（defensive-ai-spec §3）：LLM 客户端 SPI。
 * 单轮补全（system+user → content），实现方保证"失败即降级"——任何未配置/网络/解析问题
 * 一律返回 null，绝不向业务层抛异常（铁律 2：AI 通道不引入新业务错误码）。
 */
public interface AiClient {

    /**
     * 单轮补全。返回模型 message.content；未配置 key、超时、网络错误、响应不合法时返回 null。
     */
    String chat(String systemPrompt, String userMsg);
}
