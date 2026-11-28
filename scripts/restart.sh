#!/usr/bin/env bash
# ═══════════════════════════════════════════════════════════════════════════
# hazmat-lims 启停脚本：restart.sh
#   用法：bash scripts/restart.sh [--port 8080] [--profile mysql] [--build] [--reset-data]
#   行为：stop →（可选构建）→ start（等待 /health 就绪）
#   --build：先打包（关掉占用端口的实例后再 package，避免占用时 repackage 静默跳过）
#   运行时：启动器统一由 start.sh 选择——优先包内自带 runtime/bin/java，其次系统 java
# ═══════════════════════════════════════════════════════════════════════════
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PORT=8080
PROFILE=""
BUILD=0
RESET=0

while [[ $# -gt 0 ]]; do
  case "$1" in
    --port) PORT="$2"; shift 2 ;;
    --profile) PROFILE="$2"; shift 2 ;;
    --build) BUILD=1; shift ;;
    --reset-data) RESET=1; shift ;;
    *) echo "未知参数：$1"; exit 2 ;;
  esac
done

bash "$ROOT/scripts/stop.sh" --port "$PORT" || true

if [[ "$BUILD" == "1" ]]; then
  MVN="${MAVEN_BIN:-mvn}"
  echo "[restart] 打包：$MVN package -DskipTests"
  ( cd "$ROOT/server" && "$MVN" package -DskipTests ) || exit 1
fi

START_ARGS=(--port "$PORT")
[[ -n "$PROFILE" ]] && START_ARGS+=(--profile "$PROFILE")
[[ "$RESET" == "1" ]] && START_ARGS+=(--reset-data)
exec bash "$ROOT/scripts/start.sh" "${START_ARGS[@]}"
