# 按钮图标统一

**日期**: 2026-04-05  
**文件**: admin-webapp 8 个文件 + platform-webapp 13 个文件  
**类型**: UI 一致性小修 — 无 API 变更，无新功能

---

## 问题

- `dts-admin-webapp` 的操作按钮（编辑/删除/详情）绝大多数没有图标
- `dts-platform-webapp` 内部部分页面有图标、部分没有，风格不统一
- `user-management.tsx` 和 `user-detail.tsx` 使用 Iconify 子组件方式，与其他页面的 antd `icon={}` prop 方式不一致

---

## 设计方案

**图标规范：**
- `编辑` → `icon={<EditOutlined />}`
- `删除` → `icon={<DeleteOutlined />}`
- `详情` / `查看详情` → `icon={<EyeOutlined />}`

**原则：**
- 只加 `icon={}` prop，不改变按钮的 `type`、`size`、`danger`、`onClick`、文字等
- 每个文件顶部确认 `@ant-design/icons` import 已包含所需图标，缺少则补上
- `user-management.tsx` 和 `user-detail.tsx`：移除 Iconify JSX children，改为 antd `icon={}` prop

---

## 变更文件清单

### Admin-webapp (`source/dts-admin-webapp/src/admin/views/`)

| 文件 | 按钮 |
|------|------|
| `role-management.tsx` | 详情、编辑、删除 |
| `user-management.tsx` | 编辑（Iconify→antd） |
| `user-detail.tsx` | 编辑（Iconify→antd） |
| `role-detail.tsx` | 编辑角色 |
| `my-changes.tsx` | 查看详情 |
| `org-management.tsx` | 编辑 |
| `workflow-config.tsx` | 删除 |
| `data-lake-config.tsx` | 编辑、删除 |

### Platform-webapp (`source/dts-platform-webapp/src/pages/`)

| 文件 | 按钮 |
|------|------|
| `modeling/ModelTemplatesPage.tsx` | 编辑、删除 |
| `workbench/index.tsx` | 编辑 |
| `governance/TemplatesPage.tsx` | 编辑、删除 |
| `governance/SubjectAreasPage.tsx` | 编辑域属性、删除域 |
| `services/DataProductsPage.tsx` | 详情 |
| `catalog/AssetDetailPage.tsx` | 详情 |
| `explore/etl/components/ExecutionHistoryTable.tsx` | 详情 |
| `workbench/WorkflowCenterPage.tsx` | 查看详情 |
| `governance/IndicatorsPage.tsx` | 编辑、删除 |
| `governance/GlossaryPage.tsx` | 编辑、删除（type=link） |
| `governance/ReferenceCodesPage.tsx` | 编辑、删除（type=link，多处） |
| `governance/ElementsPage.tsx` | 编辑、删除（type=link） |
| `governance/AssetOwnershipPage.tsx` | 编辑（type=link） |
