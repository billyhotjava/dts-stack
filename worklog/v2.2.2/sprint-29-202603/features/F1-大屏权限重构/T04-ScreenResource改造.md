# T04: ScreenResource 改造（替换旧 ACL 调用）

**优先级**: P0
**状态**: READY
**依赖**: T01, T02

## 目标
将 ScreenResource 中所有 screenAclService 调用替换为 ScreenPermissionService，创建时调用 ScreenOwnershipService

## 技术设计

### 替换映射

| 位置 | 旧调用 | 新调用 |
|------|--------|--------|
| list() | screenAclService.snapshot() | screenPermissionService.snapshot() |
| get() | screenAclService.snapshot() | screenPermissionService.snapshot() |
| create() | ensureCreatorOwner() | screenOwnershipService.registerOwnership() |
| update() | screenAclService.snapshot().canEdit() | screenPermissionService.snapshot().canEdit() |
| delete() | screenAclService.snapshot().isOwner() | screenPermissionService.snapshot().isOwner() |
| publish() | screenAclService.hasPermission(MANAGE) | screenPermissionService.snapshot().canEdit() |

### PermissionSnapshot 映射

旧：`canRead, canEdit, canPublish, canManage, canDelete, isOwner`
新：`canRead, canEdit, isOwner`（canPublish = canEdit, canManage = isOwner, canDelete = isOwner）

### 创建时自动填充 ownerDeptCode

```java
// ScreenResource.create()
screen.setOwnerDeptCode(PlatformContext.from(request).dept());
screenOwnershipService.registerOwnership(screen.getId(), username, deptCode);
```

## 影响范围
- 修改: `ScreenResource.java` — 移除 screenAclService 字段，注入新 service
- 修改: `toListResponse()` / `toDetailResponse()` — 适配新 PermissionSnapshot

## 验证
- [ ] 大屏 CRUD 操作正常
- [ ] 权限检查走 ScreenPermissionService
- [ ] 创建时 ownerDeptCode 自动填充
- [ ] 编译无 screenAclService 引用残留

## 完成标准
- [ ] ScreenResource 不再依赖 ScreenAclService
- [ ] 所有端点权限检查走新 service
