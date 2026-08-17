# T02：接通看板发布受众与 BI 服务登记

**优先级**：P0
**状态**：DRAFT
**依赖**：T01、F3/T02

## 用户可测试目标

A2 发布 Dashboard 时配置部门、角色、密级和有效期；Analytics 以稳定资产身份幂等登记到平台 `bi_report_link`。A3 在列表和直链可消费，A4 均不可见/403；登记失败或过期不形成“看似已发布”的服务。

## 数据/API 契约

- Expand `bi_report_link(asset_type,asset_key,asset_version)`，唯一 `(engine,asset_type,asset_key)`；保留既有 queryDatasetId/version、URL、dept/role/classification/expires/enabled。
- 内部 `PUT /api/internal/reports/registrations` 使用 F4 README command；仅 SERVICE_INTERNAL，幂等 upsert。
- `assetKey` 使用稳定 Catalog/Analytics identity，不以 title/url 猜匹配。
- Analytics publish 写本地 revision + outbox；平台 registration 成功后进入 `PUBLISHED/AVAILABLE`；失败为 `PUBLISHED/PENDING_REGISTRATION`，消费者不可见，reconciler 重试。
- disable/archive/expiry 同步更新消费资格；历史 revision 和 audit 保留。

## UI/UX

- 发布 drawer 展示依赖快照、blockers/warnings、部门/角色多选、classification、expiresAt 和 preview URL。
- 分类不得低于依赖最高密级；空受众按政策阻断，禁止默认全员。
- 发布后列表显示版本、受众摘要、有效期、registration sync 状态；pending 可由有权限用户重试。
- 消费者列表只有可消费项；A4 直链后端 403，而不是返回空 Dashboard。
- 平台既有 BiLinks 页面显示 asset type/key/version 与 reconcile 状态，不暴露内部 SQL。

## RED → GREEN

1. RED：ReportLink 主要以 URL/engine 标识，Dashboard 发布未形成统一受众登记。
2. GREEN schema/service IT：相同 assetKey 重放只更新一条；URL/title 变化 identity 不漂移。
3. GREEN cross-service fault IT：平台超时/500、重复 outbox、乱序 disable，最终一致且不半公开。
4. GREEN auth IT：A3 列表+直链+交互允许；A4 列表过滤、直链/export 403；过期 ≤60s 生效。
5. GREEN Chrome95：发布 drawer、错误/pending/retry、键盘/焦点和受众摘要通过。

## 影响范围与先决 impact

预计跨模块 owner：Platform `BiReportLink`/ReportsResource/service/DTO/Liquibase，Analytics Dashboard publication/outbox/client，webapp publish UI/BiLinks。逐 symbol fresh impact；不得覆盖现有 Tableau/Superset/PowerBI 等 engine 行为。

## Definition of Done

- [ ] Expand/backfill/唯一约束兼容现有 1 条 DTS_BI 和其他 engine fixture。
- [ ] registration service-auth、幂等、故障/reconcile/disable/expiry 测试通过。
- [ ] 三角色列表/直链/export 正负向通过，classification 无降级。
- [ ] pending registration 不向消费者暴露，维护者可诊断和重试。
- [ ] 平台仍是受众 owner，无 Analytics 平行登记表。
