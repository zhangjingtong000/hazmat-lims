package com.qingyan.hazmat.web;

import com.qingyan.hazmat.common.BizException;
import com.qingyan.hazmat.common.Result;
import com.qingyan.hazmat.common.TableData;
import com.qingyan.hazmat.common.Validators;
import com.qingyan.hazmat.hazmat.entity.ChemCheck;
import com.qingyan.hazmat.hazmat.entity.ChemCheckDiff;
import com.qingyan.hazmat.hazmat.mapper.ChemCheckMapper;
import com.qingyan.hazmat.hazmat.service.CheckService;
import com.qingyan.hazmat.security.RequirePerm;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 账物盘点（§5）：制单（双人）/ 结单（差异记录不改库存）/ 调账（增量修正）/
 * 列表 / 详情（差异明细）。双人制/仅本单盘点人/批次守卫全在 CheckService。
 */
@RestController
@RequestMapping("/chem/check")
public class CheckController {

    private final CheckService checkService;
    private final ChemCheckMapper checkMapper;

    public CheckController(CheckService checkService, ChemCheckMapper checkMapper) {
        this.checkService = checkService;
        this.checkMapper = checkMapper;
    }

    /** 制单：body {scopeCategory?, checkerTwoId}——checker_one 恒为当前登录人（两人不同守卫在服务层）。 */
    @PostMapping
    @RequirePerm("hzm:check:do")
    public Result<ChemCheck> add(@RequestBody ChemCheck body, HttpServletRequest request) {
        // A4 后端强校验：盘点人二必填（双人制），范围分类可空但取值受限
        Validators.notNull(body.getCheckerTwoId(), "盘点人二");
        if (body.getScopeCategory() != null) {
            Validators.range(body.getScopeCategory(), 0, 4, "盘点范围分类");
        }
        long uid = ((Number) request.getAttribute("uid")).longValue();
        return Result.ok(checkService.create(body.getScopeCategory(), uid, body.getCheckerTwoId()));
    }

    /** 结单：body 为差异行数组 [{batchId, actualQty, remark?}, ...]（book/diff 由服务层快照计算，不改库存）。 */
    @PostMapping("/{id}/finish")
    @RequirePerm("hzm:check:do")
    public Result<Void> finish(@PathVariable Long id, @RequestBody List<ChemCheckDiff> diffs,
                               HttpServletRequest request) {
        if (diffs != null) {
            for (ChemCheckDiff diff : diffs) {
                Validators.notNull(diff.getBatchId(), "批次");
                Validators.notNull(diff.getActualQty(), "实物量");
                Validators.nonNegative(diff.getActualQty(), "实物量");
                Validators.maxLen(diff.getRemark(), 200, "差异说明");
            }
        }
        long uid = ((Number) request.getAttribute("uid")).longValue();
        checkService.finish(id, diffs == null ? List.of() : diffs, uid);
        return Result.ok();
    }

    /** 调账（增量修正：diff>0 回加 / diff<0 条件扣减，台账同步；批次 status=0 守卫⑦-6）。 */
    @PostMapping("/{id}/adjust")
    @RequirePerm("hzm:check:adjust")
    public Result<Void> adjust(@PathVariable Long id, @RequestBody Map<String, Object> body,
                               HttpServletRequest request) {
        long uid = ((Number) request.getAttribute("uid")).longValue();
        Object diffId = body.get("diffId");
        if (!(diffId instanceof Number n)) {
            throw new BizException(4101, "缺少差异明细 ID");
        }
        checkService.adjust(id, n.longValue(), uid);
        return Result.ok();
    }

    /** 盘点单列表分页（result 可空；登录即可）。 */
    @GetMapping("/list")
    public Result<TableData<ChemCheck>> list(@RequestParam(required = false) Integer result,
                                             @RequestParam(defaultValue = "1") int page,
                                             @RequestParam(defaultValue = "10") int size) {
        int offset = (page - 1) * size;
        List<ChemCheck> records = checkMapper.selectPage(result, offset, size);
        return Result.ok(TableData.of(records, checkMapper.countPage(result)));
    }

    /** 详情：单头 + 差异明细（按插入序）。 */
    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        ChemCheck check = checkMapper.selectById(id);
        if (check == null) {
            throw new BizException(4101, "盘点单不存在");
        }
        Map<String, Object> vo = new LinkedHashMap<>();
        vo.put("check", check);
        vo.put("diffs", checkService.diffs(id));
        return Result.ok(vo);
    }
}
