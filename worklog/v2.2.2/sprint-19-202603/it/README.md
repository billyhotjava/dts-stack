# Integration Test — Sprint-19: 统一资产权限管控

## 测试矩阵

| 角色 | 本部门资产 | 其他部门资产 | 归属管理 | 跨部门授权 |
|------|-----------|-------------|---------|-----------|
| SYS_ADMIN / OP_ADMIN | MANAGE | MANAGE | Yes | Yes |
| INST_DATA_OWNER | MANAGE | MANAGE | Yes | Yes |
| INST_LEADER | READ | READ | No | No |
| DEPT_DATA_OWNER | MANAGE | 需显式授权 | 本部门 | No |
| DEPT_LEADER | READ | 需显式授权 | No | No |
| EMPLOYEE | 需显式授权 | 需显式授权 | No | No |

## 测试场景

### IT-01: 角色权限矩阵验证
- [ ] 各角色访问本部门/跨部门资产的权限判定正确
- [ ] 超管/院级角色可见全部资产

### IT-02: 授权生命周期
- [ ] 创建授权 → 生效 → 到期自动失效
- [ ] 撤销授权立即生效
- [ ] 审计日志完整记录

### IT-03: 资产归属自动化
- [ ] 数据源同步新表自动继承部门归属
- [ ] 手工修改归属后同步不覆盖

### IT-04: Analytics 集成
- [ ] analytics 卡片/仪表盘列表仅返回有权资产
- [ ] 无权卡片在仪表盘中显示占位符
- [ ] 权限变更后缓存超时后生效

### IT-05: 数据迁移
- [ ] Screen ACL 正确迁移到 asset_grant
- [ ] 现有数据源 ownership 补填完整

## 证据存放

测试日志和截图存放于 `sprint-19-202603/it/` 目录下。
