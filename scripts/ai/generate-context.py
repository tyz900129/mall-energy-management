#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""扫描代码生成 AI 上下文事实快照。

只读源码，不修改任何业务代码。输出到 docs/ai/generated/。
用法：python scripts/ai/generate-context.py
"""
import re
import subprocess
from datetime import datetime
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
BACKEND = ROOT / 'server-spring/energy-management/src/main/java/com/wanyuecity/energy'
MIGRATIONS = ROOT / 'server-spring/energy-management/src/main/resources/db/migration'
OUT = ROOT / 'docs/ai/generated'

ANN_RE = re.compile(r'@(Get|Post|Put|Delete|Patch)Mapping\b')
AUTH_RE = re.compile(r"has(?:Any)?Authority\s*\(([^)]*)\)")
CODE_RE = re.compile(r'\b[a-z][a-z0-9-]*(?:\.[a-z0-9-]+)+\b')
TABLE_RE = re.compile(r'CREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?`?([A-Za-z0-9_]+)`?', re.I)


def git_anchor():
    try:
        branch = subprocess.run(['git', '-C', str(ROOT), 'rev-parse', '--abbrev-ref', 'HEAD'],
                                capture_output=True, text=True, timeout=10).stdout.strip()
        commit = subprocess.run(['git', '-C', str(ROOT), 'rev-parse', '--short', 'HEAD'],
                                capture_output=True, text=True, timeout=10).stdout.strip()
        return f'{branch}@{commit}' if branch and commit else 'unknown'
    except Exception:
        return 'unknown'


def balanced(text, start):
    depth = 0
    for i in range(start, len(text)):
        if text[i] == '(':
            depth += 1
        elif text[i] == ')':
            depth -= 1
            if depth == 0:
                return text[start:i + 1]
    return text[start:]


def class_base(text):
    m = re.search(r'@RequestMapping\s*\(', text)
    if not m:
        return ''
    values = re.findall(r'"([^"]*)"', balanced(text, text.index('(', m.start())))
    return values[0] if values else ''


def class_permission(text):
    class_pos = re.search(r'\b(?:public\s+)?(?:final\s+)?class\s+\w+', text)
    head = text[:class_pos.start()] if class_pos else text[:2000]
    hits = list(AUTH_RE.finditer(head))
    if not hits:
        return ''
    codes = re.findall(r"'([^']+)'|\"([^\"]+)\"", hits[-1].group(1))
    return ', '.join(sorted({a or b for a, b in codes if a or b}))


def normalize(path):
    return '/' + path.strip().strip('/') if path.strip().strip('/') else ''


def join_path(base, path):
    base = normalize(base)
    path = normalize(path)
    if base and path:
        return base + path
    return base or path or '(未声明路径)'


def permission_in(text, start, end):
    hits = list(AUTH_RE.finditer(text[start:end]))
    if not hits:
        return ''
    codes = re.findall(r"'([^']+)'|\"([^\"]+)\"", hits[-1].group(1))
    return ', '.join(sorted({a or b for a, b in codes if a or b}))


def signature_end(text, ann_end):
    """返回方法签名左括号的绝对位置，用于切分“本方法区间”。"""
    m = re.search(r'\b(?:public|protected|private)\s+', text[ann_end:])
    rest_start = ann_end + m.end() if m else ann_end
    rel = text[rest_start:].find('(')
    return rest_start + rel if rel != -1 else ann_end + 200


def method_name(text, ann_end):
    """兼容一行式 Controller：@GetMapping("/x") public ApiResponse<...>list(...)。

    取 public/protected/private 之后、第一个左括号之前的最后一个标识符作为方法名。
    """
    m = re.search(r'\b(?:public|protected|private)\s+', text[ann_end:])
    if not m:
        return '?'
    rest = text[ann_end + m.end():]
    paren = rest.find('(')
    head = rest[:paren] if paren != -1 else rest[:200]
    names = re.findall(r'\w+', head)
    return names[-1] if names else '?'


def controller_rows(java_path):
    text = java_path.read_text(encoding='utf-8', errors='ignore')
    rel = java_path.relative_to(BACKEND)
    module = rel.parts[0] if len(rel.parts) > 1 else 'root'
    base = class_base(text)
    default_perm = class_permission(text)
    rows = []
    matches = list(ANN_RE.finditer(text))
    for i, m in enumerate(matches):
        http = m.group(1).upper()
        ann_end = m.end()
        paths = ['']
        paren = text.find('(', ann_end)
        if paren != -1 and not text[ann_end:paren].strip():
            block = balanced(text, paren)
            paths = re.findall(r'"([^"]*)"', block) or ['']
            ann_end = paren + len(block) - 1
        method = method_name(text, ann_end)
        seg_start = matches[i - 1].end() if i else 0
        perm = permission_in(text, seg_start, signature_end(text, ann_end)) or default_perm
        for path in paths:
            rows.append({
                'module': module,
                'controller': java_path.stem,
                'method': method,
                'http': http,
                'path': join_path(base, path),
                'permission': perm,
            })
    return rows


def collect_api():
    rows = []
    for path in sorted(BACKEND.rglob('*Controller.java')):
        try:
            rows.extend(controller_rows(path))
        except Exception as exc:
            print(f'WARN 解析失败 {path}: {exc}')
    rows.sort(key=lambda r: (r['module'], r['controller'], r['path'], r['http']))
    return rows


def collect_tables():
    rows = []
    for path in sorted(MIGRATIONS.glob('V*.sql'), key=lambda p: int(re.search(r'V(\d+)', p.name).group(1))):
        version = int(re.search(r'V(\d+)', path.name).group(1))
        text = path.read_text(encoding='utf-8', errors='ignore')
        for table in sorted(set(TABLE_RE.findall(text))):
            rows.append({'table': table, 'version': version, 'migration': path.name})
    return rows


def collect_permissions():
    sources = {}
    for path in sorted(BACKEND.rglob('*.java')):
        text = path.read_text(encoding='utf-8', errors='ignore')
        rel = path.relative_to(BACKEND)
        for m in AUTH_RE.finditer(text):
            for a, b in re.findall(r"'([^']+)'|\"([^\"]+)\"", m.group(1)):
                code = a or b
                if code and CODE_RE.fullmatch(code):
                    sources.setdefault(code, set()).add(str(rel))
    resolver = next(BACKEND.rglob('RequestPermissionResolver.java'), None)
    if resolver:
        text = resolver.read_text(encoding='utf-8', errors='ignore')
        for token in re.findall(r'"([^"]+)"', text):
            if CODE_RE.fullmatch(token):
                sources.setdefault(token, set()).add('security/RequestPermissionResolver.java')
    for path in sorted(MIGRATIONS.glob('V*.sql')):
        text = path.read_text(encoding='utf-8', errors='ignore')
        for token in re.findall(r"'([^']+)'", text):
            if CODE_RE.fullmatch(token):
                sources.setdefault(token, set()).add(path.name)
    return sources


def collect_modules(api_rows):
    modules = []
    for directory in sorted(p for p in BACKEND.iterdir() if p.is_dir()):
        files = list(directory.rglob('*.java'))
        loc = sum(len(p.read_text(encoding='utf-8', errors='ignore').splitlines()) for p in files)
        controllers = len([p for p in files if p.name.endswith('Controller.java')])
        endpoints = sum(1 for r in api_rows if r['module'] == directory.name)
        modules.append({'module': directory.name, 'files': len(files), 'loc': loc,
                        'controllers': controllers, 'endpoints': endpoints})
    root_files = list(BACKEND.glob('*.java'))
    if root_files:
        modules.append({'module': 'root', 'files': len(root_files),
                        'loc': sum(len(p.read_text(encoding='utf-8', errors='ignore').splitlines()) for p in root_files),
                        'controllers': len([p for p in root_files if p.name.endswith('Controller.java')]),
                        'endpoints': sum(1 for r in api_rows if r['module'] == 'root')})
    modules.sort(key=lambda m: (-m['endpoints'], m['module']))
    return modules


def header(title):
    return (f'# {title}\n\n'
            f'> 自动生成，请勿手改。生成命令：`python scripts/ai/generate-context.py`\n'
            f'> 生成时间：{datetime.now().strftime("%Y-%m-%d %H:%M:%S")} ｜ 代码锚点：{git_anchor()}\n\n')


def write_api(rows):
    lines = [header('接口地图（自动生成）'),
             f'共 {len(rows)} 个接口，{len({(r["module"], r["controller"]) for r in rows})} 个控制器。\n',
             '> “代码内权限”为空表示该接口由 `RequestPermissionResolver` 的路径映射鉴权，未写 `@PreAuthorize`。\n',
             '| 模块 | Controller | 方法 | HTTP | 路径 | 代码内权限 |',
             '|---|---|---|---|---|---|']
    for r in rows:
        lines.append(f'| {r["module"]} | {r["controller"]} | {r["method"]} | {r["http"]} | `{r["path"]}` | '
                     f'{("`" + r["permission"] + "`") if r["permission"] else "—"} |')
    (OUT / 'api-map.md').write_text('\n'.join(lines) + '\n', encoding='utf-8')


def write_tables(rows):
    versions = sorted({r['version'] for r in rows})
    lines = [header('数据表清单（自动生成）'),
             f'共 {len(rows)} 张表，来自 {len(versions)} 个含建表语句的迁移脚本（最高 V{max(versions) if versions else 0}）。\n',
             '| 表名 | 创建迁移 | 版本 |', '|---|---|---|']
    for r in rows:
        lines.append(f'| `{r["table"]}` | {r["migration"]} | V{r["version"]} |')
    (OUT / 'tables.md').write_text('\n'.join(lines) + '\n', encoding='utf-8')


def write_permissions(sources):
    lines = [header('权限码清单（自动生成）'),
             f'共 {len(sources)} 个权限码，来源为 `@PreAuthorize`、`RequestPermissionResolver` 与迁移脚本。\n',
             '> 角色授权关系以数据库 `sys_role_permission` 为准，本表不反映“谁拥有”。\n',
             '| 权限码 | 出现位置 |', '|---|---|']
    for code in sorted(sources):
        places = '<br>'.join(f'`{x}`' for x in sorted(sources[code]))
        lines.append(f'| `{code}` | {places} |')
    (OUT / 'permission-codes.md').write_text('\n'.join(lines) + '\n', encoding='utf-8')


def write_modules(modules, api_rows, tables):
    total_files = sum(m['files'] for m in modules)
    total_loc = sum(m['loc'] for m in modules)
    lines = [header('模块规模（自动生成）'),
             f'共 {total_files} 个 Java 文件、约 {total_loc} 行；{len(api_rows)} 个接口；{len(tables)} 张表。\n',
             '| 模块 | Java 文件 | 行数 | Controller | 接口数 |', '|---|---|---|---|---|']
    for m in modules:
        lines.append(f'| {m["module"]} | {m["files"]} | {m["loc"]} | {m["controllers"]} | {m["endpoints"]} |')
    (OUT / 'modules.md').write_text('\n'.join(lines) + '\n', encoding='utf-8')


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    api_rows = collect_api()
    tables = collect_tables()
    permissions = collect_permissions()
    modules = collect_modules(api_rows)
    write_api(api_rows)
    write_tables(tables)
    write_permissions(permissions)
    write_modules(modules, api_rows, tables)
    print(f'OK 接口 {len(api_rows)} ｜ 表 {len(tables)} ｜ 权限码 {len(permissions)} ｜ 模块 {len(modules)}')
    print(f'输出目录：{OUT}')


if __name__ == '__main__':
    main()
