-- hazmat-lims / hazmat-management 数据库 schema（H2 MODE=MySQL 兼容，MySQL profile 同源）
-- 22 表：化学品域 14 + 系统域 8。幂等建表（IF NOT EXISTS），演示数据由应用层注入。
-- 注意：CREATE INDEX IF NOT EXISTS 是 H2 语法；MySQL 8 导入前需按 README 的转换脚本去掉 IF NOT EXISTS。
-- 规格：详细设计 v8 §2（冻结版）。守卫/约束在服务层（守卫失败码见错误码表），DB 层仅做
-- 结构性约束（唯一键防并发重复审批等）。

-- ══════════ 化学品域（14 表）══════════

-- 台账主表（category 单值取最高风险类别；stock_qty=Σ批次余量，事务内原子维护）
CREATE TABLE IF NOT EXISTS chem_info (
  id               BIGINT AUTO_INCREMENT PRIMARY KEY,
  code             VARCHAR(32)  NOT NULL,          -- HG-<类别码>-年-4位序
  name             VARCHAR(100) NOT NULL,
  alias            VARCHAR(100) DEFAULT '',
  cas_no           VARCHAR(16)  DEFAULT '',        -- CAS 登记号（查重键之一）
  category         TINYINT      NOT NULL,          -- 0普通化学品/1普通危化品/2易制毒/3易制爆/4剧毒
  compat_group     VARCHAR(8)   DEFAULT 'GEN',     -- 主相容组 OX/RD/AC/AK/ORG/FLAM/GEN
  compat_group2    VARCHAR(8)   DEFAULT NULL,      -- 次相容组（多属性化学品，可空）
  spec             VARCHAR(50)  DEFAULT '',
  unit             VARCHAR(16)  DEFAULT '',
  stock_qty        DECIMAL(12,2) DEFAULT 0,        -- 现存总量=Σ批次余量（守恒）
  min_stock        DECIMAL(12,2) DEFAULT 0,        -- 安全库存下限（看板预警）
  max_once         DECIMAL(12,2) DEFAULT NULL,     -- 剧毒单次上限（仅 category=4 非空）
  cabinet_id       BIGINT       DEFAULT NULL,      -- 默认柜位（展示；禁配以批次实际柜位为准）
  expiry_warn_days INT          DEFAULT 30,
  ghz_pictogram    VARCHAR(50)  DEFAULT '',        -- GHS 象形图代码
  first_aid        VARCHAR(200) DEFAULT '',        -- 应急处置要点
  status           TINYINT      DEFAULT 0,         -- 0在管/1封存/2清空
  deleted          TINYINT      DEFAULT 0,         -- 逻辑删除（三条件守卫在服务层）
  create_time      DATETIME     DEFAULT CURRENT_TIMESTAMP,
  update_time      DATETIME     DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_chem_code ON chem_info(code);
CREATE INDEX IF NOT EXISTS idx_chem_cas ON chem_info(cas_no);
CREATE INDEX IF NOT EXISTS idx_chem_cat ON chem_info(category);

-- 批次表（FEFO 载体；Σqty_left(status=0)=台账现存；封存即扣减台账）
CREATE TABLE IF NOT EXISTS chem_batch (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  chemical_id  BIGINT       NOT NULL,
  stock_in_id  BIGINT       DEFAULT NULL,          -- 溯源：来源入库单
  lot_no       VARCHAR(32)  NOT NULL,
  qty_in       DECIMAL(12,2) NOT NULL,
  qty_left     DECIMAL(12,2) NOT NULL DEFAULT 0,   -- ≥0（条件更新守卫）
  expiry_date  DATE         DEFAULT NULL,
  cabinet_id   BIGINT       DEFAULT NULL,          -- 实际柜位（禁配/移柜对象）
  status       TINYINT      DEFAULT 0,             -- 0在库/1耗尽/2废弃/3过期封存
  sealed_time  DATETIME     DEFAULT NULL,
  in_time      DATETIME     DEFAULT CURRENT_TIMESTAMP,
  update_time  DATETIME     DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_batch_lot ON chem_batch(chemical_id, lot_no);
CREATE INDEX IF NOT EXISTS idx_batch_chem ON chem_batch(chemical_id, status);
CREATE INDEX IF NOT EXISTS idx_batch_cabinet ON chem_batch(cabinet_id, status);

-- 申购单（分级审批主单；in_qty 累计已入——条件更新对账）
CREATE TABLE IF NOT EXISTS purchase_req (
  id             BIGINT AUTO_INCREMENT PRIMARY KEY,
  code           VARCHAR(32)  NOT NULL,            -- AP-年月日-3位序
  applicant_id   BIGINT       NOT NULL,
  advisor_id     BIGINT       NOT NULL,            -- 提交时指定导师（导师节点指定制）
  chemical_name  VARCHAR(100) NOT NULL,            -- 申购为新品快照，入库时才建台账
  cas_no         VARCHAR(16)  DEFAULT '',
  category       TINYINT      NOT NULL,
  spec           VARCHAR(50)  DEFAULT '',
  unit           VARCHAR(16)  DEFAULT '',
  qty            DECIMAL(12,2) NOT NULL,
  purpose        VARCHAR(200) DEFAULT '',
  experiment     VARCHAR(100) DEFAULT '',
  status         TINYINT      DEFAULT 0,           -- 0草稿/1审批中/2已批/3驳回/4已入库关闭/5已作废
  current_seq    TINYINT      DEFAULT 1,           -- 当前待审批级
  in_qty         DECIMAL(12,2) DEFAULT 0,          -- 累计已入（≤qty 守卫 4402）
  create_time    DATETIME     DEFAULT CURRENT_TIMESTAMP,
  audit_time     DATETIME     DEFAULT NULL
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_pr_code ON purchase_req(code);
CREATE INDEX IF NOT EXISTS idx_pr_applicant ON purchase_req(applicant_id, status);
CREATE INDEX IF NOT EXISTS idx_pr_advisor ON purchase_req(advisor_id, status);

-- 通用审批记录（biz_type 1申购/2废弃；DB 层唯一键防并发重复审批）
CREATE TABLE IF NOT EXISTS approval_record (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  biz_type    TINYINT      NOT NULL,
  biz_id      BIGINT       NOT NULL,
  seq         TINYINT      NOT NULL,
  node_key    VARCHAR(20)  DEFAULT '',
  node_name   VARCHAR(20)  DEFAULT '',
  approver_id BIGINT       NOT NULL,
  comment     VARCHAR(200) DEFAULT '',
  result      TINYINT      NOT NULL,              -- 1通过/0驳回
  create_time DATETIME     DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_appr_biz ON approval_record(biz_type, biz_id, seq);

-- 入库单（剧毒两步签；拒收终态不建批次）
CREATE TABLE IF NOT EXISTS stock_in (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  code          VARCHAR(32)  NOT NULL,             -- RK-年月日-3位序
  req_id        BIGINT       DEFAULT NULL,         -- 关联申购单
  chemical_id   BIGINT       DEFAULT NULL,         -- 验收确认后指向台账（新品入库时创建）
  lot_no        VARCHAR(32)  DEFAULT '',
  qty           DECIMAL(12,2) NOT NULL,
  expiry_date   DATE         DEFAULT NULL,
  cabinet_id    BIGINT       DEFAULT NULL,
  supplier      VARCHAR(100) DEFAULT '',
  permit_no     VARCHAR(50)  DEFAULT '',           -- 易制毒/易制爆公安备案号
  receiver_id   BIGINT       DEFAULT NULL,
  checker_id    BIGINT       DEFAULT NULL,
  reject_reason VARCHAR(200) DEFAULT '',
  status        TINYINT      DEFAULT 0,            -- 0待验收/1已入库/2已拒收
  in_time       DATETIME     DEFAULT NULL,
  create_time   DATETIME     DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_si_code ON stock_in(code);

-- 领用单（双签出库；FEFO 分配经 requisition_batch）
CREATE TABLE IF NOT EXISTS requisition (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  code          VARCHAR(32)  NOT NULL,             -- LY-年月日-3位序
  chemical_id   BIGINT       NOT NULL,
  qty           DECIMAL(12,2) NOT NULL,
  purpose       VARCHAR(200) DEFAULT '',
  experiment    VARCHAR(100) DEFAULT '',
  applicant_id  BIGINT       NOT NULL,
  req_id        BIGINT       DEFAULT NULL,         -- 关联已批申购（免导师签）
  advisor_id    BIGINT       DEFAULT NULL,         -- 导师签（非关联且 category≥1 必签）
  advisor_time  DATETIME     DEFAULT NULL,
  checker_id    BIGINT       DEFAULT NULL,         -- 复核人（≠申请人，持 safety+ 角色）
  return_qty    DECIMAL(12,2) DEFAULT 0,           -- 累计已还（条件更新对账 4602）
  expect_return DATE         DEFAULT NULL,         -- 归还时限（超期未还预警）
  status        TINYINT      DEFAULT 0,            -- 0待导师/1待复核/2已出库/3驳回/4归还完毕关闭
  create_time   DATETIME     DEFAULT CURRENT_TIMESTAMP,
  out_time      DATETIME     DEFAULT NULL
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_req_code ON requisition(code);
CREATE INDEX IF NOT EXISTS idx_req_chem ON requisition(chemical_id, status);

-- 领用批次明细（FEFO 分配结果；改派=更新行，幂等条件见服务层）
CREATE TABLE IF NOT EXISTS requisition_batch (
  id       BIGINT AUTO_INCREMENT PRIMARY KEY,
  req_id   BIGINT NOT NULL,
  batch_id BIGINT NOT NULL,
  qty      DECIMAL(12,2) NOT NULL,
  seq_no   TINYINT DEFAULT 1
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_rb ON requisition_batch(req_id, batch_id);

-- 余量归还单（回原批次；确认人限安全员/学院）
CREATE TABLE IF NOT EXISTS stock_return (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  code         VARCHAR(32)  NOT NULL,              -- GH-年月日-3位序
  req_id       BIGINT       NOT NULL,
  batch_id     BIGINT       NOT NULL,              -- 回原批次（status=0 守卫 4204）
  qty          DECIMAL(12,2) NOT NULL,
  applicant_id BIGINT       NOT NULL,
  comment      VARCHAR(200) DEFAULT '',            -- 拒绝原因（return:reject）
  confirmer_id BIGINT       DEFAULT NULL,
  status       TINYINT      DEFAULT 0,             -- 0待确认/1已回库/2已拒绝
  create_time  DATETIME     DEFAULT CURRENT_TIMESTAMP,
  return_time  DATETIME     DEFAULT NULL,
  confirm_time DATETIME     DEFAULT NULL
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_sr_code ON stock_return(code);

-- 废弃物处置单（batch_id 一律强制；混合废液按化学品拆单）
CREATE TABLE IF NOT EXISTS waste_disposal (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  code            VARCHAR(32)  NOT NULL,           -- FQ-年月日-3位序
  chemical_id     BIGINT       NOT NULL,
  batch_id        BIGINT       NOT NULL,           -- type=3 指过期封存批次；type=1/2 指在库批次
  type            TINYINT      NOT NULL,           -- 1废液/2空瓶/3过期整批
  qty             DECIMAL(12,2) NOT NULL,
  method          VARCHAR(100) DEFAULT '',
  disposal_target VARCHAR(100) DEFAULT '',         -- 处置去向（完成时必填）
  voucher_no      VARCHAR(50)  DEFAULT '',         -- 转移凭证号（剧毒/易制毒/易制爆完成时必填）
  operator_id     BIGINT       NOT NULL,
  approver_id     BIGINT       DEFAULT NULL,
  status          TINYINT      DEFAULT 0,          -- 0待审批/1已处置/2已驳回
  create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
  finish_time     DATETIME     DEFAULT NULL
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_wd_code ON waste_disposal(code);

-- 存放柜（剧毒专柜 dual_lock=1；柜内有剧毒批次禁改类型）
CREATE TABLE IF NOT EXISTS storage_cabinet (
  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  code       VARCHAR(16)  NOT NULL,
  name       VARCHAR(50)  DEFAULT '',
  type       TINYINT      DEFAULT 6,                -- 1酸/2碱/3有机/4氧化剂/5剧毒专柜/6通用
  dual_lock  TINYINT      DEFAULT 0,
  location   VARCHAR(50)  DEFAULT ''
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_cab_code ON storage_cabinet(code);

-- 禁配规则（成对存储，UNIQUE 防重、禁自反；应用层成对写删）
CREATE TABLE IF NOT EXISTS compat_rule (
  id      BIGINT AUTO_INCREMENT PRIMARY KEY,
  group_a VARCHAR(8) NOT NULL,
  group_b VARCHAR(8) NOT NULL,
  note    VARCHAR(100) DEFAULT ''
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_compat ON compat_rule(group_a, group_b);

-- 账物核对单（双人盘点；finish 仅记录，adjust 增量修正）
CREATE TABLE IF NOT EXISTS chem_check (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  code            VARCHAR(32)  NOT NULL,           -- PD-年月日-3位序
  scope_category  TINYINT      DEFAULT NULL,       -- NULL=全部
  checker_one_id  BIGINT       NOT NULL,
  checker_two_id  BIGINT       NOT NULL,
  diff_count      INT          DEFAULT 0,
  result          TINYINT      DEFAULT 0,          -- 0盘点中/1平/2不平
  remark          VARCHAR(200) DEFAULT '',
  checked_time    DATETIME     DEFAULT NULL,
  create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_chk_code ON chem_check(code);

-- 盘点差异明细（adjust 按 diff 增量修正，批次 status=0 守卫）
CREATE TABLE IF NOT EXISTS chem_check_diff (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  check_id    BIGINT        NOT NULL,
  batch_id    BIGINT        NOT NULL,
  book_qty    DECIMAL(12,2) NOT NULL,
  actual_qty  DECIMAL(12,2) NOT NULL,
  diff_qty    DECIMAL(12,2) NOT NULL,
  remark      VARCHAR(100)  DEFAULT '',
  adjust_time DATETIME      DEFAULT NULL
);
CREATE INDEX IF NOT EXISTS idx_ccd_check ON chem_check_diff(check_id);

-- ══════════ 系统域（8 表，流水线公共底座）══════════

CREATE TABLE IF NOT EXISTS sys_user (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  username    VARCHAR(50)  NOT NULL,
  password    VARCHAR(200) NOT NULL,                -- PBKDF2 散列
  nickname    VARCHAR(50)  DEFAULT '',
  phone       VARCHAR(20)  DEFAULT '',
  status      TINYINT      DEFAULT 0,              -- 0启用/1停用
  create_time DATETIME     DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_user_name ON sys_user(username);

CREATE TABLE IF NOT EXISTS sys_role (
  id        BIGINT AUTO_INCREMENT PRIMARY KEY,
  role_name VARCHAR(50)  NOT NULL,
  role_key  VARCHAR(50)  NOT NULL,
  status    TINYINT      DEFAULT 0,
  remark    VARCHAR(200) DEFAULT ''
);

CREATE TABLE IF NOT EXISTS sys_menu (
  id        BIGINT AUTO_INCREMENT PRIMARY KEY,
  parent_id BIGINT       DEFAULT 0,
  menu_name VARCHAR(50)  NOT NULL,
  path      VARCHAR(100) DEFAULT '',
  perms     VARCHAR(100) DEFAULT '',                 -- 按钮级权限标识（25 键种子）
  menu_type CHAR(1)      DEFAULT 'C',               -- M目录/C菜单/F按钮
  order_num INT          DEFAULT 0
);

CREATE TABLE IF NOT EXISTS sys_user_role (
  user_id BIGINT NOT NULL,
  role_id BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS sys_role_menu (
  role_id BIGINT NOT NULL,
  menu_id BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS sys_oper_log (
  id        BIGINT AUTO_INCREMENT PRIMARY KEY,
  module    VARCHAR(30)  DEFAULT '',              -- 业务模块（AOP 切面提取）
  action    VARCHAR(30)  DEFAULT '',              -- 动作
  operator  VARCHAR(50)  DEFAULT '',              -- 操作人用户名（含被拦截的匿名尝试）
  status    VARCHAR(20)  DEFAULT '',              -- 成功/失败/403
  oper_time DATETIME     DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_log_time ON sys_oper_log(oper_time);

CREATE TABLE IF NOT EXISTS notice (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  title        VARCHAR(100) NOT NULL,
  content      VARCHAR(500) DEFAULT '',
  publisher_id BIGINT       DEFAULT NULL,
  create_by    VARCHAR(50)  DEFAULT NULL,
  status       TINYINT      DEFAULT 0,
  create_time  DATETIME     DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS dict_item (
  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  dict_type  VARCHAR(30) NOT NULL,                  -- unit/compat_group/waste_method/cabinet_type
  item_name  VARCHAR(50) NOT NULL,
  item_value VARCHAR(50) DEFAULT '',
  status     TINYINT      DEFAULT 0,                -- 停用不删改
  order_num  INT          DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_dict_type ON dict_item(dict_type, status);

-- ══════════ AI 层（防御性 AI 层标准模块，defensive-ai-spec §3）══════════

-- AI 问答缓存（question 归一化 sha256 唯一；intent_json 存解析出的查询条件可回放；
-- source=ai|keyword 标记生成通道。种子演示问答由 DemoDataRunner 注入，无 key 首启可演示）
CREATE TABLE IF NOT EXISTS ai_qa_cache (
  id             BIGINT AUTO_INCREMENT PRIMARY KEY,
  question_hash  VARCHAR(64)  NOT NULL,   -- 归一化(去空白/转小写)后 sha256
  question_norm  VARCHAR(255) NOT NULL,
  intent_json    VARCHAR(512) NOT NULL,   -- 解析出的查询条件（可回放）
  answer_summary VARCHAR(1024),           -- 展示话术（法规通道=条文引用串）
  source         VARCHAR(16)  NOT NULL,   -- ai | keyword
  created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_qhash ON ai_qa_cache(question_hash);
