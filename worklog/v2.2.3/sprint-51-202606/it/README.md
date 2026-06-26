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
