# 数据优先编辑流程验收证据

- 时间：2026-08-29
- 浏览器：Chromium `95.0.4638.0`（Linux snapshot position `920005`）
- 产物：`LEGACY_BROWSER_BUILD=1` 的生产 `dist`
- 数据：受控 Card fixture，2 个字段、3 行样例，不依赖业务系统

验收结果：

- PASS：右侧“数据”页签连续呈现“数据来源 → 样例校验 → 字段映射”。
- PASS：成功态每次加载仅调用一次既有 Card 查询，没有属性面板第二查询链路。
- PASS：样例行仅存在于编辑会话；持久化回调只接收 `_sourceColumns`。
- PASS：`1366×768` 与 `768×900` 均无文档级横向溢出，流程区和样例表可读。
- PASS：查询返回 HTTP 504 时设计器保持可用，流程区显示明确错误且不显示陈旧样例表。
- PASS：成功态无 console error、page error 或 request failure；错误态仅保留受控 504 资源日志。

执行命令：

```bash
CHROME95_EXECUTABLE_PATH=/tmp/dts-chrome95-data-workflow-qV3Us5/chrome-linux/chrome \
E2E_BASE_URL=http://127.0.0.1:4173 \
npx playwright test --config=playwright.screen-data-workflow.chrome95.config.ts
```

截图：

- [桌面成功态](editor-data-workflow-1366x768.png)
- [窄屏成功态](editor-data-workflow-768x900.png)
- [桌面错误态](editor-data-workflow-error-1366x768.png)
