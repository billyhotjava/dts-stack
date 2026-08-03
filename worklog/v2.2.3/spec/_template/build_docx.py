#!/usr/bin/env python3
"""Markdown → docx，复用参考 docx 的 styles/theme/fonts，仅重写 word/document.xml。

由 worklog/v2.2.3/s10/v4/governance/build-docx.py 参数化而来：
源文件、样式模板、输出路径改为命令行参数；新增封面与固定位置的目录域。

用法：
  python3 build_docx.py --src 正文.md --ref style-ref.docx --out dist/xxx.docx \
      --title "数据管理平台需求规格说明书" --subtitle "v2.2.3" --date 2026-08-03
"""
import argparse
import re
import zipfile


def esc(t: str) -> str:
    return t.replace('&', '&amp;').replace('<', '&lt;').replace('>', '&gt;')


def runs(text, mono=False, bold_all=False, sz=None):
    """inline 解析：**bold** 与 `code`，返回 <w:r> 串"""
    out = []
    parts = re.split(r'\*\*(.+?)\*\*', text)
    for i, seg in enumerate(parts):
        bold = (i % 2 == 1) or bold_all
        subs = re.split(r'`([^`]+)`', seg)
        for j, ss in enumerate(subs):
            if not ss:
                continue
            code = (j % 2 == 1)
            rpr = []
            if bold:
                rpr.append('<w:b/><w:bCs/>')
            if code or mono:
                rpr.append('<w:rFonts w:ascii="Courier New" w:hAnsi="Courier New" w:cs="Courier New"/>')
                if not sz:
                    rpr.append('<w:sz w:val="20"/><w:szCs w:val="20"/>')
            if sz:
                rpr.append(f'<w:sz w:val="{sz}"/><w:szCs w:val="{sz}"/>')
            rp = f'<w:rPr>{"".join(rpr)}</w:rPr>' if rpr else ''
            out.append(f'<w:r>{rp}<w:t xml:space="preserve">{esc(ss)}</w:t></w:r>')
    return ''.join(out)


def para(text, style=None, ppr_extra='', **kw):
    inner = (f'<w:pStyle w:val="{style}"/>' if style else '') + ppr_extra
    ppr = f'<w:pPr>{inner}</w:pPr>' if inner else ''
    return f'<w:p>{ppr}{runs(text, **kw)}</w:p>'


CELL_BORDER = ('<w:tcBorders>'
               '<w:top w:val="single" w:sz="6" w:space="0" w:color="9CA3AF"/>'
               '<w:left w:val="single" w:sz="6" w:space="0" w:color="9CA3AF"/>'
               '<w:bottom w:val="single" w:sz="6" w:space="0" w:color="9CA3AF"/>'
               '<w:right w:val="single" w:sz="6" w:space="0" w:color="9CA3AF"/>'
               '</w:tcBorders>')
CELL_MAR = ('<w:tcMar><w:top w:w="80" w:type="dxa"/><w:left w:w="100" w:type="dxa"/>'
            '<w:bottom w:w="80" w:type="dxa"/><w:right w:w="100" w:type="dxa"/></w:tcMar>')


def cell(text, header=False):
    shd = '<w:shd w:val="clear" w:color="auto" w:fill="E5E7EB"/>' if header else ''
    p = (f'<w:p><w:pPr><w:spacing w:before="0" w:after="0"/></w:pPr>'
         f'{runs(text, bold_all=header, sz=21)}</w:p>')
    return (f'<w:tc><w:tcPr><w:tcW w:w="0" w:type="auto"/>{CELL_BORDER}{shd}{CELL_MAR}'
            f'</w:tcPr>{p}</w:tc>')


def table(rows):
    has_header = any(c.strip() for c in rows[0])
    if not has_header:
        rows = rows[1:]
    trs = []
    for ri, r in enumerate(rows):
        tcs = ''.join(cell(c, header=(has_header and ri == 0)) for c in r)
        trs.append(f'<w:tr>{tcs}</w:tr>')
    return ('<w:tbl><w:tblPr><w:tblW w:w="5000" w:type="pct"/>'
            '<w:tblLayout w:type="autofit"/>'
            '<w:tblLook w:val="04A0" w:firstRow="1" w:lastRow="0" w:firstColumn="1" '
            'w:lastColumn="0" w:noHBand="0" w:noVBand="1"/></w:tblPr>'
            + ''.join(trs) + '</w:tbl><w:p/>')


