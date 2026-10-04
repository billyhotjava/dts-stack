# Sprint-31 F7: 观测、审计与性能准入契约

## 目标

F7 不新增一套独立监控系统，而是把 Sprint-31 主链路已有的审计、事件、运行面板和服务认证日志收敛成可交付的准入口径：

```text
接入 / ODS / dbt / Catalog / 语义指标 / BI 大屏 / 权限
  -> 关键动作有 audit action code
  -> 运行状态可在 platform-webapp 观测
  -> 大文件和大表能力边界明确
  -> 服务间调用失败能通过结构化日志定位
```

## 运行观测入口

| 能力 | 后端入口 | 前端入口 | 说明 |
|---|---|---|---|
| ELT 链路运行 | `GET /api/platform/sprint27/elt-console` | `/explore/etl` | 聚合 ingestion 运行、队列、失败和超时信息 |
| 指标运营 | `GET /api/platform/sprint27/metric-operations` | `/bi-apps/metrics/operations` | 聚合指标概览、语义对象、模型运行信息 |
| 事件观测 | `GET /api/platform/sprint27/events-console` | `/ops/events` | 展示 platform event outbox 分发状态、失败和审计绑定 |
| 审计证据 | `GET /api/platform/sprint27/audit-evidence` | `/ops/audit-evidence` | 从事件 outbox 反查审计动作覆盖情况 |
| 发布治理 | `GET /api/platform/sprint27/release-governance` | `/ops/release-governance` | 聚合治理门禁、ingestion、dbt release gate、事件分发和指标校验 |

## 审计动作覆盖矩阵

| 链路 | 关键动作 | 审计动作码 / 事件码 | 当前结论 |
|---|---|---|---|
| 数据源与 ODS 预检 | 数据源读取、ODS 预检 | `FOUNDATION_ODS_PRECHECK` | 已有动作码，最终 IT 需补真实请求输出 |
| ingestion 任务 | 创建、执行、失败、血缘同步 | `INGESTION_TASK_CREATE`, `INGESTION_TASK_EXECUTE`, `INGESTION_LINEAGE_SYNC` | 已覆盖服务层和资源层 |
| dbt 开发与发布 | compile/test/docs/run、quality gate、release gate、release submit | `ETL_DBT_COMPILE_EXECUTE`, `ETL_DBT_TEST_EXECUTE`, `ETL_DBT_DOCS_EXECUTE`, `ETL_DBT_MODELS_EXECUTE`, `ETL_DBT_QUALITY_GATE_READ`, `ETL_DBT_RELEASE_GATE_READ`, `ETL_DBT_RELEASE_SUBMIT_EXECUTE` | 已覆盖主操作；发布事件进入 outbox |
| Catalog / 资产门户 | 列表、详情、合约、schema 合约、治理缺口、血缘失败、血缘同步 | `CATALOG_ASSET_LIST`, `CATALOG_ASSET_VIEW`, `CATALOG_ASSET_CONTRACT_VIEW`, `CATALOG_ASSET_SCHEMA_CONTRACT_VIEW`, `CATALOG_GOVERNANCE_GAP_VIEW`, `CATALOG_LINEAGE_FAILURE_REPORT_VIEW`, `CATALOG_LINEAGE_SYNC` | 已覆盖 Sprint-31A 资产事实源读写入口 |
| 语义指标 | 主题域、对象、维度、指标、模型、运行、审核、发布、BI 注册、血缘注册 | `SEMANTIC_*`, `METRIC.SEMANTIC_MODEL.*` | platform 旧入口有审计；Sprint-32 后 `dts-metrics` 需只消费 platform 审计/事件契约 |
| BI 大屏权限 | SCREEN 授权检查、授权写入、analytics fallback | `asset_permission_audit`, `screen.permission.local_fallback`, `VIS_DASHBOARD_SHARE_GRANT` | platform `asset_grant` 为事实源，本地 fallback 命中必须告警 |
| 发布治理 | 治理门禁查看、release console 查看 | `GOV_OPS_RELEASE_GATE_VIEW`, `SPRINT27_RELEASE_GOVERNANCE_VIEW` | 已作为最终上线前聚合入口 |

## 性能准入结论

| 场景 | 当前边界 | 准入结论 |
|---|---|---|
| UI 文件上传 / 解析 | ingestion multipart 默认 `50MB`，CSV/Excel 解析默认 `100000` 行，Excel 注释为 `20MB` | v2.2.3 不承诺通过 UI 直传 100MB/500MB/百万行 |
| 数据标准附件 | platform `DATA_STANDARD_MAX_FILE_SIZE` 默认 `209715200` 字节 | 附件层可到 200MB，但不等于接入解析链路可承诺 |
| SQL/Hive 查询预览 | 查询结果硬上限约 `100000` 行，dbt preview 约 `500` 行 | 只作为预览/抽样，不作为大表导出能力 |
| 100MB CSV 样例 | 需要绕过 UI 解析上限或提升 ingestion multipart/流式解析 | 最终 IT 可作为 P1 验证，不作为当前版本上线阻断 |
| 500MB / 百万行 CSV | 需要流式 parser、分片上传、异步入湖、进度与断点恢复 | 当前明确不承诺；进入后续大文件接入专项 |

当前企业交付口径：

- JDBC/数据库表是主接入路径，大表通过数据库/调度链路处理，不走浏览器内同步解析。
- 文件接入当前定位为中小文件、样例、临时导入；超过当前边界必须进入异步分片/流式接入专项。
- 页面和 API 不能暗示 500MB/百万行已正式可用，避免现场承诺失控。

## 服务间调用失败诊断

platform 入站服务认证由 `ServiceDependencyAuthenticationFilter` 统一处理，结构化日志格式：

```text
event=service_auth_denied service=<service> reason=<reason>
```

已知 reason：

| reason | 含义 | 排查方向 |
|---|---|---|
| `service_unknown` | `X-DTS-Service` 不在可信服务列表 | 检查 `dts.platform.inbound.service-auth.trusted-services` / `trusted-service-names` |
| `endpoint_not_allowed` | 服务名可信，但访问端点不在 allowlist | 检查调用方 URL 和 `ServiceDependencyAuthenticationFilter#isServicePathAllowed` |
| `token_missing` | 缺少 `X-DTS-Service-Token` | 检查调用方 header 注入和 compose/env token 传递 |
| `token_mismatch` | token 与平台配置或动态 token 不匹配 | 检查 `DTS_ADMIN_SERVICE_TOKEN`、服务专用 token 和数据库 svc_token |

建议最终 IT 检索：

```bash
docker logs dts-platform --since 30m 2>&1 | grep 'event=service_auth_denied'
docker logs dts-analytics --since 30m 2>&1 | grep 'analytics_permission_fallback'
```

## 最终验收

最终统一验证时至少归档：

1. `/ops/events` 截图或 `/api/platform/sprint27/events-console` 输出摘要；
2. `/ops/release-governance` 截图或接口输出摘要；
3. `service_auth_denied` 检索结果，要求正常链路为 0；
4. `analytics_permission_fallback` 检索结果，要求迁移后为 0，迁移期命中必须解释；
5. 大文件准入检查结论，必须说明当前版本承诺和不承诺边界。
