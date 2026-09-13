# F6 Chrome95 与 source-contract 验收

**状态**: DONE  
**目标**: 使用 source-contract、生产构建和浏览器 smoke 证明页面不是空实现，并满足 Chrome95 约束。

## Tasks

| Task | 内容 | 状态 | 证据 |
|------|------|------|------|
| T01 | source-contract 覆盖路由、按钮、API、证据面 | DONE | `pnpm exec tsx --test ...` |
| T02 | Chrome95 兼容生产构建 | DONE | `pnpm build` with `LEGACY_BROWSER_BUILD=1` |
| T03 | 桌面 1366x768 浏览器 smoke | DONE | `it/screenshots/sql-modeling-mocked-desktop-1366x768.png` |
| T04 | 窄屏浏览器 smoke | DONE | `it/screenshots/sql-modeling-mocked-mobile-390x844.png` |
| T05 | 已知风险记录 | DONE | `it/README.md` |

## 约束检查

- 未引入 container queries、`:has()`、`toSorted`、`Object.groupBy`、复杂拖拽或现代浏览器专用 API。
- 字段表收敛为两列，状态合并到“数据元标准”列，避免 1366x768 右侧面板列挤压。
- SQL 建模页仍是桌面级工作台，窄屏只做 smoke，不作为移动端完整重构验收。
