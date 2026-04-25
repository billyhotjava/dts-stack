# T05: API 配置存储与迁移设计

**优先级**: P0
**状态**: DRAFT
**依赖**: T01, T02

## 目标

解决 API 配置比 JDBC 配置复杂、现有 props 字段容量和安全边界不足的问题。

## 范围

- 评估 `infra_data_source.props` 长度和类型是否迁移为 jsonb/clob。
- 设计 `data_source_secret` 或复用现有 secure props 的 secret reference 模型。
- 明确公共配置、敏感配置、运行时覆盖配置的落库位置。
- 设计迁移脚本、回滚策略和兼容读取逻辑。

## 完成标准

- [ ] API 配置不会被 2048 字符限制卡住。
- [ ] 密钥值不落普通配置字段。
- [ ] 老数据源读取不受影响。
- [ ] 有 migration、rollback 和数据校验方案。

