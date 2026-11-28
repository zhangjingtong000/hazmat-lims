package com.qingyan.hazmat.hazmat.service;

import com.qingyan.hazmat.common.BizException;
import com.qingyan.hazmat.hazmat.entity.ApprovalRecord;
import com.qingyan.hazmat.hazmat.entity.ChemBatch;
import com.qingyan.hazmat.hazmat.entity.ChemInfo;
import com.qingyan.hazmat.hazmat.entity.WasteDisposal;
import com.qingyan.hazmat.hazmat.mapper.ApprovalRecordMapper;
import com.qingyan.hazmat.hazmat.mapper.ChemInfoMapper;
import com.qingyan.hazmat.hazmat.mapper.PurchaseReqMapper;
import com.qingyan.hazmat.hazmat.mapper.WasteDisposalMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 废弃物处置（销账侧）：批次匹配守卫（batch.chemical_id==chemical_id，错批即销错台账）；
 * type=1/2 废液/空瓶=在库批次销账（批次条件扣减 + 台账同事务同步），
 * type=3 过期整批=仅销批次（wipeBatch：qty_left→0、status 3→2；口径假设"封存时台账已扣"，
 * 故不再动台账）。注意：当前没有把批次置 status=3 的运行时入口（updateBatchStatus 零调用点），
 * 该分支只对演示种子或人工改库的批次生效。审批单级：category≤1 学院须持 audit-college /
 * category≥2 校级须持 audit-campus（与申购链同轴）；approver≠operator 回避；
 * 审批结论复用通用审批记录 approval_record（biz_type=2），UNIQUE 兜底转 4103。
 */
@Service
public class WasteService {

    public static final int BIZ_WASTE = 2;

    public static final int TYPE_LIQUID = 1;   // 废液
    public static final int TYPE_BOTTLE = 2;   // 空瓶
    public static final int TYPE_EXPIRED = 3;  // 过期整批

    private static final String PERM_COLLEGE = "hzm:purchase:audit-college";
    private static final String PERM_CAMPUS = "hzm:purchase:audit-campus";

    private final WasteDisposalMapper wasteMapper;
    private final ChemInfoMapper chemMapper;
    private final PurchaseReqMapper reqMapper;
    private final ApprovalRecordMapper recordMapper;

    public WasteService(WasteDisposalMapper wasteMapper, ChemInfoMapper chemMapper,
                        PurchaseReqMapper reqMapper, ApprovalRecordMapper recordMapper) {
        this.wasteMapper = wasteMapper;
        this.chemMapper = chemMapper;
        this.reqMapper = reqMapper;
        this.recordMapper = recordMapper;
    }

    /** 编码生成：FQ-年月日-NNN（当日序，唯一索引兜底重试）。 */
    public String nextCode() {
        String day = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String prefix = "FQ-" + day + "-";
        return prefix + String.format("%03d", wasteMapper.countToday(prefix) + 1);
    }

    /** 建单：批次一律强制（混合废液按化学品拆单——一张处置单一种化学品一个批次）。 */
    @Transactional
    public WasteDisposal submit(Long chemId, Long batchId, int type, BigDecimal qty, String method, long operatorId) {
        if (chemId == null || batchId == null) {
            throw new BizException(4101, "必须指定化学品与批次");
        }
        ChemBatch batch = chemMapper.selectBatch(batchId);
        if (batch == null) {
            throw new BizException(4101, "批次不存在");
        }
        // 错批守卫：处置单上的化学品必须就是批次所属化学品（否则错批销错台账）
        if (!chemId.equals(batch.getChemicalId())) {
            throw new BizException(4603, "处置批次与化学品不匹配");
        }
        WasteDisposal wd = new WasteDisposal();
        wd.setCode(nextCode());
        wd.setChemicalId(chemId);
        wd.setBatchId(batchId);
        wd.setType(type);
        wd.setMethod(method == null ? "" : method);
        wd.setOperatorId(operatorId);
        wd.setStatus(0);
        if (type == TYPE_EXPIRED) {
            // 过期整批：仅针对过期封存批次（status=3），销账量=封存余量整批
            if (batch.getStatus() == null || batch.getStatus() != 3) {
                throw new BizException(4502, "type=3 仅针对过期封存批次");
            }
            wd.setQty(batch.getQtyLeft() == null ? BigDecimal.ZERO : batch.getQtyLeft());
        } else if (type == TYPE_LIQUID || type == TYPE_BOTTLE) {
            // 废液/空瓶：在库批次销账，处置量不得超过批次余量
            if (batch.getStatus() == null || batch.getStatus() != 0) {
                throw new BizException(4501, "仅对在库批次销账");
            }
            if (qty == null || qty.signum() <= 0) {
                throw new BizException(4101, "处置数量必须为正");
            }
            if (batch.getQtyLeft() == null || qty.compareTo(batch.getQtyLeft()) > 0) {
                throw new BizException(4501, "处置数量超过批次余量");
            }
            wd.setQty(qty);
        } else {
            throw new BizException(4101, "未知处置类型：" + type);
        }
        wasteMapper.insert(wd);
        return wd;
    }

