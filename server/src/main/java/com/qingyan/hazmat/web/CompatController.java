package com.qingyan.hazmat.web;

import com.qingyan.hazmat.common.Result;
import com.qingyan.hazmat.hazmat.entity.CompatRule;
import com.qingyan.hazmat.hazmat.mapper.CompatRuleMapper;
import com.qingyan.hazmat.hazmat.service.CompatService;
import com.qingyan.hazmat.security.RequirePerm;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 禁配规则（§5）：列表 / 成对新增（禁自反，服务层 4101）/ 成对删除（XML 双向删）。
 */
@RestController
@RequestMapping("/chem/compat")
public class CompatController {

    private final CompatService compatService;
    private final CompatRuleMapper compatRuleMapper;

    public CompatController(CompatService compatService, CompatRuleMapper compatRuleMapper) {
        this.compatService = compatService;
        this.compatRuleMapper = compatRuleMapper;
    }

    /** 禁配规则列表（全量，登录即可）。 */
    @GetMapping
    public Result<List<CompatRule>> list() {
        return Result.ok(compatRuleMapper.selectAll());
    }

    /** 成对新增禁配规则（写入 (a,b) 与 (b,a) 两行）。 */
    @PostMapping
    @RequirePerm("hzm:compat:maint")
    public Result<Void> add(@RequestBody CompatRule rule) {
        // A4 后端强校验：组别必填、限长；同组自配与重复由服务层守卫
        com.qingyan.hazmat.common.Validators.maxLen(rule.getGroupA(), 8, "组 A");
        com.qingyan.hazmat.common.Validators.maxLen(rule.getGroupB(), 8, "组 B");
        com.qingyan.hazmat.common.Validators.maxLen(rule.getNote(), 200, "说明");
        compatService.addPair(rule.getGroupA(), rule.getGroupB(), rule.getNote());
        return Result.ok();
    }

    /** 成对删除禁配规则（按双向删除）。 */
    @DeleteMapping
    @RequirePerm("hzm:compat:maint")
    public Result<Void> remove(@RequestParam String groupA, @RequestParam String groupB) {
        compatService.removePair(groupA, groupB);
        return Result.ok();
    }
}
