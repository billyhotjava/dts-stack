# T02: ScreenAclPanel 检查与清理

**优先级**: P1
**状态**: READY
**依赖**: F1/T07

## 目标

Review `ScreenAclPanel` 是否有旧 ACL 逻辑残留，确保其使用新的授权端点，同时检查 platform-webapp 合并带来的任何旧权限代码。

## 检查点

### ScreenAclPanel.tsx

- [ ] 授权调用走 `analyticsApi.addScreenGrant`（新端点 `PUT /bi/api/screens/{id}/grants`）
- [ ] `granteeId` 传 analytics user.id 数字字符串（与 ScreenSharePanel 一致）
- [ ] 无硬编码角色权限判断
- [ ] 无旧 `ScreenAclService` 调用

### analyticsApi.ts 中的权限相关

- [ ] `addScreenGrant` 的请求体字段名与后端 `ScreenResource.addGrant` 解析字段一致（`granteeType`/`granteeId`/`permission`）
- [ ] 无旧 ACL 相关 API 方法残留（如 `updateAcl`、`getAcl` 等旧命名）

### 全局检查

```bash
grep -r "ScreenAcl\|screen_acl\|aclService\|AclService" \
  src/analytics --include="*.ts" --include="*.tsx"
```

上述命令应无匹配（或匹配仅在 ScreenAclPanel 组件本身，而非旧 Service 层）。

## 完成标准

- [ ] `ScreenAclPanel` 使用新授权端点，行为正确
- [ ] 旧 ACL 相关代码（若有）已删除
- [ ] TypeScript 编译无错误
