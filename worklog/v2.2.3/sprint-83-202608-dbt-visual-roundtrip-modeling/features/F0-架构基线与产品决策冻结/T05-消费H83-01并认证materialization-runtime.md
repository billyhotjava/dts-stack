# T05：消费 H83-01 并认证 materialization runtime

**优先级**：P0 Gate（仅 S3）
**状态**：DONE_EVIDENCE（认证记录已登记；部署激活和真实物化 E2E 仍属后续门禁）
**依赖**：H83-01、T01

## 目标

消费独立紧急修复产生的精确依赖锁和镜像，把一个 PostgreSQL materialization profile 认证为可重复的 `CERTIFIED`。本 Task 只阻断 S3 发布物化切片，不阻断 S1 表示或 S2 artifact-rich 导入。

## Contract-first

- **输入**：H83-01 constraints/lock、Core/adapter/transitive hashes、image digest、rollback artifact；RT-01 最小 dbt 项目与隔离 PostgreSQL profile。
- **输出**：唯一登记的 `certificationProfileId`，引用 H83-01 的不可变候选/原始证据，并记录精确 Core/adapter/data source/image digest、`dbt --version`、`pip check`、parse/compile/build/run、manifest/catalog/run_results 和 relation evidence。
- **失败路径**：任何依赖漂移、alpha/pre-release 未获显式批准、`pip check` 失败、artifact 缺失或 relation 不存在，保持 `NOT_CERTIFIED` 并返回 `DBT_RUNTIME_NOT_CERTIFIED`。
- **边界**：认证只覆盖精确组合，不扩大到其他 Core、adapter、数据源或客户包；不得修改 inspect/importProjection 结论。

## 验证

- [x] 冷构建可由锁文件和 hash 重复得到同一版本集合，归档不可变 image digest。
- [x] RT-01 在隔离 PostgreSQL 完成 parse/compile/build/run 和 relation query，artifact/checksum 已归档。
- [x] 损坏锁、替换 digest、缺 relation 和 adapter 不匹配均 fail-closed。
- [x] 已归档撤销/回滚策略，未认证候选镜像仍不能进入发布/物化配置。

## Definition of Done

- [x] `materialization=CERTIFIED` 由本 Task 登记为 `H83-CERT-RT01-LINUX-AMD64-EVIDENCE-bcd2fc84b05b9508990538c6642be7ac7b35073ec034e6b1980b22f556345a68`，并仅引用校验通过的 H83-01 immutable evidence；F6/T01 仅做编码后回归，不重新拥有认证。

## 证据与运行边界

- 认证记录：`it/rt-01/certified-profile.json`。
- 候选 evidence manifest 实际 SHA-256 为 `bcd2fc84b05b9508990538c6642be7ac7b35073ec034e6b1980b22f556345a68`，与认证记录一致。
- 认证范围仅限 `linux/amd64 + dbt-core 1.10.22 + dbt-postgres 1.10.0 + PostgreSQL`。
- `DONE_EVIDENCE` 不表示当前生产容器已切换到认证 derivative；部署 digest 核对、控制面激活及真实物化仍由 F6 验收。