def codeblock(lines):
    shd = '<w:shd w:val="clear" w:color="auto" w:fill="F3F4F6"/>'
    bd = ('<w:pBdr><w:top w:val="single" w:sz="4" w:color="D1D5DB"/>'
          '<w:left w:val="single" w:sz="4" w:color="D1D5DB"/>'
          '<w:bottom w:val="single" w:sz="4" w:color="D1D5DB"/>'
          '<w:right w:val="single" w:sz="4" w:color="D1D5DB"/></w:pBdr>')
    out = []
    for ln in lines:
        r = (f'<w:r><w:rPr><w:rFonts w:ascii="Courier New" w:hAnsi="Courier New" '
             f'w:eastAsia="SimSun"/><w:sz w:val="17"/><w:szCs w:val="17"/></w:rPr>'
             f'<w:t xml:space="preserve">{esc(ln) if ln else " "}</w:t></w:r>')
        out.append(f'<w:p><w:pPr>{bd}{shd}<w:spacing w:before="0" w:after="0"/>'
                   f'<w:ind w:left="200" w:right="200"/></w:pPr>{r}</w:p>')
    return ''.join(out)


def blockquote(text):
    bd = '<w:pBdr><w:left w:val="single" w:sz="18" w:color="6B7280"/></w:pBdr>'
    shd = '<w:shd w:val="clear" w:color="auto" w:fill="F9FAFB"/>'
    return (f'<w:p><w:pPr>{bd}{shd}<w:ind w:left="200"/>'
            f'<w:spacing w:before="120" w:after="120"/></w:pPr>{runs(text)}</w:p>')


def bullet(text, num=None):
    mark = f'{num} ' if num else '• '
    return (f'<w:p><w:pPr><w:ind w:left="420" w:hanging="220"/>'
            f'<w:spacing w:before="40" w:after="40"/></w:pPr>'
            f'<w:r><w:t xml:space="preserve">{mark}</w:t></w:r>{runs(text)}</w:p>')


def centered(text, sz, bold=False, before=0, after=0):
    rpr = ['<w:b/><w:bCs/>'] if bold else []
    rpr.append(f'<w:sz w:val="{sz}"/><w:szCs w:val="{sz}"/>')
    return (f'<w:p><w:pPr><w:jc w:val="center"/>'
            f'<w:spacing w:before="{before}" w:after="{after}"/></w:pPr>'
            f'<w:r><w:rPr>{"".join(rpr)}</w:rPr>'
            f'<w:t xml:space="preserve">{esc(text)}</w:t></w:r></w:p>')


def cover(title, subtitle, date, org):
    out = ['<w:p/>' * 6]
    out.append(centered(title, 56, bold=True, after=240))
    if subtitle:
        out.append(centered(subtitle, 32, after=120))
    out.append('<w:p/>' * 8)
    if org:
        out.append(centered(org, 28, after=80))
    if date:
        out.append(centered(date, 28))
    return ''.join(out)


# 「目录」标题不使用 Heading 样式，避免目录把自己也列进去
TOC = ('<w:p><w:pPr><w:pageBreakBefore/><w:jc w:val="center"/>'
       '<w:spacing w:before="240" w:after="360"/></w:pPr>'
       '<w:r><w:rPr><w:b/><w:bCs/><w:sz w:val="36"/><w:szCs w:val="36"/></w:rPr>'
       '<w:t>目录</w:t></w:r></w:p>'
       '<w:p><w:r><w:fldChar w:fldCharType="begin" w:dirty="true"/></w:r>'
       '<w:r><w:instrText xml:space="preserve"> TOC \\o "1-3" \\h \\z \\u </w:instrText></w:r>'
       '<w:r><w:fldChar w:fldCharType="separate"/></w:r>'
       '<w:r><w:t>（打开文档后如未显示目录，请全选后按 F9 更新域）</w:t></w:r>'
       '<w:r><w:fldChar w:fldCharType="end"/></w:r></w:p>')


