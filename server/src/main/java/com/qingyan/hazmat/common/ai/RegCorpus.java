package com.qingyan.hazmat.common.ai;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 法规语料加载器 + 关键词/词频 top-k 检索（零新依赖，不引向量库——defensive-ai-spec §3）。
 * 语料打进 jar：classpath:regs/*.json（文件头 // 注释行记录来源 URL 与采集日期，解析前剥离）。
 * 目录为空/文件缺失时语料列表为空（法规通道自动隐藏），不影响其余功能。
 */
@Component
public class RegCorpus {

    private static final Logger log = LoggerFactory.getLogger(RegCorpus.class);

    /** 全文检索词元：ASCII 词（小写）+ CJK 相邻双字。 */
    private static final Pattern ASCII = Pattern.compile("[0-9A-Za-z]+");
    private static final Pattern CJK = Pattern.compile("[\\u4e00-\\u9fff]+");

    private final List<RegArticle> articles = new ArrayList<>();
    private final Map<String, RegArticle> byKey = new HashMap<>(); // law + ' ' + no → 条文

    private final ObjectMapper mapper = new ObjectMapper();

    public RegCorpus() {
        try {
            var resources = new PathMatchingResourcePatternResolver()
                    .getResources("classpath:regs/*.json");
            for (var res : resources) {
                try (var in = res.getInputStream()) {
                    String raw = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                    StringBuilder json = new StringBuilder();
                    for (String line : raw.split("\n", -1)) {
                        if (!line.stripLeading().startsWith("//")) {
                            json.append(line).append('\n');
                        }
                    }
                    List<RegArticle> parsed = mapper.readValue(json.toString(),
                            new TypeReference<List<RegArticle>>() {
                            });
                    for (RegArticle a : parsed) {
                        if (a.getLaw() != null && a.getNo() != null && a.getText() != null
                                && !a.getText().isBlank()) {
                            articles.add(a);
                            byKey.put(a.getLaw() + " " + a.getNo(), a);
                        }
                    }
                }
            }
        } catch (Exception e) {
            // 语料缺失/损坏不是错误：法规通道自动隐藏（静默，不影响其余功能）
            log.info("法规语料未加载（{}），法规检索通道自动隐藏", e.getClass().getSimpleName());
        }
        log.info("法规语料已加载：{} 条", articles.size());
    }

    /** 测试夹具入口：以内存条文构建。 */
    public RegCorpus(List<RegArticle> fixture) {
        articles.addAll(fixture);
        for (RegArticle a : fixture) {
            byKey.put(a.getLaw() + " " + a.getNo(), a);
        }
    }

    /** 语料是否可用（法规通道开关）。 */
    public boolean isEnabled() {
        return !articles.isEmpty();
    }

    public List<RegArticle> all() {
        return List.copyOf(articles);
    }

    /** 按条文号精确取（缓存回放用，key=law+" "+no）。 */
    public RegArticle findByNo(String law, String no) {
        return byKey.get(law + " " + no);
    }

    /**
     * 关键词/词频 top-k 检索：词元带 IDF 权重打分（稀有词如"堆码""双人收发"权重高，
     * 高频通用词如"化学品"权重低——防超长条文霸榜），score>0 的条文按分排序取前 k（稳定序）。
     */
    public List<RegArticle> search(String question, int topK) {
        if (articles.isEmpty() || question == null || question.isBlank()) {
            return List.of();
        }
        List<String> tokens = tokenize(question).stream().distinct().toList();
        // 每词元语料文档频次 → IDF 权重（N/df，双字组词天然稀疏，无需 log 平滑）
        Map<String, Double> weights = new HashMap<>();
        for (String t : tokens) {
            int df = 0;
            for (RegArticle a : articles) {
                if (haystack(a).contains(t)) {
                    df++;
                }
            }
            if (df > 0) {
                weights.put(t, (double) articles.size() / df);
            }
        }
        List<Map.Entry<RegArticle, Integer>> scored = new ArrayList<>();
        for (int i = 0; i < articles.size(); i++) {
            RegArticle a = articles.get(i);
            int score = score(a, tokens, weights);
            if (score > 0) {
                scored.add(Map.entry(a, score * 1000 + (articles.size() - i))); // 同分保原始序（稳定）
            }
        }
        scored.sort((x, y) -> Integer.compare(y.getValue(), x.getValue()));
        List<RegArticle> top = new ArrayList<>();
        for (Map.Entry<RegArticle, Integer> e : scored) {
            if (top.size() >= topK) {
                break;
            }
            top.add(e.getKey());
        }
        return top;
    }

    private int score(RegArticle a, List<String> tokens, Map<String, Double> weights) {
        String hay = haystack(a).toLowerCase();
        int score = 0;
        for (String t : tokens) {
            Double w = weights.get(t);
            if (w != null) {
                // 词元频次封顶 3：长条文靠重复词再多刷分也有限
                score += (int) Math.round(w * Math.min(count(hay, t), 3));
            }
        }
        return score;
    }

    private String haystack(RegArticle a) {
        return (a.getLaw() + " " + a.getNo() + " " + a.getTitle() + " " + a.getText()).toLowerCase();
    }

    private static int count(String hay, String needle) {
        int n = 0;
        int idx = 0;
        while ((idx = hay.indexOf(needle, idx)) >= 0) {
            n++;
            idx += needle.length();
        }
        return n;
    }

    /** 分词：ASCII 连续段（小写）+ 中文双字组（单字文本降级为单字）。 */
    public static List<String> tokenize(String q) {
        List<String> tokens = new ArrayList<>();
        Matcher m = ASCII.matcher(q);
        while (m.find()) {
            tokens.add(m.group().toLowerCase());
        }
        Matcher c = CJK.matcher(q);
        while (c.find()) {
            String seg = c.group();
            if (seg.length() == 1) {
                tokens.add(seg);
            } else {
                for (int i = 0; i + 1 < seg.length(); i++) {
                    tokens.add(seg.substring(i, i + 2));
                }
            }
        }
        return tokens;
    }
}
