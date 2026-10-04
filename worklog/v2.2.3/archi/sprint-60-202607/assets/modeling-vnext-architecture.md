# 建模 vNext 架构说明

## 1. 领域对象

| 对象 | 含义 | 不负责什么 |
|---|---|---|
| BusinessProcess | 业务生命周期，例如项目节点计划闭环 | 不保存 SQL，不等于项目空间 |
| BusinessObject | 业务上被管理的实体、事实或快照 | 不等于物理表 |
| WarehousePlan | 主题域、数仓层、建模意图和目标粒度 | 不生成运行任务 |
| StandardBinding | 字段标准、类型、码表、密级和质量约束 | 不负责 Join |
| ModelSpec | 可编译的模型设计规格 | 不直接替代 dbt 运行时 |
| DbtArtifact | SQL、schema、tests、docs 和 manifest 绑定 | 不承担业务审批 |
| PipelineRun | Addax、dbt、Airflow 的运行证据 | 不修改模型语义 |

## 2. 双模式所有权

### DESIGNER_GENERATED

```text
BusinessObject + StandardBinding + ModelSpec
  -> deterministic compiler
  -> dbt SQL/schema/tests/docs
```

ModelSpec 是事实源，生成物带 checksum 和 revision。

### DBT_MANAGED

```text
dbt SQL + manifest.json
  -> importer
  -> ModelSpec registration + lineage + field snapshot
```

SQL 是事实源。DTS 不能静默改写 SQL；模型字段、粒度或来源变化必须产生 drift 事件。

## 3. 运行边界

```text
Addax: source -> PostgreSQL ODS
dbt:   ODS/STG -> DWD -> DWS -> ADS
Airflow: Addax task -> dbt parse/run/test -> evidence callback
DTS:   spec, artifact, audit, lineage, run evidence
```

## 4. 兼容策略

- 旧 `/api/semantic/*` 保持读取和兼容写入，内部转换为新 ModelSpec 视图。
- 旧 dbt 项目通过 manifest 导入，不要求重写 SQL。
- 新生成模型不复用旧模型 ID；通过 `legacyRef` 保存原模型来源。
- 旧模型可以标记为 `LEGACY_READONLY`，继续运行但不参与新设计器编辑。
