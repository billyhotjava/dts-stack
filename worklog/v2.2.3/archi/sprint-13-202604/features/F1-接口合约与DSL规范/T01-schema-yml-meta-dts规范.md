# T01: dbt `schema.yml meta.dts` 扩展规范

**优先级**: P0
**状态**: READY
**依赖**: 无

## 目标

定义 dbt `schema.yml` 里 `meta.dts` 命名空间下的全部字段——这是本项目的"**语义层契约**"。工程师在这里声明：哪些 model 对外开放、哪些列是 measure/dimension、表之间的 join 关系、密级标签、审批元信息。

## 交付物

`worklog/v2.2.3/sprint-13-202604/assets/specs/01-schema-yml-meta-dts.md`

## 规范内容大纲

### 1. 顶层约定

- 命名空间：`meta.dts`（避免与 dbt 其他 package 冲突）
- 兼容性：本规范不破坏 dbt 原生 `columns`/`tests`/`description` 字段
- 版本：`meta.dts.spec_version: "1"`（未来升级用）
- 位置：model 级和 column 级都可以写

### 2. Model 级字段

| 字段 | 类型 | 必填 | 说明 |
|---|---|:---:|---|
| `spec_version` | string | ✅ | 固定 `"1"` |
| `exposed_to_modeler` | bool | ✅ | 是否对分析师建模画布可见；默认 `false` |
| `security_level` | enum | ✅ | `PUBLIC \| INTERNAL \| SENSITIVE \| CONFIDENTIAL`（对齐 `DataSecurityLevel`） |
| `subject_area` | string | 推荐 | 主题域 code，对齐 `CatalogDomain` |
| `grain` | string | 推荐 | 人类可读的粒度描述（如 `"order × day"`） |
| `row_security_predicate` | string | 可选 | SQL 片段模板，`{{user.dept_id}}` 等占位（由 SecurityInjector 渲染） |
| `joins` | list | 可选 | 见 3 |
| `metrics` | list | 可选 | model 级指标（不绑单列，如 count_distinct composite） |
| `description_rich` | string | 推荐 | LLM 预留字段（Sprint-13 不用） |
| `synonyms` | list\<string\> | 可选 | LLM 预留字段 |
| `sample_queries` | list | 可选 | LLM 预留字段 |

### 3. `joins` 字段 schema

```yaml
joins:
  - to: dim_customer              # 目标 model 名
    type: many_to_one             # many_to_one | one_to_many | many_to_many | one_to_one
    on: "{{this}}.customer_id = {{to}}.customer_id"
    relationship: inner           # inner | left
    fanout_warning: false         # 当 type=one_to_many 时建议 true
    approval_required: false      # 是否要经过审批才能在画布上使用
    description: "订单到客户主数据"
```

**约束：**
- `{{this}}` / `{{to}}` 必须用占位，不能硬编码表名（方便改名）
- `type` 必须与实际 FK 基数一致——CI 会用 `dbt run-operation check_join_cardinality` 校验
- `many_to_many` 必须指定中间表（写在 `through` 字段），否则 ManifestIngestor 拒绝加载

### 4. Column 级字段

#### 4.1 `metric` 子字段（measure 声明）

```yaml
columns:
  - name: revenue_cents
    description: 营收（含税，分为单位）
    meta:
      dts:
        metric:
          type: sum                           # sum | avg | count | count_distinct | min | max | custom_sql
          label: 营收
          label_en: Revenue
          description_rich: "..."             # LLM 预留
          format:
            type: currency_cny
            scale: 100                        # 原值 / 100 = 元
          security_level: INTERNAL            # 不写时继承 model 级
          synonyms: ["销售额", "GMV"]         # LLM 预留
```

**`custom_sql` 用法（慎用）：**
```yaml
metric:
  type: custom_sql
  sql: "SUM(CASE WHEN status='paid' THEN amount_cents ELSE 0 END)"
  label: 付费营收
```
- `custom_sql` 必须通过 linter 检测（禁止子查询、禁止 FROM、禁止分号等）
- `custom_sql` 的 measure 在 fanout 场景会被 SymmetricAggregate 特殊处理

