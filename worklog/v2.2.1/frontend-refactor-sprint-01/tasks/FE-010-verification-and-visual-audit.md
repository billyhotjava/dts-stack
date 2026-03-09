# FE-010

## 标题

完成三端构建验证与视觉审计收口。

## 范围

- 三个 webapp 的构建验证
- sprint 状态板与任务卡收口
- 人工视觉核对清单

## 目标

- 确认三端全部可构建
- 确认客户可见路径不再出现假内容
- 确认三端看起来属于同一个产品

## 交付

- 最终构建结果
- 手工审计记录
- 状态板更新

## 当前结果

- 2026-03-09 构建验证通过：
  - `pnpm -C source/dts-platform-webapp build`
  - `pnpm -C source/dts-admin-webapp build`
  - `pnpm -C source/dts-analytics-webapp/modern build`
- `admin-webapp` 构建存在既有 vite chunk warning，但未阻塞产物生成
- sprint 状态板已更新

## 人工视觉核对清单

- `platform-webapp`
  - `workbench` 总览页与待办页
  - `foundation/task-scheduling`
  - `etl` 详情页、历史页、创建页
  - `modeling/dbt` 文件浏览页
- `admin-webapp`
  - `/admin/system`
  - `infra-settings`
  - `workflow-config`
  - `other-config`
- `analytics modern`
  - 常规控制台壳与侧栏
  - 大屏设计器 `ScreenDesigner`
  - 公开页 `PublicScreenPage`
  - 预览页 `ScreenPreviewPage`
  - 导出页 `ScreenExportPage`

## 残余风险

- 当前会话没有实际浏览器人工走查能力，视觉审计仍需人工打开页面确认
- `platform-webapp` 仍有 `FE-005` 未完全收口，因此 sprint 还不能标记为整体完成

## 验收

- `pnpm -C source/dts-platform-webapp build`
- `pnpm -C source/dts-admin-webapp build`
- `pnpm -C source/dts-analytics-webapp/modern build`
- sprint 文档补齐验证结果与残余风险

## 风险

- 仅靠 build 不能替代视觉走查，必须保留人工页面核对
