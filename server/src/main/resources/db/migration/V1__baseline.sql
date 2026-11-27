-- ═══════════════════════════════════════════════════════════════════════════
-- V1 基线（baseline）：与 db/schema.sql 同源。
-- 首启由 spring.sql.init 执行 schema.sql 建齐 21 张业务表（全部 CREATE TABLE IF NOT EXISTS），
-- 本脚本作为**版本链起点**：不含业务 DDL，只确认迁移基础设施表存在。
--
-- ⚠ 纪律：版本登记（version/script/checksum/applied_at）由 SchemaMigrationRunner 在执行成功后
-- 统一写入，**迁移脚本自身不得写 schema_version**（否则会与运行器重复登记 → 主键冲突）。
-- 幂等：全部语句 IF NOT EXISTS，重复执行结果相同。
-- ═══════════════════════════════════════════════════════════════════════════
CREATE TABLE IF NOT EXISTS schema_version (
  version    VARCHAR(20)  NOT NULL PRIMARY KEY,
  script     VARCHAR(120) NOT NULL,
  checksum   VARCHAR(64)  DEFAULT NULL,
  applied_at DATETIME     DEFAULT CURRENT_TIMESTAMP
);
