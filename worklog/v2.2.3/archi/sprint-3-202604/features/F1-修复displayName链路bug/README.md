# F1: 修复 displayName 链路 bug (Phase 0)

**优先级**: P0
**状态**: DONE

## 目标
修复当前 fullName/displayName 在写入 Keycloak、读取 Keycloak、admin 解析、platform 消费 四个环节的 bug，为后续重构打下正确基础。

## 问题总结

1. `toRepresentation()` 把 fullName 塞进 Keycloak firstName 字段
2. `toUserDto()` 不从 attributes 读取 fullName，从顶级字段取（永远为 null）
3. `PersonnelImportService` 同时设置 firstName=fullName
4. `resolveFullName()` 优先级链过于复杂（4 种 attribute 别名 + 3 级 fallback）
5. `AdminDirectoryGateway.toSummary()` 不检查 attributes，与 admin 侧逻辑不一致
6. `buildUserProfile()` 依赖可能为空的 claims/userinfo 中的 fullName

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | 修复 Keycloak 写入：只写 attributes.fullName | P0 | DONE | - |
| T02 | 修复 Keycloak 读取：从 attributes 取 fullName | P0 | DONE | T01 |
| T03 | 统一 resolveFullName 优先级 | P0 | DONE | T02 |
| T04 | 修复前端 ScreenAclPanel 标识列显示 | P1 | DONE | - |

## 完成标准
- [x] Keycloak 中 firstName 不再被写入 fullName 值
- [x] 全链路 displayName 解析结果一致
- [ ] 已有用户数据通过迁移脚本修正（清理 firstName 中的脏数据）
- [x] 前端权限管理面板正确显示用户姓名和角色中文名
