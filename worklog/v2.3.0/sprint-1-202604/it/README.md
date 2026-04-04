# 集成测试 (Integration Tests)

**前置条件**: v2.2.3 displayName 修复已验证通过

## 测试计划

### Phase 1 (F1) 验证
- [ ] 人员 Excel 导入 → Keycloak 用户创建成功
- [ ] 组织同步 → Keycloak groups 树结构正确
- [ ] AdminKeycloakUser 表无新增记录
- [ ] 定时同步任务不再执行

### Phase 2 (F2) 验证
- [ ] 用户查询 → 缓存命中时无 Keycloak API 调用
- [ ] 停止 Keycloak → 缓存宽限期内查询正常
- [ ] 缓存过期后 + Keycloak 恢复 → 自动刷新

### Phase 3 (F3) 验证
- [ ] 迁移脚本执行后 Keycloak attributes 完整
- [ ] PersonProfile 表无新写入

### Phase 4 (F4) 验证
- [ ] scopeDeptCode 迁移数据正确
- [ ] 组织树接口返回结果与迁移前一致

### Phase 5 (F5) 验证
- [ ] DROP 表后应用正常启动
- [ ] 全功能回归测试通过

## 测试证据

每个 Phase 完成后在此目录下添加测试截图/日志。
