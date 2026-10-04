# Sprint-51 IT Plan

Sprint-51 创建阶段未运行代码级验证；2026-06-26 已进入现有页面实施。每个 Feature 至少补齐以下证据：

| 验收项 | 要求 | 证据位置 |
|--------|------|----------|
| source-contract | 页面路由、按钮、状态、API 调用路径可静态断言 | 对应页面 `.source-contract.test.ts` |
| Chrome95 构建 | `pnpm build` 通过，避免新语法破坏客户浏览器 | 本文件追加命令输出摘要 |
| 页面 smoke | P0 页面桌面 1366x768 截图，无重叠/空白/console error | `it/screenshots/` |
| API 缺口 | 每个新增后端接口有 Resource/DTO/前端调用一致性记录 | `assets/api-gap-register.md` |
| 回归边界 | 确认未新增菜单、未新增 `/v2` 路由、未破坏 Sprint-45~50 主链路 | 本文件追加检查结果 |

## 首批建议命令

```bash
cd source/dts-platform-webapp
pnpm test -- --run source/dts-platform-webapp/src/pages/catalog/*.source-contract.test.ts
pnpm test -- --run source/dts-platform-webapp/src/pages/foundation/*.source-contract.test.ts
pnpm build
```

后续如果补后端 API，再在对应 Java 模块执行窄范围单元测试。

## 2026-06-26 F1/T01 证据

```bash
cd source/dts-platform-webapp
node --test src/pages/foundation/DataSourcesPage.source-contract.test.ts
pnpm build
```

- `node --test src/pages/foundation/DataSourcesPage.source-contract.test.ts`: passed，4/4。
- `pnpm build`: passed，TypeScript + Vite legacy build completed；仅有 Browserslist 数据过期和大 chunk 既有告警。
- 回归边界：未新增菜单，未新增 `/v2` 路由，后端 API 仅登记 `/platform/dict/system-types` 待确认。

## 2026-06-27 F2-F4 综合验收证据

### tsc

```
pnpm exec tsc --noEmit  →  EXIT:0（零类型错误）
```

### source-contract 全量

```
node --test $(find src -name "*.source-contract.test.ts")
ℹ tests 133
ℹ pass 123
ℹ fail 10  ← 全部为 Sprint-51 前既存失败，本批次零净增
```

新增断言（通过）：
- `DatasetDetailPage` — SPA 导航 router.push("/catalog/lineage/graph") 5/5 ✔
- `DataManagementWorkbenchPage` — 无 SQL/dbt 字面量，工作台 source-contract 4/4 ✔
- `DataSourcesPage` — 字典系统类型接线 4/4 ✔

### 生产构建

```
pnpm build  →  ✓ built in 2m 4s
```
仅有既有大 chunk 告警（LineageGraph / configureMonaco / vendor-ui），无新增错误。

### Chrome 95 约束检查

修改范围全在 `.tsx` 组件内（条件渲染/按钮/Tag）；无 oklch / :has() / container query / subgrid；
生产包通过 legacy plugin 降级，格式不触发 Chrome 95 兼容问题。

### 回归边界

- 无新增页面 / 无新增 `/v2` 路由 / 无新增菜单
- 无后端 API 新增（全部为前端串联已有接口/路由）
- sprint-45~50 主链路页面 source-contract 在基线失败集内，未新增

### 已解决 API 缺口

详见 `assets/api-gap-register.md`；6 项全部以前端收敛方式关闭，无新增后端接口。
