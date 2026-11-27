package com.qingyan.hazmat.web;

import com.qingyan.hazmat.common.Result;
import com.qingyan.hazmat.hazmat.entity.PurchaseReq;
import com.qingyan.hazmat.hazmat.entity.Requisition;
import com.qingyan.hazmat.hazmat.entity.StockReturn;
import com.qingyan.hazmat.hazmat.entity.WasteDisposal;
import com.qingyan.hazmat.hazmat.mapper.ApprovalRecordMapper;
import com.qingyan.hazmat.hazmat.mapper.PurchaseReqMapper;
import com.qingyan.hazmat.hazmat.mapper.RequisitionMapper;
import com.qingyan.hazmat.hazmat.mapper.StockReturnMapper;
import com.qingyan.hazmat.hazmat.mapper.WasteDisposalMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 审批中心待办（§5）：四类聚合——待审申购（status=1）/ 待签领用（status IN 0,1）/
 * 待审废弃（status=0）/ 待确认归还（status=0）。登录即可（前端按 perms 过滤审批按钮；
 * 服务层审批/确认时仍有 4104/4105/4201/权限键精确守卫）。
 * G4 已办：附"我的已办"聚合（approval_record approver 视角 + 领用导师/复核双签，近 20 条）。
 */
@RestController
@RequestMapping("/chem/todo")
public class TodoController {

    private final PurchaseReqMapper purchaseReqMapper;
    private final RequisitionMapper requisitionMapper;
    private final WasteDisposalMapper wasteMapper;
    private final StockReturnMapper stockReturnMapper;
    private final ApprovalRecordMapper recordMapper;

    public TodoController(PurchaseReqMapper purchaseReqMapper, RequisitionMapper requisitionMapper,
                          WasteDisposalMapper wasteMapper, StockReturnMapper stockReturnMapper,
                          ApprovalRecordMapper recordMapper) {
        this.purchaseReqMapper = purchaseReqMapper;
        this.requisitionMapper = requisitionMapper;
        this.wasteMapper = wasteMapper;
        this.stockReturnMapper = stockReturnMapper;
        this.recordMapper = recordMapper;
    }

    /** 三类待办聚合计数 + 列表（各取近 20 条，按提交时间先后）+ 我的已办（G4）。 */
    @GetMapping
    public Result<Map<String, Object>> todo(HttpServletRequest request) {
        long uid = ((Number) request.getAttribute("uid")).longValue();
        long purchaseCount = purchaseReqMapper.countPending();
        long requisitionCount = requisitionMapper.countPending();
        long wasteCount = wasteMapper.countPending();
        long returnCount = stockReturnMapper.countPending();

        List<PurchaseReq> purchases = purchaseReqMapper.selectPending();
        List<Requisition> requisitions = requisitionMapper.selectPending();
        List<WasteDisposal> wastes = wasteMapper.selectPending();
        List<StockReturn> returns = stockReturnMapper.selectPending();
        List<Map<String, Object>> done = recordMapper.selectDone(uid, 20);

        Map<String, Object> vo = new LinkedHashMap<>();
        vo.put("purchaseCount", purchaseCount);
        vo.put("requisitionCount", requisitionCount);
        vo.put("wasteCount", wasteCount);
        vo.put("returnCount", returnCount);
        vo.put("purchases", purchases);
        vo.put("requisitions", requisitions);
        vo.put("wastes", wastes);
        vo.put("returns", returns);
        vo.put("doneCount", done == null ? 0 : done.size());
        vo.put("done", done);
        return Result.ok(vo);
    }
}
