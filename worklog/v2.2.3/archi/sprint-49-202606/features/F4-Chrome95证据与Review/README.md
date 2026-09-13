# F4: Chrome95 证据与 Review

**优先级**: P0
**状态**: DONE

## 目标

把 Sprint-49 的前端整改用 TDD、构建、浏览器截图和代码 review 形成可交付证据。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | source-contract 与构建验证 | P0 | DONE | F1 |
| T02 | Chrome95 风险扫描与截图 | P0 | DONE | T01 |
| T03 | 代码 review 与提交收口 | P0 | DONE | T02 |

## 完成说明

- Sprint-49 全量 source-contract 回归：42/42 pass。
- 生产构建通过：`pnpm build` 通过，保留既有 Browserslist 过期提示和 chunk size warning。
- Chrome95 风险扫描未命中生产代码中的 `:has()`、`dvh/svh/lvh`、container query、拖拽库、`structuredClone`、`ResizeObserver`、`bodyStyle`。
- 三条 Playwright smoke 均通过，并更新 1366x768 截图证据。
- 代码 review 无阻断问题；剩余风险为既有全局导航 `li` 嵌套 warning，非本 Sprint 修改范围。

## 验收证据

- 集成测试记录：`worklog/v2.2.3/sprint-49-202606/it/README.md`。
- Review 记录：`worklog/v2.2.3/sprint-49-202606/features/F4-Chrome95证据与Review/T03-代码review与提交收口.md`。
