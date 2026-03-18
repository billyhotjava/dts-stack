# BUG-001: Chrome 95 菜单选中色不可读

- **优先级**: P0
- **状态**: TODO

## 问题

三个 webapp 侧边栏菜单选中时背景色偏暗，文字看不清。

## 涉及文件

- `source/dts-admin-webapp/src/global.css` — OKLCH fallback（388-408 行）
- `source/dts-platform-webapp/src/global.css` — OKLCH fallback（417-438 行）
- `source/dts-platform-webapp/src/components/nav/styles/nav-item-classes.ts` — active 状态 class
- `source/dts-analytics-webapp/modern/` — 侧边栏样式

## 分析

Chrome 95 不支持 OKLCH（Chrome 111 才引入）。代码已有 `@supports` 的 sRGB fallback，但 fallback 颜色值可能对比度不足。需要：
1. 检查 sRGB fallback 颜色的 active 状态对比度
2. 调整 `--color-primary` 系列的 fallback 值，确保 WCAG AA（≥ 4.5:1）
3. 三个 webapp 统一修复

## 交付标准

- [ ] Chrome 95 下菜单选中文字清晰可读
- [ ] Chrome 120+ 不受影响
