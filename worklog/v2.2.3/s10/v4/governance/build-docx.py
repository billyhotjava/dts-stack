#!/usr/bin/env python3
"""把 数据治理方案.md 按《数据管理平台数据模型v4.docx》的样式生成 docx。
做法：复用参考 docx 的 styles/theme/fonts，仅重写 word/document.xml。"""
import re, shutil, zipfile, sys, html

SRC_MD = '数据治理方案.md'
REF = '../数据管理平台数据模型v4.docx'  # 样式模板
OUT = '数据治理方案.docx'

def esc(t):
    return t.replace('&', '&amp;').replace('<', '&lt;').replace('>', '&gt;')

def runs(text, mono=False, bold_all=False, sz=None):
    """inline 解析：**bold** 与 `code`，返回 <w:r> 串"""
    out = []
    # 先按 ** 分段
    parts = re.split(r'\*\*(.+?)\*\*', text)
    for i, seg in enumerate(parts):
        bold = (i % 2 == 1) or bold_all
        # 段内再按 `code` 分
        subs = re.split(r'`([^`]+)`', seg)
        for j, ss in enumerate(subs):
            if not ss:
                continue
            code = (j % 2 == 1)
            rpr = []
            if bold: rpr.append('<w:b/><w:bCs/>')
            if code or mono:
                rpr.append('<w:rFonts w:ascii="Courier New" w:hAnsi="Courier New" w:cs="Courier New"/>')
                if not sz: rpr.append('<w:sz w:val="20"/><w:szCs w:val="20"/>')
            if sz: rpr.append(f'<w:sz w:val="{sz}"/><w:szCs w:val="{sz}"/>')
            rp = f'<w:rPr>{"".join(rpr)}</w:rPr>' if rpr else ''
            out.append(f'<w:r>{rp}<w:t xml:space="preserve">{esc(ss)}</w:t></w:r>')
    return ''.join(out)

def para(text, style=None, ppr_extra='', **kw):
    ppr = ''
    inner = (f'<w:pStyle w:val="{style}"/>' if style else '') + ppr_extra
    if inner:
        ppr = f'<w:pPr>{inner}</w:pPr>'
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
    for i, ln in enumerate(lines):
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

TOC = ('<w:p><w:pPr><w:pStyle w:val="Heading1"/><w:pageBreakBefore/></w:pPr>'
       '<w:r><w:t>目录</w:t></w:r></w:p>'
       '<w:p><w:r><w:fldChar w:fldCharType="begin" w:dirty="true"/></w:r>'
       '<w:r><w:instrText xml:space="preserve"> TOC \\o "1-2" \\h \\z \\u </w:instrText></w:r>'
       '<w:r><w:fldChar w:fldCharType="separate"/></w:r>'
       '<w:r><w:t>（打开文档后如未显示目录，请全选后按 F9 更新域）</w:t></w:r>'
       '<w:r><w:fldChar w:fldCharType="end"/></w:r></w:p>')

lines = open(SRC_MD, encoding='utf-8').read().split('\n')
body = []
i = 0
first_h1 = True
toc_inserted = False
while i < len(lines):
    ln = lines[i]
    if ln.startswith('```'):
        block = []
        i += 1
        while i < len(lines) and not lines[i].startswith('```'):
            block.append(lines[i]); i += 1
        body.append(codeblock(block)); i += 1; continue
    if ln.startswith('|') and i + 1 < len(lines) and re.match(r'^\|[\s\-:|]+\|?\s*$', lines[i+1]):
        rows = []
        hdr = [c.strip() for c in ln.strip().strip('|').split('|')]
        rows.append(hdr); i += 2
        while i < len(lines) and lines[i].startswith('|'):
            rows.append([c.strip() for c in lines[i].strip().strip('|').split('|')]); i += 1
        body.append(table(rows)); continue
    if ln.startswith('# '):
        t = ln[2:].strip()
        # 目录插在第一个“第一篇”之前
        if t.startswith('第一篇') and not toc_inserted:
            body.append(TOC); toc_inserted = True
        extra = '' if first_h1 else '<w:pageBreakBefore/>'
        body.append(para(t, 'Heading1', ppr_extra=extra)); first_h1 = False
        i += 1; continue
    if ln.startswith('## '):
        body.append(para(ln[3:].strip(), 'Heading2')); i += 1; continue
    if ln.startswith('### '):
        body.append(para(ln[4:].strip(), 'Heading3')); i += 1; continue
    if ln.startswith('#### '):
        body.append(para(ln[5:].strip(), 'Heading4')); i += 1; continue
    if ln.startswith('> '):
        body.append(blockquote(ln[2:].strip())); i += 1; continue
    if ln.startswith('- '):
        body.append(bullet(ln[2:].strip())); i += 1; continue
    m = re.match(r'^(\d+)\.\s+(.*)$', ln)
    if m:
        body.append(bullet(m.group(2), num=m.group(1) + '.')); i += 1; continue
    if ln.strip() == '---' or ln.strip() == '':
        i += 1; continue
    body.append(para(ln.strip())); i += 1

# 取参考文档的 document 根标签与 sectPr
refz = zipfile.ZipFile(REF)
refdoc = refz.read('word/document.xml').decode('utf-8')
root_open = refdoc[:refdoc.index('<w:body>') + len('<w:body>')]
sectpr = re.search(r'<w:sectPr.*?</w:sectPr>', refdoc, re.S).group(0)
newdoc = root_open + ''.join(body) + sectpr + '</w:body></w:document>'

# settings.xml 注入 updateFields（打开时自动刷新目录）
settings = refz.read('word/settings.xml').decode('utf-8')
if 'updateFields' not in settings:
    settings = settings.replace('<w:settings ', '<w:settings ', 1)
    settings = re.sub(r'(<w:settings[^>]*>)', r'\1<w:updateFields w:val="true"/>', settings, 1)

shutil.copy(REF, OUT)
import os
# 重建 zip：复制除 document.xml/settings.xml 外的所有条目
with zipfile.ZipFile(REF) as zin, zipfile.ZipFile(OUT, 'w', zipfile.ZIP_DEFLATED) as zout:
    for item in zin.infolist():
        if item.filename == 'word/document.xml':
            zout.writestr(item, newdoc)
        elif item.filename == 'word/settings.xml':
            zout.writestr(item, settings)
        else:
            zout.writestr(item, zin.read(item.filename))
print('OK paragraphs:', newdoc.count('<w:p>'), 'tables:', newdoc.count('<w:tbl>'), 'bytes:', len(newdoc))
