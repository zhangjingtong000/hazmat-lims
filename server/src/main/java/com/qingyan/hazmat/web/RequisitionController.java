package com.qingyan.hazmat.web;

import com.qingyan.hazmat.common.BizException;
import com.qingyan.hazmat.common.Result;
import com.qingyan.hazmat.common.TableData;
import com.qingyan.hazmat.common.Validators;
import com.qingyan.hazmat.hazmat.entity.Requisition;
import com.qingyan.hazmat.hazmat.entity.RequisitionBatch;
import com.qingyan.hazmat.hazmat.entity.StockReturn;
import com.qingyan.hazmat.hazmat.mapper.RequisitionBatchMapper;
import com.qingyan.hazmat.hazmat.mapper.RequisitionMapper;
import com.qingyan.hazmat.hazmat.service.RequisitionService;
import com.qingyan.hazmat.security.RequirePerm;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 领用/归还：提交 / 列表（mine/todo）/ 详情（FEFO 明细）/ 导师签 / 复核签 /
 * 改派 / 归还 / 归还确认/拒绝。FEFO/双签/超领/对账守卫全在 RequisitionService。
 */
@RestController
@RequestMapping("/chem/req")
public class RequisitionController {

    private final RequisitionService requisitionService;
    private final com.qingyan.hazmat.hazmat.mapper.StockReturnMapper stockReturnMapper;
    private final RequisitionMapper requisitionMapper;
    private final RequisitionBatchMapper requisitionBatchMapper;

    public RequisitionController(RequisitionService requisitionService,
                                 RequisitionMapper requisitionMapper,
                                 RequisitionBatchMapper requisitionBatchMapper,
                          com.qingyan.hazmat.hazmat.mapper.StockReturnMapper stockReturnMapper) {
        this.requisitionService = requisitionService;
        this.requisitionMapper = requisitionMapper;
        this.requisitionBatchMapper = requisitionBatchMapper;
        this.stockReturnMapper = stockReturnMapper;
    }

    /** 提交领用（起点定档在服务层；req_id 关联已批申购双校验 4403/4404）。 */
    @PostMapping
    @RequirePerm("hzm:req:submit")
    public Result<Requisition> submit(@RequestBody Requisition body, HttpServletRequest request) {
        // 后端强校验：化学品/数量/用途必填，文本长度与列宽一致
        Validators.notNull(body.getChemicalId(), "化学品");
        Validators.positive(body.getQty(), "数量");
        Validators.requiredLen(body.getPurpose(), 200, "用途");
        Validators.maxLen(body.getExperiment(), 100, "实验项目");
        long uid = ((Number) request.getAttribute("uid")).longValue();
        return Result.ok(requisitionService.submit(body.getChemicalId(), body.getQty(), uid,
                body.getReqId(), body.getExpectReturn(), body.getPurpose(), body.getExperiment()));
    }

    /**
     * 领用列表分页（登录即可）。mine=true 只看本人；todo=true → status IN (0,1) 全量
     * （前端按 perms 过滤导师签/复核按钮，服务层 4104/4105/4201 精确守卫）。
     */
    @GetMapping("/list")
    public Result<TableData<Requisition>> list(@RequestParam(required = false) Integer status,
                                               @RequestParam(defaultValue = "false") boolean mine,
                                               @RequestParam(defaultValue = "false") boolean todo,
                                               @RequestParam(defaultValue = "1") int page,
                                               @RequestParam(defaultValue = "10") int size,
                                               HttpServletRequest request) {
        int offset = (page - 1) * size;
        Long applicantId = mine ? ((Number) request.getAttribute("uid")).longValue() : null;
        List<Requisition> records = requisitionMapper.selectPage(applicantId, status, todo, offset, size);
        return Result.ok(TableData.of(records, requisitionMapper.countPage(applicantId, status, todo)));
    }

