package com.qingyan.hazmat.hazmat.service;

import com.qingyan.hazmat.common.BizException;
import com.qingyan.hazmat.hazmat.entity.ChemBatch;
import com.qingyan.hazmat.hazmat.entity.ChemInfo;
import com.qingyan.hazmat.hazmat.entity.PurchaseReq;
import com.qingyan.hazmat.hazmat.entity.Requisition;
import com.qingyan.hazmat.hazmat.entity.RequisitionBatch;
import com.qingyan.hazmat.hazmat.entity.StockReturn;
import com.qingyan.hazmat.hazmat.mapper.ChemInfoMapper;
import com.qingyan.hazmat.hazmat.mapper.PurchaseReqMapper;
import com.qingyan.hazmat.hazmat.mapper.RequisitionBatchMapper;
import com.qingyan.hazmat.hazmat.mapper.RequisitionMapper;
import com.qingyan.hazmat.hazmat.mapper.StockReturnMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

/**
 * 领用/归还（R5 FEFO 核心）：提交→导师签→复核签→FEFO 跨批次出库→归还回库→核销/改派。
 * 状态机（C2/N-5）：关联已批申购或 category=0 → 1 待复核；非关联且 category≥1 → 0 待导师；
 * 0→1→2 已出库，任一签驳回→3 终态；归还闭环 2→4（Σ归还≥领用自动 / 核销 close）。
 * 守卫纪律：台账封存拒领 4504（V5-1）；批次条件扣减 affected=0 换批、全败 4501（R4）；
 * 改派幂等条件更新 ⑦-4（防双回加幻影余量）；归还超量对账条件更新 4602（N-2）。
 */
@Service
public class RequisitionService {

    /** 双签/改派/归还确认共用权限（任意持权人可审模式） */
    public static final String PERM_COUNTERSIGN = "hzm:stockin:countersign";
    /** 导师节点权限键（与申购链同键，R1 指定制+角色校验 N-6） */
    public static final String PERM_AUDIT_ADVISOR = "hzm:purchase:audit-advisor";
    /** 学院级权限键（归还确认的第二选择） */
    public static final String PERM_AUDIT_COLLEGE = "hzm:purchase:audit-college";

    private final ChemInfoMapper chemInfoMapper;
    private final PurchaseReqMapper purchaseReqMapper;
    private final RequisitionMapper requisitionMapper;
    private final RequisitionBatchMapper requisitionBatchMapper;
    private final StockReturnMapper stockReturnMapper;

    public RequisitionService(ChemInfoMapper chemInfoMapper, PurchaseReqMapper purchaseReqMapper,
                              RequisitionMapper requisitionMapper, RequisitionBatchMapper requisitionBatchMapper,
                              StockReturnMapper stockReturnMapper) {
        this.chemInfoMapper = chemInfoMapper;
        this.purchaseReqMapper = purchaseReqMapper;
        this.requisitionMapper = requisitionMapper;
        this.requisitionBatchMapper = requisitionBatchMapper;
        this.stockReturnMapper = stockReturnMapper;
    }

    /** 领用单编码：LY-年月日-NNN（当日序，唯一索引兜底重试）。 */
    public String nextCode() {
        String day = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String prefix = "LY-" + day + "-";
        int seq = requisitionMapper.countToday(prefix) + 1;
        return prefix + String.format("%03d", seq);
    }

    /** 归还单编码：GH-年月日-NNN。 */
    public String nextReturnCode() {
        String day = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String prefix = "GH-" + day + "-";
        int seq = stockReturnMapper.countToday(prefix) + 1;
        return prefix + String.format("%03d", seq);
    }

