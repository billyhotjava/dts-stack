# T03：完成真实 PostgreSQL 升级回滚验收

**优先级**：P0  
**状态**：READY  
**依赖**：F2、F4、F5

## 目标

在真实数据库中证明首装、重复安装、内容升级、本地覆盖、冲突、弃用、替代和回滚的事务真实性。

## 技术设计

- 使用 v1/v2 样例包覆盖三方合并全部分支。
- SQL 对账 baseline/override/effective/run/run_item、引用和审计行。
- 注入事务失败、CAS 冲突、跨租户、重复提交和回滚重试。
- 计量单位覆盖基准解析、循环阻断和数据元引用。
- PJM 包验证只有批准候选落库。

## 影响范围

- PostgreSQL/Testcontainers/部署数据库
- Liquibase
- 标准包服务和 IT evidence

## 验证

- [ ] HTTP 结果和 SQL 计数/字段快照一致。
- [ ] 任一失败无半包数据。
- [ ] 回滚后客户扩展和本地改写仍在。

## 完成标准

- [ ] 保存可复现请求、响应、runId 和 SQL 对账。
- [ ] mock 或 repository 单测不替代此 Task。
