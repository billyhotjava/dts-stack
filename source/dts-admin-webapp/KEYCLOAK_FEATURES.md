# Keycloak管理UI功能说明

> 迁移说明：原版 A 页面目录 `src/pages/management/system/*` 已废弃并迁移至新版 B 视图 `src/admin/views/*`。
> 左侧导航已指向 `/admin/*` 路由，旧路径 `/management/system/*` 仍可访问，但会直接渲染新版视图以保持兼容。

## 概述

本项目为Keycloak身份认证系统提供了完整的Web管理界面，包含用户、角色、组的完整CRUD操作功能。

## 已实现功能

### 1. Keycloak类型定义 (`src/types/keycloak.ts`)

定义了与后端DTO对象对应的TypeScript类型：
- `KeycloakUser` - 用户信息
- `KeycloakRole` - 角色信息
- `KeycloakGroup` - 组信息
- 各种请求/响应类型
- 表格行数据类型

### 2. API服务层 (`src/api/services/keycloakService.ts`)

提供了完整的Keycloak API调用服务：

#### KeycloakUserService - 用户管理
- `getAllUsers()` - 获取用户列表
- `searchUsers()` - 搜索用户
- `getUserById()` - 获取用户详情
- `createUser()` - 创建用户
- `updateUser()` - 更新用户
- `deleteUser()` - 删除用户
- `resetPassword()` - 重置密码
- `setUserEnabled()` - 启用/禁用用户
- `getUserRoles()` - 获取用户角色
- `assignRolesToUser()` - 分配角色
- `removeRolesFromUser()` - 移除角色

#### KeycloakRoleService - 角色管理
- `getAllRealmRoles()` - 获取所有Realm角色
- `getRoleByName()` - 根据名称获取角色
- `createRole()` - 创建角色
- `updateRole()` - 更新角色
- `deleteRole()` - 删除角色

#### KeycloakGroupService - 组管理
- `getAllGroups()` - 获取所有组
- `getGroupById()` - 获取组详情
- `createGroup()` - 创建组
- `updateGroup()` - 更新组
- `deleteGroup()` - 删除组
- `getGroupMembers()` - 获取组成员
- `addUserToGroup()` - 添加用户到组
- `removeUserFromGroup()` - 从组移除用户
- `getUserGroups()` - 获取用户所属组

### 3. 用户管理界面（新版 B）

#### 用户管理视图 (`src/admin/views/user-management.tsx`)
- 用户总览、搜索、角色与状态筛选
- 基于变更单的新增/修改/禁用/角色调整申请（右侧表单 `ChangeRequestForm`）
- 兼容后端数组或容器结构（items/list/records/data）

### 4. 角色管理界面（新版 B）

#### 角色管理视图 (`src/admin/views/role-management.tsx`)
- 角色列表、搜索与明细
- 角色新增/编辑/删除（以变更单驱动）

### 5. 组织（组）管理界面（新版 B）

#### 组织管理视图 (`src/admin/views/org-management.tsx`)
- 组织树/组的管理与成员查看
- 组织结构调整通过变更单审批

### 6. UI组件补充

#### Alert组件 (`src/ui/alert.tsx`)
- 错误信息展示
- 成功提示
- 警告信息

## 技术特性

### 1. 完整的CRUD操作
- 用户、角色、组的增删改查
- 关联关系管理（用户-角色、用户-组）

### 2. 用户友好的界面
- 响应式设计
- 搜索和分页
- 操作确认对话框
- 加载状态提示

### 3. 错误处理
- API错误捕获和展示
- 表单验证
- 用户操作反馈

### 4. 类型安全
- 完整的TypeScript类型定义
- 编译时类型检查

## 文件结构（已迁移）

```
src/
├── types/
│   └── keycloak.ts                 # Keycloak类型定义
├── api/
│   └── services/
│       └── keycloakService.ts      # 兼容服务（保留）
├── admin/views/
│   ├── user-management.tsx         # 用户管理（新版）
│   ├── role-management.tsx         # 角色管理（新版）
│   ├── org-management.tsx          # 组织/组管理（新版）
│   ├── approval-center.tsx         # 任务审批
│   ├── audit-center.tsx            # 日志审计
│   └── portal-menus.tsx            # 门户菜单管理
└── ui/
    └── alert.tsx                   # Alert组件
```

## 使用说明

1. 在左侧导航进入 管理 -> 用户/角色/组织，或直接访问 `/admin/users`、`/admin/roles`、`/admin/orgs`。
2. 所有新增/编辑/禁用等敏感变更通过变更单发起并由审批中心处理。

## 后端API对接

本前端界面对接的后端API路径：
- 用户管理：`/api/keycloak/users`
- 角色管理：`/api/keycloak/roles`
- 组管理：`/api/keycloak/groups`

所有API调用都包含完整的错误处理和用户反馈机制。