def md_to_body(lines):
    body = []
    i = 0
    while i < len(lines):
        ln = lines[i]
        if ln.startswith('```'):
            block = []
            i += 1
            while i < len(lines) and not lines[i].startswith('```'):
                block.append(lines[i])
                i += 1
            body.append(codeblock(block))
            i += 1
            continue
        if ln.startswith('|') and i + 1 < len(lines) and re.match(r'^\|[\s\-:|]+\|?\s*$', lines[i + 1]):
            rows = [[c.strip() for c in ln.strip().strip('|').split('|')]]
            i += 2
            while i < len(lines) and lines[i].startswith('|'):
                rows.append([c.strip() for c in lines[i].strip().strip('|').split('|')])
                i += 1
            body.append(table(rows))
            continue
        if ln.startswith('# '):
            body.append(para(ln[2:].strip(), 'Heading1', ppr_extra='<w:pageBreakBefore/>'))
            i += 1
            continue
        if ln.startswith('## '):
            body.append(para(ln[3:].strip(), 'Heading2'))
            i += 1
            continue
        if ln.startswith('### '):
            body.append(para(ln[4:].strip(), 'Heading3'))
            i += 1
            continue
        if ln.startswith('#### '):
            body.append(para(ln[5:].strip(), 'Heading4'))
            i += 1
            continue
        if ln.startswith('> '):
            body.append(blockquote(ln[2:].strip()))
            i += 1
            continue
        if ln.startswith('- '):
            body.append(bullet(ln[2:].strip()))
            i += 1
            continue
        m = re.match(r'^(\d+)\.\s+(.*)$', ln)
        if m:
            body.append(bullet(m.group(2), num=m.group(1) + '.'))
            i += 1
            continue
        if ln.strip() in ('---', ''):
            i += 1
            continue
        body.append(para(ln.strip()))
        i += 1
    return body


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--src', required=True)
    ap.add_argument('--ref', required=True, help='样式模板 docx')
    ap.add_argument('--out', required=True)
    ap.add_argument('--title', default='')
    ap.add_argument('--subtitle', default='')
    ap.add_argument('--date', default='')
    ap.add_argument('--org', default='')
    ap.add_argument('--no-cover', action='store_true')
    ap.add_argument('--no-toc', action='store_true')
    args = ap.parse_args()

    with open(args.src, encoding='utf-8') as fh:
        lines = fh.read().split('\n')

    parts = []
    if not args.no_cover and args.title:
        parts.append(cover(args.title, args.subtitle, args.date, args.org))
    if not args.no_toc:
        parts.append(TOC)
    parts.extend(md_to_body(lines))

    with zipfile.ZipFile(args.ref) as refz:
        refdoc = refz.read('word/document.xml').decode('utf-8')
        settings = refz.read('word/settings.xml').decode('utf-8')

    root_open = refdoc[:refdoc.index('<w:body>') + len('<w:body>')]
    sectpr = re.search(r'<w:sectPr.*?</w:sectPr>', refdoc, re.S).group(0)
    newdoc = root_open + ''.join(parts) + sectpr + '</w:body></w:document>'

    if 'updateFields' not in settings:
        settings = re.sub(r'(<w:settings[^>]*>)', r'\1<w:updateFields w:val="true"/>', settings, 1)

    with zipfile.ZipFile(args.ref) as zin, \
            zipfile.ZipFile(args.out, 'w', zipfile.ZIP_DEFLATED) as zout:
        for item in zin.infolist():
            if item.filename == 'word/document.xml':
                zout.writestr(item, newdoc)
            elif item.filename == 'word/settings.xml':
                zout.writestr(item, settings)
            else:
                zout.writestr(item, zin.read(item.filename))

    print(f'  {args.out}  段落 {newdoc.count("<w:p>")}  表格 {newdoc.count("<w:tbl>")}')


if __name__ == '__main__':
    main()