    /** 详情：单头 + FEFO 分配明细（领用批次行）。 */
    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        Requisition req = requisitionMapper.selectById(id);
        if (req == null) {
            throw new BizException(4101, "领用单不存在");
        }
        List<RequisitionBatch> batches = requisitionBatchMapper.selectByReq(id);
        Map<String, Object> vo = new LinkedHashMap<>();
        vo.put("req", req);
        vo.put("batches", batches);
        return Result.ok(vo);
    }

    /** 导师签（仅 0 待导师；approve/reject，驳回 comment 必填在服务层）。 */
    @PostMapping("/{id}/advisor")
    @RequirePerm("hzm:req:advisor")
    public Result<Void> advisor(@PathVariable Long id, @RequestBody Map<String, Object> body,
                                HttpServletRequest request) {
        long uid = ((Number) request.getAttribute("uid")).longValue();
        boolean approve = Boolean.TRUE.equals(body.get("approve"));
        String comment = body.get("comment") == null ? null : String.valueOf(body.get("comment"));
        requisitionService.advisor(id, uid, approve, comment);
        return Result.ok();
    }

    /** 复核双签（仅 1 待复核；通过即 FEFO 出库）。 */
    @PostMapping("/{id}/check")
    @RequirePerm("hzm:req:check")
    public Result<Void> check(@PathVariable Long id, @RequestBody Map<String, Object> body,
                              HttpServletRequest request) {
        long uid = ((Number) request.getAttribute("uid")).longValue();
        boolean approve = Boolean.TRUE.equals(body.get("approve"));
        String comment = body.get("comment") == null ? null : String.valueOf(body.get("comment"));
        requisitionService.check(id, uid, approve, comment);
        return Result.ok();
    }

    /** 改派：body {originalBatchId, newBatchId}——回加原批/扣新批，幂等条件更新在服务层。 */
    @PutMapping("/{id}/dispatch")
    @RequirePerm("hzm:req:dispatch")
    public Result<Void> dispatch(@PathVariable Long id, @RequestBody Map<String, Object> body,
                                 HttpServletRequest request) {
        long uid = ((Number) request.getAttribute("uid")).longValue();
        Long originalBatchId = requireLong(body.get("originalBatchId"), "缺少原批次 ID");
        Long newBatchId = requireLong(body.get("newBatchId"), "缺少目标批次 ID");
        requisitionService.dispatch(id, originalBatchId, newBatchId, uid);
        return Result.ok();
    }

    /** 提交余量归还：body {batchId, qty}（仅领用申请人，服务层 4104 守卫）。 */
    @PostMapping("/{id}/return")
    @RequirePerm("hzm:req:return")
    public Result<StockReturn> submitReturn(@PathVariable Long id,
                                            @RequestBody Map<String, Object> body,
                                            HttpServletRequest request) {
        long uid = ((Number) request.getAttribute("uid")).longValue();
        Long batchId = requireLong(body.get("batchId"), "缺少归还批次 ID");
        BigDecimal qty = requireDecimal(body.get("qty"), "缺少归还数量");
        return Result.ok(requisitionService.submitReturn(id, batchId, qty, uid));
    }

    /** 某领用单的全部归还记录（含已拒绝——拒因留痕可审计）。 */
    @GetMapping("/{id}/returns")
    public Result<List<StockReturn>> returnsByReq(@PathVariable Long id) {
        return Result.ok(stockReturnMapper.selectByReq(id));
    }

    /** 归还确认（H-1）：确认人限安全员/学院（4202 服务层守卫）；足额自动关单。 */
    @PostMapping("/return/{rid}/confirm")
    @RequirePerm("hzm:return:confirm")
    public Result<Void> confirmReturn(@PathVariable Long rid, HttpServletRequest request) {
        long uid = ((Number) request.getAttribute("uid")).longValue();
        requisitionService.confirmReturn(rid, uid);
        return Result.ok();
    }

    /** 归还拒绝：comment 必填（服务层守卫）。 */
    @PostMapping("/return/{rid}/reject")
    @RequirePerm("hzm:return:reject")
    public Result<Void> rejectReturn(@PathVariable Long rid, @RequestBody Map<String, String> body,
                                     HttpServletRequest request) {
        long uid = ((Number) request.getAttribute("uid")).longValue();
        requisitionService.rejectReturn(rid, body.get("comment"), uid);
        return Result.ok();
    }

    private static Long requireLong(Object value, String message) {
        if (value instanceof Number n) {
            return n.longValue();
        }
        if (value instanceof String s && !s.isBlank()) {
            return Long.valueOf(s);
        }
        throw new BizException(4101, message);
    }

    private static BigDecimal requireDecimal(Object value, String message) {
        if (value instanceof Number n) {
            return new BigDecimal(String.valueOf(n));
        }
        if (value instanceof String s && !s.isBlank()) {
            return new BigDecimal(s);
        }
        throw new BizException(4101, message);
    }

    /** 核销关闭：材料用尽、Σ归还<qty 时 2→4。 */
    @org.springframework.web.bind.annotation.PostMapping("/{id}/close")
    @com.qingyan.hazmat.security.RequirePerm("hzm:req:submit")
    public com.qingyan.hazmat.common.Result<Void> close(@PathVariable Long id,
            @org.springframework.web.bind.annotation.RequestBody java.util.Map<String, String> body,
            jakarta.servlet.http.HttpServletRequest request) {
        long uid = ((Number) request.getAttribute("uid")).longValue();
        requisitionService.close(id, uid, body.get("comment"));
        return com.qingyan.hazmat.common.Result.ok(null);
    }
}
