package com.qingyan.hazmat.hazmat.service;

import com.qingyan.hazmat.common.BizException;
import com.qingyan.hazmat.hazmat.entity.ChemInfo;
import com.qingyan.hazmat.hazmat.entity.CompatRule;
import com.qingyan.hazmat.hazmat.mapper.ChemInfoMapper;
import com.qingyan.hazmat.hazmat.mapper.CompatRuleMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 禁配同存（按批次实际柜位）：柜内在库批次的化学品主/次相容组与 compat_rule
 * "有序对"集合成对匹配，任一组合命中即 4301（报冲突品名+规则 note）。
 * 规则成对写删（a,b 与 b,a 两行，UNIQUE(group_a,group_b) 禁自反）。
 */
@Service
public class CompatService {

    private final CompatRuleMapper ruleMapper;
    private final ChemInfoMapper chemMapper;

    public CompatService(CompatRuleMapper ruleMapper, ChemInfoMapper chemMapper) {
        this.ruleMapper = ruleMapper;
        this.chemMapper = chemMapper;
    }

    /** 禁配校验：cabinetId/chem 任一为空直接放行；柜内无在库批次亦放行。 */
    public void checkCabinet(Long cabinetId, ChemInfo chem) {
        if (cabinetId == null || chem == null) {
            return;
        }
        List<ChemInfo> mates = chemMapper.selectCabinetMates(cabinetId);
        if (mates.isEmpty()) {
            return;
        }
        Map<String, CompatRule> rules = new HashMap<>();
        for (CompatRule rule : ruleMapper.selectAll()) {
            rules.put(pairKey(rule.getGroupA(), rule.getGroupB()), rule);
        }
        for (ChemInfo mate : mates) {
            if (chem.getId() != null && chem.getId().equals(mate.getId())) {
                continue; // 同一台账重复入柜不构成禁配
            }
            for (String mateGroup : groupsOf(mate)) {
                for (String chemGroup : groupsOf(chem)) {
                    CompatRule hit = rules.get(pairKey(mateGroup, chemGroup));
                    if (hit != null) {
                        throw new BizException(4301, "禁配冲突：" + mate.getName() + " 与 "
                                + chem.getName() + " 不得同柜共存（" + hit.getNote() + "）");
                    }
                }
            }
        }
    }

    /** 成对新增禁配规则：a==b 拒绝（4101）；写入 (a,b) 与 (b,a) 两行。 */
    @Transactional
    public void addPair(String groupA, String groupB, String note) {
        if (groupA == null || groupA.isBlank() || groupB == null || groupB.isBlank()) {
            throw new BizException(4101, "相容组标识必填");
        }
        if (groupA.equals(groupB)) {
            throw new BizException(4101, "禁配组不得自反（groupA 与 groupB 相同）");
        }
        CompatRule ab = new CompatRule();
        ab.setGroupA(groupA);
        ab.setGroupB(groupB);
        ab.setNote(note);
        CompatRule ba = new CompatRule();
        ba.setGroupA(groupB);
        ba.setGroupB(groupA);
        ba.setNote(note);
        ruleMapper.insertPair(ab);
        ruleMapper.insertPair(ba);
    }

    /** 成对删除禁配规则（XML 按双向删除）。 */
    @Transactional
    public void removePair(String groupA, String groupB) {
        if (groupA == null || groupA.isBlank() || groupB == null || groupB.isBlank()) {
            throw new BizException(4101, "相容组标识必填");
        }
        ruleMapper.deletePair(groupA, groupB);
    }

    /** 主/次相容组（次组可空），任一命中即冲突。 */
    private List<String> groupsOf(ChemInfo info) {
        List<String> groups = new ArrayList<>(2);
        if (info.getCompatGroup() != null && !info.getCompatGroup().isBlank()) {
            groups.add(info.getCompatGroup());
        }
        if (info.getCompatGroup2() != null && !info.getCompatGroup2().isBlank()) {
            groups.add(info.getCompatGroup2());
        }
        return groups;
    }

    /** 有序对键：两端排序后拼接（规则成对存储，方向无关）。 */
    private static String pairKey(String a, String b) {
        return a.compareTo(b) <= 0 ? a + ">" + b : b + ">" + a;
    }
}
