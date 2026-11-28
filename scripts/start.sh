#!/usr/bin/env bash
# ═══════════════════════════════════════════════════════════════════════════
# hazmat-lims 启停脚本：start.sh
#   用法：bash scripts/start.sh [--port 8080] [--profile mysql] [--reset-data]
#   前置：已构建 jar（server/target/hazmat-management-1.0.0.jar）；未构建时先执行
#         cd server && mvn package -DskipTests   （或 bash scripts/restart.sh --build）
#   行为：① 检查端口占用（占用即拒绝启动，避免"二次启动"假成功）
#         ② 日志写入 logs/app.log（nohup 后台）
#         ③ 轮询 /health 直到 200 或超时（默认 90s，含首次种子注入约 25s）
#   --reset-data：删除 server/data 后启动（H2 演示数据重建；生产 MySQL profile 下无效且拒绝执行）
# ═══════════════════════════════════════════════════════════════════════════
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SERVER_DIR="$ROOT/server"
JAR="$SERVER_DIR/target/hazmat-management-1.0.0.jar"
PORT=8080
PROFILE=""
RESET_DATA=0
WAIT_SECONDS=90

while [[ $# -gt 0 ]]; do
  case "$1" in
    --port) PORT="$2"; shift 2 ;;
    --profile) PROFILE="$2"; shift 2 ;;
    --reset-data) RESET_DATA=1; shift ;;
    --wait) WAIT_SECONDS="$2"; shift 2 ;;
    *) echo "未知参数：$1"; exit 2 ;;
  esac
done

if [[ ! -f "$JAR" ]]; then
  echo "[start] 未找到可执行 jar：$JAR"
  echo "[start] 请先构建：cd server && mvn package -DskipTests"
  exit 1
fi

# ── 端口占用检查（Windows Git Bash / Linux 均可）──
if command -v netstat >/dev/null 2>&1; then
  if netstat -ano 2>/dev/null | grep -E "[:.]${PORT}[[:space:]]" | grep -qi "LISTENING"; then
    echo "[start] 端口 ${PORT} 已被占用，拒绝重复启动。先执行 bash scripts/stop.sh --port ${PORT}"
    exit 1
  fi
fi

if [[ "$RESET_DATA" == "1" ]]; then
  if [[ "$PROFILE" == "mysql" ]]; then
    echo "[start] MySQL profile 下 --reset-data 无意义（数据不在本地 ./data），已拒绝执行"
    exit 1
  fi
  echo "[start] 重置演示数据：删除 $SERVER_DIR/data"
  rm -rf "$SERVER_DIR/data"
fi

mkdir -p "$ROOT/logs"
LOG="$ROOT/logs/app.log"

# ── 运行时选择：优先包内自带的 runtime（发行包不要求用户装 Java），其次系统 java ──
JAVA_BIN=""
for c in "$ROOT/runtime/bin/java" "$ROOT/../runtime/bin/java"; do
  if [[ -x "$c" ]]; then JAVA_BIN="$c"; break; fi
done
if [[ -z "$JAVA_BIN" ]]; then
  if command -v java >/dev/null 2>&1; then
    JAVA_BIN="java"
    echo "[start] 提示：未发现内置 runtime/bin/java，使用系统 java"
  else
    echo "[start] 未找到 Java 运行时：既没有内置 runtime/bin/java，系统里也没有 java。"
    echo "[start] 发行包可自带 runtime 目录（与 jar 同级）；从源码构建时才需要系统装 Java 17+。"
    exit 1
  fi
fi
echo "[start] 启动 hazmat-lims：port=$PORT profile=${PROFILE:-default} jar=$JAR java=$JAVA_BIN"

pushd "$SERVER_DIR" >/dev/null
ARGS=("--server.port=$PORT")
if [[ -n "$PROFILE" ]]; then
  ARGS+=("--spring.profiles.active=$PROFILE")
fi
nohup "$JAVA_BIN" -jar "$JAR" "${ARGS[@]}" > "$LOG" 2>&1 &
PID=$!
popd >/dev/null
echo "$PID" > "$ROOT/logs/app.pid"
echo "[start] 进程已拉起 pid=$PID，日志：$LOG"

# ── 就绪探针（/health）──
for i in $(seq 1 "$WAIT_SECONDS"); do
  sleep 1
  if curl -fsS "http://localhost:$PORT/health" >/dev/null 2>&1; then
    echo "[start] 已就绪（${i}s）：$(curl -fsS "http://localhost:$PORT/health")"
    exit 0
  fi
  if ! kill -0 "$PID" 2>/dev/null; then
    echo "[start] 进程已退出，日志尾部："
    tail -n 30 "$LOG"
    exit 1
  fi
done

echo "[start] 等待 ${WAIT_SECONDS}s 未就绪，请检查日志：$LOG"
tail -n 30 "$LOG"
exit 1
