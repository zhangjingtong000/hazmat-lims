package com.qingyan.hazmat.hazmat.service;

import com.qingyan.hazmat.common.BizException;
import com.qingyan.hazmat.hazmat.entity.ChemBatch;
import com.qingyan.hazmat.hazmat.entity.ChemInfo;
import com.qingyan.hazmat.hazmat.entity.StockIn;
import com.qingyan.hazmat.hazmat.mapper.ChemInfoMapper;
import com.qingyan.hazmat.hazmat.mapper.PurchaseReqMapper;
import com.qingyan.hazmat.hazmat.mapper.StockInMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 入库与"五双"双签 + 落账。
 * 普通化学品/category≤3：提交即一步双签（receiver≠checker、至少一人持会签权限），状态直达 1 并落账；
 * category=4 剧毒：提交仅 receiver 首签（status=0），第二签独立 countersign（须持学院审批权限）后才落账。
 * 拒收为终态旁路：仅 status=0 可拒、拒因必填、不建批次不回写申购。
 * 落账（一步 submit 与剧毒 countersign 共用）：禁配校验 → 建批次 → 台账加库存 →
 * 关联申购条件回写（4402）→ stock_in 条件置 1。全部状态推进用条件更新（affected=0 即 4101）。
 */
@Service
public class StockInService {

    /** 会签权限键（收发双方至少一人持有，4202）。 */
    public static final String PERM_COUNTERSIGN = "hzm:stockin:countersign";
    /** 剧毒第二签角色键：复用学院审批权限。 */
    public static final String PERM_COLLEGE_AUDIT = "hzm:purchase:audit-college";

    /** 台账编码类别码（category 0-4 → PT/WH/YZ/YB/JD）。 */
    private static final String[] CATEGORY_CODES = {"PT", "WH", "YZ", "YB", "JD"};

    private final StockInMapper stockInMapper;
    private final ChemInfoMapper chemMapper;
    private final PurchaseReqMapper reqMapper;
    private final CompatService compatService;

    public StockInService(StockInMapper stockInMapper, ChemInfoMapper chemMapper,
                          PurchaseReqMapper reqMapper, CompatService compatService) {
        this.stockInMapper = stockInMapper;
        this.chemMapper = chemMapper;
        this.reqMapper = reqMapper;
        this.compatService = compatService;
    }

    /** 入库单编码 RK-年月日-NNN（当日序，仿申购编码生成）。 */
    public String nextCode() {
        String day = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String prefix = "RK-" + day + "-";
        return prefix + String.format("%03d", stockInMapper.countToday(prefix) + 1);
    }

    /** 台账编码 HG-<类别码>-<年>-NNNN（新建台账时生成）。 */
    public String nextChemCode(int category) {
        if (category < 0 || category >= CATEGORY_CODES.length) {
            throw new BizException(4001, "未知的化学品分类：" + category);
        }
        String prefix = "HG-" + CATEGORY_CODES[category] + "-" + LocalDate.now().getYear() + "-";
        return prefix + String.format("%04d", chemMapper.countByCodePrefix(prefix) + 1);
    }

    /** 指定既有台账入库（一步模式；chemicalId 必填）。 */
    @Transactional
    public StockIn submit(StockIn form, long receiverId) {
        return submit(form, null, receiverId);
    }

    /**
     * 提交入库（一步双签 / 剧毒首签）。
     * draft 仅在 form.chemicalId 为空且 CAS 未命中时用于新建台账（品名/CAS/类别/相容组）。
     */
    @Transactional
    public StockIn submit(StockIn form, ChemInfo draft, long receiverId) {
        if (form.getQty() == null || form.getQty().signum() <= 0) {
            throw new BizException(4101, "入库数量必须为正");
        }
        if (form.getLotNo() == null || form.getLotNo().isBlank()) {
            throw new BizException(4101, "批号必填");
        }
        if (form.getCabinetId() == null) {
            throw new BizException(4101, "必须指定存放柜位");
        }
        Long checkerId = form.getCheckerId();
        if (checkerId == null) {
            throw new BizException(4101, "复核人必填（一步双签登记）");
        }
        if (checkerId == receiverId) {
            throw new BizException(4201, "双签不得同人");
        }
        if (reqMapper.countPerm(receiverId, PERM_COUNTERSIGN) == 0
                && reqMapper.countPerm(checkerId, PERM_COUNTERSIGN) == 0) {
            throw new BizException(4202, "收发双方均不持会签权限（" + PERM_COUNTERSIGN + "）");
        }
        ChemInfo chem = resolveChemical(form, draft);

        form.setChemicalId(chem.getId());
        form.setReceiverId(receiverId);
        form.setStatus(0);
        if (chem.getCategory() == 4) {
            form.setCheckerId(null); // 剧毒两步签：第二签留待 countersign 登记
        }
        insertWithCode(form);
        if (chem.getCategory() != 4) {
            settle(form, checkerId); // 一步模式：提交即落账
        }
        return form;
    }

    /** 编码生成：RK-年月日-3位序（uk_si_code 兜底重试）。 */
    private void insertWithCode(StockIn form) {
        String day = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
        String prefix = "RK-" + day + "-";
        org.springframework.dao.DuplicateKeyException last = null;
        for (int i = 0; i < 3; i++) {
            form.setCode(prefix + String.format("%03d", stockInMapper.countToday(prefix) + 1));
            try {
                stockInMapper.insert(form);
                return;
            } catch (org.springframework.dao.DuplicateKeyException e) {
                last = e; // 并发重号，重取当日序
            }
        }
        throw last;
    }

