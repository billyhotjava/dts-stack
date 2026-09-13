# F1：普通实现可运行 dbt 制品

**优先级**：P0
**状态**：DONE
**依赖**：F0 GO

## 目标

让当前 `DESIGNER_GENERATED` ModelImplementation 的输入、字段映射和上游依赖生成可重现、可执行且准确表达目标关系的 dbt graph/bundle，同时保持 ModelSpec/Implementation 为唯一 canonical owner。

## 契约定义

| 类型 | 契约 | 关键字段 |
|---|---|---|
| 校验 | `ImplementationValidationView` 兼容扩展 | `valid,code,blockers,executionPlan` |
| compiler | `ModelLifecycleCompilerPort.compile` | current model + current implementation |
| artifact | `modeling_dbt_artifact` | implementation revision/checksum、content checksum |
| bundle | `DbtScopedProjectService.prepareCandidate` | candidate entries → projectDir/selector/bundleChecksum |
| dbt meta | SQL `config(meta=...)` | modelSpecId、model/implementation revision/checksum、candidate 在运行时注入 |

## UI/UX 规格

数据实现页验证时显示：

- 可执行：目标关系名、有效物化方式、adapter、分区/保留能力；
- 不可执行：一个根因一个 blocker，包含具名修复动作；
- compile 成功只显示“已生成构建制品”，不得显示“已建表”。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|---|---|---|---|---|
| T01 | 对齐实现设置与执行能力校验契约 | P0 | DONE | F0/T02 |
| T02 | 生成准确 alias、materialization 与生命周期 meta | P0 | DONE | T01 |
| T03 | 将 lifecycle artifact 注入可重现 scoped dbt project | P0 | DONE | T02 |

## Definition of Ready

- [x] settings 和 capability 行为已在 Sprint §5 固定。
- [x] compiler 输入输出和错误码已命名。
- [x] overlay seam 已选择，禁止另造 SQL 模型 owner。
- [x] F0 架构复审为 DEV/TEST 实施 GO。

## 完成标准

- [x] 真实 UI payload 不再触发 `IMPLEMENTATION_SETTING_NOT_ALLOWED`。
- [x] targetPhysicalName 精确进入 alias。
- [x] 物理来源与上游模型分别投影为 `source()/ref()`，manifest graph 可追溯。
- [x] unsupported SNAPSHOT/partition fail-closed。
- [x] 相同输入产生相同 artifact/bundle checksum。
- [x] compile 过程中不写 pipeline run、relation observation 或 physical asset。

## 完成证据

- T01：`../../it/evidence/f1-t01-execution-plan/README.md`
- T02：`../../it/evidence/f1-t02-compiler-artifacts/README.md`
- T03：`../../it/evidence/f1-t03-scoped-dbt-project/README.md`

F1 只证明“current canonical implementation 可形成真实可运行 dbt bundle”；尚未创建
pipeline run、提交 Airflow、核验目标关系或登记物理资产，这些仍由 F2～F4 关闭。
