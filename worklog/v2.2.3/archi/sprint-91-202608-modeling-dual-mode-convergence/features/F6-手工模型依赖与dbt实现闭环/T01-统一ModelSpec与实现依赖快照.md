# T01: 统一 ModelSpec 与实现依赖快照

**优先级**: P0
**状态**: CODE_COMPLETE / E2E_PENDING
**依赖**: F0/T03 完成并取得真实样本 ID

## 目标

在既有 ModelSpec/implementation/candidate seam 上建立唯一依赖解析器，把业务依赖和 dbt 实现证据归一化为不可变快照；后续草稿、编译、候选、物化和治理投影只消费该快照。

## 技术设计 (Contract-first)

### 输入

- immutable `modelSpecId + modelRevision + modelChecksum`；
- immutable `implementationRevision + implementationChecksum`；
- ModelSpec `sourceRefs / dependsOn / dimensionRefs`；
- 既有静态 validator 解析出的 dbt `source()/ref()` 依赖。

### 输出

```text
ModelImplementationDependencySnapshot {
  modelSpecId: UUID
  modelRevision: int
  modelChecksum: string
  implementationRevision: int
  implementationChecksum: string
  physicalSources: [{sourceBindingId, resolvedVersion, dbtSourceUniqueId}]
  modelInputs: [{modelSpecId, revision, checksum,
                 implementationRevision, implementationChecksum,
                 dbtUniqueId, role: UPSTREAM|DIMENSION}]
  dependencyChecksum: sha256
}
```

- 数组按稳定身份排序后计算 checksum；同一输入重放结果必须逐字节相同。
- 快照写入或可确定性重建于既有 implementation config/artifact 与 candidate pin，不新增独立依赖表。
- 若既有结构确实无法承载，必须先补 ADR、expand-only migration 与回滚设计，本 Task 不得暗建新台账。

### 一致性规则

- `source()` 必须命中已确认的 `sourceRefs`；`ref()` 必须命中 `dependsOn` 或 `dimensionRefs`。
- 实现中多出的依赖返回 `MODEL_IMPLEMENTATION_DEPENDENCY_UNDECLARED`。
- ModelSpec 声明但实现未使用的必需依赖返回 `MODEL_IMPLEMENTATION_DEPENDENCY_MISSING`。
- revision/checksum/implementation pin 漂移返回 `MODEL_IMPLEMENTATION_DEPENDENCY_PIN_STALE`。
- 环依赖返回 `MODEL_IMPLEMENTATION_DEPENDENCY_CYCLE`，并给出最短可解释环路径。
- ODS binding 未确认或 resolved version 漂移返回 `MODEL_SOURCE_BINDING_STALE`。

## 复用与禁止项

- 复用现有 ModelSpec revision、静态 validator、implementation artifact、candidate entry 与 audit correlation。
- 不新增 SQL parser、依赖数据库、客户端 dependency checksum 或按物化场景临时猜图。
- 不把系统生成 STG 节点加入业务 `modelInputs`；其身份归属 CONFIG bundle。

## 下游契约

| 消费方 | 只能使用的字段 |
|---|---|
| F6/T02 工作台 | physicalSources/modelInputs 的业务可读投影 |
| F6/T03 草稿 | 全量快照 + parsed dependency 对账 |
| F7 编译器 | 稳定 source/ref 身份与 role |
| F8 planner/runtime | pins、role、dependencyChecksum |
| F5/Sprint-93 | candidate/audit 中的 checksum 与 lineage projection |

## Definition of Ready

- [x] snapshot 字段、排序、checksum 与稳定错误码已冻结。
- [x] 既有 validator/artifact/candidate 复用 seam 已指定。
- [ ] F0/T03 样本 ID、source resolved version 与四层 pins 已归档。

## 验证 (RED→GREEN)

- [x] ODS+DIM→FACT、上游模型链与多根依赖快照已有聚焦自动化；四层真实样本留待 IT-11。
- [x] 未声明 ref、缺少维度 ref、stale pin、source version 漂移、cycle 使用稳定错误码。
- [x] 相同输入乱序后 checksum 相同；任一 pin 改变 checksum 必须改变。
- [x] 手工草稿与 DESIGNER compile 复用同一 resolver；ZIP apply 等价性留待 IT-15。
- [x] repository 使用一次递归闭包查询加一次来源查询，限制 64 个根、256 个闭包节点，无逐节点 N+1。

## Definition of Done

- [x] 唯一依赖快照可被 F6/T03、F7/T02、F8/T01 直接消费。
- [x] 无新增依赖台账、parser 或客户端可篡改字段。
- [ ] 版本、checksum 与错误码已有自动化证据；真实审计 correlation 待 IT-11/IT-15。
