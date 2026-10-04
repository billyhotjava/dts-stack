#!/usr/bin/env python3
"""Spring MVC 接口清点脚本（只读扫描源码，输出 Markdown 清单）。

用法:
    python3 rest_inventory.py <service-src-root> <output.md> [path-keyword ...]

- 扫描 @RestController 类，拼接类级 @RequestMapping 与方法级 @*Mapping。
- 可选 path-keyword：只保留文件路径包含任一关键字的控制器。
- 生成的清单需人工核对；行号以脚本运行时源码为准。
"""

import re
import sys
from pathlib import Path

CLASS_DECL = re.compile(r"public\s+(?:final\s+)?class\s+(\w+)")
METHOD_DECL = re.compile(
    r"(?:public|protected)\s+(?:static\s+)?(?:final\s+)?[\w<>,\[\]\s\.\?]+\s+(\w+)\s*\("
)
MAPPING = re.compile(r"@(Get|Post|Put|Delete|Patch)Mapping\b\s*(\([^)]*\))?")
REQUEST_MAPPING = re.compile(r"@RequestMapping\b\s*(\([^)]*\))?")
REST_CONTROLLER = re.compile(r"@RestController\b")
QUOTED = re.compile(r'"([^"]*)"')


def annotation_path(args: str | None) -> str:
    if not args:
        return ""
    values = QUOTED.findall(args)
    return values[0] if values else ""


def join_paths(base: str, sub: str) -> str:
    parts = [p.strip().strip('"') for p in (base, sub) if p and p.strip()]
    joined = "/".join(part.strip("/") for part in parts if part.strip("/") != "")
    return "/" + joined if joined else ""


def scan_file(path: Path, root: Path):
    lines = path.read_text(encoding="utf-8", errors="replace").splitlines()
    if not any(REST_CONTROLLER.search(line) for line in lines):
        return []
    class_name = ""
    base_path = ""
    for line in lines:
        match = CLASS_DECL.search(line)
        if match:
            class_name = match.group(1)
            break
    class_index = next(
        (index for index, line in enumerate(lines) if class_name and f"class {class_name}" in line), len(lines)
    )
    for index in range(class_index - 1, -1, -1):
        match = REQUEST_MAPPING.search(lines[index])
        if match:
            base_path = annotation_path(match.group(1))
            break
    rows = []
    for index, line in enumerate(lines):
        match = MAPPING.search(line)
        if not match:
            continue
        method_path = annotation_path(match.group(2))
        http = match.group(1).upper()
        handler = ""
        for probe in range(index + 1, min(index + 8, len(lines))):
            fake = METHOD_DECL.search(lines[probe])
            if fake:
                handler = fake.group(1)
                break
        rel = path.relative_to(root)
        rows.append((http, join_paths(base_path, method_path), f"{class_name}#{handler}", f"{rel}:{index + 1}"))
    return rows


def main() -> int:
    if len(sys.argv) < 3:
        print(__doc__)
        return 2
    root = Path(sys.argv[1])
    output = Path(sys.argv[2])
    keywords = sys.argv[3:]
    rows = []
    for path in sorted(root.rglob("*.java")):
        rel = str(path.relative_to(root))
        if keywords and not any(key in rel for key in keywords):
            continue
        for http, url, handler, location in scan_file(path, root):
            if not url or url == "/":
                continue
            rows.append((url, http, handler, location))
    rows.sort(key=lambda item: (item[0], item[1]))
    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open("w", encoding="utf-8") as handle:
        handle.write(f"# REST 接口清单：{root.name}\n\n")
        handle.write(f"- 控制器方法数：{len(rows)}\n")
        handle.write(f"- 生成脚本：`rest_inventory.py`（需人工核对）\n\n")
        handle.write("| 路径 | 方法 | 控制器#方法 | 定位 |\n|---|---|---|---|\n")
        for url, http, handler, location in rows:
            handle.write(f"| `{url}` | {http} | `{handler}` | `{location}` |\n")
    print(f"{root.name}: {len(rows)} endpoints -> {output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
