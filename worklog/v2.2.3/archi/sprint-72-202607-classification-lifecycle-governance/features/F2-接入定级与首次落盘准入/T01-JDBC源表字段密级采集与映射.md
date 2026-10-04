# T01: JDBC 源表字段密级采集与映射

**优先级**: P0
**状态**: IN_PROGRESS
**编码状态**: DONE（统一验证延后）
**依赖**: F1-T03

## 目标

让 MySQL、达梦、PostgreSQL 等 JDBC 接入在 Schema 探测后形成表/字段密级候选并由用户确认。

## 技术设计

- 读取源端可用扩展属性或接入配置映射。
- 表级密级作为字段下限，字段允许单独升密。
- 替换 catalog sync 中无条件默认 INTERNAL 的写法为 `PENDING_CLASSIFICATION` + seal。
- 保存来源系统、原始值、映射规则和确认人。

## 影响范围

`JdbcCatalogSyncService`、`PostgresCatalogSyncService`、`InceptorCatalogSyncService`、数据源 DTO/API。

## 验证

- [ ] 表/字段映射、未知值和重复同步测试。
- [ ] 已封存字段重新同步不会被源端低值覆盖。

## 完成标准

- [ ] JDBC 接入结果可解释到源字段或确认动作。
- [ ] 接入同步不再静默降低旧密级。
