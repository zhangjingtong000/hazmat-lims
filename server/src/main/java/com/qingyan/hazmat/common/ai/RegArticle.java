package com.qingyan.hazmat.common.ai;

/** 法规条文（语料单元）。law=条例|GB15603；no=条文号（如 第二十四条 / 5.10）。 */
public class RegArticle {

    private String law;
    private String no;
    private String title;
    private String text;

    public RegArticle() {
    }

    public RegArticle(String law, String no, String title, String text) {
        this.law = law;
        this.no = no;
        this.title = title;
        this.text = text;
    }

    public String getLaw() {
        return law;
    }

    public void setLaw(String law) {
        this.law = law;
    }

    public String getNo() {
        return no;
    }

    public void setNo(String no) {
        this.no = no;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }
}
