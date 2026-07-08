# T02: 前端构建与 Chrome 95 兼容

**优先级**: P0
**状态**: DONE
**依赖**: F1-T03,F2,F3

## 目标

确保低代码向导和指标联动实现不破坏前端构建、类型检查和客户 Chrome 95 兼容要求。

## 技术设计

- 禁用 Chrome 95 不支持的 CSS 和 JS 语法。
- 页面布局使用稳定尺寸和响应式约束，避免步骤卡、按钮、标签在窄屏挤压。
- 新 helper 必须有单测或 source-contract 覆盖。
- 不引入新的重型依赖。

## 影响范围

- `source/dts-platform-webapp`

## 验证

- [x] `pnpm exec tsc --noEmit`
- [x] `pnpm build`
- [x] Chrome 95 CSS/语法扫描。
- [x] `git diff --check`

## 完成标准

- [x] 构建与兼容性验证通过。

