# Chrome 95 验收证据

- 时间：2026-07-18
- 浏览器：Chromium `95.0.4638.0`（Linux snapshot position `920005`）
- 视口：`1366x768`、`390x844`
- 产物：`LEGACY_BROWSER_BUILD=1` 的生产 `dist`
- 结果：Playwright `1 passed (5.2s)`；页面异常 `0`，失败请求 `0`

执行命令：

```bash
CHROME95_EXECUTABLE_PATH=/tmp/dts-chrome95-ttQJ8k/chrome-linux/chrome \
E2E_BASE_URL=http://127.0.0.1:4173 \
pnpm exec playwright test --config=playwright.chrome95.config.ts
```

覆盖指标卡快速双击防重、柱体、扇区、折线数据点、地图区域、表格行、重置、内部视图进入/返回、窄屏无横向溢出，以及 HTTP 500 下钻失败后保留面包屑并可重置。

截图：

- [桌面下钻态](desktop-1366x768-drill.png)
- [窄屏返回根态](narrow-390x844-root.png)

fixture 只使用 `key/value/name` 等中性字段；API 参数通过显式 `selectedKey={{selectedKey}}` 模板消费通用映射，不包含业务领域推断。
