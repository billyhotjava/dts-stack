# F9: 可登录浏览器验收与回归基线

**优先级**: P0
**状态**: READY

## 目标

把当前被登录守卫和 `platform.dts.local` DNS 阻断的 browser smoke 变成可重复执行的验证基线，覆盖桌面、窄屏和 Chrome 95 风险。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | 可登录 Playwright 环境与账号夹具 | P0 | READY | F1/T01 |
| T02 | 端到端旅程页面 smoke 脚本 | P0 | READY | T01 |
| T03 | Chrome 95 与窄屏视觉回归基线 | P0 | READY | T02 |

## 完成标准

- [ ] Playwright 能进入 `/workbench?journey=e2e-data-product` 而不是停在登录页。
- [ ] Browser smoke 覆盖工作台、数据源、标准、建模、指标、服务、运行证据。
- [ ] 1366x768 和窄屏下无按钮遮挡、文本溢出、关键区域空白。
- [ ] 登录、DNS、代理等环境 blocker 写入 IT 证据。
