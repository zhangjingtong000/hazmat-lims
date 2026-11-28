package com.qingyan.hazmat.hazmat.service;

import com.qingyan.hazmat.common.BizException;
import com.qingyan.hazmat.hazmat.entity.ChemBatch;
import com.qingyan.hazmat.hazmat.entity.ChemCheck;
import com.qingyan.hazmat.hazmat.entity.ChemCheckDiff;
import com.qingyan.hazmat.hazmat.mapper.ChemCheckDiffMapper;
import com.qingyan.hazmat.hazmat.mapper.ChemCheckMapper;
import com.qingyan.hazmat.hazmat.mapper.ChemInfoMapper;
import com.qingyan.hazmat.hazmat.mapper.PurchaseReqMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 账物盘点：create（双人制单）→ finish（双人提交差异记录，**不改库存**，
 * 盘点与调账两段分离）→ adjust（持权人增量修正，批次+台账同事务 ±）。
 * adjust 守卫：批次须 status=0。finish 与 adjust 之间批次可能已被封存/销账，
 * 对不在 Σ 里的批次做加减会破不变量——失败时该差异行 remark 标"需重盘"；
 * 增量修正语义：diff>0 回加 / diff<0 条件扣减，禁"设为 actual_qty"。
 */
@Service
public class CheckService {

    public static final String PERM_ADJUST = "hzm:check:adjust";

    public static final int RESULT_DOING = 0;   // 盘点中
    public static final int RESULT_BALANCED = 1; // 平
    public static final int RESULT_UNBALANCED = 2; // 不平

    private final ChemCheckMapper checkMapper;
    private final ChemCheckDiffMapper diffMapper;
    private final ChemInfoMapper chemMapper;
    private final PurchaseReqMapper reqMapper;
    private final ApprovalChainService chainService;
    private final CheckRemarkService remarkService;

    public CheckService(ChemCheckMapper checkMapper, ChemCheckDiffMapper diffMapper,
                        ChemInfoMapper chemMapper, PurchaseReqMapper reqMapper,
                        ApprovalChainService chainService, CheckRemarkService remarkService) {
        this.checkMapper = checkMapper;
        this.diffMapper = diffMapper;
        this.chemMapper = chemMapper;
        this.reqMapper = reqMapper;
        this.chainService = chainService;
        this.remarkService = remarkService;
    }

    /** 编码生成：PD-年月日-NNN（当日序，唯一索引兜底重试）。 */
    public String nextCode() {
        String day = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String prefix = "PD-" + day + "-";
        return prefix + String.format("%03d", checkMapper.countToday(prefix) + 1);
    }

    /** 制单：双人盘点（checker_two≠checker_one，4201）；scope_category 可空=全部。 */
    @Transactional
    public ChemCheck create(Integer scopeCategory, long checkerOne, long checkerTwo) {
        if (checkerTwo == checkerOne) {
            throw new BizException(4201, "两名盘点人不得为同一人");
        }
        if (scopeCategory != null) {
            chainService.chainFor(scopeCategory); // 分类合法性（未知分类 4001）
        }
        ChemCheck check = new ChemCheck();
        check.setCode(nextCode());
        check.setScopeCategory(scopeCategory);
        check.setCheckerOneId(checkerOne);
        check.setCheckerTwoId(checkerTwo);
        check.setDiffCount(0);
        check.setResult(RESULT_DOING);
        checkMapper.insert(check);
        return check;
    }

