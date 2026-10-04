#!/usr/bin/env python3
"""Java 接口/抽象类/实现关系清点脚本（只读扫描，输出 Markdown）。

用法:
    python3 interface_inventory.py <service-src-root> ... --output <output.md>
"""

import re
import sys
from collections import defaultdict
from pathlib import Path

PACKAGE = re.compile(r"^package\s+([\w\.]+);", re.M)
IFACE = re.compile(r"public\s+interface\s+(\w+)(?:\s+extends\s+([\w,\s\.]+))?")
ABSTRACT = re.compile(r"public\s+abstract\s+class\s+(\w+)(?:\s+extends\s+([\w\.]+))?(?:\s+implements\s+([\w,\s\.]+))?")
CLASS = re.compile(r"public\s+(?:final\s+)?class\s+(\w+)(?:\s+extends\s+([\w\.]+))?(?:\s+implements\s+([\w,\s\.]+))?")

NAME = re.compile(r"(\w+)$")


def type_name(raw: str) -> str:
    raw = raw.strip()
    match = NAME.search(raw)
    return match.group(1) if match else raw


def collect(root: Path):
    interfaces = {}
    implementors = defaultdict(list)
    subclasses = defaultdict(list)
    for path in sorted(root.rglob("*.java")):
        text = path.read_text(encoding="utf-8", errors="replace")
        rel = str(path.relative_to(root))
        package = PACKAGE.search(text)
        package_name = package.group(1) if package else ""
        for match in IFACE.finditer(text):
            name, parents = match.group(1), match.group(2) or ""
            interfaces[name] = {
                "file": rel,
                "package": package_name,
                "extends": [type_name(item) for item in parents.split(",") if item.strip()],
            }
        for match in ABSTRACT.finditer(text):
            name, parent, impls = match.group(1), match.group(2), match.group(3) or ""
            if parent:
                subclasses[type_name(parent)].append((name, rel))
            for item in impls.split(","):
                if item.strip():
                    implementors[type_name(item)].append((name, rel))
        for match in CLASS.finditer(text):
            name, parent, impls = match.group(1), match.group(2), match.group(3) or ""
            if parent:
                subclasses[type_name(parent)].append((name, rel))
            for item in impls.split(","):
                if item.strip():
                    implementors[type_name(item)].append((name, rel))
    return interfaces, implementors, subclasses


DENY = {
    "Serializable",
    "HealthIndicator",
    "Comparable",
    "Runnable",
    "ApplicationRunner",
    "CommandLineRunner",
    "WebMvcConfigurer",
    "HandlerInterceptor",
}


def label_for(root: Path) -> str:
    return root.parts[-4] if len(root.parts) >= 4 else root.name


def main() -> int:
    argv = sys.argv[1:]
    if "--output" not in argv or len(argv) < 3:
        print(__doc__)
        return 2
    output = Path(argv[argv.index("--output") + 1])
    roots = [Path(item) for item in argv[: argv.index("--output")]]
    lines = ["# 接口 / 抽象类 / 实现关系清单", "", f"- 服务根：{[str(root) for root in roots]}", ""]
    for root in roots:
        interfaces, implementors, subclasses = collect(root)
        multi = sorted(
            (
                (name, impls)
                for name, impls in implementors.items()
                if len(impls) >= 2 and name not in DENY
            ),
            key=lambda item: (-len(item[1]), item[0]),
        )
        single = sorted(
            ((name, impls) for name, impls in implementors.items() if len(impls) == 1),
            key=lambda item: item[0],
        )
        abstracts = sorted((name, subs) for name, subs in subclasses.items() if name and name[0].isupper())
        lines.append(f"## {label_for(root)}")
        lines.append("")
        lines.append(f"- 接口数：{len(interfaces)}；多实现接口：{len(multi)}；单实现接口：{len(single)}")
        lines.append(f"- 出现父类/被继承的类型：{len(abstracts)}")
        lines.append("")
        lines.append("### 多实现接口")
        lines.append("")
        lines.append("| 接口 | 实现类 | 定位 |")
        lines.append("|---|---|---|")
        for name, impls in multi:
            impl_cells = "<br>".join(impl for impl, _ in impls)
            loc_cells = "<br>".join(f"`{loc}`" for _, loc in impls)
            lines.append(f"| `{name}` | {impl_cells} | {loc_cells} |")
        lines.append("")
        lines.append("### 抽象类 / 被继承类")
        lines.append("")
        lines.append("| 父类型 | 子类 | 定位 |")
        lines.append("|---|---|---|")
        for name, subs in abstracts:
            if len(subs) > 20:
                lines.append(f"| `{name}` | 共 {len(subs)} 个子类（JPA 实体公共基类，不逐条展开） | — |")
                continue
            sub_cells = "<br>".join(sub for sub, _ in subs)
            loc_cells = "<br>".join(f"`{loc}`" for _, loc in subs)
            lines.append(f"| `{name}` | {sub_cells} | {loc_cells} |")
        lines.append("")
        lines.append("### 单实现接口（清单）")
        lines.append("")
        lines.append("| 接口 | 实现 | 定位 |")
        lines.append("|---|---|---|")
        for name, impls in single:
            impl, loc = impls[0]
            lines.append(f"| `{name}` | `{impl}` | `{loc}` |")
        lines.append("")
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text("\n".join(lines), encoding="utf-8")
    print(f"written {output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
