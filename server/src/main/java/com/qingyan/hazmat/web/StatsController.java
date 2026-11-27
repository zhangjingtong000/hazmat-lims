package com.qingyan.hazmat.web;

import com.qingyan.hazmat.common.Result;
import com.qingyan.hazmat.hazmat.mapper.ChemInfoMapper;
import com.qingyan.hazmat.hazmat.mapper.PurchaseReqMapper;
import com.qingyan.hazmat.hazmat.mapper.RequisitionMapper;
import com.qingyan.hazmat.hazmat.mapper.WasteDisposalMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 统计看板/大屏（§5）：dashboard 四类预警仅聚合计数（⑦-9 不含单据明细——越权读封堵）；
 * ledger 按分类分组库存聚合；screen 大屏三区数据（分类分布/批次状态分布/处置统计）。
 * 已批超 7 天未入库预警排除已作废（status=5 天然不在 status=2 口径内）。
 */
@RestController
@RequestMapping("/chem/stats")
public class StatsController {

    /** 已批未入库预警阈值（天）。 */
    private static final int OVERDUE_DAYS = 7;

    private final ChemInfoMapper chemInfoMapper;
    private final PurchaseReqMapper purchaseReqMapper;
    private final RequisitionMapper requisitionMapper;
    private final WasteDisposalMapper wasteMapper;

    public StatsController(ChemInfoMapper chemInfoMapper, PurchaseReqMapper purchaseReqMapper,
                           RequisitionMapper requisitionMapper, WasteDisposalMapper wasteMapper) {
        this.chemInfoMapper = chemInfoMapper;
        this.purchaseReqMapper = purchaseReqMapper;
        this.requisitionMapper = requisitionMapper;
        this.wasteMapper = wasteMapper;
    }

    /** 四类预警聚合计数：①效期临近（批次各自台账 expiry_warn_days 内，含已过期）②低于安全库存 ③待审单据 ④已批超 7 天未入库。 */
    @GetMapping("/dashboard")
    public Result<Map<String, Object>> dashboard() {
        long expiryWarn = chemInfoMapper.countExpiryWarn();
        long lowStock = chemInfoMapper.countLowStock();
        long pendingDocs = purchaseReqMapper.countPending()
                + requisitionMapper.countPending()
                + wasteMapper.countPending();
        long overdueStockin = purchaseReqMapper.countApprovedOverdue(OVERDUE_DAYS);

        Map<String, Object> vo = new LinkedHashMap<>();
        vo.put("expiryWarn", expiryWarn);
        vo.put("lowStock", lowStock);
        vo.put("pendingDocs", pendingDocs);
        vo.put("overdueStockin", overdueStockin);
        return Result.ok(vo);
    }

    /** 库存台账统计：按分类分组（台账数 + Σ现存）。 */
    @GetMapping("/ledger")
    public Result<List<Map<String, Object>>> ledger() {
        return Result.ok(chemInfoMapper.ledgerByCategory());
    }

    /** 大屏数据：分类分布 / 批次状态分布 / 处置统计（三区横向）。 */
    @GetMapping("/screen")
    public Result<Map<String, Object>> screen() {
        Map<String, Object> vo = new LinkedHashMap<>();
        vo.put("categoryDist", chemInfoMapper.ledgerByCategory());
        vo.put("batchStatusDist", chemInfoMapper.batchStatusDist());
        vo.put("disposalStats", wasteMapper.statsByType());
        return Result.ok(vo);
    }
}
