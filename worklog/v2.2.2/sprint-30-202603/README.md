# Sprint-30: BI 数据管理重构 — 删除数据湖概念，表格化管理

**时间**: 2026-03
**状态**: READY
**目标**: 重构 BI 分析的"数据管理"页面，删除独立数据库连接管理，复用平台数据源列表，上传 Excel/CSV 走快速分析路径

## 背景

当前 BI 分析的"数据管理"功能存在以下问题：
- 维护了独立的数据库连接导入流程（DatabaseNewPage Tab1），与平台 `infra_data_source` 重复
- "数据湖"概念暴露给用户，实际只是 `is_system=true` 的内置 PostgreSQL
- 卡片网格布局信息密度低，不适合管理场景
- `DatabaseNewPage` 是独立页面，上传需要跳转

## 重构目标

1. **删除**独立的数据库连接管理（导入平台数据源流程）
2. **复用**平台 `/api/infra/data-sources` 作为"数据湖列表"
3. **表格化**：单页面双表格布局（上方数据湖列表 + 下方我的上传）
4. **上传简化**：Excel/CSV 上传改为弹窗，目标库固定为内置数据湖 `upload` schema
5. **用户隔离**：每个用户只能看到自己上传的数据

## 页面结构

```
数据管理
├── 数据湖列表（表格，只读）
│   数据来源: GET /api/infra/data-sources (平台所有数据源)
│   列: 名称 | 类型 | 连接地址 | 状态 | 操作(同步元数据·查看表)
│   权限: 所有 BI 用户可见；"同步元数据"仅管理员
│
└── 我的上传（表格，当前用户）                [上传 Excel/CSV]
    数据来源: GET /bi/api/database/{dataLakeId}/my-uploads
    列: 表名 | 文件名 | 行数 | 列数 | 上传时间 | 操作(查看·删除)
    上传目标: 内置数据湖 is_system=true, schema=upload
    权限: 所有用户可上传，只能看到/删除自己的数据
```

## 上传弹窗流程

复用现有 `UploadedDataEditor` 组件：
1. 拖拽/选择文件（Excel/CSV，最大 50MB）
2. Excel 多 sheet 时选择 sheet
3. 配置表头行号
4. 编辑表名、列名、列类型（text/number/date/boolean）
5. 预览前 10 行
6. 确认上传 → POST /bi/api/database/{dataLakeId}/upload-table (schema=upload)

## 删除项

| 删除项 | 原位置 | 原因 |
|--------|--------|------|
| 卡片网格布局 | DataPage.tsx | 替换为表格 |
| "导入平台数据源"流程 | DatabaseNewPage.tsx Tab1 | 数据湖列表直接读平台 API |
| DatabaseNewPage.tsx | 路由 /bi/data/new | 上传改为弹窗 |
| DatabaseEditPage.tsx | 路由 /bi/data/:id/edit | 数据源由平台管理 |
| 数据库删除功能 | DataPage.tsx | 数据源由平台管理 |
| createDatabase / deleteDatabase 前端调用 | DataPage + DatabaseNewPage | 不再需要 |

## 保留项

| 保留项 | 来源 | 调整 |
|--------|------|------|
| UploadedDataEditor | components/UploadedDataEditor.tsx | 目标库固定数据湖，schema 固定 upload |
| uploadTable API | analyticsApi.ts | 不变 |
| listMyUploads / deleteUploadTable API | analyticsApi.ts | 不变 |
| listPlatformDataSources API | analyticsApi.ts | 不变 |
| DatabaseDetailPage | pages/DatabaseDetailPage.tsx | 保留（查看表结构） |
| TableDetailPage / FieldDetailPage | pages/ | 保留（查看字段） |

## 路由变更

| 路由 | 变更 |
|------|------|
| `/bi/data` | 重写为双表格页面 |
| `/bi/data/new` | **删除** |
| `/bi/data/:dbId/edit` | **删除** |
| `/bi/data/:dbId` | 保留 |
| `/bi/data/:dbId/tables/:tableId` | 保留 |
| `/bi/data/:dbId/tables/:tableId/fields/:fieldId` | 保留 |

## 后端变更

无新增接口，复用现有 API：
- `GET /api/infra/data-sources` — 平台数据源列表
- `GET /bi/api/database` — 获取 is_system 数据湖 ID
- `GET /bi/api/database/{id}/my-uploads` — 当前用户上传列表
- `POST /bi/api/database/{id}/upload-table` — 上传表
- `DELETE /bi/api/database/{id}/upload-table/{name}` — 删除上传表
- `POST /bi/api/database/{id}/sync_schema` — 同步元数据

## 权限模型

| 操作 | 权限要求 |
|------|----------|
| 查看数据湖列表 | 所有 BI 用户 |
| 同步元数据 | is_data_admin 或 is_superuser |
| 查看表详情 | 所有 BI 用户 |
| 上传 Excel/CSV | 所有用户 |
| 查看/删除自己的上传 | 上传者本人 |

## Feature 列表

| ID | Feature | Task 数 | 状态 |
|----|---------|---------|------|
| F1 | DataPage 重写为双表格 | 5 | READY |

## 完成标准

- [ ] DataPage 改为双表格布局（数据湖列表 + 我的上传）
- [ ] 数据湖列表读取平台 `/api/infra/data-sources`
- [ ] 上传弹窗复用 UploadedDataEditor，目标固定为数据湖 upload schema
- [ ] 用户只能看到/删除自己的上传
- [ ] DatabaseNewPage、DatabaseEditPage 已删除
- [ ] 路由 /bi/data/new、/bi/data/:id/edit 已移除
- [ ] 现有 DatabaseDetailPage / TableDetailPage / FieldDetailPage 正常工作