    /** 审批（单级）：approve→销账；reject→驳回终态（comment 必填）。 */
    @Transactional
    public void audit(long id, long approverId, boolean approve, String comment) {
        WasteDisposal wd = mustGet(id);
        if (wd.getStatus() == null || wd.getStatus() != 0) {
            throw new BizException(4101, "仅待审批状态可审批");
        }
        if (!approve && (comment == null || comment.isBlank())) {
            throw new BizException(4101, "驳回必须填写意见");
        }
        // 回避：处置人不得审批本人的处置单
        if (wd.getOperatorId() != null && wd.getOperatorId() == approverId) {
            throw new BizException(4104, "处置人不能审批本人的处置单");
        }
        ChemInfo chem = chemMapper.selectById(wd.getChemicalId());
        if (chem == null) {
            throw new BizException(4101, "化学品台账不存在");
        }
        // 审批层级与申购链同轴单级：category≤1 学院 / category≥2 校级
        Integer category = chem.getCategory();
        boolean campusLevel = category != null && category >= 2;
        String perm = campusLevel ? PERM_CAMPUS : PERM_COLLEGE;
        if (reqMapper.countPerm(approverId, perm) == 0) {
            throw new BizException(4105, "当前用户不持有该审批层级的权限");
        }

        ApprovalRecord rec = new ApprovalRecord();
        rec.setBizType(BIZ_WASTE);
        rec.setBizId(id);
        rec.setSeq(1); // 单级审批
        rec.setNodeKey(campusLevel ? "campus" : "college");
        rec.setNodeName(campusLevel ? "校级审批" : "学院审批");
        rec.setApproverId(approverId);
        rec.setComment(comment == null ? "" : comment);
        rec.setResult(approve ? 1 : 0);
        try {
            recordMapper.insert(rec);
        } catch (DuplicateKeyException e) {
            throw new BizException(4103, "该单已有审批结论（重复提交）");
        }

        if (!approve) {
            if (wasteMapper.reject(id, approverId) == 0) {
                throw new BizException(4101, "单据状态已变化，请刷新");
            }
            return;
        }
        if (wasteMapper.approve(id, approverId) == 0) {
            throw new BizException(4101, "单据状态已变化，请刷新");
        }
        // 销账（与置已处置同事务，守恒）
        if (wd.getType() != null && wd.getType() == TYPE_EXPIRED) {
            // type=3 过期整批：仅销批次（口径假设封存时台账已扣，故不再动 stock_qty；status=3 目前只能演示种子/人工改库产生）
            if (chemMapper.wipeBatch(wd.getBatchId()) == 0) {
                throw new BizException(4502, "封存批次状态已变化，销账失败");
            }
        } else {
            // type=1/2：批次条件扣减 + 台账原子同步
            if (chemMapper.deductBatch(wd.getBatchId(), wd.getQty()) == 0) {
                throw new BizException(4501, "批次余量不足，销账失败");
            }
            if (chemMapper.changeStock(wd.getChemicalId(), wd.getQty().negate()) == 0) {
                throw new BizException(4501, "台账同步扣减失败");
            }
        }
    }

    /** 某处置单的审批记录（详情页步骤条数据源）。 */
    public List<ApprovalRecord> records(long id) {
        return recordMapper.selectByBiz(BIZ_WASTE, id);
    }

    private WasteDisposal mustGet(long id) {
        WasteDisposal wd = wasteMapper.selectById(id);
        if (wd == null) {
            throw new BizException(4101, "处置单不存在");
        }
        return wd;
    }
}
