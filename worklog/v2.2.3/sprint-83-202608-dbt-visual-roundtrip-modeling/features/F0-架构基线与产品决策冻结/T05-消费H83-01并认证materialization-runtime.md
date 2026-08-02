# T05：消费 H83-01 并认证 materialization runtime

**优先级**：P0 Gate（仅 S3）  
**状态**：BLOCKED（等待 H83-01 immutable runtime evidence）  
**依赖**：H83-01、T01

## 目标

消费独立紧急修复产生的精确依赖锁和镜像，把一个 PostgreSQL materialization profile 认证为可重复的 `CERTIFIED`。本 Task 只阻断 S3 发布物化切片，不阻断 S1 表示或 S2 artifact-rich 导入。

## Contract-first

- **输入**：H83-01 constraints/lock、Core/adapter/transitive hashes、image digest、rollback artifact；RT-01 最小 dbt 项目与隔离 PostgreSQL profile。
- **输出**：唯一登记的 `certificationProfileId`，引用 H83-01 的不可变候选/原始证据，并记录精确 Core/adapter/data source/image digest、`dbt --version`、`pip check`、parse/compile/build/run、manifest/catalog/run_results 和 relation evidence。
- **失败路径**：任何依赖漂移、alpha/pre-release 未获显式批准、`pip check` 失败、artifact 缺失或 relation 不存在，保持 `NOT_CERTIFIED` 并返回 `DBT_RUNTIME_NOT_CERTIFIED`。
- **边界**：认证只覆盖精确组合，不扩大到其他 Core、adapter、数据源或客户包；不得修改 inspect/importProjection 结论。

## 验证

- [ ] 冷构建可由锁文件和 hash 重复得到同一版本集合，归档不可变 image digest。
- [ ] RT-01 在真实 PostgreSQL 完成 parse/compile/build/run 和 relation query，artifact/checksum 可复现。
- [ ] 损坏锁、替换 digest、缺 relation 和 adapter 不匹配均 fail-closed。
- [ ] rollback 演练恢复上一可用镜像，未认证镜像不能进入发布/物化配置。

## Definition of Done

- [ ] `materialization=CERTIFIED` 只由本 Task 登记，且只引用校验通过的 H83-01 immutable evidence；F6/T01 仅做编码后回归，不重新拥有认证。
