#!/usr/bin/env python3
"""docx → pdf，导出前刷新目录域，使 PDF 中的目录带真实页码。

`soffice --headless --convert-to pdf` 不会展开 TOC 域，导出的 PDF 目录页只有占位文字。
本脚本通过 UNO 打开文档、refresh 索引后再导出。

用法：
  python3 docx_to_pdf.py a.docx b.docx ...
"""
import os
import subprocess
import sys
import time

import uno
from com.sun.star.beans import PropertyValue

PORT = 2202
CONN = f'socket,host=127.0.0.1,port={PORT};urp;StarOffice.ComponentContext'


def ensure_soffice():
    ctx_local = uno.getComponentContext()
    resolver = ctx_local.ServiceManager.createInstanceWithContext(
        'com.sun.star.bridge.UnoUrlResolver', ctx_local)
    for attempt in range(30):
        try:
            return resolver.resolve('uno:' + CONN)
        except Exception:
            if attempt == 0:
                subprocess.Popen([
                    'soffice', '--headless', '--invisible', '--nologo',
                    '--nodefault', '--norestore',
                    f'--accept={CONN}',
                ], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            time.sleep(1)
    raise RuntimeError('无法连接 soffice')


def pv(name, value):
    p = PropertyValue()
    p.Name = name
    p.Value = value
    return p


def convert(desktop, src):
    src = os.path.abspath(src)
    out = os.path.splitext(src)[0] + '.pdf'
    doc = desktop.loadComponentFromURL(
        uno.systemPathToFileUrl(src), '_blank', 0,
        (pv('Hidden', True), pv('ReadOnly', False), pv('UpdateDocMode', 3)))
    try:
        # 刷新目录 / 索引域
        try:
            idx = doc.getDocumentIndexes()
            for i in range(idx.getCount()):
                idx.getByIndex(i).update()
        except Exception:
            pass
        try:
            doc.refresh()
        except Exception:
            pass
        doc.storeToURL(uno.systemPathToFileUrl(out),
                       (pv('FilterName', 'writer_pdf_Export'),))
    finally:
        doc.close(False)
    return out


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        sys.exit(1)
    ctx = ensure_soffice()
    desktop = ctx.ServiceManager.createInstanceWithContext(
        'com.sun.star.frame.Desktop', ctx)
    for src in sys.argv[1:]:
        out = convert(desktop, src)
        print(f'  {out}')


if __name__ == '__main__':
    main()
