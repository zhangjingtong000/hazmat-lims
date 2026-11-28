package com.qingyan.hazmat.service;

import com.qingyan.hazmat.common.ai.AiAskService;
import com.qingyan.hazmat.common.ai.AiClient;
import com.qingyan.hazmat.common.ai.AiQaCache;
import com.qingyan.hazmat.hazmat.mapper.AiQaCacheMapper;
import com.qingyan.hazmat.common.ai.RegArticle;
import com.qingyan.hazmat.common.ai.RegCorpus;
import com.qingyan.hazmat.hazmat.entity.ChemInfo;
import com.qingyan.hazmat.hazmat.mapper.ChemInfoMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 防御性 AI 层降级链单测：缓存命中不出网、无 key 数据问题走
 * 关键词降级、LLM 输出白名单外字段过滤、语料检索 top-k 命中。全部不真连外网。
 */
class AiAskServiceTest {

    private AiClient aiClient;
    @SuppressWarnings("unchecked")
    private final ObjectProvider<AiClient> aiProvider = Mockito.mock(ObjectProvider.class);
    private RegCorpus corpus;
    private AiQaCacheMapper cacheMapper;
    private ChemInfoMapper chemMapper;
    private AiAskService service;

    @BeforeEach
    void setUp() {
        aiClient = Mockito.mock(AiClient.class);
        when(aiProvider.getIfAvailable()).thenReturn(aiClient);
        corpus = new RegCorpus(List.of(
                new RegArticle("GB15603", "5.10", "5 储存要求",
                        "剧毒化学品、监控化学品、易制毒化学品、易制爆危险化学品，应按规定将储存地点、储存数量、"
                                + "流向及管理人员的情况报相关部门备案，剧毒化学品应在专用仓库内单独存放，并实行双人收发、双人保管制度。"),
                new RegArticle("GB15603", "6.2.5", "6 装卸搬运与堆码",
                        "仓库堆垛间距应满足以下要求：主通道大于或等于200cm；墙距大于或等于50cm。"),
                new RegArticle("条例", "第二十四条", "第四章 储存",
                        "危险化学品应当储存在专用仓库、专用场地或者专用储存室内，并由专人负责管理；剧毒化学品"
                                + "应当在专用仓库内单独存放，并实行双人收发、双人保管制度。")));
        cacheMapper = Mockito.mock(AiQaCacheMapper.class);
        chemMapper = Mockito.mock(ChemInfoMapper.class);
        service = new AiAskService(aiProvider, corpus, cacheMapper, chemMapper);
    }

    private ChemInfo chem(String name) {
        ChemInfo c = new ChemInfo();
        c.setId(11L);
        c.setCode("HG-JD-2026-0011");
        c.setName(name);
        c.setStockQty(new BigDecimal("1"));
        return c;
    }

    private AiQaCache cached(String intentJson, String summary) {
        AiQaCache c = new AiQaCache();
        c.setIntentJson(intentJson);
        c.setAnswerSummary(summary);
        c.setSource("ai");
        return c;
    }

    // ── ① 缓存命中不出网 ──
    @Test
    void 缓存命中直接回放且不出网() {
        when(cacheMapper.selectByHash(anyString())).thenReturn(
                cached("{\"intent\":\"list\",\"keyword\":\"丙酮\",\"category\":null,\"status\":null}",
                        "丙酮现存 6 瓶，存放于有机溶剂柜 HG-O01。"));
        when(chemMapper.selectPage(Mockito.eq("丙酮"), Mockito.isNull(), Mockito.isNull(), anyInt(), anyInt()))
                .thenReturn(List.of(chem("丙酮")));
        when(chemMapper.countPage(Mockito.eq("丙酮"), Mockito.isNull(), Mockito.isNull())).thenReturn(1L);

        Map<String, Object> resp = service.ask("查一下丙酮的库存");

        assertEquals("cache", resp.get("source"));
        assertEquals("丙酮现存 6 瓶，存放于有机溶剂柜 HG-O01。", resp.get("summary"));
        assertEquals(1L, ((Number) resp.get("total")).longValue());
        verify(aiClient, never()).chat(anyString(), anyString());   // 不出网
        verify(cacheMapper, never()).insert(any(AiQaCache.class)); // 回放不再写缓存
    }

