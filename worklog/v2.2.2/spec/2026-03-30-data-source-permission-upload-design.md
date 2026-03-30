# 数据菜单权限与 CSV 上传功能设计

> 日期: 2026-03-30
> 状态: 已确认
> 范围: dts-analytics + dts-analytics-webapp

## 背景

当前"数据"菜单的平台数据源导入功能仅限 superuser（ROLE_OP_ADMIN），普通用户和数据管理员均无权访问。同时"其他数据源"（CSV/Excel 上传）功能缺少用户隔离，上传表在 biadmin schema 下无法区分归属。

## 目标

1. 引入三级权限体系：数据管理员 / 普通用户 / 未认证
2. 内置数据湖不可删改（已实现）
3. 开放 CSV 上传给所有用户，表按用户名隔离
4. 上传表 schema 从 biadmin 改为 upload（已实现）

---

## 一、权限模型

### 三级权限

| 权限级别 | 角色 | 能力 |
|---------|------|------|
| 数据管理员 (dataAdmin) | ROLE_OP_ADMIN, ROLE_INST_DATA_OWNER, ROLE_INST_LEADER | 导入/删除/修改平台数据源、同步 schema、管理数据库 |
| 普通用户 (user) | 所有认证用户 | 上传 CSV、删除自己的上传表、查看数据库列表和 metadata、使用数据做分析/大屏 |
| 未认证 | - | 无权访问 |

### 端点权限

| 端点 | 方法 | 权限 |
|------|------|------|
| `/api/platform/data-sources` | GET | dataAdmin |
| `/api/database` | POST | dataAdmin |
| `/api/database/{id}` | PUT | dataAdmin |
| `/api/database/{id}` | DELETE | dataAdmin |
| `/api/database/{id}/sync_schema` | POST | dataAdmin |
| `/api/database/{id}/upload-table` | POST | user |
| `/api/database` | GET | user |
| `/api/database/{id}` | GET | user |
| `/api/database/{id}/metadata` | GET | user |
| `/api/database/{id}/my-uploads` | GET | user |
| `/api/database/{id}/upload-table/{tableName}` | DELETE | user (仅自己的) |
| `/api/user/current` | GET | user |

### 实现：MetabaseAuth.requireDataAdmin()

在 `MetabaseAuth` 中新增方法：

```java
static final Set<String> DATA_ADMIN_ROLES = Set.of(
    "ROLE_OP_ADMIN", "ROLE_INST_DATA_OWNER", "ROLE_INST_LEADER"
);
```

通过 `X-DTS-Roles` 请求头解析角色列表，判断是否包含任一管理角色。不改 AnalyticsUser 实体，不改数据库表。

---

## 二、CSV 上传与表命名

### 表命名规则

```
upload.upload_{username}_{yyyyMMdd}_{sanitized_name}
```

- username: 来自 X-DTS-User 头，做 sanitize（小写，去特殊字符）
- 示例: `upload.upload_test230911_20260330_销售数据`

### 用户隔离

- **列表**: 按 `upload_{currentUsername}_` 前缀过滤，只返回当前用户的表
- **删除**: 只允许删除以自己用户名为前缀的表
- **读取**: 不限制。大屏/分析卡片可以 SQL 查询任何 upload 表

### 上传目标

固定为内置数据湖，不需要用户选择目标数据库。前端去掉"选择目标数据库"下拉框。

### 删除行为

用户删除上传表后，引用该表的大屏组件变成无效组件（失去数据链接），不做级联保护。

---

## 三、前端交互

### DataPage（数据列表页）

- 内置数据湖显示"内置"标签，无删除按钮（已实现）
- 普通用户不显示"导入平台数据源"按钮，改为"上传数据"按钮
- 数据管理员显示"导入平台数据源"按钮

### DatabaseNewPage（导入/上传页）

| 角色 | 平台数据源标签 | 其他数据源标签 |
|------|--------------|--------------|
| 数据管理员 | 可见，可导入 | 可见，可上传 CSV |
| 普通用户 | 隐藏 | 可见，可上传 CSV |

- 普通用户访问 `/data/new` 时，只显示"其他数据源"标签
- 上传成功后跳转到数据湖详情页

### 用户信息 API

新增 `GET /api/user/current`，返回：

```json
{
  "id": 1,
  "username": "test230911",
  "displayName": "财务部员工1",
  "isDataAdmin": true/false,
  "isSuperuser": true/false
}
```

前端据此控制按钮/标签页的可见性。

---

## 四、新增 API

| 端点 | 方法 | 用途 |
|------|------|------|
| `GET /api/database/{dbId}/my-uploads` | GET | 返回当前用户在 upload schema 下的表列表 |
| `DELETE /api/database/{dbId}/upload-table/{tableName}` | DELETE | 删除指定上传表（仅限前缀匹配当前用户） |
| `GET /api/user/current` | GET | 返回当前用户信息含角色判断 |

---

## 五、涉及文件变更清单

### 后端 (dts-analytics)

| 文件 | 变更 |
|------|------|
| `MetabaseAuth.java` | 新增 requireDataAdmin() 方法，DATA_ADMIN_ROLES 常量 |
| `PlatformIntegrationResource.java` | dataSources 端点改为 requireDataAdmin |
| `DatabaseResource.java` | 管理端点改为 requireDataAdmin；新增 my-uploads、delete-upload-table 端点 |
| `DatabaseUploadTableService.java` | 表名加入 username 前缀；新增按用户过滤和删除方法 |
| `DataLakeDatabaseInitializer.java` | 已实现，无需改动 |
| 新增 `UserResource.java` | GET /api/user/current 端点 |

### 前端 (dts-analytics-webapp)

| 文件 | 变更 |
|------|------|
| `analyticsApi.ts` | 新增 getCurrentUser、listMyUploads、deleteUploadTable API 方法 |
| `DataPage.tsx` | 根据 isDataAdmin 控制按钮文案和跳转 |
| `DatabaseNewPage.tsx` | 根据 isDataAdmin 控制标签页可见性；去掉数据库选择器；上传固定到数据湖 |
| `UploadedDataEditor.tsx` | 去掉 databaseId prop（固定数据湖）|

---

## 六、不在本次范围内

- 部门级数据权限隔离
- 上传文件大小/频率限制
- 上传历史记录/审计
- 上传表的自动过期清理
