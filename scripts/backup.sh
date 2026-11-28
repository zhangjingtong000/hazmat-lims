#!/usr/bin/env bash
# ═══════════════════════════════════════════════════════════════════════════
# hazmat-lims 备份脚本：scripts/backup.sh
#
#   用法：bash scripts/backup.sh [--profile mysql] [--out DIR] [--keep N]
#
#   ① H2（默认 profile，内嵌文件库）：
#      **必须冷备**（先 stop.sh）：H2 单进程独占数据库文件，Windows 下运行中复制会报
#      "Device or resource busy" 并产出损坏包（Linux 上也只得到崩溃一致快照）。
#      脚本会自动探测 8080 是否在跑：在跑 → 明确拒绝并给出停服指令（--force 可强行尝试，
#      但产物不保证可用，仅用于排障）。
#      产物：backups/h2-YYYYmmdd-HHMMSS.tar.gz（含 data 目录，打包后自动校验可读性）
#   ② MySQL（--profile mysql）：
#      打印 mysqldump 命令（口令从环境变量读取，不落盘、不进历史）并直接执行（若已安装 mysqldump）。
#      产物：backups/mysql-<db>-YYYYmmdd-HHMMSS.sql.gz
#   ③ 保留策略：--keep N 保留最近 N 份（默认 14），更早的自动清理。
#
#   恢复：停服后用同一台机器的 Java/tar 解开备份包，把 data 目录放回 server/ 下再启服（详见 README「部署」）
# ═══════════════════════════════════════════════════════════════════════════
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT="$ROOT/backups"
KEEP=14
PROFILE=""   # 默认 H2 文件快照；--profile mysql 走 mysqldump 逻辑备份
PORT=8080
FORCE=0

while [[ $# -gt 0 ]]; do
  case "$1" in
    --profile) PROFILE="$2"; shift 2 ;;
    --out) OUT="$2"; shift 2 ;;
    --keep) KEEP="$2"; shift 2 ;;
    --port) PORT="$2"; shift 2 ;;
    --force) FORCE=1; shift ;;
    *) echo "未知参数：$1"; exit 2 ;;
  esac
done

mkdir -p "$OUT"
STAMP="$(date +%Y%m%d-%H%M%S)"

if [[ "$PROFILE" == "mysql" ]]; then
  DB="${MYSQL_DATABASE:-hazmat}"
  HOST="${MYSQL_HOST:-127.0.0.1}"
  PORT="${MYSQL_PORT:-3306}"
  USER="${MYSQL_USERNAME:-hazmat}"
  TARGET="$OUT/mysql-${DB}-${STAMP}.sql.gz"
  echo "[backup] MySQL 逻辑备份：$DB@$HOST:$PORT -> $TARGET"
  echo "[backup] 等价命令（口令走环境变量，不落盘）："
  echo "  mysqldump -h$HOST -P$PORT -u$USER -p\"\$MYSQL_PASSWORD\" --single-transaction --routines --triggers $DB | gzip > $TARGET"
  if ! command -v mysqldump >/dev/null 2>&1; then
    echo "[backup] 未检测到 mysqldump：命令已列出，请在装有 MySQL 客户端的机器上执行（或使用云数据库快照）。"
    exit 0
  fi
  if [[ -z "${MYSQL_PASSWORD:-}" ]]; then
    echo "[backup] 环境变量 MYSQL_PASSWORD 未设置，出于安全不交互提示口令；请设置后重跑。"
    exit 1
  fi
  MYSQL_PWD="$MYSQL_PASSWORD" mysqldump -h"$HOST" -P"$PORT" -u"$USER" \
    --single-transaction --routines --triggers "$DB" | gzip > "$TARGET" || exit 1
  echo "[backup] 完成：$(du -h "$TARGET" | awk '{print $1}')"
else
  DATA="$ROOT/server/data"
  if [[ ! -d "$DATA" ]]; then
    echo "[backup] 未发现 H2 数据目录：$DATA（服务尚未启动过？）"
    exit 1
  fi

  # H2 单进程独占：运行中复制在 Windows 必失败（文件锁），Linux 上也非严格一致 → 强制冷备
  RUNNING=0
  if command -v netstat >/dev/null 2>&1 \
     && netstat -ano 2>/dev/null | grep -E "[:.]${PORT}[[:space:]]" | grep -qi "LISTENING"; then
    RUNNING=1
  fi
  if [[ "$RUNNING" == "1" && "$FORCE" != "1" ]]; then
    echo "[backup] 检测到端口 ${PORT} 正在运行：H2 数据文件被进程独占，运行中复制会产出损坏包。"
    echo "[backup] 请按冷备流程执行（推荐）："
    echo "         bash scripts/stop.sh --port ${PORT}"
    echo "         bash scripts/backup.sh"
    echo "         bash scripts/start.sh --port ${PORT}"
    echo "[backup] 如确需在运行中尝试（仅排障，产物不保证可用）：加 --force"
    exit 1
  fi
  if [[ "$RUNNING" == "1" ]]; then
    echo "[backup] ⚠ --force：服务仍在运行，产物可能损坏（H2 文件被占用）"
  fi

  TARGET="$OUT/h2-${STAMP}.tar.gz"
  echo "[backup] H2 文件快照（冷备）：$DATA -> $TARGET"
  if ! tar -czf "$TARGET" -C "$ROOT/server" data; then
    rm -f "$TARGET"
    echo "[backup] 打包失败（文件被占用？），已删除不完整产物：$TARGET"
    exit 1
  fi
  if ! tar -tzf "$TARGET" >/dev/null 2>&1; then
    rm -f "$TARGET"
    echo "[backup] 产物校验失败（压缩包不可读），已删除：$TARGET"
    exit 1
  fi
  DB_FILE_COUNT="$(tar -tzf "$TARGET" | grep -c 'hazmatmgmt.*\.db$' || true)"
  if [[ "$DB_FILE_COUNT" -lt 1 ]]; then
    rm -f "$TARGET"
    echo "[backup] 产物中未发现 H2 数据库文件（hazmatmgmt*.db），已删除：$TARGET"
    exit 1
  fi
  echo "[backup] 完成并校验通过（含 ${DB_FILE_COUNT} 个数据库文件）：$(du -h "$TARGET" | awk '{print $1}')"
fi

# ── 保留最近 KEEP 份 ──
if [[ "$KEEP" =~ ^[0-9]+$ ]] && [[ "$KEEP" -gt 0 ]]; then
  ls -1t "$OUT"/*.tar.gz "$OUT"/*.sql.gz 2>/dev/null | tail -n +"$((KEEP + 1))" | while read -r old; do
    echo "[backup] 清理过期备份：$old"
    rm -f "$old"
  done
fi

echo "[backup] 当前备份目录："
ls -lht "$OUT" 2>/dev/null | head -n 8
echo "[backup] 恢复：解开备份包，把 data 目录放回 server/ 下再启服（详见 README「部署」）"
