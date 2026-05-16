# dts-metrics 服务拆分设计

## 设计原则

1. platform 是唯一事实源：IAM、资产、数据源、权限、审计、审批、事件和 dbt 发布入口都保留在 platform。
2. dts-metrics 是可选增值服务：负责指标语义建模、行业指标包、DWS/ADS 生成和 BI Dataset 注册。
3. 合作方不接触平台源码：合作方通过 `metric-pack`、配置台和审核流程交付行业指标。
4. SQL 由 `dts-metrics` 受控生成、由 platform/dbt 发布门禁审核：第一版不接受合作方任意 SQL，只接受受控 DSL。
5. 基础版可独立交付：关闭 metrics 后，数据源、ELT、dbt、资产目录和 SQL/dbt 工作流不受影响。

## 服务边界

| 能力 | platform | dts-metrics | analytics |
|---|---|---|---|
| 用户/角色/组织/租户 | 事实源 | 只读调用 | 只读调用 |
| 数据源注册和密钥 | 事实源 | 只引用 source id | 不持有 |
| Catalog/资产目录 | 事实源 | 读取/写入指标资产映射 | 消费 |
| asset_grant | 事实源 | 校验/申请授权 | 校验/消费 |
| 审计/审批/事件 | 事实源 | 发起事件 | 发起事件 |
| dbt 发布 | 网关和门禁 | 生成候选 artifact | 消费结果 |
| 指标口径/公式/行业包 | 不承载业务实现 | 事实源 | 消费 |
| 大屏/看板 | 入口和权限 | 提供语义数据集 | 事实源 |

## Sprint-32 MVP 边界

Sprint-32 只承诺把 `dts-metrics` 作为独立服务跑通，并打通 platform 事实源契约。以下能力必须做薄：

- BI Dataset 注册先落 platform/QueryDataset 契约或候选记录，不承诺 Superset 远端注册。
- dashboard 自动生成只校验 `dashboards.yml` 元数据，不承诺生成完整大屏实例。
- 历史 `semantic_*` 数据迁移先提供 dry-run 和映射报告，不默认执行生产迁移。
- 复杂 SQL 优化、跨事实表 join 优化、指标推荐和合作方在线配置台不进入 MVP。

## API 契约草案

### dts-metrics 对外 API

```text
GET    /api/metrics/health
GET    /api/metrics/domains
POST   /api/metrics/domains
GET    /api/metrics/business-objects
POST   /api/metrics/business-objects
GET    /api/metrics/dimensions
POST   /api/metrics/dimensions
GET    /api/metrics/metrics
POST   /api/metrics/metrics
POST   /api/metrics/models/{id}/generate-artifacts
POST   /api/metrics/models/{id}/preview
POST   /api/metrics/models/{id}/submit-review
POST   /api/metrics/models/{id}/publish
POST   /api/metrics/packs/import
POST   /api/metrics/packs/{id}/validate
POST   /api/metrics/packs/{id}/publish
```

### dts-metrics 调用 platform API

```text
GET  /api/platform/catalog/assets/{assetId}
GET  /api/platform/catalog/datasets/{datasetId}/schema
POST /api/internal/asset-permission/check
POST /api/internal/audit-events
POST /api/internal/dbt/publish-requests
POST /api/internal/bi/datasets/register
GET  /api/internal/capabilities
```

## 数据归属

建议第一版使用独立 schema 或独立库，命名为 `dts_metrics`。实体前缀统一使用 `metric_`，避免继续扩散 `semantic_` 命名。

```text
metric_subject_domain
metric_business_object
metric_object_asset_mapping
metric_dimension
metric_metric
metric_formula
metric_model
metric_model_dimension
metric_model_metric
metric_model_filter
metric_pack
metric_pack_artifact
metric_generated_artifact
metric_publish_record
```

## metric-pack v0.1

```text
metric-pack/
  manifest.yml
  domains.yml
  business-objects.yml
  dimensions.yml
  metrics.yml
  models.yml
  datasets.yml
  dashboards.yml
  docs/
```

必填约束：

- `manifest.yml` 必须声明 pack id、版本、行业、作者、兼容 DTS 版本、依赖资产标签。
- 指标必须声明业务含义、公式、单位、格式、统计粒度、过滤条件、负责人和版本。
- 模型必须引用 platform 已登记资产，不允许引用未登记物理表。
- 第一版不允许 `raw_sql`，只允许受控公式 DSL。
- 维度和指标必须能解析到具体业务对象和来源资产，跨对象指标必须在模型中显式声明来源模型。

## 回滚策略

- platform 保留 `/api/semantic/**` 兼容代理一个 Sprint。
- 代理优先转发到 `dts-metrics`，服务不可用时按配置返回友好禁用状态，不默认执行旧逻辑。
- 已发布 dbt artifact 保留版本记录，回滚时按 publish record 恢复上一版。
- metric-pack 发布必须可撤销，撤销动作只影响指标配置和生成物，不删除底层明细资产。
