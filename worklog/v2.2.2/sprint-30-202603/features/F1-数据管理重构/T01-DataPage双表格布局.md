# T01: 重写 DataPage 为双表格布局

**状态**: READY
**文件**: `source/dts-platform-webapp/src/analytics/pages/DataPage.tsx`

## 目标

将现有卡片网格 DataPage 重写为单页面双表格：
1. 上方：数据湖列表（平台数据源，只读）
2. 下方：我的上传（当前用户 Excel/CSV 上传记录）

## 数据湖列表表格

**数据源**: `analyticsApi.listPlatformDataSources()` → `GET /bi/api/platform/data-sources`

**列定义**:
| 列 | 字段 | 说明 |
|----|------|------|
| 名称 | name | 数据源名称 |
| 类型 | type | PostgreSQL/MySQL/Oracle/DM 等 |
| 连接地址 | jdbcUrl | JDBC URL，过长截断 |
| 状态 | status | Tag: 已连接(绿)/未连接(灰) |
| 操作 | - | 同步元数据(仅管理员) · 查看表 |

**操作**:
- 同步元数据: 先通过 `analyticsApi.listDatabases()` 找到对应 analytics 库 ID，调用 `syncDatabaseSchema(dbId)`
- 查看表: 跳转 `/bi/data/{dbId}`（analytics 内部 DB ID）

## 我的上传表格

**数据源**: 先从 `analyticsApi.listDatabases()` 找 `is_system=true` 的数据湖 ID，再调 `analyticsApi.listMyUploads(dataLakeId)`

**列定义**:
| 列 | 字段 | 说明 |
|----|------|------|
| 表名 | tableName | 自动生成或用户自定义 |
| 文件名 | fileName | 原始上传文件名 |
| 行数 | rowCount | 数据行数 |
| 列数 | columnCount | 列数 |
| 上传时间 | createdAt | 格式 YYYY/MM/DD HH:mm |
| 操作 | - | 查看 · 删除 |

**操作**:
- 查看: 跳转表详情页
- 删除: `analyticsApi.deleteUploadTable(dataLakeId, tableName)`，confirm 后刷新列表
- 上传按钮: 区域标题右侧 [上传 Excel/CSV]，点击打开上传弹窗（T02）

## 实现要点

- 使用 antd `<Table>` 组件
- 两个表格各自独立 loading 状态
- 数据湖列表搜索：按名称/类型过滤
- 我的上传搜索：按表名/文件名过滤
- 空状态：分别显示"暂无数据源"/"暂无上传数据"