    /**
     * 结单：逐条差异按批次当前余量快照 book_qty 记录，diff=actual-book；
     * 仅记录不修账（评审③：finish/adjust 两段分离），diff_count=非零差异数，
     * result=全平?1:2，条件更新锁定"盘点中"防重复结单。
     */
    @Transactional
    public void finish(long checkId, List<ChemCheckDiff> diffs, long user) {
        ChemCheck check = mustGet(checkId);
        Integer result = check.getResult();
        if (result == null || result != RESULT_DOING) {
            throw new BizException(4101, "该盘点单已结单");
        }
        if (user != check.getCheckerOneId() && user != check.getCheckerTwoId()) {
            throw new BizException(4105, "仅本单两名盘点人可提交盘点结果");
        }
        int diffCount = 0;
        for (ChemCheckDiff d : diffs) {
            if (d == null || d.getBatchId() == null || d.getActualQty() == null) {
                throw new BizException(4101, "差异行必须指定批次与实盘数");
            }
            ChemBatch batch = chemMapper.selectBatch(d.getBatchId());
            if (batch == null) {
                throw new BizException(4101, "批次不存在：" + d.getBatchId());
            }
            // book=批次当前余量快照（此时点即账面口径）
            BigDecimal book = batch.getQtyLeft() == null ? BigDecimal.ZERO : batch.getQtyLeft();
            BigDecimal diff = d.getActualQty().subtract(book);
            d.setCheckId(checkId);
            d.setBookQty(book);
            d.setDiffQty(diff);
            if (diff.signum() != 0) {
                diffCount++;
            }
            diffMapper.insert(d);
        }
        if (checkMapper.finish(checkId, diffCount, diffCount == 0 ? RESULT_BALANCED : RESULT_UNBALANCED) == 0) {
            throw new BizException(4101, "该盘点单已结单，请刷新");
        }
    }

    /**
     * 调账：持 'hzm:check:adjust'（4105）；差异行未调过账（4101）；
     * 批次须 status=0——否则该差异行 remark 标"需重盘"并拒绝（封存批次已退出 Σ，
     * 加减其余量必破守恒）；增量修正：diff>0→addBatch(+diff)，diff<0→deductBatch(|diff|)
     * （条件更新，不足→4101），台账 changeStock 同事务同步；落 adjust_time 防重复调账。
     *
     * <p>"需重盘"标记走 {@link CheckRemarkService} 的独立事务：本方法抛出 4101 会回滚
     * 当前事务，标记若同事务则一并丢失。
     */
    @Transactional
    public void adjust(long checkId, long diffId, long user) {
        if (reqMapper.countPerm(user, PERM_ADJUST) == 0) {
            throw new BizException(4105, "当前用户不持有盘点调账权限");
        }
        ChemCheckDiff diff = diffMapper.selectById(diffId);
        if (diff == null || diff.getCheckId() == null || diff.getCheckId() != checkId) {
            throw new BizException(4101, "差异明细不存在或不属于该盘点单");
        }
        if (diff.getAdjustTime() != null) {
            throw new BizException(4101, "该差异已调账，不可重复调整");
        }
        ChemBatch batch = chemMapper.selectBatch(diff.getBatchId());
        if (batch == null) {
            remarkService.markRecheck(diffId);
            throw new BizException(4101, "批次不存在，差异行已标记需重盘");
        }
        if (batch.getStatus() == null || batch.getStatus() != 0) {
            // finish 与 adjust 之间批次已被封存/废弃——标记"需重盘"后拒绝。
            remarkService.markRecheck(diffId);
            throw new BizException(4101, "批次已不在库（封存/废弃），差异行已标记需重盘");
        }
        BigDecimal diffQty = diff.getDiffQty();
        if (diffQty == null || diffQty.signum() == 0) {
            throw new BizException(4101, "该差异行无差异，无需调账");
        }
        if (diffQty.signum() > 0) {
            // 盘盈：仅限指定既有批次增余量
            if (chemMapper.addBatch(batch.getId(), diffQty) == 0) {
                throw new BizException(4101, "批次回加失败");
            }
        } else {
            // 盘亏：条件扣减，余量不足即拒（并发口径与 R4 同族）
            if (chemMapper.deductBatch(batch.getId(), diffQty.abs()) == 0) {
                throw new BizException(4101, "批次余量不足以调减");
            }
        }
        if (chemMapper.changeStock(batch.getChemicalId(), diffQty) == 0) {
            throw new BizException(4101, "台账同步失败");
        }
        diffMapper.markAdjusted(diffId);
    }

    /** 某盘点单的差异明细（详情页数据源）。 */
    public List<ChemCheckDiff> diffs(long checkId) {
        return diffMapper.selectByCheck(checkId);
    }

    private ChemCheck mustGet(long id) {
        ChemCheck check = checkMapper.selectById(id);
        if (check == null) {
            throw new BizException(4101, "盘点单不存在");
        }
        return check;
    }
}
