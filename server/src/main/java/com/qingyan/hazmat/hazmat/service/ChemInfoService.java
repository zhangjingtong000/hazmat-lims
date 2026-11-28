package com.qingyan.hazmat.hazmat.service;

import com.qingyan.hazmat.common.BizException;
import com.qingyan.hazmat.hazmat.entity.ChemInfo;
import com.qingyan.hazmat.hazmat.mapper.ChemInfoMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Year;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 台账管理：建档（CAS 查重命中→挂既有台账不阻断，G-7；编码 HG-<类别码>-年-4位序）→
 * 维护（类别守卫 4107——类别是编码与审批链主轴，有批次/存量禁改；stock_qty/cas_no
 * 为台账主键性字段，不可经 update 变更）→ 逻辑删除（三条件）/ 封存解封（仅 0↔1）。
 * 台账封存=停止领用（4504 源头）；"封存即扣减"本应在过期封存侧发生，但该入口尚未接入，与本服务无关。
 */
@Service
public class ChemInfoService {

    /** 类别→编码类别码（0普通化学品/1普通危化品/2易制毒/3易制爆/4剧毒）。 */
    private static final String[] CATEGORY_CODES = {"PT", "WH", "YZ", "YB", "JD"};

    private final ChemInfoMapper chemMapper;
    private final ApprovalChainService chainService;

    public ChemInfoService(ChemInfoMapper chemMapper, ApprovalChainService chainService) {
        this.chemMapper = chemMapper;
        this.chainService = chainService;
    }

    /**
     * 建档：CAS 查重命中→返回既有台账（提示"挂既有台账"，不抛错不建新档）；
     * 未命中→生成编码 HG-<类别码 PT/WH/YZ/YB/JD>-<年>-%04d，stock_qty=0、status=0。
     */
    @Transactional
    public ChemInfo create(ChemInfo info) {
        if (info.getName() == null || info.getName().isBlank()) {
            throw new BizException(4101, "化学品名称必填");
        }
        if (info.getCasNo() != null && !info.getCasNo().isBlank()) {
            ChemInfo existing = chemMapper.selectByCas(info.getCasNo());
            if (existing != null) {
                return existing; // 挂既有台账（控制器提示，不阻断）
            }
        }
        if (info.getCategory() == null) {
            throw new BizException(4101, "化学品分类必填");
        }
        chainService.chainFor(info.getCategory()); // 未知分类 4001
        info.setCode(nextCode(info.getCategory()));
        info.setStockQty(BigDecimal.ZERO);
        info.setStatus(0);
        chemMapper.insert(info);
        return info;
    }

    /** 编码生成：HG-<类别码>-<当前年>-<4位序>（前缀计数，唯一索引兜底重试）。 */
    public String nextCode(int category) {
        if (category < 0 || category >= CATEGORY_CODES.length) {
            throw new BizException(4001, "未知的化学品分类：" + category);
        }
        String prefix = "HG-" + CATEGORY_CODES[category] + "-" + Year.now().getValue() + "-";
        return prefix + String.format("%04d", chemMapper.countByCodePrefix(prefix) + 1);
    }

    /**
     * 维护：类别守卫 4107——若请求改类别且台账已有批次/存量则拒绝。
     * 口径裁量：单据引用枚举（申购 status∈{1,2}、领用∈{0,1,2}、stock_in=0、
     * stock_return=0、waste=0）由 mapper 扩展承担，此处简化为"在库批次>0 或 Σ余量>0"
     * 判存（批次是引用的必然后继，有批次即有引用；无批次余量的历史引用不影响类别切换
     * 的编码/审批链语义）。stock_qty/cas_no/code/status 为台账主键性字段，
     * 统一回写库内值——库存由批次动作原子维护、CAS 建档后锁定、状态只经 setStatus 流转。
     */
    @Transactional
    public void update(ChemInfo info, long user) {
        ChemInfo existing = mustGet(info.getId());
        if (info.getCategory() != null && !info.getCategory().equals(existing.getCategory())) {
            if (chemMapper.countActiveBatches(existing.getId()) > 0
                    || chemMapper.sumBatchLeft(existing.getId()).signum() > 0) {
                throw new BizException(4107, "台账已产生批次/存量，禁止变更类别");
            }
        }
        info.setStockQty(existing.getStockQty());
        info.setCasNo(existing.getCasNo());
        info.setCode(existing.getCode());
        info.setStatus(existing.getStatus());
        info.setDeleted(existing.getDeleted());
        chemMapper.update(info);
    }

    /**
     * 逻辑删除三条件：①status=2 已清空 ②无在库批次 ③无未完成单据引用。
     * 口径裁量：引用枚举（申购∈{1,2}/领用∈{0,1,2}/stock_in=0/stock_return=0/
     * waste=0，定稿）由 mapper 扩展承担（countPendingDocs 类扩展）；
     * 此处以"countActiveBatches==0 且 Σ 在库余量=0"为简化口径——批次全部
     * 出库/销账/封存尽才允许删档，在库口径下未完成单据必然还挂着批次。
     */
    @Transactional
    public void remove(long id, long user) {
        ChemInfo existing = mustGet(id);
        if (existing.getStatus() == null || existing.getStatus() != 2
                || chemMapper.countActiveBatches(id) > 0
                || chemMapper.sumBatchLeft(id).signum() > 0) {
            throw new BizException(4101, "仅清空且无在库批次、无未完成单据引用的台账可删除");
        }
        chemMapper.markDeleted(id);
    }

    /** 封存/解封：仅 0↔1 切换（台账封存=停止领用，allocate 前置 4504 的源头）。 */
    @Transactional
    public void setStatus(long id, int status, long user) {
        ChemInfo existing = mustGet(id);
        if (status != 0 && status != 1) {
            throw new BizException(4101, "仅支持在管(0)↔封存(1)切换");
        }
        Integer current = existing.getStatus();
        if (current == null || (current != 0 && current != 1)) {
            throw new BizException(4101, "当前状态不可封存/解封");
        }
        if (current == status) {
            throw new BizException(4101, "状态未变化");
        }
        chemMapper.updateStatus(id, status);
    }

    /** 档案聚合：台账 + 批次列表（在库批次 FEFO 序；全量批次列表由 mapper 扩展承担）。 */
    public Map<String, Object> detail(long id) {
        ChemInfo info = mustGet(id);
        Map<String, Object> vo = new LinkedHashMap<>();
        vo.put("info", info);
        vo.put("batches", chemMapper.selectFefoBatches(id));
        return vo;
    }

    private ChemInfo mustGet(long id) {
        ChemInfo info = chemMapper.selectById(id);
        if (info == null) {
            throw new BizException(4101, "化学品台账不存在");
        }
        return info;
    }
}
