#!/usr/bin/env python3
"""把 outline.md（目录框架）转成 正文.md（可填写的骨架）。

层级映射：
  `# 第X部分 …`   → `# `   （分篇标题，仅 08 使用）
  `## N 章节`      → `# `   （一级章节）
  `- N.N …`        → `## `  （二级）
  `  - N.N.N …`    → `### ` （三级）

首行文档标题与 `>` 说明块为 outline 自身的元信息，不进入正文。
"""
import argparse
import re


def convert(text: str) -> str:
    out = []
    for raw in text.split('\n'):
        ln = raw.rstrip()
        s = ln.strip()

        # 丢弃 outline 元信息
        if s.startswith('> ') or s == '>':
            continue
        if s.startswith('# ') and '目录框架' in s:
            continue
        if s == '---':
            continue

        # 分篇标题保持 H1
        if re.match(r'^# 第.+部分', s):
            out.append(s)
            continue
        # 一级章节
        if s.startswith('## '):
            out.append('# ' + s[3:].strip())
            continue
        # 列表项按缩进定级
        m = re.match(r'^(\s*)- (.*)$', ln)
        if m:
            indent = len(m.group(1))
            level = 2 if indent < 2 else 3
            out.append('#' * level + ' ' + m.group(2).strip())
            continue
        if not s:
            out.append('')
            continue
        out.append(s)

    # 压缩连续空行
    compact = []
    for ln in out:
        if ln == '' and compact and compact[-1] == '':
            continue
        compact.append(ln)
    return '\n'.join(compact).strip() + '\n'


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--src', required=True)
    ap.add_argument('--out', required=True)
    args = ap.parse_args()
    with open(args.src, encoding='utf-8') as fh:
        text = fh.read()
    result = convert(text)
    with open(args.out, 'w', encoding='utf-8') as fh:
        fh.write(result)
    heads = sum(1 for l in result.split('\n') if l.startswith('#'))
    print(f'  {args.out}  标题 {heads} 条')


if __name__ == '__main__':
    main()