    /** 提交领用（N-5 起点分支在此定档）：校验台账在管、剧毒单次上限、关联申购双校验+超领。 */
    @Transactional
    public Requisition submit(Long chemId, BigDecimal qty, long applicantId, Long reqId,
                              LocalDate expectReturn, String purpose, String experiment) {
        if (qty == null || qty.signum() <= 0) {
            throw new BizException(4101, "领用数量必须为正");
        }
        ChemInfo info = chemInfoMapper.selectById(chemId);
        if (info == null) {
            throw new BizException(4101, "台账不存在");
        }
        if (info.getStatus() == null || info.getStatus() != 0) {
            throw new BizException(4504, "台账已封存，停止领用");
        }
        if (info.getMaxOnce() != null && qty.compareTo(info.getMaxOnce()) > 0) {
            throw new BizException(4701, "超过剧毒单次领用上限");
        }
        if (reqId != null) {
            // V5-6 关联引用双校验：①已批申购 ②CAS 与台账匹配
            PurchaseReq purchase = purchaseReqMapper.selectById(reqId);
            if (purchase == null || purchase.getStatus() == null || purchase.getStatus() != 2) {
                throw new BizException(4404, "关联申购单不是已批状态");
            }
            if (!Objects.equals(purchase.getCasNo(), info.getCasNo())) {
                throw new BizException(4404, "关联申购 CAS 与台账不匹配");
            }
            // C3 超领：Σ(该申购下 status IN(2,4) 领用)+本次 ≤ 申购 qty
            BigDecimal used = requisitionMapper.sumQtyByReq(reqId);
            if (used.add(qty).compareTo(purchase.getQty()) > 0) {
                throw new BizException(4403, "关联申购剩余可领量不足");
            }
        }
        // N-5 初始态分支：关联已批申购或普通化学品 → 直接待复核；非关联且 category≥1 → 待导师
        boolean needAdvisor = reqId == null && info.getCategory() != null && info.getCategory() >= 1;
        Requisition req = new Requisition();
        req.setCode(nextCode());
        req.setChemicalId(chemId);
        req.setQty(qty);
        req.setPurpose(purpose);
        req.setExperiment(experiment);
        req.setApplicantId(applicantId);
        req.setReqId(reqId);
        req.setExpectReturn(expectReturn);
        req.setReturnQty(BigDecimal.ZERO);
        req.setStatus(needAdvisor ? 0 : 1);
        requisitionMapper.insert(req);
        return req;
    }

    /** 导师签（仅 0 待导师，N-6）：回避+角色校验；通过 0→1，驳回→3 终态。 */
    @Transactional
    public void advisor(long id, long userId, boolean approve, String comment) {
        Requisition req = mustGet(id);
        if (req.getStatus() == null || req.getStatus() != 0) {
            throw new BizException(4101, "单据不在待导师状态");
        }
        if (!approve && (comment == null || comment.isBlank())) {
            throw new BizException(4101, "驳回必须填写意见");
        }
        if (userId == req.getApplicantId()) {
            throw new BizException(4104, "申请人不能审批本人的单据");
        }
        if (purchaseReqMapper.countPerm(userId, PERM_AUDIT_ADVISOR) == 0) {
            throw new BizException(4105, "当前用户不持有导师签权限");
        }
        if (!approve) {
            if (requisitionMapper.reject(id, 0) == 0) {
                throw new BizException(4101, "单据状态已变化，请刷新");
            }
            return;
        }
        if (requisitionMapper.approveAdvisor(id, userId) == 0) {
            throw new BizException(4101, "单据状态已变化，请刷新");
        }
    }

    /** 复核双签（仅 1 待复核）：同人守卫+safety+ 角色；通过→FEFO 出库，驳回→3 终态。 */
    @Transactional
    public void check(long id, long checkerId, boolean approve, String comment) {
        Requisition req = mustGet(id);
        if (req.getStatus() == null || req.getStatus() != 1) {
            throw new BizException(4101, "单据不在待复核状态");
        }
        if (!approve && (comment == null || comment.isBlank())) {
            throw new BizException(4101, "驳回必须填写意见");
        }
        if (checkerId == req.getApplicantId()) {
            throw new BizException(4201, "复核与申请人不得同人");
        }
        if (purchaseReqMapper.countPerm(checkerId, PERM_COUNTERSIGN) == 0) {
            throw new BizException(4202, "当前用户不持有双签复核权限");
        }
        if (!approve) {
            if (requisitionMapper.reject(id, 1) == 0) {
                throw new BizException(4101, "单据状态已变化，请刷新");
            }
            return;
        }
        allocateFefo(req, checkerId);
    }