    /**
     * 剧毒第二签（countersign）：仅 status=0；checker≠receiver；checker 须持学院审批权限。
     * user 为当前操作人（审计语义；第二签资格以 checkerId 为准，按钮权限由控制器注解承担）。
     */
    @Transactional
    public void countersign(long id, long checkerId, long user) {
        StockIn rec = stockInMapper.selectById(id);
        if (rec == null) {
            throw new BizException(4101, "入库单不存在");
        }
        if (rec.getStatus() == null || rec.getStatus() != 0) {
            throw new BizException(4101, "仅待验收入库单可会签");
        }
        if (rec.getReceiverId() != null && rec.getReceiverId() == checkerId) {
            throw new BizException(4201, "双签不得同人");
        }
        if (reqMapper.countPerm(checkerId, PERM_COLLEGE_AUDIT) == 0) {
            throw new BizException(4202, "第二签须持学院审批权限（" + PERM_COLLEGE_AUDIT + "）");
        }
        settle(rec, checkerId);
    }

    /** 拒收：仅 status=0（条件更新 WHERE status=0），拒因必填；不建批次不回写申购。 */
    @Transactional
    public void reject(long id, String reason, long user) {
        if (reason == null || reason.isBlank()) {
            throw new BizException(4101, "拒收原因必填");
        }
        if (stockInMapper.reject(id, reason) == 0) {
            if (stockInMapper.selectById(id) == null) {
                throw new BizException(4101, "入库单不存在");
            }
            throw new BizException(4101, "仅待验收入库单可拒收（状态已变化，请刷新）");
        }
    }

    /** 台账解析：指定 id → 查台账；否则按 CAS 查重——命中挂既有台账，未命中按 draft 新建（stock_qty=0，compat_group 默认 GEN）。 */
    private ChemInfo resolveChemical(StockIn form, ChemInfo draft) {
        ChemInfo chem;
        if (form.getChemicalId() != null) {
            chem = chemMapper.selectById(form.getChemicalId());
            if (chem == null) {
                throw new BizException(4101, "指定台账不存在");
            }
        } else {
            String casNo = draft == null ? null : draft.getCasNo();
            String name = draft == null ? null : draft.getName();
            if (casNo == null || casNo.isBlank() || name == null || name.isBlank()) {
                throw new BizException(4101, "新化学品入库必须提供品名与 CAS 号");
            }
            chem = chemMapper.selectByCas(casNo);
            if (chem == null) {
                Integer category = draft.getCategory();
                if (category == null || category < 0 || category >= CATEGORY_CODES.length) {
                    throw new BizException(4001, "未知的化学品分类：" + category);
                }
                chem = new ChemInfo();
                chem.setName(name);
                chem.setCasNo(casNo);
                chem.setCategory(category);
                chem.setCompatGroup(draft.getCompatGroup() == null || draft.getCompatGroup().isBlank()
                        ? "GEN" : draft.getCompatGroup());
                chem.setCompatGroup2(draft.getCompatGroup2());
                chem.setStockQty(BigDecimal.ZERO);
                chem.setStatus(0);
                chem.setDeleted(0);
            }
        }
        // 预检：建台账/落账前先看目标柜是否已有禁配品（落账时还会再校验一次，防单据在途期间柜况变化）
        compatService.checkCabinet(form.getCabinetId(), chem);
        if (chem.getId() == null) {
            chem.setCode(nextChemCode(chem.getCategory()));
            chemMapper.insert(chem);
        }
        return chem;
    }

    /** 落账（一步 submit 与剧毒 countersign 共用）：建批次 → 台账加库存 → 申购条件回写 → 条件置 1。 */
    private void settle(StockIn rec, long checkerId) {
        ChemInfo chem = chemMapper.selectById(rec.getChemicalId());
        compatService.checkCabinet(rec.getCabinetId(), chem);

        if (rec.getReqId() != null) {
            // 物质一致性守卫（4404 对称）：入库化学品的 CAS 必须与关联申购单一致，
            // 防"申购 A 物、入库 B 物"仍核销申购的绕链路径
            com.qingyan.hazmat.hazmat.entity.PurchaseReq linked = reqMapper.selectById(rec.getReqId());
            if (linked == null) {
                throw new BizException(4404, "关联申购单不存在");
            }
            String reqCas = linked.getCasNo() == null ? "" : linked.getCasNo().trim();
            String chemCas = chem.getCasNo() == null ? "" : chem.getCasNo().trim();
            if (!reqCas.isEmpty() && !reqCas.equalsIgnoreCase(chemCas)) {
                throw new BizException(4405, "入库化学品与关联申购单不一致（CAS 不符：申购 "
                        + reqCas + "，入库 " + chemCas + "）");
            }
            if (reqMapper.incrInQty(rec.getReqId(), rec.getQty()) == 0) {
                throw new BizException(4402, "累计入库量超过申购量");
            }
        }

        ChemBatch batch = new ChemBatch();
        batch.setChemicalId(rec.getChemicalId());
        batch.setStockInId(rec.getId());
        batch.setLotNo(rec.getLotNo());
        batch.setQtyIn(rec.getQty());
        batch.setQtyLeft(rec.getQty());
        batch.setExpiryDate(rec.getExpiryDate());
        batch.setCabinetId(rec.getCabinetId());
        batch.setStatus(0);
        chemMapper.insertBatch(batch);

        chemMapper.changeStock(rec.getChemicalId(), rec.getQty());

        if (stockInMapper.complete(rec.getId(), checkerId) == 0) {
            throw new BizException(4101, "入库单状态已变化，请刷新");
        }
    }
}
