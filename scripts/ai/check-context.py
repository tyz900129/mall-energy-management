#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""校验 AI 上下文文档与代码事实是否一致。

只读检查，不修改任何文件。发现问题时返回非 0。
用法：python scripts/ai/check-context.py
"""
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
BACKEND = ROOT / 'server-spring/energy-management/src/main/java/com/wanyuecity/energy'
MIGRATIONS = ROOT / 'server-spring/energy-management/src/main/resources/db/migration'
FRONTEND = ROOT / 'server-spring/energy-admin'
DOCS = [ROOT / 'AGENTS.md'] + sorted((ROOT / 'docs/ai').rglob('*.md'))
EXCLUDE = {ROOT / 'docs/ai/generated/api-map.md', ROOT / 'docs/ai/generated/tables.md',
           ROOT / 'docs/ai/generated/permission-codes.md', ROOT / 'docs/ai/generated/modules.md'}

TOKEN_RE = re.compile(r'`([^`]+)`')
CODE_RE = re.compile(r'^[a-z][a-z0-9-]*(?:\.[a-z0-9-]+)+$')
PERM_RE = re.compile(r'^(access|complaint|energy|inspection|calendar|ops|system|visitor)\.[a-z0-9.\-]+$')
TABLE_RE = re.compile(r'CREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?`?([A-Za-z0-9_]+)`?', re.I)
EXT = ('.java', '.vue', '.ts', '.md', '.sql', '.py', '.ps1', '.json', '.yml', '.yaml', '.xml', '.html')
PREFIXES = ('server-spring/', 'docs/', 'scripts/', '.github/', '.cursor/', '.trae/')

errors, warnings = [], []


def fail(msg):
    errors.append(msg)


def warn(msg):
    warnings.append(msg)


def expand(token):
    if '|' not in token:
        return [token]
    parts = token.split('|')
    first = parts[0]
    if '.' in first:
        prefix = first.rsplit('.', 1)[0]
        return [first] + [f'{prefix}.{p}' for p in parts[1:]]
    if '_' in first:
        prefix = first.rsplit('_', 1)[0]
        return [first] + [f'{prefix}_{p}' for p in parts[1:]]
    return [first]


def resolve(tok):
    tok = tok.strip().replace('\\', '/')
    if tok in ('AGENTS.md', 'CLAUDE.md'):
        return ROOT / tok
    for prefix in PREFIXES:
        if tok.startswith(prefix):
            return ROOT / tok
    if tok.startswith('src/'):
        return FRONTEND / tok
    if tok.endswith('.java'):
        return BACKEND / tok
    if tok.endswith('.sql') and tok.startswith('V'):
        return MIGRATIONS / tok
    return None


def looks_like_path(tok):
    if any(ch in tok for ch in ('*', '{', '}', '=', '<', '>', ' ')):
        return False
    if tok.startswith(PREFIXES) or tok.startswith('src/'):
        return True
    return tok.endswith(EXT) and tok not in ('AGENTS.md', 'CLAUDE.md')


def known_facts():
    tables = set()
    for path in MIGRATIONS.glob('V*.sql'):
        tables.update(TABLE_RE.findall(path.read_text(encoding='utf-8', errors='ignore')))
    codes = set()
    for path in BACKEND.rglob('*.java'):
        text = path.read_text(encoding='utf-8', errors='ignore')
        for token in re.findall(r"'([^']+)'", text):
            if CODE_RE.match(token):
                codes.add(token)
    for path in MIGRATIONS.glob('V*.sql'):
        text = path.read_text(encoding='utf-8', errors='ignore')
        for token in re.findall(r"'([^']+)'", text):
            if CODE_RE.match(token):
                codes.add(token)
    versions = {int(re.search(r'V(\d+)', p.name).group(1)) for p in MIGRATIONS.glob('V*.sql')}
    return tables, codes, versions


def java_exists(token):
    """模块卡里常写裸类名，允许按文件名在 backend 下递归查找。"""
    name = token.strip().replace('\\', '/').rsplit('/', 1)[-1]
    if not name.endswith('.java'):
        return False
    return next(BACKEND.rglob(name), None) is not None


def check_tables_in_line(rel, line, tables):
    """只在“- 表：”行内校验表名，避免把普通反引号内容误判为表。"""
    body = line.split('表：', 1)[1]
    for token in TOKEN_RE.findall(body):
        first = token.split('|')[0]
        if not re.match(r'^[a-z][a-z0-9_]*$', first):
            continue
        for name in expand(token):
            if name not in tables:
                fail(f'{rel}: 表不存在 `{name}`')


def check_docs(tables, codes, versions):
    for doc in DOCS:
        if doc in EXCLUDE or not doc.exists():
            continue
        text = doc.read_text(encoding='utf-8', errors='ignore')
        rel = doc.relative_to(ROOT)
        for line in text.splitlines():
            if line.strip().startswith('- 表：'):
                check_tables_in_line(rel, line, tables)
        for token in TOKEN_RE.findall(text):
            if looks_like_path(token):
                target = resolve(token)
                if target and not target.exists() and not java_exists(token):
                    fail(f'{rel}: 路径不存在 `{token}`')
            if PERM_RE.match(token):
                if '|' in token:
                    for name in expand(token):
                        if name not in codes:
                            fail(f'{rel}: 权限码不存在 `{name}`')
                elif token not in codes:
                    fail(f'{rel}: 权限码不存在 `{token}`')
        for version in {int(x) for x in re.findall(r'\bV(\d+)\b', text)}:
            if version not in versions:
                fail(f'{rel}: 迁移 V{version} 不存在')
        if 'modules/' in str(rel) and '最后核对' not in text:
            fail(f'{rel}: 缺少“最后核对”段落')


def check_anchor(versions):
    text = (ROOT / 'AGENTS.md').read_text(encoding='utf-8', errors='ignore')
    declared = {int(x) for x in re.findall(r'V(\d+)', text)}
    if declared and max(declared) != max(versions):
        fail(f'AGENTS.md: 声明最高 V{max(declared)}，实际最高 V{max(versions)}')


def check_generated():
    latest = MIGRATIONS.glob('V*.sql')
    newest = max(latest, key=lambda p: p.stat().st_mtime)
    api = ROOT / 'docs/ai/generated/api-map.md'
    if not api.exists():
        fail('docs/ai/generated 缺失，请先运行 generate-context.py')
        return
    head = '\n'.join(api.read_text(encoding='utf-8', errors='ignore').splitlines()[:4])
    anchor = re.search(r'代码锚点：(\S+)', head)
    try:
        current = subprocess.run(['git', '-C', str(ROOT), 'rev-parse', '--abbrev-ref', 'HEAD'],
                                 capture_output=True, text=True, timeout=10).stdout.strip()
        commit = subprocess.run(['git', '-C', str(ROOT), 'rev-parse', '--short', 'HEAD'],
                                capture_output=True, text=True, timeout=10).stdout.strip()
        if anchor and anchor.group(1) != f'{current}@{commit}':
            warn(f'生成物锚点 {anchor.group(1)} 与当前 {current}@{commit} 不一致，建议重新生成')
    except Exception:
        pass
    if newest.stat().st_mtime > api.stat().st_mtime:
        warn('迁移脚本比生成物新，建议重新运行 generate-context.py')


def check_index():
    index = (ROOT / 'docs/ai/README.md').read_text(encoding='utf-8', errors='ignore')
    agents = (ROOT / 'AGENTS.md').read_text(encoding='utf-8', errors='ignore')
    for card in sorted((ROOT / 'docs/ai/modules').glob('*.md')):
        if card.name == '_template.md':
            continue
        if card.name not in index or card.name not in agents:
            warn(f'{card.name} 未在 docs/ai/README.md 或 AGENTS.md 索引中登记')


def main():
    tables, codes, versions = known_facts()
    check_docs(tables, codes, versions)
    check_anchor(versions)
    check_generated()
    check_index()
    for item in errors:
        print(f'[FAIL] {item}')
    for item in warnings:
        print(f'[WARN] {item}')
    print(f'检查完成：{len(errors)} 个错误，{len(warnings)} 个警告')
    return 1 if errors else 0


if __name__ == '__main__':
    sys.exit(main())
