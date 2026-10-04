# T05: grants 端点（GET/PUT/DELETE）

**优先级**: P0
**状态**: READY
**依赖**: T04

## 目标
新建 `/api/screens/{id}/grants` 端点，代理到 platform 的 asset_grant API，替代旧 `/api/screens/{id}/acl`

## 技术设计

### 端点设计

```
GET    /api/screens/{id}/grants     — 获取大屏的所有授权记录
PUT    /api/screens/{id}/grants     — 添加/更新授权（body: { granteeType, granteeId, permission })
DELETE /api/screens/{id}/grants/{grantId} — 撤销某条授权
```

### 权限控制
- GET: 需要 isOwner 或 OP_ADMIN
- PUT: 需要 isOwner 或 OP_ADMIN
- DELETE: 需要 isOwner 或 OP_ADMIN

### 代理到 platform

```java
// GET → platform GET /api/asset-grants?assetType=SCREEN&assetId={id}
// PUT → platform POST /api/asset-grants { assetType: "SCREEN", assetId, granteeType, granteeId, permission }
// DELETE → platform DELETE /api/asset-grants/{grantId}
```

### 响应格式

```json
[
  {
    "id": "grant-uuid",
    "granteeType": "USER",
    "granteeId": "test230913",
    "granteeName": "test230913",
    "permission": "READ",
    "grantedBy": "opadmin",
    "grantedAt": "2026-03-30T10:00:00Z"
  }
]
```

## 影响范围
- 新建: `ScreenResource` 中添加 grants 端点（或新建 `ScreenGrantResource.java`）
- 依赖: PlatformPermissionClient 需要扩展 grant CRUD 方法

## 验证
- [ ] 可以授权给 USER/DEPT/ROLE
- [ ] 可以设置 READ 或 EDIT 权限
- [ ] 可以撤销授权
- [ ] 非 OWNER/OP_ADMIN 无法操作

## 完成标准
- [ ] 三个端点功能正常
- [ ] 返回格式包含 granteeName（前端展示用）
