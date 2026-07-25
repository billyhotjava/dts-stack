# 模型包架构与转换策略

## 1. 总体流程

```text
dbt project
  └─ dbt parse/compile/docs generate
       ├─ manifest.json
       ├─ catalog.json
       └─ schema.yml / meta.dts
              ↓
       DTS package generator
              ↓
       dts-model-package.json
              ↓
       preview → human confirmation → apply
              ↓
       ModelSpec → ModelImplementation → dbt artifact / physical evidence
```

## 2. 包结构

```json
{
  "schemaVersion": "dts.model-package/v1",
  "packageId": "pm-analytics-v3",
  "packageChecksum": "sha256",
  "dbt": {
    "projectName": "pm_analytics_v3",
    "manifestVersion": "v12"
  },
  "defaults": {
    "planRef": "由导入向导绑定",
    "domainRef": "由导入向导绑定"
  },
  "sources": [],
  "technicalNodes": [],
  "models": []
}
```

每个模型必须保存：

- dbt `unique_id`、resource path、raw/compiled SQL checksum。
- 名称、描述、目标层、物化方式、tags、columns、tests 和 dependencies。
- 显式 DTS 业务语义：模型类型、粒度、字段作用、事实时间、消费场景、维度策略。
- 转换结论、阻断原因和人工覆盖来源。

包内不得保存租户数据库 UUID 作为可跨环境真值。`plan/domain/sourceBinding` 在导入目标环境中通过稳定业务标识解析并在预检结果中显示。

## 3. `meta.dts` 最小语义

SQL 无法可靠推断以下业务信息，因此必须由 `schema.yml` 的 `meta.dts` 或导入预检人工补齐：

```yaml
meta:
  dts:
    model_type: FACT
    grain:
      statement: 每行代表一条预算台账快照
      keys: [budget_id]
    fact_shape: PERIODIC_SNAPSHOT
    time_semantics:
      type: SNAPSHOT_DATE
      fields: [source_imported_at]
    domain_code: PROJECT_MANAGEMENT
```

禁止仅依据 `dim_`、`dwd_`、目录名或 SQL 形态自动猜测粒度与业务类别后直接写入。

## 4. 转换分类

| 分类 | 判定 | 应用结果 |
|---|---|---|
| `DESIGNER_GENERATED` | 仅安全字段选择、别名、cast、受控 join/去重，且业务语义完整 | ModelSpec + 普通实现 |
| `DBT_BACKED` | 含聚合、窗口、CASE、常量、宏、复杂表达式或自定义 materialization | 普通 ModelSpec + DBT_MANAGED implementation/artifact |
| `BLOCKED` | 缺粒度、来源、业务分类、依赖，或存在循环/跨租户/所有权冲突 | 只出现在预检，不允许 apply |

分类器必须给出稳定 reason code，不能只返回自然语言。

## 5. STG 与技术节点

- `STG`、`ephemeral`、macro 和 test 属于技术实现图，不创建四类 ModelSpec。
- 技术节点必须进入 package 的 `technicalNodes`，保留依赖、SQL checksum 和资源路径。
- 下游 canonical 模型引用 STG 时，artifact/compile 图必须可解析；禁止为了目录整洁而从包中丢弃 STG。
- 旧 ODS/STG ModelSpec 不在本 Sprint 自动迁移。

## 6. PJM 黄金样例

预检应形成：

| dbt 节点 | DTS 结果 |
|---|---|
| `stg_pm__budget_v2` | `TECHNICAL_ONLY` |
| `biz_dwd_budget_v2` | `FACT@DWD + DBT_BACKED` |
| `biz_dws_budget_v2` | `SUMMARY@DWS + DBT_BACKED` |
| `biz_ads_budget_kpi_v2` | `APPLICATION@ADS + DBT_BACKED` |
| `biz_ads_budget_derived_v2` | `APPLICATION@ADS + DBT_BACKED` |
| `dim_node_type_v2` | 缺静态生成器时 `DIMENSION@DWD + DBT_BACKED` |

## 7. 一致性与幂等

- `packageChecksum` 覆盖规范化 manifest 元数据、模型语义、字段、依赖、配置和 SQL checksum。
- 每个候选使用 `batchId + dbtUniqueId` 派生稳定 idempotency key。
- 同键同载荷返回原结果；同键异载荷返回冲突。
- apply 必须重新计算 preview hash，避免预检后来源、模型 revision 或包内容发生变化。
