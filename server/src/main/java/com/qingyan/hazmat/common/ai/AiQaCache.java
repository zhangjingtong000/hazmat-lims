package com.qingyan.hazmat.common.ai;

import java.time.LocalDateTime;

/** AI 问答缓存（ai_qa_cache 表，defensive-ai-spec §3）。 */
public class AiQaCache {

    private Long id;
    private String questionHash;
    private String questionNorm;
    private String intentJson;
    private String answerSummary;
    private String source;
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getQuestionHash() {
        return questionHash;
    }

    public void setQuestionHash(String questionHash) {
        this.questionHash = questionHash;
    }

    public String getQuestionNorm() {
        return questionNorm;
    }

    public void setQuestionNorm(String questionNorm) {
        this.questionNorm = questionNorm;
    }

    public String getIntentJson() {
        return intentJson;
    }

    public void setIntentJson(String intentJson) {
        this.intentJson = intentJson;
    }

    public String getAnswerSummary() {
        return answerSummary;
    }

    public void setAnswerSummary(String answerSummary) {
        this.answerSummary = answerSummary;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
