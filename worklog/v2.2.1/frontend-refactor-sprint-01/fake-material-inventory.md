# Fake Material Inventory

## 说明

本清单只记录本次 sprint 要处理的 `faker / demo / placeholder / dummy / mock` 材料。

处理原则：

- 客户不可见的纯 demo/dev 入口直接删除
- 客户可见但仍被真实路由依赖的内容，用真实空态或受限态替换
- 删除后必须同步修复路由、菜单、导入关系

## Platform Webapp

已处理：

- `source/dts-platform-webapp/src/layouts/components/notice.tsx`
- `source/dts-platform-webapp/src/pages/sys/others/kanban/task-utils.ts`
- `source/dts-platform-webapp/src/pages/sys/others/kanban/types.ts`
- `source/dts-platform-webapp/src/pages/sys/others/calendar/event-utils.ts`
- `source/dts-platform-webapp/src/pages/sys/others/calendar/styles.ts`
- `source/dts-platform-webapp/src/api/services/demoService.ts`
- `source/dts-platform-webapp/src/debug/register-koal-devtools.ts`
- `source/dts-platform-webapp/src/pages/common/FeaturePlaceholder.tsx`
- `source/dts-platform-webapp/src/pages/common/V3Placeholder.tsx`
- `source/dts-platform-webapp/src/pages/foundation/TaskSchedulingPage.tsx`

### `faker`

- `source/dts-platform-webapp/src/layouts/components/notice.tsx`
- `source/dts-platform-webapp/src/pages/sys/others/kanban/task-utils.ts`
- `source/dts-platform-webapp/src/pages/sys/others/calendar/event-utils.ts`

### `demo / placeholder / dummy`

- `source/dts-platform-webapp/src/api/services/demoService.ts`
- `source/dts-platform-webapp/src/api/services/iamService.ts`
- `source/dts-platform-webapp/src/debug/register-koal-devtools.ts`
- `source/dts-platform-webapp/src/api/services/koalPkiClient.ts`
- `source/dts-platform-webapp/src/pages/common/FeaturePlaceholder.tsx`
- `source/dts-platform-webapp/src/pages/common/V3Placeholder.tsx`
- `source/dts-platform-webapp/src/pages/foundation/TaskSchedulingPage.tsx`

## Admin Webapp

已处理：

- `source/dts-admin-webapp/src/layouts/components/notice.tsx`
- `source/dts-admin-webapp/src/pages/sys/others/kanban/kanban-column.tsx`
- `source/dts-admin-webapp/src/pages/sys/others/kanban/task-utils.ts`
- `source/dts-admin-webapp/src/pages/sys/others/kanban/index.tsx`
- `source/dts-admin-webapp/src/pages/sys/others/kanban/kanban-task.tsx`
- `source/dts-admin-webapp/src/pages/sys/others/kanban/task-detail.tsx`
- `source/dts-admin-webapp/src/pages/sys/others/kanban/types.ts`
- `source/dts-admin-webapp/src/pages/sys/others/calendar/event-utils.ts`
- `source/dts-admin-webapp/src/pages/sys/others/calendar/index.tsx`
- `source/dts-admin-webapp/src/pages/sys/others/calendar/calendar-event-form.tsx`
- `source/dts-admin-webapp/src/pages/sys/others/calendar/calendar-event.tsx`
- `source/dts-admin-webapp/src/pages/sys/others/calendar/calendar-header.tsx`
- `source/dts-admin-webapp/src/pages/sys/others/calendar/styles.ts`
- `source/dts-admin-webapp/src/pages/sys/others/permission/index.tsx`
- `source/dts-admin-webapp/src/pages/sys/others/permission/page-test.tsx`
- `source/dts-admin-webapp/src/api/services/demoService.ts`

### `faker`

- `source/dts-admin-webapp/src/layouts/components/notice.tsx`
- `source/dts-admin-webapp/src/pages/sys/others/kanban/kanban-column.tsx`
- `source/dts-admin-webapp/src/pages/sys/others/kanban/task-utils.ts`
- `source/dts-admin-webapp/src/pages/sys/others/kanban/index.tsx`
- `source/dts-admin-webapp/src/pages/sys/others/calendar/event-utils.ts`
- `source/dts-admin-webapp/src/pages/sys/others/calendar/index.tsx`
- `source/dts-admin-webapp/src/pages/sys/others/calendar/calendar-event-form.tsx`
- `source/dts-admin-webapp/src/pages/sys/others/permission/page-test.tsx`

### `demo / placeholder / dummy`

- `source/dts-admin-webapp/src/api/services/demoService.ts`
- `source/dts-admin-webapp/src/admin/views/infra-settings.tsx`
- `source/dts-admin-webapp/src/admin/views/workflow-config.tsx`
- `source/dts-admin-webapp/src/admin/views/system/other-config.tsx`

## Analytics Modern

### `demo / placeholder / dummy`

- `source/dts-analytics-webapp/modern/src/i18n.ts`
- `source/dts-analytics-webapp/modern/src/pages/screens/plugins/builtinPluginAdapters.tsx`
- `source/dts-analytics-webapp/modern/src/pages/screens/components/ScreenHealthPanel.tsx`
- `source/dts-analytics-webapp/modern/src/pages/screens/components/ComponentRenderer.tsx`

## 删除顺序建议

1. 先删 route/menu 不会依赖的纯 demo/dev 内容
2. 再处理被真实路由引用的假页面与占位卡片
3. 最后处理 analytics 运行时和插件侧的 demo 适配器
