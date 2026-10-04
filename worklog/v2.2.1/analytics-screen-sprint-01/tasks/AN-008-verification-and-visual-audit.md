# AN-008

## 标题

构建验证与人工走查收口。

## 范围

- `source/dts-analytics-webapp/modern`
- `worklog/v2.2.1/analytics-screen-sprint-01`

## 目标

- 在模板和交互增强完成后，完成构建验证和人工走查
- 明确哪些能力已交付，哪些仍然是 `v2`

## 验收

- `pnpm -C source/dts-analytics-webapp/modern build`
- 模板库可见 5 套新模板
- 预览态可验证过滤、下钻、上卷、动作入口
- 人工走查记录补回 `status-board.md`

## 走查清单

- 模板库分类、搜索、标签显示
- `QMS / PLM / HR / 财务 / 项目管理` 模板创建
- 项目管理模板的过滤、下钻、上卷、详情面板、动作入口
- 旧模板仍可创建与预览

## 当前进度

- 状态：`done`
- 已验证：
  - `pnpm -C source/dts-analytics-webapp/modern test src/pages/screens/renderers/shared/actionUtils.test.ts src/pages/screens/renderers/shared/chartUtils.test.ts`
    - `8/8` 通过
  - `pnpm -C source/dts-analytics-webapp/modern build`
    - `✓ built in 5.62s`
  - `pnpm -C source/dts-analytics-webapp/modern typecheck`
    - `tsc --noEmit` 通过
  - `git diff --check`
    - 通过
  - `DTS_WEB_E2E_WITH_ANALYTICS_DEV_SERVER=1 DTS_ANALYTICS_URL=http://127.0.0.1:19335/analytics/ pnpm -C tests/web-e2e exec playwright test specs/biz/analytics-screen-template-runtime.spec.ts --project=chromium`
    - 通过
- 补充说明：
  - Playwright smoke 已覆盖模板库 5 套内置模板可见、项目管理模板创建、发布、预览、过滤、行点击下钻、breadcrumb 上卷、详情面板关闭
  - 人工视觉走查清单仍保留给现场验收，但不再阻塞本 sprint 代码侧收口
