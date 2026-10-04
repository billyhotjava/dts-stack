# 集成测试 (Integration Tests)

## 测试计划

### F1 验证
- [ ] 新建用户 → Keycloak attributes.fullName 有值，firstName 为空
- [ ] 更新用户 fullName → Keycloak attributes.fullName 更新，firstName 不变
- [ ] 人员导入 → Keycloak 数据正确（无 firstName 脏数据）
- [ ] 登录 → 前端拿到的 user.fullName 为中文姓名
- [ ] 大屏权限面板 → 用户显示姓名，角色显示中文名
- [ ] 4 处 resolveFullName 对同一用户返回相同结果

## 测试证据

完成后在此目录下添加测试截图/日志。