    /** FEFO 出库（R5）：Σ余量预检→条件扣减逐批（affected=0 换批）→明细落库→台账同步→1→2。 */
    private void allocateFefo(Requisition req, long checkerId) {
        List<ChemBatch> batches = chemInfoMapper.selectFefoBatches(req.getChemicalId());
        BigDecimal need = req.getQty();
        BigDecimal avail = BigDecimal.ZERO;
        for (ChemBatch batch : batches) {
            avail = avail.add(batch.getQtyLeft() == null ? BigDecimal.ZERO : batch.getQtyLeft());
        }
        if (avail.compareTo(need) < 0) {
            throw new BizException(4501, "在库批次余量不足");
        }
        int seqNo = requisitionBatchMapper.countByReq(req.getId()) + 1;
        for (ChemBatch batch : batches) {
            if (need.signum() == 0) {
                break;
            }
            BigDecimal take = need.min(batch.getQtyLeft());
            // 条件扣减：并发余量被抢 affected=0 → 换下一批重试（R4 治本修法）
            if (chemInfoMapper.deductBatch(batch.getId(), take) == 0) {
                continue;
            }
            RequisitionBatch row = new RequisitionBatch();
            row.setReqId(req.getId());
            row.setBatchId(batch.getId());
            row.setQty(take);
            row.setSeqNo(seqNo++);
            requisitionBatchMapper.insert(row);
            need = need.subtract(take);
        }
        if (need.signum() > 0) {
            // 理论上被 avail 预检兜住，防御并发窗口残余
            throw new BizException(4501, "在库批次余量不足");
        }
        if (chemInfoMapper.changeStock(req.getChemicalId(), req.getQty().negate()) == 0) {
            throw new BizException(4501, "台账现存不足，出库失败");
        }
        // 出库动作全部落定后才置 1→2（条件更新，并发复核互斥）
        if (requisitionMapper.markOut(req.getId(), checkerId) == 0) {
            throw new BizException(4101, "单据状态已变化，请刷新");
        }
    }

    /** 改派（V6-4/⑦-4）：明细行条件更新幂等→回加原批→扣新批；等量换批台账不动。 */
    @Transactional
    public void dispatch(long id, long originalBatchId, long newBatchId, long userId) {
        if (purchaseReqMapper.countPerm(userId, PERM_COUNTERSIGN) == 0) {
            throw new BizException(4105, "当前用户不持有双签改派权限");
        }
        Requisition req = mustGet(id);
        if (req.getStatus() == null || req.getStatus() != 2) {
            throw new BizException(4101, "仅已出库单据可改派");
        }
        RequisitionBatch row = requisitionBatchMapper.selectRow(id, originalBatchId);
        if (row == null) {
            throw new BizException(4101, "改派明细不存在");
        }
        ChemBatch target = chemInfoMapper.selectBatch(newBatchId);
        if (target == null || !Objects.equals(target.getChemicalId(), req.getChemicalId())) {
            throw new BizException(4502, "改派目标批次无效");
        }
        // 幂等条件更新：affected=0 即已被并发改派（防双回加产生幻影余量）
        try {
            if (requisitionBatchMapper.reassign(id, originalBatchId, newBatchId) == 0) {
                throw new BizException(4101, "该明细已被并发改派，请刷新");
            }
        } catch (DuplicateKeyException e) {
            throw new BizException(4101, "该单已存在相同目标批次明细");
        }
        // 回加原批→扣新批，任一失败抛异常整体回滚（明细行更新一并回滚）
        if (chemInfoMapper.addBatch(originalBatchId, row.getQty()) == 0) {
            throw new BizException(4502, "原批次已不在库，回加失败");
        }
        if (chemInfoMapper.deductBatch(newBatchId, row.getQty()) == 0) {
            throw new BizException(4501, "新批次余量不足，改派失败");
        }
    }

    /** 提交余量归还：领用单须已出库、批次在库且化学品匹配；超量对账延迟到确认时条件更新。 */
    @Transactional
    public StockReturn submitReturn(long reqId, long batchId, BigDecimal qty, long applicantId) {
        Requisition req = mustGet(reqId);
        if (req.getStatus() == null || req.getStatus() != 2) {
            throw new BizException(4101, "领用单未出库，不可归还");
        }
        if (req.getApplicantId() != applicantId) {
            throw new BizException(4104, "仅领用申请人可发起归还");
        }
        if (qty == null || qty.signum() <= 0) {
            throw new BizException(4101, "归还数量必须为正");
        }
        ChemBatch batch = chemInfoMapper.selectBatch(batchId);
        if (batch == null || batch.getStatus() == null || batch.getStatus() != 0) {
            throw new BizException(4204, "归还批次非在库（过期封存/废弃请走废弃流程）");
        }
        if (!Objects.equals(batch.getChemicalId(), req.getChemicalId())) {
            throw new BizException(4601, "归还批次与领用化学品不匹配");
        }
        StockReturn ret = new StockReturn();
        ret.setCode(nextReturnCode());
        ret.setReqId(reqId);
        ret.setBatchId(batchId);
        ret.setQty(qty);
        ret.setApplicantId(applicantId);
        ret.setStatus(0);
        stockReturnMapper.insert(ret);
        return ret;
    }

