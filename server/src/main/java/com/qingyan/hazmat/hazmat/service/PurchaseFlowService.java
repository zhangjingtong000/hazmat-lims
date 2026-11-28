package com.qingyan.hazmat.hazmat.service;

import com.qingyan.hazmat.common.BizException;
import com.qingyan.hazmat.hazmat.entity.ApprovalRecord;
import com.qingyan.hazmat.hazmat.entity.PurchaseReq;
import com.qingyan.hazmat.hazmat.mapper.ApprovalRecordMapper;
import com.qingyan.hazmat.hazmat.mapper.PurchaseReqMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 申购流转：草稿→提交→分级审批→已批→入库关闭；作废为旁路终态。
 * 审批链配置集中在 ApprovalChainService；全部状态推进用条件更新（affected=0 即拒），
 * 重复审批由 DB 层 UNIQUE(biz_type,biz_id,seq) 兜底（应用层转 4103）。
 */
@Service
public class PurchaseFlowService {

    public static final int BIZ_PURCHASE = 1;

    private final PurchaseReqMapper reqMapper;
    private final ApprovalRecordMapper recordMapper;
    private final ApprovalChainService chainService;

    public PurchaseFlowService(PurchaseReqMapper reqMapper, ApprovalRecordMapper recordMapper,
                               ApprovalChainService chainService) {
        this.reqMapper = reqMapper;
        this.recordMapper = recordMapper;
        this.chainService = chainService;
    }

    /** 保存申购单（草稿或直接可提交的单据本体），编码 AP-年月日-NNN。 */
    @Transactional
    public PurchaseReq save(PurchaseReq req) {
        if (req.getChemicalName() == null || req.getChemicalName().isBlank()) {
            throw new BizException(4101, "化学品名称必填");
        }
        if (req.getQty() == null || req.getQty().signum() <= 0) {
            throw new BizException(4101, "申购数量必须为正");
        }
        chainService.chainFor(req.getCategory()); // 分类合法性校验
        if (req.getAdvisorId() == null) {
            throw new BizException(4101, "必须指定导师");
        }
        if (req.getStatus() == null) {
            req.setStatus(0);
        }
        req.setCurrentSeq(1);
        for (int i = 0; i < 3; i++) { // uk_pr_code 兜底：并发重号重取当日序
            req.setCode(nextCode());
            try {
                reqMapper.insert(req);
                return req;
            } catch (org.springframework.dao.DuplicateKeyException e) {
                // 单号冲突，重取当日序再试
            }
        }
        throw new BizException(4101, "申购单号生成冲突，请重试");
    }

    /** 编码生成：AP-年月日-NNN（当日序，唯一索引兜底重试）。 */
    public String nextCode() {
        String day = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String prefix = "AP-" + day + "-";
        int seq = reqMapper.countToday(prefix) + 1;
        return prefix + String.format("%03d", seq);
    }

    /** 提交：草稿→审批中，起点由分类决定（current_seq=1 恒从导师起）。 */
    @Transactional
    public void submit(long id, long userId) {
        PurchaseReq req = mustGet(id);
        if (req.getStatus() != 0) {
            throw new BizException(4101, "仅草稿可提交");
        }
        if (req.getApplicantId() != userId) {
            throw new BizException(4104, "仅申请人本人可提交");
        }
        if (reqMapper.markSubmitted(id, userId, 1) == 0) {
            throw new BizException(4101, "单据状态已变化，请刷新");
        }
    }

    /** 逐级审批（approve/reject）。全链通过→status=2；任一级驳回→3 终态。 */
    @Transactional
    public void audit(long id, long approverId, boolean approve, String comment) {
        PurchaseReq req = mustGet(id);
        if (req.getStatus() != 1) {
            throw new BizException(4101, "单据不在审批中");
        }
        if (!approve && (comment == null || comment.isBlank())) {
            throw new BizException(4101, "驳回必须填写意见");
        }
        List<ApprovalChainService.ApprovalNode> chain = chainService.chainFor(req.getCategory());
        int seq = req.getCurrentSeq();
        ApprovalChainService.ApprovalNode node = chain.get(seq - 1);

        if (approverId == req.getApplicantId()) {
            throw new BizException(4104, "申请人不能审批本人的单据");
        }
        if (seq == 1) {
            if (approverId != req.getAdvisorId()) {
                throw new BizException(4105, "第一级须由单据指定的导师审批");
            }
        } else {
            ApprovalRecord prev = recordMapper.selectBySeq(BIZ_PURCHASE, id, seq - 1);
            if (prev != null && prev.getApproverId() == approverId) {
                throw new BizException(4203, "同一审批人不得连续审批两级");
            }
        }
        if (reqMapper.countPerm(approverId, node.roleKey()) == 0) {
            throw new BizException(4105, "当前用户不持有该节点的审批权限");
        }

        ApprovalRecord rec = new ApprovalRecord();
        rec.setBizType(BIZ_PURCHASE);
        rec.setBizId(id);
        rec.setSeq(seq);
        rec.setNodeKey(node.nodeKey());
        rec.setNodeName(node.nodeName());
        rec.setApproverId(approverId);
        rec.setComment(comment == null ? "" : comment);
        rec.setResult(approve ? 1 : 0);
        try {
            recordMapper.insert(rec);
        } catch (DuplicateKeyException e) {
            throw new BizException(4103, "该级已有审批结论（重复提交）");
        }

        if (!approve) {
            if (reqMapper.reject(id, seq) == 0) {
                throw new BizException(4101, "单据状态已变化，请刷新");
            }
            return;
        }
        int toSeq = seq < chain.size() ? seq + 1 : 0; // 0 表示链走完→已批
        if (reqMapper.advance(id, seq, toSeq) == 0) {
            throw new BizException(4101, "单据状态已变化，请刷新");
        }
    }

    /** 作废：草稿或已批且未入库，仅申请人；条件更新四守卫合一。 */
    @Transactional
    public void voidReq(long id, long userId) {
        if (reqMapper.voidReq(id, userId, BigDecimal.ZERO) == 0) {
            PurchaseReq req = reqMapper.selectById(id);
            if (req == null) {
                throw new BizException(4101, "单据不存在");
            }
            if (req.getApplicantId() != userId) {
                throw new BizException(4104, "仅申请人本人可作废");
            }
            if (req.getInQty().signum() > 0) {
                throw new BizException(4101, "已部分入库的申购不可作废");
            }
            throw new BizException(4101, "当前状态不可作废");
        }
    }

    /** 某业务单的审批链（详情页步骤条数据源）。 */
    public List<ApprovalRecord> records(long id) {
        return recordMapper.selectByBiz(BIZ_PURCHASE, id);
    }

    private PurchaseReq mustGet(long id) {
        PurchaseReq req = reqMapper.selectById(id);
        if (req == null) {
            throw new BizException(4101, "申购单不存在");
        }
        return req;
    }
}
