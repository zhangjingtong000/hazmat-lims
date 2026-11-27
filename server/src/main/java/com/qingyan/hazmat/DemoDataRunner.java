package com.qingyan.hazmat;

import com.qingyan.hazmat.common.ai.AiQaCache;
import com.qingyan.hazmat.hazmat.entity.ApprovalRecord;
import com.qingyan.hazmat.hazmat.entity.ChemBatch;
import com.qingyan.hazmat.hazmat.entity.ChemInfo;
import com.qingyan.hazmat.hazmat.entity.CompatRule;
import com.qingyan.hazmat.hazmat.entity.PurchaseReq;
import com.qingyan.hazmat.hazmat.entity.Requisition;
import com.qingyan.hazmat.hazmat.entity.RequisitionBatch;
import com.qingyan.hazmat.hazmat.entity.StorageCabinet;
import com.qingyan.hazmat.hazmat.mapper.ApprovalRecordMapper;
import com.qingyan.hazmat.hazmat.mapper.ChemInfoMapper;
import com.qingyan.hazmat.hazmat.mapper.CompatRuleMapper;
import com.qingyan.hazmat.hazmat.mapper.PurchaseReqMapper;
import com.qingyan.hazmat.hazmat.mapper.RequisitionBatchMapper;
import com.qingyan.hazmat.hazmat.mapper.RequisitionMapper;
import com.qingyan.hazmat.hazmat.mapper.StorageCabinetMapper;
import com.qingyan.hazmat.security.PasswordUtil;
import com.qingyan.hazmat.system.entity.Notice;
import com.qingyan.hazmat.system.entity.SysMenu;
import com.qingyan.hazmat.system.entity.SysRole;
import com.qingyan.hazmat.system.entity.SysUser;
import com.qingyan.hazmat.system.mapper.NoticeMapper;
import com.qingyan.hazmat.system.mapper.SysMenuMapper;
import com.qingyan.hazmat.system.mapper.SysRoleMapper;
import com.qingyan.hazmat.system.mapper.SysUserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 首次启动注入演示数据（幂等：sys_user 已有数据则跳过）。
 * 密码一律 PasswordUtil 散列；种子规格=详细设计 §7（13 种化学品真实 CAS、柜位 6、
 * 禁配 6 对、25 按钮键菜单树、四级审批全链单据、临期/过期批次）。
 */
