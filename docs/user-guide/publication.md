# Word/PDF 出版与校验说明

**用途**：供文档维护人员生成发布文件。最终用户无需阅读本文件。

## 单一内容源

发布版正文按以下顺序合并：

1. `README.md`：手册入口、状态判读和版本边界；
2. `getting-started.md`：第一次完成数据建设任务；
3. `stage-guide.md`：九阶段任务指南。

`screenshot-register.md` 是维护清单，不进入面向用户的 Word/PDF。

## 构建依赖

- Pandoc 3.x；
- LibreOffice；
- `Noto Sans CJK SC` 或其他可用简体中文字体；
- Poppler 的 `pdfinfo`、`pdftotext` 用于校验。

构建过程必须在本地完成，不把手册、截图或业务数据上传到第三方转换服务。

## 生成 Word

在 `docs/user-guide/` 目录执行：

```bash
mkdir -p dist

sed '1s/^# .*/# 使用说明/' README.md | pandoc \
  - \
  getting-started.md \
  stage-guide.md \
  --from=markdown \
  --standalone \
  --toc \
  --toc-depth=2 \
  --number-sections \
  --resource-path=. \
  --metadata lang="zh-Hans" \
  --metadata title="DTS 用户操作手册" \
  --metadata subtitle="v2.2.3 · 数据建设九阶段" \
  --metadata date="2026-07-27" \
  --output="dist/DTS用户操作手册-v2.2.3.docx"
```

## 生成 PDF

先用 Word 或 LibreOffice 打开生成的 DOCX：

1. 将页面设为 A4，四边页边距设为 20 mm；
2. 把目录标题设为“目录”；
3. 更新全部目录和字段；
4. 把表格行设置为不允许跨页拆分；
5. 保存 DOCX；
6. 从该 DOCX 导出 `dist/DTS用户操作手册-v2.2.3.pdf`。

不应从旧 PDF 或另一份手工编辑的 Word 反向生成，避免两种交付格式内容漂移。

正文采用 Pandoc Markdown，可在特别高的纵向截图后使用 `{width=11cm}` 一类图片尺寸属性，避免整张图片被推到下一页后留下大块空白。

## 发布前校验

```bash
unzip -t "dist/DTS用户操作手册-v2.2.3.docx"
pdfinfo "dist/DTS用户操作手册-v2.2.3.pdf"
pdftotext "dist/DTS用户操作手册-v2.2.3.pdf" -
```

至少人工核对：

- 封面标题、版本和日期正确；
- 目录存在，章节顺序正确；
- 中文没有方框或乱码；
- 表格没有超出页面；
- 截图清晰，不含口令、Token、连接串或真实个人信息；
- “已完成”“有阻塞”“证据未知”“证据已过期”的含义与当前产品一致；
- 第 6、7、9 阶段没有在真实证据闭合前使用“已完成”截图；
- Word 和 PDF 的章节、截图和版本号一致。

## 版本规则

- 页面文案或按钮变化：更新正文并重拍对应截图；
- 完成条件变化：先更新九阶段任务指南，再生成发布版；
- 大版本变化：复制新的版本目录或调整文件名，不覆盖仍需交付的历史版本；
- 生成文件不作为手工编辑源，所有修改必须先回到 Markdown。
