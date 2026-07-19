# Sprint-67 Chrome 95 浏览器证据

验证时间：2026-07-19 15:26 +08:00
浏览器：`Chromium 95.0.4638.0`
视口：1366x768 和 390x844
结果：5 passed, 0 failed

## 命令

```bash
cd source/dts-platform-webapp
pnpm preview --host 127.0.0.1 --port 4173

CHROME95_EXECUTABLE_PATH=/tmp/dts-chrome95-ttQJ8k/chrome-linux/chrome \
  E2E_BASE_URL=http://127.0.0.1:4173 \
  pnpm exec playwright test --config=playwright.sprint67.chrome95.config.ts
```

## 证据图

- `desktop-business-first-form.png`：业务目标起点的条件表单。
- `desktop-business-first.png`：服务端 nextAction 进入业务范围 Tab。
- `desktop-asset-first-form.png`：现有数据起点与初始来源。
- `desktop-asset-first.png`：服务端 nextAction 进入来源盘点 Tab。
- `desktop-recovery.png`：exact plan 在失败重试、返回和刷新后仍保持。
- `narrow-recovery.png`：390x844 窄屏，无 document/body 水平溢出。
- `model-detail-standards.png`：服务端模型上下文下的“模型设计 / 字段设计 / 字段标准”三 Tab，以及只读字段标准版本证据。
- `model-detail-standards-narrow.png`：模型详情在 390x844 下保持可用且无 document/body 水平溢出。
- `dimension-catalog-entry-desktop.png`：规划页按“维度目录 → 模型中心”展示入口，点击后精确透传 planId 并进入 canonical 维度目录。
- `dimension-catalog-entry-narrow.png`：维度目录在 390x844 下无 document/body 水平溢出。

## 边界

该套件在当前 production bundle 和真实 Chrome 95 中运行，但 WarehousePlan/ModelSpec API 由 Playwright 精确拦截。它证明 UI、路由、错误恢复、响应式与浏览器兼容性，不声称 live backend/live auth/权限 E2E。

故意注入的失败只有：

- `GET /api/modeling/warehouse-plans` -> 503，随后列表独立重试成功；
- `GET /api/modeling/warehouse-plans/plan-exact/baseline` -> 503，随后证据独立重试成功。

除上述预期 503 外，断言为 0 pageerror、0 传输失败、0 非预期 HTTP >=400 与 0 非预期 console.error。
