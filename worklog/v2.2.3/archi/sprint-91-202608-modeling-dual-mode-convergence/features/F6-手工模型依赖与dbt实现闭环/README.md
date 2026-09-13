# F6: 手工模型依赖与 dbt 实现闭环

**优先级**: P0
**状态**: CODE_COMPLETE / E2E_PENDING（T01～T03 源码与聚焦自动化完成；F0/T03 真实样本及 IT-11/IT-15 待集中验收）

## 目标

让用户不用 ZIP，也能在现有模型工作台声明来源、上游模型和维度引用，再用手工 dbt 完成实现；ModelSpec 依赖、实现依赖和 dbt `source()/ref()` 必须收敛成同一份可校验快照，供 F7 编译和 F8 物化共同消费。

## Feature 关联

| 上游 | 本 Feature | 下游 |
|---|---|---|
| F0/T03 全链路样本；F1～F4 双模式与草稿能力 | 统一依赖快照、工作台依赖编辑、ModelSpec 感知的 dbt 草稿 | F7 系统生成实现；F8 物化计划；F5 候选/发布；Sprint-93 治理证据 |

## 契约边界

- ModelSpec 的 `sourceRefs + dependsOn + dimensionRefs` 是业务依赖事实源。
- dbt 静态解析结果是实现证据，必须与业务依赖事实逐项对账，不能成为第二套依赖 owner。
- ZIP apply、手工代码提交和可视化生成均调用同一 resolver，产出同一种 dependency snapshot/checksum。
- 依赖快照复用既有 implementation/candidate artifact/config seam；未经 ADR 不新增依赖表、parser 或发布状态机。

## Task 列表

| ID | Task | 状态 | 依赖 |
|---|---|---|---|
| T01 | 统一 ModelSpec 与实现依赖快照 | CODE_COMPLETE | F0/T03 真实样本仅作为 E2E 前置 |
| T02 | 完善工作台来源、上游与维度引用编辑 | CODE_COMPLETE | T01 |
| T03 | 使高级 dbt 草稿按 ModelSpec 依赖初始化并校验 | CODE_COMPLETE | T01；F4 |

## Definition of Ready

- [x] 业务依赖 owner、实现证据和快照字段已定义。
- [x] 手工/ZIP/系统生成三种 authoring adapter 的汇合点已指定。
- [x] 禁止平行台账、平行 parser 和客户端自报依赖。
- [ ] F0/T03 真实 source binding 与链路样本已归档。

## 完成标准

- [x] FACT 可同时声明 ODS 基础来源和 DIMENSION 固定修订；DWS/ADS 可声明上游固定修订。
- [x] 手工 dbt 提交前展示 declared/parsed 对账，未声明、缺失、过期或成环均 fail closed。
- [x] 手工草稿与系统生成路径复用同一 resolver；ZIP 等价性仍由 IT-15 做真实回归。
- [x] F7、F8、F5 只消费本 Feature 的统一依赖结果，不重新推导自己的依赖图。

## 代码证据（2026-08-18）

- 后端：`ModelImplementationDependencySnapshotResolverTest`、`DbtCanonicalProjectReconstructorTest`、`CanonicalModelLifecycleCompilerAdapterTest` 通过。
- 前端：实现绑定、受管依赖文件和 dependency checksum 契约测试通过。
- 未执行：Chrome 手工链、真实 source binding 与 ZIP 等价性；统一留到 F5 集中 E2E。