#### 4.2 `dimension` 子字段

```yaml
columns:
  - name: order_date
    meta:
      dts:
        dimension:
          type: time                      # categorical | time | numeric | boolean
          label: 下单日期
          granularities: [day, week, month, quarter, year]
          timezone: Asia/Shanghai         # 仅 time 类型
          link_to:                        # 外键跳转（categorical / time 才有意义）
            model: dim_date
            on: order_date = dim_date.date_key
          security_level: INTERNAL
```

**dimension.type 枚举：**
- `categorical` — 离散类别（店铺、商品、省份）
- `time` — 时间戳/日期，必须有 granularities
- `numeric` — 连续数值（可做 binning）
- `boolean` — 只有真假

### 5. Linter 规则（T02 会把这些 CI 化）

| 规则 | 级别 |
|---|:---:|
| `spec_version` 缺失 | ERROR |
| `security_level` 缺失 | ERROR |
| `metric.label` 缺失 | ERROR |
| `metric.type=custom_sql` 但 sql 含子查询/FROM/; | ERROR |
| 同一 model 下 metric.label 重名 | ERROR |
| 跨 model metric.label 重名 | WARNING |
| `join.on` 两端列类型不一致 | ERROR |
| `exposed_to_modeler=true` 但无 `subject_area` | WARNING |
| `dimension.type=time` 但无 granularities | ERROR |

### 6. 样例

**完整样例**（放在 spec 附录）：`ads_sales_daily` 含 2 个 metric、3 个 dimension、2 个 join。
**反例**（spec 附录）：展示 linter 触发的典型错误。

### 7. 与 GovIndicatorDefinition 的映射表

```
meta.dts.metric.label           → GovIndicatorDefinition.name
meta.dts.metric.type            → GovIndicatorDefinition.aggregation_type
meta.dts.security_level         → GovIndicatorDefinition.security_level
model.name + column.name        → GovIndicatorDefinition.source_column_ref
manifest.unique_id              → GovIndicatorDefinition.dbt_unique_id  (新增字段)
dbt compile 时的 sql             → GovIndicatorDefinition.resolved_sql  (快照)
```

### 8. Open Questions（评审会必答）

1. `GovIndicatorDefinition` 的原生 UI 新建入口是否立刻 disable？还是保留 read-only？（建议：保留但打警告）
2. `custom_sql` 是否完全禁止？（建议：允许但默认需要 approver）
3. `synonyms` / `description_rich` / `sample_queries` 预留字段在 Phase 1 是否纳入 linter 检查？（建议：不纳入，仅保留 schema 空间）
4. 是否支持 dimension 级 `security_level`？（建议：支持但列级继承 model 级）
5. `joins.through`（多对多中间表）本 Sprint 是否实现？（建议：schema 预留，实现放 Sprint-14）

## 影响范围

- 新建 spec 文件：`assets/specs/01-schema-yml-meta-dts.md`
- 示例 schema.yml：`it/sample-schema-yml/ads_sales_daily.yml`, `dim_customer.yml`, `fct_orders.yml`
- 评审记录：`assets/f1-review-minutes.md`
- 无代码变更（本 Task）

## 验证

- [ ] 3 张示例 schema.yml 按新规范写完
- [ ] spec 文件含完整字段表、样例、反例、linter 规则、open questions
- [ ] 跨职能评审完成，open questions 全部有 resolution
- [ ] spec `CHANGELOG.md` 标记 `v1.0 frozen`

## 完成标准

- [ ] spec markdown 在 `assets/specs/` 下
- [ ] 3 份 `schema.yml` 样例在 `it/sample-schema-yml/`
- [ ] 评审纪要签字（至少数据工程 + 后端 + 前端 + 产品各 1 人 sign-off）
- [ ] 本 task 状态改为 DONE 并通知 F2/F3/F4/F5 owner 开工
