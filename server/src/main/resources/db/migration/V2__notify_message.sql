-- ═══════════════════════════════════════════════════════════════════════════
-- V2 站内信（notify_message）：定时任务/事件产生的主动通知落地表（G5/G6）。
-- 与既有 notice（公告板，全员可见）区分：本表按 target_user_id 定向触达个人，
-- read_flag 标记已读。CREATE TABLE IF NOT EXISTS 保证幂等（H2 / MySQL 同源）。
-- ═══════════════════════════════════════════════════════════════════════════
CREATE TABLE IF NOT EXISTS notify_message (
  id             BIGINT AUTO_INCREMENT PRIMARY KEY,
  target_user_id BIGINT       DEFAULT NULL,           -- NULL = 全员广播
  category       VARCHAR(20)  NOT NULL,               -- expiry/frozen/stockin/system
  title          VARCHAR(120) NOT NULL,
  content        VARCHAR(500) DEFAULT '',
  biz_type       VARCHAR(20)  DEFAULT NULL,           -- 关联业务类型（chem_batch 等）
  biz_id         BIGINT       DEFAULT NULL,           -- 关联业务主键（可点击跳转）
  read_flag      TINYINT      DEFAULT 0,              -- 0 未读 / 1 已读
  create_time    DATETIME     DEFAULT CURRENT_TIMESTAMP
);
