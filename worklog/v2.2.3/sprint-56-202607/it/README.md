# Sprint-56 集成测试计划

**状态**: DONE  
**目标**: 证明指标工作台拖拽建模闭环在代码、构建和浏览器层均可验收。

## 验证项

| 项目 | 命令/方式 | 预期结果 |
|------|-----------|----------|
| Helper 单测 | `pnpm exec vitest run src/pages/modeling/metric-workbench/metricCanvas.helpers.test.ts` | 6/6 通过 |
| 指标工作台契约 | `node --test src/pages/modeling/metricWorkbench.source-contract.test.ts` | 拖拽源、未绑定分组和旧约束通过 |
| TypeScript | `pnpm exec tsc --noEmit` | 0 errors |
| 生产构建 | `pnpm build` | Chrome95 legacy build 成功 |
| 浏览器 smoke | Vite preview + Playwright mock | 桌面/窄屏页面正常 |

## 证据记录

### 2026-07-01 静态与构建验证

- `pnpm exec vitest run src/pages/modeling/metric-workbench/metricCanvas.helpers.test.ts`
  - 结果: PASS, 6/6
- `node --test src/pages/modeling/metricWorkbench.source-contract.test.ts`
  - 结果: PASS, 9/9
- `pnpm exec tsc --noEmit`
  - 结果: PASS, 0 errors
- `node --test src/pages/modeling/metricWorkbench.source-contract.test.ts src/routes/sections/dashboard/metricsServiceEmbedding.source-contract.test.ts src/routes/sections/dashboard/portalGoldenLineMenu.source-contract.test.ts`
  - 结果: PASS, 17/17
- `pnpm build`
  - 结果: PASS
  - 备注: 仅出现既有 Vite chunk size warning 和 Browserslist 数据过期提示。

### 2026-07-01 浏览器 smoke

- `pnpm preview --host 0.0.0.0 --port 4173`
  - 结果: PASS
  - 地址: `http://127.0.0.1:4173/#/modeling/metric-workbench`
- Playwright 1366x768 mock smoke
  - 结果: PASS
  - 截图: `assets/metric-workbench-dnd-1366.png`
  - 断言: `metric-workbench-page=true`, React Flow nodes `4`, edges `1`, draggable metrics `成交金额ACTIVE / 订单数DRAFT`, 未绑定分组存在。
- Playwright 拖拽绑定 smoke
  - 结果: PASS
  - 操作: 拖拽未绑定指标“订单数”到业务对象“客户”节点。
  - 断言: 发出 `PUT /api/semantic/metrics/metric-2`，payload 包含 `objectId=object-2`, `code=ORDER_COUNT`, `name=订单数`, `formulaType=aggregation/count_distinct`, `status=DRAFT`。
- Playwright 390x844 mock smoke
  - 结果: PASS
  - 截图: `assets/metric-workbench-dnd-390.png`
  - 断言: body width `390`, React Flow nodes `4`, edges `1`, 未绑定分组存在，无 console/pageerror。
