# FE-001

## 标题

统一三端的浅色优先设计契约与 token 语义。

## 范围

- `source/dts-platform-webapp/src/global.css`
- `source/dts-admin-webapp/src/global.css`
- `source/dts-analytics-webapp/modern/src/styles/tokens.css`
- `source/dts-analytics-webapp/modern/src/styles.css`
- `source/dts-analytics-webapp/modern/src/layouts/layout.css`

## 目标

- 三端共享同一组 surface、text、border、status、chart、radius、spacing 语义
- 默认主题改为浅色优先
- dark theme 保留，但降为补充主题

## 交付

- 一份统一 token contract 落进三端现有样式入口
- analytics token 层与 platform/admin 变量语义对齐
- 后续壳层重构可以直接复用，不再二次定义视觉基线

## 验收

- `pnpm -C source/dts-platform-webapp build`
- `pnpm -C source/dts-admin-webapp build`
- `pnpm -C source/dts-analytics-webapp/modern build`
- 三端无缺失 CSS 变量或主题回退异常

## 当前进度

- 已完成：
  - `source/dts-platform-webapp/src/global.css`
  - `source/dts-admin-webapp/src/global.css`
  - `source/dts-analytics-webapp/modern/src/styles/tokens.css`
  - `source/dts-analytics-webapp/modern/src/styles.css`
  - `source/dts-analytics-webapp/modern/src/layouts/layout.css`
- 已验证：
  - `pnpm -C source/dts-platform-webapp build`
    - `✓ built in 29.46s`
  - `pnpm -C source/dts-admin-webapp build`
    - `✓ built in 34.98s`
  - `pnpm -C source/dts-analytics-webapp/modern install --frozen-lockfile`
    - 依赖已安装
  - `pnpm -C source/dts-analytics-webapp/modern build`
    - `✓ built in 5.11s`

## 风险

- 平台和 admin 共用壳层，变量名调整要避免一端可用、一端破坏
- analytics 已有 token 别名较多，迁移时要保留必要兼容映射