    /** 归还确认（H-1）：超量对账条件更新 4602→原批回加→台账加回→单据置 1→足额自动关闭。 */
    @Transactional
    public void confirmReturn(long returnId, long confirmerId) {
        StockReturn ret = stockReturnMapper.selectById(returnId);
        if (ret == null) {
            throw new BizException(4101, "归还单不存在");
        }
        if (ret.getStatus() == null || ret.getStatus() != 0) {
            throw new BizException(4101, "仅待确认归还单可确认");
        }
        if (purchaseReqMapper.countPerm(confirmerId, PERM_COUNTERSIGN) == 0
                && purchaseReqMapper.countPerm(confirmerId, PERM_AUDIT_COLLEGE) == 0) {
            throw new BizException(4202, "确认人须持安全员或学院角色");
        }
        Requisition req = mustGet(ret.getReqId());
        // 超量守卫（N-2 条件更新）：return_qty+:qty ≤ qty 且 status=2，affected=0 即超还/已关闭
        if (requisitionMapper.incrReturnQty(ret.getReqId(), ret.getQty()) == 0) {
            throw new BizException(4602, "累计归还超领用量（或领用单已关闭）");
        }
        // 原批次回加（status=0 守卫 4204）
        if (chemInfoMapper.addBatch(ret.getBatchId(), ret.getQty()) == 0) {
            throw new BizException(4204, "归还批次非在库，回加失败");
        }
        // 守恒：台账同步加回
        if (chemInfoMapper.changeStock(req.getChemicalId(), ret.getQty()) == 0) {
            throw new BizException(4501, "台账回加失败");
        }
        if (stockReturnMapper.confirm(returnId, confirmerId) == 0) {
            throw new BizException(4101, "归还单状态已变化，请刷新");
        }
        // 足额才关单（部分归还不触发——⑦-2 关闭语义，避免空更新）
        java.math.BigDecimal after = req.getReturnQty().add(ret.getQty());
        if (after.compareTo(req.getQty()) >= 0) {
            requisitionMapper.complete(ret.getReqId());
        }
    }

    /** 归还拒绝（N-3）：仅 0 待确认→2，拒因必填。权限由控制器 return:reject 按钮级承担。 */
    @Transactional
    public void rejectReturn(long returnId, String comment, long userId) {
        StockReturn ret = stockReturnMapper.selectById(returnId);
        if (ret == null) {
            throw new BizException(4101, "归还单不存在");
        }
        if (ret.getStatus() == null || ret.getStatus() != 0) {
            throw new BizException(4101, "仅待确认归还单可拒绝");
        }
        if (comment == null || comment.isBlank()) {
            throw new BizException(4101, "拒绝必须填写原因");
        }
        if (stockReturnMapper.reject(returnId, comment) == 0) {
            throw new BizException(4101, "归还单状态已变化，请刷新");
        }
    }

    /** 核销（⑦-2）：仅已出库 2 且申请人本人，comment 必填；条件更新 2→4 封堵"永挂 2"盲区。 */
    @Transactional
    public void close(long id, long userId, String comment) {
        if (comment == null || comment.isBlank()) {
            throw new BizException(4101, "核销必须填写说明");
        }
        if (requisitionMapper.close(id, userId) == 0) {
            Requisition req = requisitionMapper.selectById(id);
            if (req == null) {
                throw new BizException(4101, "领用单不存在");
            }
            if (req.getApplicantId() != userId) {
                throw new BizException(4104, "仅申请人本人可核销");
            }
            throw new BizException(4101, "仅已出库单据可核销");
        }
    }

    private Requisition mustGet(long id) {
        Requisition req = requisitionMapper.selectById(id);
        if (req == null) {
            throw new BizException(4101, "领用单不存在");
        }
        return req;
    }
}
