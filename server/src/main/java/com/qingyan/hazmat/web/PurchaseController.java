package com.qingyan.hazmat.web;

import com.qingyan.hazmat.common.BizException;
import com.qingyan.hazmat.common.Result;
import com.qingyan.hazmat.common.TableData;
import com.qingyan.hazmat.common.Validators;
import com.qingyan.hazmat.hazmat.entity.ApprovalRecord;
import com.qingyan.hazmat.hazmat.entity.PurchaseReq;
import com.qingyan.hazmat.hazmat.mapper.PurchaseReqMapper;
import com.qingyan.hazmat.hazmat.service.ApprovalChainService;
import com.qingyan.hazmat.hazmat.service.PurchaseFlowService;
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
 * 申购流转：保存/提交/审批/作废 + 列表（mine/todo）+ 详情（含审批链）。
 * 审批权限节点键随单据当前级动态解析（purchase:audit-<节点键>），拦截器静态注解
 * 无法表达，故在本控制器内以 countPerm 动态校验（不过者 4105，与服务层同码）。
 */
@RestController
@RequestMapping("/chem/purchase")
public class PurchaseController {

    private final PurchaseFlowService purchaseFlowService;
    private final PurchaseReqMapper purchaseReqMapper;
    private final ApprovalChainService approvalChainService;

    public PurchaseController(PurchaseFlowService purchaseFlowService,
                              PurchaseReqMapper purchaseReqMapper,
                              ApprovalChainService approvalChainService) {
        this.purchaseFlowService = purchaseFlowService;
        this.purchaseReqMapper = purchaseReqMapper;
        this.approvalChainService = approvalChainService;
    }

    /** 保存申购单（草稿 status=0 或直接 status=1 可提交本体）；申请人恒为当前登录人。 */
    @PostMapping
    @RequirePerm("hzm:purchase:submit")
    public Result<PurchaseReq> save(@RequestBody PurchaseReq req, HttpServletRequest request) {
        // 后端强校验：与前端「新建申购单」rules 对应
        Validators.requiredLen(req.getChemicalName(), 100, "化学品名");
        Validators.casNo(req.getCasNo());
        Validators.range(req.getCategory(), 0, 4, "分类");
        Validators.positive(req.getQty(), "申购数量");
        Validators.maxLen(req.getSpec(), 50, "规格");
        Validators.maxLen(req.getUnit(), 16, "单位");
        Validators.maxLen(req.getPurpose(), 200, "用途");
        Validators.maxLen(req.getExperiment(), 100, "实验项目");
        boolean submitting = req.getStatus() != null && req.getStatus() == 1;
        if (submitting) {
            Validators.notNull(req.getAdvisorId(), "导师");
        }
        long uid = ((Number) request.getAttribute("uid")).longValue();
        req.setApplicantId(uid);
        return Result.ok(purchaseFlowService.save(req));
    }

    /** 提交：仅草稿、仅申请人本人（服务层守卫）。 */
    @PostMapping("/{id}/submit")
    @RequirePerm("hzm:purchase:submit")
    public Result<Void> submit(@PathVariable Long id, HttpServletRequest request) {
        long uid = ((Number) request.getAttribute("uid")).longValue();
        purchaseFlowService.submit(id, uid);
        return Result.ok();
    }

    /**
     * 申购列表分页（登录即可）。mine=true 只看本人；todo=true → status=1 全量
     * （前端按 perms 过滤可审按钮，服务层 audit 有 4104/4105 精确守卫）。
     */
    @GetMapping("/list")
    public Result<TableData<PurchaseReq>> list(@RequestParam(defaultValue = "") String keyword,
                                               @RequestParam(required = false) Integer status,
                                               @RequestParam(defaultValue = "false") boolean mine,
                                               @RequestParam(defaultValue = "false") boolean todo,
                                               @RequestParam(defaultValue = "1") int page,
                                               @RequestParam(defaultValue = "10") int size,
                                               HttpServletRequest request) {
        int offset = (page - 1) * size;
        Long applicantId = null;
        Integer queryStatus = status;
        if (mine) {
            applicantId = ((Number) request.getAttribute("uid")).longValue();
        }
        if (todo) {
            queryStatus = 1;
        }
        List<PurchaseReq> records = purchaseReqMapper.selectPage(keyword, applicantId, queryStatus, offset, size);
        return Result.ok(TableData.of(records,
                purchaseReqMapper.countPage(keyword, applicantId, queryStatus)));
    }

    /** 详情：单头 + 审批链（步骤条数据源）。 */
    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        PurchaseReq req = purchaseReqMapper.selectById(id);
        if (req == null) {
            throw new BizException(4101, "申购单不存在");
        }
        List<ApprovalRecord> records = purchaseFlowService.records(id);
        Map<String, Object> vo = new LinkedHashMap<>();
        vo.put("req", req);
        vo.put("records", records);
        return Result.ok(vo);
    }

    /**
     * 逐级审批（approve/reject，驳回 comment 必填在服务层）。节点权限键按单据
     * 当前级动态解析：currentSeq 对应链上节点 roleKey（即 purchase:audit-<节点键>）。
     */
    @PostMapping("/{id}/audit")
    public Result<Void> audit(@PathVariable Long id, @RequestBody Map<String, Object> body,
                              HttpServletRequest request) {
        long uid = ((Number) request.getAttribute("uid")).longValue();
        PurchaseReq req = purchaseReqMapper.selectById(id);
        if (req == null) {
            throw new BizException(4101, "申购单不存在");
        }
        if (req.getStatus() == null || req.getStatus() != 1) {
            throw new BizException(4101, "单据不在审批中");
        }
        List<ApprovalChainService.ApprovalNode> chain = approvalChainService.chainFor(req.getCategory());
        int seq = req.getCurrentSeq();
        if (seq < 1 || seq > chain.size()) {
            throw new BizException(4002, "审批级次越界：" + seq);
        }
        String nodePerm = chain.get(seq - 1).roleKey();
        if (purchaseReqMapper.countPerm(uid, nodePerm) == 0) {
            throw new BizException(4105, "无操作权限: " + nodePerm);
        }
        boolean approve = Boolean.TRUE.equals(body.get("approve"));
        String comment = body.get("comment") == null ? null : String.valueOf(body.get("comment"));
        purchaseFlowService.audit(id, uid, approve, comment);
        return Result.ok();
    }

    /** 作废：草稿或已批且未入库、仅申请人本人（条件更新四守卫合一在服务层）。 */
    @PostMapping("/{id}/void")
    @RequirePerm("hzm:purchase:submit")
    public Result<Void> voidReq(@PathVariable Long id, HttpServletRequest request) {
        long uid = ((Number) request.getAttribute("uid")).longValue();
        purchaseFlowService.voidReq(id, uid);
        return Result.ok();
    }
}
