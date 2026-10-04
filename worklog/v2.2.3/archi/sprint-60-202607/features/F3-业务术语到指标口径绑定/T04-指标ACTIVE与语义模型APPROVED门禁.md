# T04: 指标 ACTIVE 与语义模型 APPROVED 门禁

**状态**: READY
**优先级**: P0

## 目标

指标 ACTIVE 和语义模型 APPROVED 不再只是状态切换，而是标准、口径、字段、权限、运行证据共同通过后的结果。

## 指标门禁

- 绑定业务对象。
- 绑定 ACTIVE 业务术语。
- 公式类型和公式 JSON 合法。
- 公式依赖字段已绑定数据元。
- 枚举筛选字段使用公共码表。

## 语义模型门禁

- 绑定 DWS/ADS 模型。
- 粒度一致。
- 维度和指标字段标准就绪。
- 权限/密级可消费。
- dbt compile/test/build 证据存在。

## 验收

- [ ] 指标 ACTIVE 失败时返回具体 blocker。
- [ ] 语义模型 APPROVED 失败时返回具体 blocker。
- [ ] 低代码向导和指标工作台都能显示这些 blocker。
