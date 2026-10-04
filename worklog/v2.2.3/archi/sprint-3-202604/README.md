# Sprint-3: IAM 修复 -- displayName 链路 bug 修复

**时间**: 2026-04
**状态**: READY
**目标**: 修复 fullName/displayName 在 Keycloak 写入、读取、admin 解析、platform 消费全链路的 bug，为 v2.3.0 IAM 重构打基础。

## 背景

当前 dts-admin 与 Keycloak 之间的用户名/显示名称处理存在多处 bug：
1. `toRepresentation()` 把 fullName 塞进 Keycloak firstName（违反 Keycloak User Profile 配置）
2. `toUserDto()` 从顶级字段读 fullName（永远为 null），不从 attributes 读
3. `PersonnelImportService` 同时设置 firstName=fullName
4. 4 处 resolveFullName 实现各自优先级不同，结果不一致
5. 前端大屏权限面板角色显示英文代码而非中文名

**约束**:
- 只修 bug，不改架构
- API 接口签名不变
- 为 v2.3.0 IAM 重构做铺垫

## Feature 列表

| ID | Feature | Task 数 | 状态 | 优先级 |
|----|---------|---------|------|--------|
| F1 | 修复 displayName 链路 bug | 4 | READY (T04 DONE) | P0 |

## 完成标准
- [ ] Keycloak 中 firstName 不再被写入 fullName 值
- [ ] 全链路 displayName 解析结果一致（attributes.fullName > dto.fullName > username）
- [ ] 前端权限管理面板正确显示用户姓名和角色中文名
- [ ] 已有用户数据修正脚本就绪