@Component
public class DemoDataRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataRunner.class);

    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysMenuMapper menuMapper;
    private final NoticeMapper noticeMapper;
    private final ChemInfoMapper chemMapper;
    private final StorageCabinetMapper cabinetMapper;
    private final CompatRuleMapper compatMapper;
    private final PurchaseReqMapper purchaseMapper;
    private final RequisitionMapper requisitionMapper;
    private final RequisitionBatchMapper requisitionBatchMapper;
    private final ApprovalRecordMapper approvalRecordMapper;
    private final com.qingyan.hazmat.system.mapper.DictItemMapper dictMapper;
    private final com.qingyan.hazmat.hazmat.mapper.AiQaCacheMapper aiQaCacheMapper;

    public DemoDataRunner(SysUserMapper userMapper, SysRoleMapper roleMapper, SysMenuMapper menuMapper,
                          NoticeMapper noticeMapper, ChemInfoMapper chemMapper,
                          StorageCabinetMapper cabinetMapper, CompatRuleMapper compatMapper,
                          PurchaseReqMapper purchaseMapper, RequisitionMapper requisitionMapper,
                          RequisitionBatchMapper requisitionBatchMapper,
                          ApprovalRecordMapper approvalRecordMapper,
                          com.qingyan.hazmat.system.mapper.DictItemMapper dictMapper,
                          com.qingyan.hazmat.hazmat.mapper.AiQaCacheMapper aiQaCacheMapper) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.menuMapper = menuMapper;
        this.noticeMapper = noticeMapper;
        this.chemMapper = chemMapper;
        this.cabinetMapper = cabinetMapper;
        this.compatMapper = compatMapper;
        this.purchaseMapper = purchaseMapper;
        this.requisitionMapper = requisitionMapper;
        this.requisitionBatchMapper = requisitionBatchMapper;
        this.approvalRecordMapper = approvalRecordMapper;
        this.dictMapper = dictMapper;
        this.aiQaCacheMapper = aiQaCacheMapper;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (userMapper.selectPage(null, 0, 1).isEmpty()) {
            seed();
            log.info("== 演示数据已注入：admin/admin123（管理员），zhang（导师）、li（安全员）、"
                    + "wang（学院）、zhao（校级）、chen/liu（申请人）密码 123456 ==");
        }
    }

    private void seed() {
        long admin = user("admin", "admin123", "系统管理员");
        long advisor = user("zhang", "123456", "张教授（导师）");
        long safety = user("li", "123456", "李安全员");
        long college = user("wang", "123456", "王院长（学院）");
        long campus = user("zhao", "123456", "赵处长（校级监管）");
        long teacher = user("chen", "123456", "陈老师（申请人）");
        long student = user("liu", "123456", "刘同学（研究生）");

        long rAdmin = role("管理员", "admin", "全部权限");
        long rAdvisor = role("导师", "advisor", "申购一级审批");
        long rSafety = role("安全员", "safety", "二级审批/柜位/盘点");
        long rCollege = role("学院", "college", "三级审批/剧毒入库二签");
        long rCampus = role("校级监管", "campus", "剧毒四级审批/处置监督");
        long rApplicant = role("申请人", "applicant", "申购/领用/归还");
        bind(admin, rAdmin);
        bind(advisor, rAdvisor);
        bind(safety, rSafety);
        bind(college, rCollege);
        bind(campus, rCampus);
        bind(teacher, rApplicant);
        bind(student, rApplicant);

        long mDir = menu(0, "化学品管理", "/chem", "M");
        long mStock = menu(0, "库存与监管", "/stock", "M");
        long mSys = menu(0, "系统管理", "/system", "M");
        long mBiz = menu(0, "信息", "/biz", "M");
        menu(mDir, "化学品台账", "/chem/info", "C");
        menu(mDir, "申购审批", "/chem/purchase", "C");
        menu(mDir, "入库验收", "/chem/stockin", "C");
        menu(mDir, "领用记录", "/chem/requisition", "C");
        menu(mStock, "批次管理", "/chem/batch", "C");
        menu(mStock, "审批中心", "/chem/todo", "C");
        menu(mStock, "废弃处置", "/chem/waste", "C");
        menu(mStock, "账物核对", "/chem/check", "C");
        menu(mStock, "基础数据", "/chem/base", "C");
        menu(mStock, "统计报表", "/chem/stats", "C");
        menu(mSys, "用户管理", "/system/user", "C");
        menu(mSys, "角色管理", "/system/role", "C");
        menu(mSys, "菜单管理", "/system/menu", "C");
        menu(mSys, "操作日志", "/system/operlog", "C");
        menu(mBiz, "通知公告", "/biz/notice", "C");
        String[] perms = {"hzm:info:add", "hzm:info:edit", "hzm:info:remove", "hzm:purchase:submit",
                "hzm:purchase:audit-advisor", "hzm:purchase:audit-safety", "hzm:purchase:audit-college",
                "hzm:purchase:audit-campus", "hzm:stockin:add", "hzm:stockin:countersign",
                "hzm:stockin:reject", "hzm:req:submit", "hzm:req:advisor", "hzm:req:check",
                "hzm:req:dispatch", "hzm:req:return", "hzm:return:confirm", "hzm:return:reject",
                "hzm:waste:submit", "hzm:waste:audit", "hzm:check:do", "hzm:check:adjust",
                "hzm:batch:move", "hzm:cabinet:maint", "hzm:compat:maint", "hzm:ai:ask"};
        for (String p : perms) {
            menu(mDir, p, "", "F", p);
        }
        grantAll(rAdmin);
        bindPerms(rAdvisor, "hzm:purchase:audit-advisor", "hzm:req:advisor");
        bindPerms(rSafety, "hzm:purchase:audit-safety", "hzm:stockin:add", "hzm:stockin:countersign", "hzm:stockin:reject",
                "hzm:waste:submit", "hzm:req:check", "hzm:req:dispatch", "hzm:return:confirm", "hzm:return:reject",
                "hzm:batch:move", "hzm:check:do", "hzm:check:adjust", "hzm:cabinet:maint");
        bindPerms(rCollege, "hzm:purchase:audit-college", "hzm:waste:audit", "hzm:stockin:countersign");
        bindPerms(rCampus, "hzm:purchase:audit-campus", "hzm:waste:audit", "hzm:stockin:countersign");
        bindPerms(rApplicant, "hzm:purchase:submit", "hzm:req:submit", "hzm:req:return");
        // 防御位 hzm:ai:ask 授予全部演示角色（人人可演示，spec §3）
        bindPerms(rAdvisor, "hzm:ai:ask");
        bindPerms(rSafety, "hzm:ai:ask");
        bindPerms(rCollege, "hzm:ai:ask");
        bindPerms(rCampus, "hzm:ai:ask");
        bindPerms(rApplicant, "hzm:ai:ask");

        long cabAcid = cabinet("HG-A01", "酸柜", 1, 0, "化学楼 201");
        cabinet("HG-B01", "碱柜", 2, 0, "化学楼 201");
        long cabOrg = cabinet("HG-O01", "有机溶剂柜", 3, 0, "化学楼 202");
        long cabOx = cabinet("HG-X01", "氧化剂柜", 4, 0, "化学楼 202");
        long cabJd = cabinet("HG-J01", "剧毒专柜", 5, 1, "化学楼 101 双人双锁库");
        cabinet("HG-G01", "通用柜", 6, 0, "化学楼 203");

        compat("OX", "RD", "氧化剂与还原剂禁止同柜");
        compat("OX", "FLAM", "氧化剂与易燃品禁止同柜");
        compat("AC", "AK", "酸与碱禁止同柜");
        compat("AC", "FLAM", "酸与易燃品分柜储存");
        compat("OX", "ORG", "氧化剂与有机试剂分柜");
        compat("AC", "ORG", "酸与有机试剂分柜");

        // ── 化学品 13（真实 CAS；group/次组/柜位/GHS/应急）──
        ChemInfo acetone = chem("HG-ORG-2026-0001", "丙酮", "67-64-1", 1, "ORG", "FLAM", cabOrg, "GHS02,GHS07", "远离火源，皮肤接触后大量清水冲洗");
        ChemInfo h2so4 = chem("HG-AC-2026-0002", "浓硫酸", "7664-93-9", 1, "AC", null, cabAcid, "GHS05,GHS07", "勿与碱直接混合，灼伤后干布拭净再冲洗");
        ChemInfo kmno4 = chem("HG-OX-2026-0003", "高锰酸钾", "7722-64-7", 1, "OX", null, cabOx, "GHS03,GHS07", "避光储存，与还原剂严禁同柜");
        ChemInfo etoh = chem("HG-FLAM-2026-0004", "无水乙醇", "64-17-5", 1, "FLAM", null, cabOrg, "GHS02", "远离火源，通风储存");
        ChemInfo naoh = chem("HG-AK-2026-0005", "氢氧化钠", "1310-73-2", 1, "AK", null, cabAcid, "GHS05", "强腐蚀，勿触黏膜");
        ChemInfo ether = chem("HG-FLAM-2026-0006", "乙醚", "60-29-7", 1, "FLAM", null, cabOrg, "GHS02,GHS07", "极易挥发，禁明火");
        ChemInfo h2o2 = chem("HG-OX-2026-0007", "过氧化氢", "7722-84-1", 1, "OX", null, cabOx, "GHS05,GHS07", "避光冷藏，与有机物隔离");
        ChemInfo hcl = chem("HG-AC-2026-0008", "盐酸", "7647-01-0", 1, "AC", null, cabAcid, "GHS05,GHS07", "挥发刺激性，通风操作");
        ChemInfo iodine = chem("HG-YZ-2026-0009", "碘", "7553-56-0", 2, "GEN", null, cabJd, "GHS07", "易制毒管制，双人领用");
        ChemInfo nh4no3 = chem("HG-YB-2026-0010", "硝酸铵", "6484-52-2", 3, "OX", null, cabOx, "GHS03", "易制爆管制，远离热源");
        ChemInfo kcn = chem("HG-JD-2026-0011", "氰化钾", "151-50-8", 4, "GEN", null, cabJd, "GHS06", "剧毒，双人双锁，遇酸产生剧毒气体");
        ChemInfo nacl = chem("HG-PT-2026-0012", "氯化钠", "7647-14-5", 0, "GEN", null, cabJd, "GHS07", "常规化学品");
        ChemInfo feso4 = chem("HG-RD-2026-0013", "七水硫酸亚铁", "7782-63-0", 1, "RD", null, cabOrg, "GHS07", "还原剂，与氧化剂隔离");

        // ── 批次（含临期批 + 过期封存批，供 FEFO/封存/预警演示）──
        batch(acetone, "LOT-20260301", "6", LocalDate.now().plusYears(1), cabOrg, 0);
        batch(kmno4, "LOT-20260401", "4", LocalDate.now().plusYears(2), cabOx, 0);
        batch(etoh, "LOT-20260201", "10", LocalDate.now().plusMonths(2), cabOrg, 0);
        batch(h2so4, "LOT-20260501", "8", LocalDate.now().plusYears(1), cabAcid, 0);
        batch(naoh, "LOT-20260601", "6", LocalDate.now().plusYears(2), cabAcid, 0);
        batch(ether, "LOT-20260701", "5", LocalDate.now().plusYears(1), cabOrg, 0);
        batch(h2o2, "LOT-20260801", "4", LocalDate.now().plusYears(1), cabOx, 0);
        batch(hcl, "LOT-20260901", "7", LocalDate.now().plusYears(1), cabAcid, 0);
        batch(iodine, "LOT-20261001", "2", LocalDate.now().plusYears(1), cabJd, 0);
        batch(nh4no3, "LOT-20261101", "3", LocalDate.now().plusYears(1), cabOx, 0);
        batch(kcn, "LOT-20261201", "1", LocalDate.now().plusYears(1), cabJd, 0);
        batch(nacl, "LOT-20270101", "20", LocalDate.now().plusYears(3), cabJd, 0);
        batch(feso4, "LOT-20270201", "5", LocalDate.now().plusYears(1), cabOrg, 0);
        ChemBatch expired = batch(h2so4, "LOT-20240101", "2", LocalDate.now().minusMonths(3), cabAcid, 3);
        expired.setSealedTime(LocalDateTime.now().minusDays(1));

        // ── 单据：剧毒四级审批全链（已批）+ 普通危化品审批中 + 已出库领用单 ──
        PurchaseReq p1 = purchase("AP-20260901-001", student, advisor, "氰化钾", "151-50-8", 4,
                "100g/瓶", "瓶", new BigDecimal("1"), "金属痕量分析", "痕量元素分析", 2, 4);
        record(p1.getId(), 1, "导师审批", advisor, 1);
        record(p1.getId(), 2, "安全员审批", safety, 1);
        record(p1.getId(), 3, "学院审批", college, 1);
        record(p1.getId(), 4, "校级审批", campus, 1);
        PurchaseReq p2 = purchase("AP-20260905-002", teacher, advisor, "乙醚", "60-29-7", 1,
                "500mL/瓶", "瓶", new BigDecimal("4"), "萃取实验", "科研课题 A", 1, 2);
        record(p2.getId(), 1, "导师审批", advisor, 1);
        Requisition req1 = requisition("LY-20260906-001", kcn, new BigDecimal("1"), student,
                p1.getId(), advisor, safety);
        RequisitionBatch rb = new RequisitionBatch();
        rb.setReqId(req1.getId());
        rb.setBatchId(firstBatchId(kcn.getId()));
        rb.setQty(new BigDecimal("1"));
        rb.setSeqNo(1);
        requisitionBatchMapper.insert(rb);

        notice("实验室安全检查通知", "下周三开展学期实验室安全大检查，请各实验室完成自查。", admin);
        notice("危化品盘点安排", "本月末进行危化品账物核对，请安全员提前准备。", safety);
        seedDict();
        seedAiQa();
    }

    /** 防御性 AI 层种子问答（spec §3：无 key 首启即可演示缓存回放）。summary 数字与上方种子数据逐一核对。 */
    private void seedAiQa() {
        // ── 数据通道（7 条，数字与 13 种化学品/6 柜位/81 瓶总现存等种子事实一致）──
        qa("现在台账里一共有多少种化学品？",
            intent("stats", "", null, null, null),
            "系统当前在管化学品台账共 13 种，累计现存 81 瓶：普通化学品 1 种 20 瓶、危险化学品 9 种 55 瓶、"
                + "易制毒 1 种 2 瓶、易制爆 1 种 3 瓶、剧毒品 1 种 1 瓶。");
        qa("氰化钾还有多少库存？",
            intent("list", "氰化钾", null, null, null),
            "氰化钾（HG-JD-2026-0011，剧毒品）现存 1 瓶，存放于 HG-J01 剧毒专柜，实行双人双锁管理，"
                + "领用须走四级审批与双签出库。");
        qa("查一下丙酮的库存",
            intent("list", "丙酮", null, null, null),
            "丙酮（HG-ORG-2026-0001）现存 6 瓶，存放于有机溶剂柜 HG-O01（化学楼 202）。");
        qa("无水乙醇还剩多少",
            intent("list", "乙醇", null, null, null),
            "无水乙醇（HG-FLAM-2026-0004）现存 10 瓶，存放于有机溶剂柜 HG-O01，出库按 FEFO 先到期先出。");
        qa("易制爆化学品有哪些",
            intent("list", "", 3, null, null),
            "易制爆危化品台账 1 种：硝酸铵（HG-YB-2026-0010），现存 3 瓶，存放于氧化剂柜 HG-X01，"
                + "申购需三级审批（导师→安全员→学院）。");
        qa("有多少过期批次？",
            intent("stats", "", null, null, null),
            "当前过期封存批次 1 个：浓硫酸 LOT-20240101 共 2 瓶（过期即封存并扣减台账现存），"
                + "可通过废弃处置流程（过期整批）核销。");
        qa("氧化剂柜里存了哪些化学品？",
            intent("stats", "氧化剂柜", null, null, null),
            "HG-X01 氧化剂柜现存 3 种化学品：高锰酸钾（4 瓶）、过氧化氢（4 瓶）、硝酸铵（3 瓶），合计 11 瓶。");
        // ── 法规通道（2 条，仅引用语料中真实采集到的条文号）──
        qa("剧毒品怎么存放？双人双锁有什么规定？",
            intent("regs", "", null, null, List.of("GB15603 5.10", "条例 第二十四条")),
            "依据 GB 15603-2022 第5.10条与《危险化学品安全管理条例》第二十四条：剧毒化学品应当在专用仓库"
                + "（本系统为 HG-J01 剧毒专柜）内单独存放，并实行双人收发、双人保管制度；储存数量构成重大"
                + "危险源的其他危险化学品同样要求单独存放与双岗管理。");
        qa("危险化学品储存配存有什么要求？",
            intent("regs", "", null, null, List.of("GB15603 5.5", "GB15603 3.3", "GB15603 3.4", "GB15603 3.5")),
            "依据 GB 15603-2022 第5.5条：危险化学品的储存配存应符合附录A（危险化学品储存配存表）及其化学品"
                + "安全技术说明书的要求。储存方式分隔离储存（第3.3条）、隔开储存（第3.4条）、分离储存（第3.5条）"
                + "三种，本系统入库与移柜前按相容组成对校验，禁配化学品不得同柜混存。");
    }

    /** intent_json 结构与 AiAskService 写缓存口径一致（regs 额外带 nos 条文号列表）。 */
    private String intent(String intent, String keyword, Integer category, Integer status, List<String> nos) {
        StringBuilder sb = new StringBuilder("{\"intent\":\"").append(intent).append('"');
        sb.append(",\"keyword\":\"").append(keyword == null ? "" : keyword).append('"');
        sb.append(",\"category\":").append(category == null ? "null" : category);
        sb.append(",\"status\":").append(status == null ? "null" : status);
        if (nos != null) {
            sb.append(",\"nos\":[");
            for (int i = 0; i < nos.size(); i++) {
                if (i > 0) {
                    sb.append(',');
                }
                sb.append('"').append(nos.get(i)).append('"');
            }
            sb.append(']');
        }
        return sb.append('}').toString();
    }

    private void qa(String question, String intentJson, String summary) {
        String norm = com.qingyan.hazmat.common.ai.AiAskService.normalize(question);
        AiQaCache c = new AiQaCache();
        c.setQuestionHash(com.qingyan.hazmat.common.ai.AiAskService.sha256(norm));
        c.setQuestionNorm(norm);
        c.setIntentJson(intentJson);
        c.setAnswerSummary(summary);
        c.setSource("ai");
        aiQaCacheMapper.insert(c);
    }

    // ── 便捷方法 ──
    private long user(String username, String pwd, String nickname) {
        SysUser u = new SysUser();
        u.setUsername(username);
        u.setPassword(PasswordUtil.encode(pwd));
        u.setNickname(nickname);
        u.setStatus("0");
        userMapper.insert(u);
        return u.getId();
    }

    private long role(String name, String key, String remark) {
        SysRole r = new SysRole();
        r.setRoleName(name);
        r.setRoleKey(key);
        r.setStatus("0");
        r.setRemark(remark);
        roleMapper.insert(r);
        return r.getId();
    }

    private void bind(long userId, long roleId) {
        roleMapper.insertUserRole(userId, roleId);
    }

    private long menu(long parent, String name, String path, String type) {
        return menu(parent, name, path, type, null);
    }

    private long menu(long parent, String name, String path, String type, String perms) {
        SysMenu m = new SysMenu();
        m.setParentId(parent);
        m.setMenuName(name);
        m.setPath(path == null ? "" : path);
        m.setPerms(perms == null ? "" : perms);
        m.setMenuType(type);
        menuMapper.insert(m);
        return m.getId();
    }

    private void grantAll(long roleId) {
        for (SysMenu m : menuMapper.selectAll()) {
            roleMapper.insertRoleMenu(roleId, m.getId());
        }
    }

    private void bindPerms(long roleId, String... keys) {
        for (SysMenu m : menuMapper.selectAll()) {
            for (String k : keys) {
                if (k.equals(m.getPerms())) {
                    roleMapper.insertRoleMenu(roleId, m.getId());
                }
            }
        }
    }

    private long cabinet(String code, String name, int type, int dualLock, String location) {
        StorageCabinet c = new StorageCabinet();
        c.setCode(code);
        c.setName(name);
        c.setType(type);
        c.setDualLock(dualLock);
        c.setLocation(location);
        cabinetMapper.insert(c);
        return c.getId();
    }

    private void compat(String a, String b, String note) {
        CompatRule r = new CompatRule();
        r.setGroupA(a);
        r.setGroupB(b);
        r.setNote(note);
        compatMapper.insertPair(r);
    }

    private ChemInfo chem(String code, String name, String cas, int category, String group,
                          String group2, Long cabinetId, String ghz, String firstAid) {
        ChemInfo c = new ChemInfo();
        c.setCode(code);
        c.setName(name);
        c.setCasNo(cas);
        c.setCategory(category);
        c.setCompatGroup(group);
        c.setCompatGroup2(group2);
        c.setSpec("标准规格");
        c.setUnit("瓶");
        c.setStockQty(BigDecimal.ZERO);
        c.setMinStock(BigDecimal.ONE);
        c.setCabinetId(cabinetId);
        c.setGhzPictogram(ghz);
        c.setFirstAid(firstAid);
        c.setStatus(0);
        chemMapper.insert(c);
        return c;
    }

    private ChemBatch batch(ChemInfo chem, String lot, String qty, LocalDate expiry,
                            Long cabinetId, int status) {
        ChemBatch b = new ChemBatch();
        b.setChemicalId(chem.getId());
        b.setLotNo(lot);
        b.setQtyIn(new BigDecimal(qty));
        b.setQtyLeft(new BigDecimal(qty));
        b.setExpiryDate(expiry);
        b.setCabinetId(cabinetId);
        b.setStatus(status);
        if (status == 0) {
            chemMapper.changeStock(chem.getId(), new BigDecimal(qty)); // 在库批次入账（守恒）
        }
        chemMapper.insertBatch(b);
        return b;
    }

    private Long firstBatchId(Long chemId) {
        List<ChemBatch> bs = chemMapper.selectFefoBatches(chemId);
        return bs.isEmpty() ? null : bs.get(0).getId();
    }

    private PurchaseReq purchase(String code, long applicant, long advisor, String name, String cas,
                                 int category, String spec, String unit, BigDecimal qty,
                                 String purpose, String experiment, int status, int currentSeq) {
        PurchaseReq r = new PurchaseReq();
        r.setCode(code);
        r.setApplicantId(applicant);
        r.setAdvisorId(advisor);
        r.setChemicalName(name);
        r.setCasNo(cas);
        r.setCategory(category);
        r.setSpec(spec);
        r.setUnit(unit);
        r.setQty(qty);
        r.setPurpose(purpose);
        r.setExperiment(experiment);
        r.setStatus(status);
        r.setCurrentSeq(currentSeq);
        r.setInQty(BigDecimal.ZERO);
        purchaseMapper.insert(r);
        return r;
    }

    private void record(long bizId, int seq, String nodeName, long approver, int result) {
        ApprovalRecord rec = new ApprovalRecord();
        rec.setBizType(1);
        rec.setBizId(bizId);
        rec.setSeq(seq);
        rec.setNodeName(nodeName);
        rec.setApproverId(approver);
        rec.setResult(result);
        approvalRecordMapper.insert(rec);
    }

    private Requisition requisition(String code, ChemInfo chem, BigDecimal qty, long applicant,
                                    Long reqId, Long advisorId, Long checkerId) {
        Requisition r = new Requisition();
        r.setCode(code);
        r.setChemicalId(chem.getId());
        r.setQty(qty);
        r.setApplicantId(applicant);
        r.setReqId(reqId);
        r.setAdvisorId(advisorId);
        r.setAdvisorTime(LocalDateTime.now());
        r.setCheckerId(checkerId);
        r.setStatus(2);
        r.setOutTime(LocalDateTime.now());
        requisitionMapper.insert(r);
        return r;
    }

    /** 字典种子：与 schema 注释口径一致（unit/compat_group/waste_method/cabinet_type 等）。 */
    private void seedDict() {
        dict("unit", "瓶", "bottle", 1);
        dict("unit", "袋", "bag", 2);
        dict("unit", "桶", "drum", 3);
        dict("unit", "盒", "box", 4);
        dict("compat_group", "有机溶剂", "ORG", 1);
        dict("compat_group", "易燃液体", "FLAM", 2);
        dict("compat_group", "酸类", "AC", 3);
        dict("compat_group", "碱类", "AK", 4);
        dict("compat_group", "氧化剂", "OX", 5);
        dict("compat_group", "还原剂", "RD", 6);
        dict("compat_group", "通用", "GEN", 9);
        dict("waste_method", "委托有资质单位处置", "entrust", 1);
        dict("waste_method", "中和处理后排放", "neutralize", 2);
        dict("waste_method", "暂存待处置", "store", 3);
        dict("cabinet_type", "普通试剂柜", "normal", 1);
        dict("cabinet_type", "防爆柜", "explosion", 2);
        dict("cabinet_type", "剧毒品保险柜", "toxic", 3);
        dict("cabinet_type", "强酸强碱柜", "corrosive", 4);
        dict("disposal_target", "学校危废暂存间", "campus-store", 1);
        dict("disposal_target", "危废转运联单", "transfer", 2);
    }

    private void dict(String type, String name, String value, int order) {
        com.qingyan.hazmat.system.entity.DictItem d = new com.qingyan.hazmat.system.entity.DictItem();
        d.setDictType(type);
        d.setItemName(name);
        d.setItemValue(value);
        d.setStatus(0);
        d.setOrderNum(order);
        dictMapper.insert(d);
    }

    private void notice(String title, String content, long publisher) {
        Notice n = new Notice();
        n.setTitle(title);
        n.setContent(content);
        n.setCreateBy(String.valueOf(publisher));
        noticeMapper.insert(n);
    }
}
