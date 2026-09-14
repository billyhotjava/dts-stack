#!/usr/bin/env python3
"""错误码清点脚本（只读扫描，输出 Markdown）。

用法:
    python3 error_code_inventory.py --output <out.md> <label>=<root> [<label>=<root> ...]

识别模式（允许换行）：
  new XxxException("CODE" ... / failure("CODE... / error("CODE...
只收录全大写下划线且长度 >= 4 的字符串；同一文件同一码去重。
"""

import re
import sys
from pathlib import Path

PATTERNS = [
    re.compile(r'new\s+\w*Exception\(\s*"([A-Z][A-Z0-9_]{3,})"\s*[,)]', re.S),
    re.compile(r'\b(?:failure|error|fail|invalid|unsupported|conflict)\(\s*"([A-Z][A-Z0-9_]{3,})(?:"\s*[,)]|:)', re.S),
]


def main() -> int:
    argv = sys.argv[1:]
    if "--output" not in argv:
        print(__doc__)
        return 2
    output_value = argv[argv.index("--output") + 1]
    output = Path(output_value)
    roots = []
    skip_next = False
    for index, item in enumerate(argv):
        if skip_next:
            skip_next = False
            continue
        if item == "--output":
            skip_next = True
            continue
        label, sep, root = item.partition("=")
        if sep and root:
            roots.append((label, Path(root)))
    lines = ["# 错误码清单（脚本生成，需人工核对）", ""]
    total = 0
    repo_root = Path.cwd()
    for label, root in roots:
        rows = {}
        for path in sorted(root.rglob("*.java")):
            text = path.read_text(encoding="utf-8", errors="replace")
            rel = str(path.relative_to(repo_root)) if str(path).startswith(str(repo_root)) else str(path)
            for pattern in PATTERNS:
                for match in pattern.finditer(text):
                    code = match.group(1)
                    line = text.count("\n", 0, match.start()) + 1
                    rows.setdefault(code, (rel, line))
        lines.append(f"## {label}（{len(rows)} 个码）")
        lines.append("")
        lines.append("| 错误码 | 首次出现 |")
        lines.append("|---|---|")
        for code in sorted(rows):
            rel, number = rows[code]
            lines.append(f"| `{code}` | `{rel}:{number}` |")
        lines.append("")
        total += len(rows)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text("\n".join(lines), encoding="utf-8")
    print(f"codes: {total} -> {output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
