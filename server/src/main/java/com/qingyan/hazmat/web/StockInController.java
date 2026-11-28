package com.qingyan.hazmat.web;

import com.qingyan.hazmat.common.Result;
import com.qingyan.hazmat.common.TableData;
import com.qingyan.hazmat.common.Validators;
import com.qingyan.hazmat.hazmat.entity.StockIn;
import com.qingyan.hazmat.hazmat.mapper.StockInMapper;
import com.qingyan.hazmat.hazmat.service.StockInService;
import com.qingyan.hazmat.security.RequirePerm;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 入库与"五双"双签：列表 / 提交（普通一步双签、剧毒仅首签）/ 剧毒第二签 / 拒收。
 * 双签/角色/落账守卫全在 StockInService；接收人恒为当前登录人。
 */
@RestController
@RequestMapping("/chem/stockin")
public class StockInController {

    private final StockInService stockInService;
    private final StockInMapper stockInMapper;

    public StockInController(StockInService stockInService, StockInMapper stockInMapper) {
        this.stockInService = stockInService;
        this.stockInMapper = stockInMapper;
    }

    /** 入库单列表分页（keyword 匹配编码/批号；status 可空；登录即可）。 */
    @GetMapping("/list")
    public Result<TableData<StockIn>> list(@RequestParam(defaultValue = "") String keyword,
                                           @RequestParam(required = false) Integer status,
                                           @RequestParam(defaultValue = "1") int page,
                                           @RequestParam(defaultValue = "10") int size) {
        int offset = (page - 1) * size;
        List<StockIn> records = stockInMapper.selectPage(keyword, status, offset, size);
        return Result.ok(TableData.of(records, stockInMapper.countPage(keyword, status)));
    }

    /** 提交入库：普通一步双签（提交即落账）/ 剧毒仅首签（第二签走 countersign）。
     *  新化学品入库时 form.draft 携带品名/CAS/类别/相容组建档草稿（CAS 命中挂既有台账）。 */
    @PostMapping
    @RequirePerm("hzm:stockin:add")
    public Result<StockIn> add(@RequestBody StockIn form, HttpServletRequest request) {
        // 后端强校验：批号/数量/柜位/复核人必填；新化学品建档草稿同步校验
        Validators.requiredLen(form.getLotNo(), 32, "批号");
        Validators.positive(form.getQty(), "数量");
        Validators.notNull(form.getCabinetId(), "存放柜位");
        Validators.notNull(form.getCheckerId(), "复核人");
        Validators.maxLen(form.getSupplier(), 100, "供应商");
        Validators.maxLen(form.getPermitNo(), 50, "公安备案号");
        if (form.getChemicalId() == null) {
            com.qingyan.hazmat.hazmat.entity.ChemInfo draft = form.getDraft();
            if (draft == null) {
                Validators.fail("既有台账与新化学品建档信息至少提供其一");
            }
            Validators.requiredLen(draft.getName(), 100, "品名");
            Validators.casNo(draft.getCasNo());
            Validators.range(draft.getCategory(), 0, 4, "分类");
            Validators.maxLen(draft.getCompatGroup(), 8, "主相容组");
            Validators.maxLen(draft.getCompatGroup2(), 8, "次相容组");
            Validators.maxLen(draft.getSpec(), 50, "规格");
        }
        long uid = ((Number) request.getAttribute("uid")).longValue();
        return Result.ok(stockInService.submit(form, form.getDraft(), uid));
    }

    /** 剧毒第二签（countersign）：body 可带 checkerId 指定第二签人，缺省为当前登录人。 */
    @PostMapping("/{id}/countersign")
    @RequirePerm("hzm:stockin:countersign")
    public Result<Void> countersign(@PathVariable Long id,
                                    @RequestBody(required = false) Map<String, Object> body,
                                    HttpServletRequest request) {
        long uid = ((Number) request.getAttribute("uid")).longValue();
        long checkerId = uid;
        if (body != null && body.get("checkerId") instanceof Number n) {
            checkerId = n.longValue();
        }
        stockInService.countersign(id, checkerId, uid);
        return Result.ok();
    }

    /** 拒收：仅 status=0 待验收（不建批次不回写申购），拒因必填。 */
    @PostMapping("/{id}/reject")
    @RequirePerm("hzm:stockin:reject")
    public Result<Void> reject(@PathVariable Long id, @RequestBody Map<String, String> body,
                               HttpServletRequest request) {
        long uid = ((Number) request.getAttribute("uid")).longValue();
        stockInService.reject(id, body.get("reason"), uid);
        return Result.ok();
    }
}
