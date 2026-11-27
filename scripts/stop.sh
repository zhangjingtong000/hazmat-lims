#!/usr/bin/env bash
# ═══════════════════════════════════════════════════════════════════════════
# hazmat-lims 启停脚本：stop.sh
#   用法：bash scripts/stop.sh [--port 8081]
#   行为：优先按 logs/app.pid 优雅停止（SIGTERM，最多等 20s）；pid 失配时按端口反查兜底；
#         停止后确认端口已释放，否则返回非 0（供 CI/运维判断）。
# ═══════════════════════════════════════════════════════════════════════════
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PORT=8081
while [[ $# -gt 0 ]]; do
  case "$1" in
    --port) PORT="$2"; shift 2 ;;
    *) echo "未知参数：$1"; exit 2 ;;
  esac
done

PID_FILE="$ROOT/logs/app.pid"

# Windows（Git Bash）下 MSYS kill 可能无法终止非本 shell 派生的进程，回退 taskkill
kill_pid_hard() {
  local pid="$1"
  if command -v taskkill >/dev/null 2>&1; then
    taskkill //F //PID "$pid" >/dev/null 2>&1 || true
  else
    kill -9 "$pid" 2>/dev/null || true
  fi
}

stop_pid() {
  local pid="$1"
  if [[ -z "$pid" ]] || ! kill -0 "$pid" 2>/dev/null; then
    return 1
  fi
  echo "[stop] 停止进程 pid=$pid（SIGTERM）"
  kill "$pid" 2>/dev/null || true
  for _ in $(seq 1 20); do
    sleep 1
    if ! kill -0 "$pid" 2>/dev/null; then
      echo "[stop] 进程已退出"
      return 0
    fi
  done
  echo "[stop] 20s 未退出，强制结束（SIGKILL/taskkill）"
  kill_pid_hard "$pid"
  sleep 1
  return 0
}

STOPPED=0
if [[ -f "$PID_FILE" ]]; then
  PID="$(cat "$PID_FILE" 2>/dev/null || true)"
  if stop_pid "$PID"; then
    STOPPED=1
  fi
  rm -f "$PID_FILE"
fi

# ── 端口兜底（pid 文件丢失/进程被外部接管/非本 shell 派生）──
if command -v netstat >/dev/null 2>&1; then
  while read -r line; do
    pid="$(echo "$line" | awk '{print $NF}')"
    [[ "$pid" =~ ^[0-9]+$ ]] || continue
    if kill -0 "$pid" 2>/dev/null; then
      stop_pid "$pid" && STOPPED=1
    else
      # MSYS 看不见该进程（例如由其它终端/服务启动）：走 taskkill 强杀
      echo "[stop] 进程 pid=$pid 不在当前会话，使用 taskkill 强制结束"
      kill_pid_hard "$pid"
      STOPPED=1
    fi
  done < <(netstat -ano 2>/dev/null | grep -E "[:.]${PORT}[[:space:]]" | grep -i "LISTENING" || true)
fi

sleep 1
if command -v netstat >/dev/null 2>&1 && netstat -ano 2>/dev/null | grep -E "[:.]${PORT}[[:space:]]" | grep -qi "LISTENING"; then
  echo "[stop] 端口 ${PORT} 仍被占用，请手工检查"
  exit 1
fi

if [[ "$STOPPED" == "1" ]]; then
  echo "[stop] 已停止，端口 ${PORT} 已释放"
else
  echo "[stop] 未发现运行中的实例（端口 ${PORT} 空闲）"
fi
exit 0
