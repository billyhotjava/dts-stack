# Keycloak管理UI功能说明

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

### 3. 用户管理界面

#### 用户列表页面 (`src/pages/management/system/user/index.tsx`)
- 分页展示用户列表
- 用户搜索功能
- 查看用户详情
- 编辑用户信息
- 重置用户密码
- 启用/禁用用户
- 删除用户

#### 用户详情页面 (`src/pages/management/system/user/detail.tsx`)
- 展示用户完整信息
- 用户角色管理
- 用户所属组展示
- 快速操作按钮

#### 用户编辑弹框 (`src/pages/management/system/user/user-modal.tsx`)
- 创建/编辑用户基本信息
- 角色分配管理（编辑模式）
- 表单验证

#### 密码重置弹框 (`src/pages/management/system/user/reset-password-modal.tsx`)
- 重置用户密码
- 临时密码设置
- 密码强度验证

### 4. 角色管理界面

#### 角色列表页面 (`src/pages/management/system/role/index.tsx`)
- 角色列表展示
- 角色搜索功能
- 创建/编辑/删除角色

#### 角色编辑弹框 (`src/pages/management/system/role/role-modal.tsx`)
- 创建/编辑角色
- 复合角色设置
- 客户端角色配置

### 5. 组管理界面

#### 组列表页面 (`src/pages/management/system/group/index.tsx`)
- 组列表展示
- 成员数量统计
- 子组数量统计
- 创建/编辑/删除组

#### 组编辑弹框 (`src/pages/management/system/group/group-modal.tsx`)
- 创建/编辑组信息
- 组路径设置
- 组描述管理

#### 组成员管理弹框 (`src/pages/management/system/group/group-members-modal.tsx`)
- 查看组成员
- 添加用户到组
- 从组移除用户
- 成员搜索功能

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

## 文件结构

```
src/
├── types/
│   └── keycloak.ts                 # Keycloak类型定义
├── api/
│   └── services/
│       └── keycloakService.ts      # API服务层
├── pages/management/system/
│   ├── user/
│   │   ├── index.tsx               # 用户列表页面
│   │   ├── detail.tsx              # 用户详情页面
│   │   ├── user-modal.tsx          # 用户编辑弹框
│   │   └── reset-password-modal.tsx # 密码重置弹框
│   ├── role/
│   │   ├── index.tsx               # 角色列表页面
│   │   └── role-modal.tsx          # 角色编辑弹框
│   └── group/
│       ├── index.tsx               # 组列表页面
│       ├── group-modal.tsx         # 组编辑弹框
│       └── group-members-modal.tsx # 组成员管理弹框
└── ui/
    └── alert.tsx                   # Alert组件
```

## 使用说明

1. **用户管理**：在系统管理 -> 用户管理中进行用户的增删改查
2. **角色管理**：在系统管理 -> 角色管理中管理系统角色
3. **组管理**：在系统管理 -> 组管理中管理用户组
4. **权限分配**：在用户详情页面可以为用户分配角色
5. **组成员管理**：在组管理页面可以管理组成员

## 后端API对接

本前端界面对接的后端API路径：
- 用户管理：`/api/keycloak/users`
- 角色管理：`/api/keycloak/roles`
- 组管理：`/api/keycloak/groups`

所有API调用都包含完整的错误处理和用户反馈机制。