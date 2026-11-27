package com.qingyan.hazmat.service;

import com.qingyan.hazmat.common.BizException;
import com.qingyan.hazmat.hazmat.entity.ChemInfo;
import com.qingyan.hazmat.hazmat.entity.CompatRule;
import com.qingyan.hazmat.hazmat.mapper.ChemInfoMapper;
import com.qingyan.hazmat.hazmat.mapper.CompatRuleMapper;
import com.qingyan.hazmat.hazmat.service.CompatService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * R3 禁配同存单测：主/次相容组 × 成对规则"有序对"匹配；成对写删。
 * 规则种子口径：OX-RD / OX-FLAM / AC-AK / AC-FLAM / OX-ORG / AC-ORG。
 */
class CompatServiceTest {

    private CompatRuleMapper ruleMapper;
    private ChemInfoMapper chemMapper;
    private CompatService service;

    @BeforeEach
    void setUp() {
        ruleMapper = Mockito.mock(CompatRuleMapper.class);
        chemMapper = Mockito.mock(ChemInfoMapper.class);
        service = new CompatService(ruleMapper, chemMapper);
    }

    private ChemInfo chem(long id, String name, String g1, String g2) {
        ChemInfo c = new ChemInfo();
        c.setId(id);
        c.setName(name);
        c.setCompatGroup(g1);
        c.setCompatGroup2(g2);
        return c;
    }

    private CompatRule rule(String a, String b, String note) {
        CompatRule r = new CompatRule();
        r.setGroupA(a);
        r.setGroupB(b);
        r.setNote(note);
        return r;
    }

    @Test
    void checkCabinet_主相容组命中即冲突4301并报品名与note() {
        when(chemMapper.selectCabinetMates(3L)).thenReturn(List.of(chem(1L, "高锰酸钾", "OX", null)));
        when(ruleMapper.selectAll()).thenReturn(List.of(rule("OX", "FLAM", "氧化剂与易燃液体禁配")));
        BizException ex = assertThrows(BizException.class,
                () -> service.checkCabinet(3L, chem(5L, "无水乙醇", "FLAM", null)));
        assertEquals(4301, ex.getCode());
        assertTrue(ex.getMessage().contains("高锰酸钾"));
        assertTrue(ex.getMessage().contains("氧化剂与易燃液体禁配"));
    }

    @Test
    void checkCabinet_次相容组任一命中即冲突() {
        // 乙醇主组 GEN 无规则，次组 FLAM 命中 OX-FLAM
        when(chemMapper.selectCabinetMates(3L)).thenReturn(List.of(chem(1L, "高锰酸钾", "OX", null)));
        when(ruleMapper.selectAll()).thenReturn(List.of(rule("OX", "FLAM", "氧化剂与易燃液体禁配")));
        assertEquals(4301, assertThrows(BizException.class,
                () -> service.checkCabinet(3L, chem(5L, "硝酸铵", "GEN", "FLAM"))).getCode());
    }

    @Test
    void checkCabinet_同化学品自身重复入柜放行() {
        when(chemMapper.selectCabinetMates(3L)).thenReturn(List.of(chem(5L, "无水乙醇", "FLAM", null)));
        when(ruleMapper.selectAll()).thenReturn(List.of(rule("OX", "FLAM", "氧化剂与易燃液体禁配")));
        service.checkCabinet(3L, chem(5L, "无水乙醇", "FLAM", null)); // mate.id == chem.id 跳过
        verify(ruleMapper).selectAll();
    }

    @Test
    void checkCabinet_柜内无在库批次放行且不查规则集() {
        when(chemMapper.selectCabinetMates(3L)).thenReturn(Collections.emptyList());
        service.checkCabinet(3L, chem(5L, "无水乙醇", "FLAM", null));
        verify(ruleMapper, never()).selectAll();
    }

    @Test
    void checkCabinet_无匹配规则放行() {
        when(chemMapper.selectCabinetMates(3L)).thenReturn(List.of(chem(1L, "氯化钠", "PT", null)));
        when(ruleMapper.selectAll()).thenReturn(List.of(rule("OX", "FLAM", "氧化剂与易燃液体禁配")));
        service.checkCabinet(3L, chem(5L, "无水乙醇", "FLAM", null));
    }

    @Test
    void addPair_自反拒绝() {
        assertEquals(4101, assertThrows(BizException.class,
                () -> service.addPair("OX", "OX", "x")).getCode());
        verify(ruleMapper, never()).insertPair(any(CompatRule.class));
    }

    @Test
    void addPair_缺组标识拒绝() {
        assertEquals(4101, assertThrows(BizException.class,
                () -> service.addPair(" ", "OX", "x")).getCode());
    }

    @Test
    void addPair_成对写入正反两行() {
        service.addPair("RD", "OX", "氧化剂与还原剂禁配");
        ArgumentCaptor<CompatRule> captor = ArgumentCaptor.forClass(CompatRule.class);
        verify(ruleMapper, Mockito.times(2)).insertPair(captor.capture());
        List<CompatRule> written = captor.getAllValues();
        assertEquals("RD", written.get(0).getGroupA());
        assertEquals("OX", written.get(0).getGroupB());
        assertEquals("OX", written.get(1).getGroupA());
        assertEquals("RD", written.get(1).getGroupB());
    }

    @Test
    void removePair_成对删除() {
        service.removePair("OX", "RD");
        verify(ruleMapper).deletePair("OX", "RD");
    }
}
