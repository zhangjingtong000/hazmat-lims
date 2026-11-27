package com.qingyan.hazmat.web;

import com.qingyan.hazmat.common.BizException;
import com.qingyan.hazmat.common.Result;
import com.qingyan.hazmat.common.TableData;
import com.qingyan.hazmat.common.Validators;
import com.qingyan.hazmat.hazmat.entity.ChemInfo;
import com.qingyan.hazmat.hazmat.mapper.ChemInfoMapper;
import com.qingyan.hazmat.hazmat.service.ChemInfoService;
import com.qingyan.hazmat.security.RequirePerm;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

/**
 * 台账管理（§5）：列表分页 / 建档（CAS 查重命中→挂既有台账不阻断）/ 维护 / 逻辑删除 /
 * 档案聚合（台账+FEFO 批次）/ CAS 查重。业务守卫全在 ChemInfoService。
 */
@RestController
@RequestMapping("/chem/info")
public class ChemInfoController {

    private final ChemInfoService chemInfoService;
    private final ChemInfoMapper chemInfoMapper;

    public ChemInfoController(ChemInfoService chemInfoService, ChemInfoMapper chemInfoMapper) {
        this.chemInfoService = chemInfoService;
        this.chemInfoMapper = chemInfoMapper;
    }

    /** 台账列表分页（keyword 匹配编码/名称/CAS；category/status 可空；登录即可）。 */
    @GetMapping("/list")
    public Result<TableData<ChemInfo>> list(@RequestParam(defaultValue = "") String keyword,
                                            @RequestParam(required = false) Integer category,
                                            @RequestParam(required = false) Integer status,
                                            @RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "10") int size) {
        int offset = (page - 1) * size;
        List<ChemInfo> records = chemInfoMapper.selectPage(keyword, category, status, offset, size);
        return Result.ok(TableData.of(records, chemInfoMapper.countPage(keyword, category, status)));
    }

    /** 建档：CAS 查重命中→返回既有台账（挂既有，不建新档）。 */
    @PostMapping
    @RequirePerm("hzm:info:add")
    public Result<ChemInfo> add(@RequestBody ChemInfo info) {
        validate(info, true);
        return Result.ok(chemInfoService.create(info));
    }

    /** 维护：类别守卫 4107 在服务层；stock_qty/cas_no/code/status 由服务层回写库内值。 */
    @PutMapping
    @RequirePerm("hzm:info:edit")
    public Result<Void> edit(@RequestBody ChemInfo info, HttpServletRequest request) {
        if (info.getId() == null) {
            throw new BizException(4101, "缺少台账 ID");
        }
        validate(info, false);
        long uid = ((Number) request.getAttribute("uid")).longValue();
        chemInfoService.update(info, uid);
        return Result.ok();
    }

    /** A4 后端强校验：与前端 el-form rules 一一对应（必填/长度/数值范围/格式）。 */
    private void validate(ChemInfo info, boolean creating) {
        if (creating) {
            Validators.requiredLen(info.getName(), 100, "品名");
            Validators.range(info.getCategory(), 0, 4, "分类");
        } else {
            Validators.maxLen(info.getName(), 100, "品名");
            if (info.getCategory() != null) {
                Validators.range(info.getCategory(), 0, 4, "分类");
            }
        }
        Validators.maxLen(info.getAlias(), 100, "别名");
        Validators.casNo(info.getCasNo());
        Validators.maxLen(info.getCompatGroup(), 8, "主相容组");
        Validators.maxLen(info.getCompatGroup2(), 8, "次相容组");
        Validators.maxLen(info.getSpec(), 50, "规格");
        Validators.maxLen(info.getUnit(), 16, "计量单位");
        Validators.nonNegative(info.getMinStock(), "安全库存下限");
        Validators.nonNegative(info.getMaxOnce(), "剧毒单次上限");
        if (info.getExpiryWarnDays() != null) {
            Validators.range(info.getExpiryWarnDays(), 1, 365, "效期预警天数");
        }
        Validators.maxLen(info.getGhzPictogram(), 50, "GHS 象形图");
        Validators.maxLen(info.getFirstAid(), 200, "应急处置要点");
    }

    /** 逻辑删除（三条件守卫在服务层，仅清空且无在库批次/未完成单据可删）。 */
    @DeleteMapping("/{ids}")
    @RequirePerm("hzm:info:remove")
    public Result<Void> remove(@PathVariable String ids, HttpServletRequest request) {
        long uid = ((Number) request.getAttribute("uid")).longValue();
        for (long id : Arrays.stream(ids.split(",")).map(Long::valueOf).toList()) {
            chemInfoService.remove(id, uid);
        }
        return Result.ok();
    }

    /** 档案聚合：台账 + 在库批次（FEFO 序）。 */
    @GetMapping("/{id}")
    public Result<java.util.Map<String, Object>> detail(@PathVariable Long id) {
        return Result.ok(chemInfoService.detail(id));
    }

    /** CAS 查重（建档预检；未命中返回 null）。 */
    @GetMapping("/cas-check")
    public Result<ChemInfo> casCheck(@RequestParam String casNo) {
        return Result.ok(chemInfoMapper.selectByCas(casNo));
    }

    /** 封存/解封（0↔1，4504 守卫源头）。 */
    @org.springframework.web.bind.annotation.PostMapping("/{id}/status")
    @com.qingyan.hazmat.security.RequirePerm("hzm:info:edit")
    public com.qingyan.hazmat.common.Result<Void> setStatus(@PathVariable Long id,
            @org.springframework.web.bind.annotation.RequestBody java.util.Map<String, Integer> body,
            jakarta.servlet.http.HttpServletRequest request) {
        long uid = ((Number) request.getAttribute("uid")).longValue();
        chemInfoService.setStatus(id, body.get("status"), uid);
        return com.qingyan.hazmat.common.Result.ok(null);
    }
}
