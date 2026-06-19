# F1: 工程脚手架

**优先级**: P0
**状态**: READY

## 目标

在 `worklog/prototype/app` 立起与现网同栈的 React19+TS+Vite+AntD5 工程骨架，搬现网 legacy 工具链（`@vitejs/plugin-legacy` chrome>=95 + `legacy-css-fallbacks` postcss + browserslist），并验证构建产物在 Chrome 95 可加载。这是 F2/F3/F4 与后续全部 sprint 的承载面。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | 初始化 Vite+React19+TS+AntD5 工程骨架 | P0 | READY | - |
| T02 | 接入 legacy 工具链（plugin-legacy + postcss + browserslist + 别名） | P0 | READY | T01 |
| T03 | 构建与 Chrome 95 产物冒烟验证 | P0 | READY | T02 |

## 完成标准

- [ ] `pnpm dev` 启动开发服务器，渲染占位首页无报错。
- [ ] `pnpm build` 产出生产包，开启 legacy 构建（`chrome >= 95`）。
- [ ] browserslist 含 `Chrome >= 95`；`legacy-css-fallbacks` postcss 插件接入。
- [ ] 路径别名（`@/*` → `src/*`）在 TS 与 Vite 两侧一致可用。
- [ ] 构建产物在 Chrome 95（或等效 target 模拟）加载、首屏渲染、控制台无致命错误。
