# F4：看板版本发布与受众

**优先级**：P0
**状态**：IMPLEMENTED（真实环境验收 BLOCKED）

## 目标

让 Analysis 和 Dashboard 从“可保存对象”升级为可校验、可版本化、可发布、可回退的服务资产；发布时钉定数据集和 Analysis revision，并通过平台既有报表登记统一管理受众、密级和有效期。

## 状态机与持久化

实体生命周期：`DRAFT → PUBLISHED → ARCHIVED`；归档恢复只回 `DRAFT`。
Revision 状态：`DRAFT → PUBLISHED → SUPERSEDED`；`VALIDATING` 为瞬时操作状态，不持久化成可消费版本。

复用 `analytics_revision`，Expand-only 增加：

- `version_no int`
- `status varchar(32)`
- `published_at timestamptz`
- `dependency_snapshot_json text`
- `contract_checksum varchar(64)`

约束：`unique(model, model_id, version_no)`，并确保每实体最多一个 current PUBLISHED；Analysis/Dashboard 实体增加 `lifecycle_status`、`published_revision_id`。回退通过历史 revision 创建新 DRAFT，不覆盖历史。

## API

| API | 输出/约束 |
|---|---|
| `POST /api/analysis/{id}/validate` | `{valid,blockers[],warnings[],dependencySnapshot}`；无持久化发布副作用 |
| `POST /api/analysis/{id}/publish` | 钉定 dataset version/checksum；并发只成功一个 revision |
| `GET /api/analysis/{id}/versions` | 有 read 才可见；已发布快照不可改 |
| `POST /api/dashboard/{id}/validate` | 批量验证全部 Analysis revisions、参数、权限、密级和预算 |
| `POST /api/dashboard/{id}/publish` | 钉定 dependency snapshot，并触发平台登记 |
| `GET /api/dashboard/{id}/versions` | 返回版本、状态、依赖健康、发布人/时间 |

发布 blocker：数据集/分析未发布、checksum 漂移、字段失效、无 write/read/export 所需权限、RLS 参数缺失、classification 冲突、查询预算超限、受众缺失、依赖查询失败。

## 平台受众登记

扩展 `bi_report_link`：`asset_type`、`asset_key`、`asset_version`，唯一 `(engine,asset_type,asset_key)`；保留现有 URL、queryDatasetId/version、部门、角色、密级和到期时间。

`PUT /api/internal/reports/registrations`（SERVICE_INTERNAL）：

```text
ReportRegistrationCommand {
  engine: DTS_BI, assetType, assetKey, assetVersion,
  title, reportType, url,
  queryDatasetId, queryDatasetVersion,
  deptCodes[], roleCodes[], classification, expiresAt, enabled
}
```

命令幂等 upsert，返回 registrationId/version/reconcileStatus。跨服务不假定分布式事务：Analytics publish 使用 outbox/补偿；登记失败不得向消费者暴露半发布资产，维护者看到待 reconcile。

## UI/UX

- Analysis/Dashboard 的“保存、校验、发布”分开；发布按钮只由 capability + valid state 控制。
- 发布抽屉显示依赖版本、blockers/warnings、部门、角色、密级、有效期和将访问的 URL。
- Dashboard 设计器只能添加已发布 Analysis revision；卡片显示 revision/version/checksum 健康。
- 版本历史支持查看与“基于此版本创建草稿”，不提供覆盖式回退。
- 消费列表只显示 PUBLISHED + enabled + 未过期 + 受众/密级匹配；直链后端再次鉴权。
- 列表分页遵循前端既有约定：UI 默认每页 10 条，切换每页条数必须重新拉取并重置到第 1 页。

## Task

| ID | Task | 优先级 | 状态 | 依赖 |
|---|---|---|---|---|
| T01 | 建立分析/看板版本、校验和发布状态机 | P0 | IMPLEMENTED | F2/T01、F3/T01 |
| T02 | 接通看板发布受众与 BI 服务登记 | P0 | IMPLEMENTED（实联验收待完成） | T01、F3/T02 |

## DoR / 完成标准

- [x] 状态机、Expand 字段、约束、API 和 blocker 已定义。
- [x] 平台 ReportLink 是受众 owner，Analytics 不建第二套登记。
- [ ] 迁移脚本具备 expand/backfill/validate/rollback，并通过旧数据 fixture。
- [ ] 并发 publish、登记故障、reconcile、过期和角色负向 IT 通过。
- [ ] Chrome 95 下发布弹窗、版本历史、四态和键盘旅程通过。
