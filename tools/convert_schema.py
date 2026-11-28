#!/usr/bin/env python3
"""把 H2 版建表脚本转换成 MySQL 8 可导入的版本。

H2 接受 ``CREATE INDEX IF NOT EXISTS``，而 MySQL 8 不接受这个子句
（``ERROR 1064 (42000)``）。本脚本只改索引语句：去掉 ``IF NOT EXISTS``，
其余内容（含 ``CREATE TABLE IF NOT EXISTS``、注释、数据类型）原样保留。

用法::

    python tools/convert_schema.py
    python tools/convert_schema.py --in server/src/main/resources/schema.sql --out schema-mysql.sql

输出固定为 UTF-8 无 BOM，避免 Windows 下 ``Set-Content -Encoding utf8``
在 PowerShell 5.1 里写出 BOM 的问题（MySQL 客户端通常能吃 BOM，但不保证）。
"""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
DEFAULT_IN = REPO_ROOT / "server" / "src" / "main" / "resources" / "schema.sql"
DEFAULT_OUT = REPO_ROOT / "schema-mysql.sql"

INDEX_PATTERN = re.compile(r"^CREATE (UNIQUE )?INDEX IF NOT EXISTS", re.IGNORECASE)


def convert(text: str) -> str:
    lines = []
    for line in text.splitlines():
        lines.append(INDEX_PATTERN.sub(lambda m: "CREATE " + (m.group(1) or "") + "INDEX", line))
    return "\n".join(lines) + "\n"


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="H2 schema.sql → MySQL 8 建表脚本")
    parser.add_argument("--in", dest="src", type=Path, default=DEFAULT_IN, help="源文件（默认 %(default)s）")
    parser.add_argument("--out", dest="dst", type=Path, default=DEFAULT_OUT, help="输出文件（默认 %(default)s）")
    args = parser.parse_args(argv)

    if not args.src.is_file():
        print(f"[convert] 源文件不存在：{args.src}", file=sys.stderr)
        return 1

    text = args.src.read_text(encoding="utf-8")
    out = convert(text)
    args.dst.write_text(out, encoding="utf-8", newline="\n")

    changed = sum(1 for line in text.splitlines() if INDEX_PATTERN.match(line))
    print(f"[convert] {args.src} -> {args.dst}（去掉 {changed} 条索引语句的 IF NOT EXISTS）")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
