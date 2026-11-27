package com.qingyan.hazmat.web;

import com.qingyan.hazmat.common.BizException;
import com.qingyan.hazmat.common.Result;
import com.qingyan.hazmat.common.TableData;
import com.qingyan.hazmat.common.Validators;
import com.qingyan.hazmat.hazmat.entity.ApprovalRecord;
import com.qingyan.hazmat.hazmat.entity.ChemInfo;
import com.qingyan.hazmat.hazmat.entity.WasteDisposal;
import com.qingyan.hazmat.hazmat.mapper.ChemInfoMapper;
import com.qingyan.hazmat.hazmat.mapper.WasteDisposalMapper;
import com.qingyan.hazmat.hazmat.service.WasteService;
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
 * 废弃物处置（§5）：建单（强制批次+⑦-11 错批守卫在服务层）/ 列表 / 详情 / 审批。
 * 审批完成合规校验（控制器侧）：disposal_target 必填 4101；剧毒(4)/易制毒(2)/易制爆(3)
 *voucher_no 必填 4101——先落库（updateCompletion）再走服务层审批销账。
 */
@RestController
@RequestMapping("/chem/waste")
public class WasteController {

    private final WasteService wasteService;
    private final WasteDisposalMapper wasteMapper;
    private final ChemInfoMapper chemMapper;

    public WasteController(WasteService wasteService, WasteDisposalMapper wasteMapper,
                           ChemInfoMapper chemMapper) {
        this.wasteService = wasteService;
        this.wasteMapper = wasteMapper;
        this.chemMapper = chemMapper;
    }

    /** 建单：body {chemicalId, batchId, type, qty?, method?}（批次强制，服务层守卫）。 */
    @PostMapping
    @RequirePerm("hzm:waste:submit")
    public Result<WasteDisposal> add(@RequestBody WasteDisposal body, HttpServletRequest request) {
        // A4 后端强校验：化学品/批次强制（⑦-11 错批守卫在服务层），数量为正、文本限长
        Validators.notNull(body.getChemicalId(), "化学品");
        Validators.notNull(body.getBatchId(), "批次");
        Validators.range(body.getType(), 1, 3, "处置类型");
        if (body.getType() != null && body.getType() != 3) {
            Validators.positive(body.getQty(), "数量");
        }
        Validators.maxLen(body.getMethod(), 120, "处置方式");
        long uid = ((Number) request.getAttribute("uid")).longValue();
        return Result.ok(wasteService.submit(body.getChemicalId(), body.getBatchId(),
                body.getType() == null ? 0 : body.getType(), body.getQty(), body.getMethod(), uid));
    }

    /** 处置单列表分页（keyword 匹配编码；status 可空；登录即可）。 */
    @GetMapping("/list")
    public Result<TableData<WasteDisposal>> list(@RequestParam(defaultValue = "") String keyword,
                                                 @RequestParam(required = false) Integer status,
                                                 @RequestParam(defaultValue = "1") int page,
                                                 @RequestParam(defaultValue = "10") int size) {
        int offset = (page - 1) * size;
        List<WasteDisposal> records = wasteMapper.selectPage(keyword, status, offset, size);
        return Result.ok(TableData.of(records, wasteMapper.countPage(keyword, status)));
    }

    /** 处置单详情：单头（联表带化学品/批次/经办审批人，H2 打印数据源） + 审批记录。 */
    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        WasteDisposal wd = wasteMapper.selectDetailById(id);
        if (wd == null) {
            throw new BizException(4101, "处置单不存在");
        }
        List<ApprovalRecord> records = wasteService.records(id);
        Map<String, Object> vo = new LinkedHashMap<>();
        vo.put("waste", wd);
        vo.put("records", records);
        return Result.ok(vo);
    }

    /**
     * 审批（approve/reject，驳回 comment 必填在服务层）。approve 时完成合规校验：
     * disposal_target 非空（4101）；category∈{2,3,4}（易制毒/易制爆/剧毒）voucher_no 非空（4101）。
     * 审批层级回避/权限/销账守卫在服务层。
     */
    @PostMapping("/{id}/audit")
    @RequirePerm("hzm:waste:audit")
    public Result<Void> audit(@PathVariable Long id, @RequestBody Map<String, Object> body,
                              HttpServletRequest request) {
        long uid = ((Number) request.getAttribute("uid")).longValue();
        WasteDisposal wd = wasteMapper.selectById(id);
        if (wd == null) {
            throw new BizException(4101, "处置单不存在");
        }
        boolean approve = Boolean.TRUE.equals(body.get("approve"));
        String comment = body.get("comment") == null ? null : String.valueOf(body.get("comment"));
        String disposalTarget = body.get("disposalTarget") == null ? null
                : String.valueOf(body.get("disposalTarget"));
        String voucherNo = body.get("voucherNo") == null ? null
                : String.valueOf(body.get("voucherNo"));
        Validators.maxLen(comment, 500, "审批意见");
        Validators.maxLen(disposalTarget, 120, "处置去向");
        Validators.maxLen(voucherNo, 50, "转移凭证号");

        if (approve) {
            if (disposalTarget == null || disposalTarget.isBlank()) {
                throw new BizException(4101, "完成处置必须填写处置去向（disposal_target）");
            }
            ChemInfo chem = chemMapper.selectById(wd.getChemicalId());
            int category = chem == null || chem.getCategory() == null ? 0 : chem.getCategory();
            if (category >= 2 && (voucherNo == null || voucherNo.isBlank())) {
                throw new BizException(4101, "剧毒/易制毒/易制爆处置必须填写转移凭证号（voucher_no）");
            }
            // 合规信息随审批落库（updateCompletion 与服务层销账不同事务，字段为登记性质）
            wasteMapper.updateCompletion(id, disposalTarget, voucherNo);
        }
        wasteService.audit(id, uid, approve, comment);
        return Result.ok();
    }
}
