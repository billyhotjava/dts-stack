# T01：固化编码后 dbt/adapter/恶意包回归矩阵

**优先级**：P1
**状态**：DRAFT  
**依赖**：F0/T02、对应实现切片；S3 materialization 回归另依赖 F0/T05

## 目标

消费 F0/T02 的 parser/工程 fixture 产物；执行 S3 materialization 回归时再消费 F0/T05 固定的 RT-01 认证证据。在对应编码完成后，把 inspect/import/materialization 三轴、精确 dbt Core/adapter/image digest、资源类型及攻击包固定为可重复的 contract/integration 回归。该 Task 不承担首次运行时认证，因此不会与 F0/T04 形成依赖环。

## Contract-first

- **矩阵维度**：artifact-rich/source-only（enforced+完整 name/data_type、非 enforced/缺 type、无字段）/complex/blocked/drift/malicious × Core × manifest schema × adapter package × 数据源 × image digest。
- **断言**：inspection/importProjection/materialization、issues、uniqueId、dependency、field provenance、ownership、checksum、性能/限制、清理结果。
- **失败路径**：未声明版本、adapter、数据源或未认证 profile 不得被测试“顺便支持”；返回明确 UNSUPPORTED/BLOCKED/DBT_RUNTIME_NOT_CERTIFIED。
- **数据**：fixture 无客户敏感信息且有生成说明。

## Definition of Done

- [ ] 每项可在 CI/隔离环境产生确定红绿结果。
- [ ] 复核 H83-01 原始证据与 F0/T05 登记的精确 PostgreSQL profile；若 digest 或依赖发生变化，本 Task 立即 fail-closed 并要求重新执行 H83-01 + F0/T05，不得自行认证或临时生成未归档 profile。
- [ ] 非 enforced/缺 type/无字段、动态/macro/package 缺口、下游传播、BLOCKED 不可选择、逐项运行失败才 PARTIAL 与 DBT_MANAGED ownership 全部固定。
- [ ] 工程支持矩阵与客户兼容声明明确分栏；没有客户脱敏包时客户声明保持 GAP，不影响工程回归结论。
