#!/usr/bin/env bash
# ═══════════════════════════════════════════════════════════════════════════
# hazmat-lims 一键构建并启动：根目录 ./start.sh
#   做三件事：① 构建前端（web/dist）② 把 dist 拷进 server 的静态目录并 package
#             ③ 调 scripts/start.sh 后台启动并等 /health 就绪
#   用法：./start.sh [--port 8080] [--profile mysql] [--reset-data]
#   环境：MAVEN_BIN 可指定 mvn 路径；NPM_BIN 可指定 npm 路径
#   说明：只跑后端而不构建前端时用 scripts/start.sh（需已有 jar）；
#         Linux/macOS 直接执行，Windows 用 Git Bash：bash start.sh
# ═══════════════════════════════════════════════════════════════════════════
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PORT=8080
PROFILE=""
RESET=0

while [[ $# -gt 0 ]]; do
  case "$1" in
    --port) PORT="${2:?缺少 --port 的值}"; shift 2 ;;
    --profile) PROFILE="${2:?缺少 --profile 的值}"; shift 2 ;;
    --reset-data) RESET=1; shift ;;
    -h|--help) sed -n '3,11p' "$0"; exit 0 ;;
    *) echo "未知参数：$1"; exit 2 ;;
  esac
done

command -v node >/dev/null 2>&1 || { echo "[start] 未找到 node，前端构建需要 Node 18+"; exit 1; }
NPM_BIN="${NPM_BIN:-npm}"
MVN_BIN="${MAVEN_BIN:-mvn}"

# 允许 MAVEN_BIN/NPM_BIN 传绝对路径（Windows 的 mvn.cmd 在 Git Bash 里没有可执行位，
# command -v 会误判），带路径时只检查文件存在。
if [[ "$MVN_BIN" == */* ]]; then
  [[ -f "$MVN_BIN" ]] || { echo "[start] 找不到 Maven：$MVN_BIN"; exit 1; }
elif ! command -v "$MVN_BIN" >/dev/null 2>&1; then
  echo "[start] 未找到 $MVN_BIN，后端打包需要 Maven 3.9+"; exit 1
fi
if [[ "$NPM_BIN" == */* ]]; then
  [[ -f "$NPM_BIN" ]] || { echo "[start] 找不到 npm：$NPM_BIN"; exit 1; }
fi

echo "[start] ① 构建前端：web/"
cd "$ROOT/web"
if [[ ! -d node_modules ]]; then
  echo "[start] 首次构建，执行 npm ci"
  "$NPM_BIN" ci
fi
"$NPM_BIN" run build

echo "[start] ② 同步前端产物到 server/src/main/resources/static 并打包后端"
STATIC="$ROOT/server/src/main/resources/static"
rm -rf "$STATIC"
mkdir -p "$STATIC"
cp -R "$ROOT/web/dist/." "$STATIC/"
cd "$ROOT/server"
"$MVN_BIN" -B -DskipTests package

echo "[start] ③ 启动服务"
START_ARGS=(--port "$PORT")
[[ -n "$PROFILE" ]] && START_ARGS+=(--profile "$PROFILE")
[[ "$RESET" == "1" ]] && START_ARGS+=(--reset-data)
exec bash "$ROOT/scripts/start.sh" "${START_ARGS[@]}"
