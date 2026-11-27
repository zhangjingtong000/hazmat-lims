#!/usr/bin/env python3
"""从 schema.sql 生成 MyBatis 实体（POJO，风格同 asset：手写 getter/setter）。
联表（sys_user_role/sys_role_menu）不生成实体。一次性工具，schema 变更后可重跑。"""
import io
import os
import re

TOOL_DIR = os.path.dirname(os.path.abspath(__file__))
SERVER_DIR = os.path.dirname(TOOL_DIR)
SCHEMA = os.path.join(SERVER_DIR, 'src', 'main', 'resources', 'schema.sql')
BASE = os.path.join(SERVER_DIR, 'src', 'main', 'java', 'com', 'qingyan', 'hazmat')
SKIP = {'sys_user_role', 'sys_role_menu'}

TYPE_MAP = {'BIGINT': 'Long', 'VARCHAR': 'String', 'DECIMAL': 'java.math.BigDecimal',
            'TINYINT': 'Integer', 'INT': 'Integer', 'DATE': 'java.time.LocalDate',
            'DATETIME': 'java.time.LocalDateTime', 'CHAR': 'String', 'TEXT': 'String'}

def camel(s):
    return ''.join(w.capitalize() for w in s.split('_'))

def field_camel(s):
    parts = s.split('_')
    return parts[0] + ''.join(w.capitalize() for w in parts[1:])

t = io.open(SCHEMA, encoding='utf-8').read()
tables = re.findall(r'CREATE TABLE IF NOT EXISTS (\w+) \((.*?)\n\);', t, re.S)
print(f'解析到 {len(tables)} 张表')

made = 0
for name, body in tables:
    if name in SKIP:
        continue
    cls = camel(name)
    domain = 'hazmat.entity' if name.startswith('chem_') or name in (
        'purchase_req', 'approval_record', 'stock_in', 'requisition', 'requisition_batch',
        'stock_return', 'waste_disposal', 'storage_cabinet', 'compat_rule') else 'system.entity'
    pkg = f'com.qingyan.hazmat.{domain}'
    fields = []
    for line in body.split('\n'):
        line = line.strip().rstrip(',')
        if not line or line.startswith('--'):
            continue
        m = re.match(r'(\w+)\s+(\w+)(?:\([^)]*\))?\s*(.*)', line)
        if not m:
            continue
        col, sqltype, rest = m.group(1), m.group(2).upper(), m.group(3)
        cmt = ''
        cm = re.search(r'--\s*(.*)', rest)
        if cm:
            cmt = cm.group(1).strip()
        ftype = TYPE_MAP.get(sqltype, 'String')
        fname = field_camel(col)
        fields.append((fname, ftype, cmt))
    imports = sorted({f[1] for f in fields if '.' in f[1]})
    out = [f'package {pkg};', '']
    if imports:
        out += [f'import {i};' for i in imports] + ['']
    out += [f'/** {name}（schema.sql 同源生成）。 */', f'public class {cls} {{', '']
    for fname, ftype, cmt in fields:
        if cmt:
            out.append(f'    /** {cmt} */')
        out.append(f'    private {ftype} {fname};')
        out.append('')
    for fname, ftype, _ in fields:
        cap = fname[0].upper() + fname[1:]
        out += [f'    public {ftype} get{cap}() {{', f'        return {fname};', '    }', '',
                f'    public void set{cap}({ftype} {fname}) {{', f'        this.{fname} = {fname};',
                '    }', '']
    out += ['}', '']
    path = os.path.join(BASE, *domain.split('.'), f'{cls}.java')
    io.open(path, 'w', encoding='utf-8').write('\n'.join(out))
    made += 1
    print(f'  {cls}.java ← {name} ({len(fields)} 字段)')
print(f'生成 {made} 个实体')