    // ── ② 无 key → 数据问题走关键词降级 ──
    @Test
    void 无key数据问题走关键词降级() {
        when(cacheMapper.selectByHash(anyString())).thenReturn(null);
        when(aiProvider.getIfAvailable()).thenReturn(null); // 无 LLM Bean
        when(chemMapper.selectPage(anyString(), Mockito.isNull(), Mockito.isNull(), anyInt(), anyInt()))
                .thenReturn(List.of(chem("氰化钾")));
        when(chemMapper.countPage(anyString(), Mockito.isNull(), Mockito.isNull())).thenReturn(1L);

        Map<String, Object> resp = service.ask("氰化钾还有多少库存？");

        assertEquals("keyword", resp.get("source"));
        assertEquals("list", resp.get("intent"));
        @SuppressWarnings("unchecked")
        Map<String, Object> conditions = (Map<String, Object>) resp.get("conditions");
        assertEquals("氰化钾还有多少库存？", conditions.get("keyword")); // 问题整体作关键词
        verify(cacheMapper).insert(any(AiQaCache.class)); // 降级结果同样落缓存
    }

    // ── 白名单外字段被过滤 ──
    @Test
    void LLM输出白名单外字段一律丢弃() {
        when(cacheMapper.selectByHash(anyString())).thenReturn(null);
        when(aiClient.chat(anyString(), anyString())).thenReturn(
                "{\"intent\":\"list\",\"keyword\":\"丙酮\",\"category\":9,\"status\":0,"
                        + "\"cabinet_id\":7,\"sql\":\"DROP TABLE ai_qa_cache\",\"role\":\"admin\"}");
        when(chemMapper.selectPage(Mockito.eq("丙酮"), Mockito.isNull(), Mockito.eq(0), anyInt(), anyInt()))
                .thenReturn(List.of(chem("丙酮")));
        when(chemMapper.countPage(Mockito.eq("丙酮"), Mockito.isNull(), Mockito.eq(0))).thenReturn(1L);

        Map<String, Object> resp = service.ask("查一下丙酮");

        assertEquals("ai", resp.get("source"));
        @SuppressWarnings("unchecked")
        Map<String, Object> conditions = (Map<String, Object>) resp.get("conditions");
        assertEquals(3, conditions.size()); // 仅 keyword/category/status 三键
        assertTrue(conditions.containsKey("keyword") && conditions.containsKey("category")
                && conditions.containsKey("status"));
        assertFalse(conditions.containsKey("sql") || conditions.containsKey("cabinet_id")
                || conditions.containsKey("role"));
        assertNullValue(conditions.get("category")); // 越界值(9)按 null 丢弃
        // 注入的字段不应进入任何查询条件
        verify(chemMapper).selectPage(Mockito.eq("丙酮"), Mockito.isNull(), Mockito.eq(0), anyInt(), anyInt());
        assertFalse(String.valueOf(resp).contains("DROP TABLE"));
    }

    private static void assertNullValue(Object o) {
        assertTrue(o == null, "越界白名单值应被置 null，实际: " + o);
    }

    // ── 语料检索 top-k 命中正确条文 ──
    @Test
    void 语料检索topk命中正确条文() {
        // 检索器口径：双人双锁问题命中 5.10/第二十四条，不命中堆码条文
        List<RegArticle> top = corpus.search("剧毒品怎么存放？双人双锁有什么规定？", 2);
        assertEquals(2, top.size());
        assertTrue(top.stream().allMatch(a -> !a.getNo().equals("6.2.5")));
        assertTrue(top.get(0).getText().contains("双人收发、双人保管"));

        // 无 key regs 意图：跳过生成直接返回条文列表（source=keyword，功能即法规检索）
        when(cacheMapper.selectByHash(anyString())).thenReturn(null);
        when(aiProvider.getIfAvailable()).thenReturn(null);

        Map<String, Object> resp = service.ask("剧毒品双人双锁存放有什么规定？");

        assertEquals("regs", resp.get("intent"));
        assertEquals("keyword", resp.get("source"));
        @SuppressWarnings("unchecked")
        List<RegArticle> regs = (List<RegArticle>) resp.get("regs");
        assertFalse(regs.isEmpty());
        assertEquals("GB15603", regs.get(0).getLaw());
        assertTrue(regs.stream().noneMatch(r -> "6.2.5".equals(r.getNo())));
    }

    // ── 补充：解析失败/异常均静默降级，不抛错 ──
    @Test
    void LLM解析失败静默降级() {
        when(cacheMapper.selectByHash(anyString())).thenThrow(new RuntimeException("db down"));
        when(aiClient.chat(anyString(), anyString())).thenReturn("我不是 JSON，我说了算");
        when(chemMapper.selectPage(contains("丙酮"), Mockito.isNull(), Mockito.isNull(), anyInt(), anyInt()))
                .thenReturn(List.of(chem("丙酮")));
        when(chemMapper.countPage(anyString(), Mockito.isNull(), Mockito.isNull())).thenReturn(1L);

        Map<String, Object> resp = service.ask("丙酮库存");
        assertEquals("keyword", resp.get("source")); // 缓存失败+解析失败 → 仍有关键词兜底
    }
}
