# Sprint-94 顺延范围契约交接

- **定稿日期**：2026-08-17
- **性质**：后续 Sprint 输入，不属于 Sprint-94 Feature/Task，不参与本 Sprint 状态与任务统计。
- **阶段门禁**：以 `metabase-retirement-roadmap.md` 为准；不得从本文直接启动实现。

## 1. 去向

| 顺延能力 | 目标阶段 | 启动前置 |
|---|---|---|
| 旧 Card 写入只读兼容准备 | S1 | S0 已建立可查询的观测口，并累计至少 14 天可读数据 |
| collections/models/trash 等兼容重定向 | S2 | S1 期间旧写调用趋零，pilot 部门验证与紧急回切通过 |
| 大屏复用已发布 Analysis revision | S3 | Sprint-94 的 Analysis、网关与 revision 契约已交付，S2 稳定至少 30 天 |
| preview/apply/replay/rollback 兼容迁移 | S3 | 三分类 fixture、备份恢复和漂移检测通过 |
| 表、列与 route handler 物理删除 | S4 | 连续 30 天零调用、全量 inventory、恢复证明和独立变更评审 |

## 2. S3 大屏依赖契约

```text
ScreenAnalysisSource {
  kind: "analysis",
  analysisId: long,
  revisionId: long,
  parameters: object
}
```

- 只能选择具有 `read` 且处于 PUBLISHED 的 Analysis revision。
- Screen published version 的依赖快照钉定 `revisionId + datasetVersion + contractChecksum`，不得静默切换 latest。
- 运行复用 `AnalysisQueryGateway.execute`；Screen 不复制 AnalysisQuerySpec、底层 SQL或权限规则。
- 旧 `{cardId,...}` 由兼容 adapter 读取并标识 `convertible / legacy-read-only / invalid`；不可证明等价的引用不得自动改写。
- 沿用 `analytics_screen_version` 的 published/current 语义；不得另建 Screen 生命周期。
- `ScreenResource.java` 与 `analyticsApi.ts` 已超大，后续必须通过独立 service/resource/client seam 承接，原文件不得继续增长。

### 后续 UI 验收

- 组件面板增加“已发布分析”，展示名称、revision、数据集版本、图表类型、owner 和健康状态。
- loading/empty/error/success 四态完整；旧组件显示“兼容只读”，不暴露 Card/MBQL 技术词。
- revision 失效或权限变化时保留布局并显示 blocker，不自动升级。
- Chrome 95 下选择器、画布 overflow、键盘替代和发布弹窗不得裁剪。

## 3. S3 兼容迁移契约

| API | 语义 |
|---|---|
| `POST /api/admin/analytics/migration/preview` | 只读分类 `convertible / legacy-read-only / invalid`，返回 `previewHash` 和计数 |
| `POST /api/admin/analytics/migration/apply` | `{previewHash,batchSize<=200}`；逐项记录 before/after，重放幂等 |
| `POST /api/admin/analytics/migration/rollback` | `{batchId}`；若后续用户写入则 409 fail-closed |
| `GET /api/admin/analytics/migration/status` | 返回批次、分类、失败、调用量和 rollback 状态 |

允许后续 Sprint 新增两个运维事实表，但不得成为业务 owner：

- `analytics_compat_migration_batch`
- `analytics_compat_migration_item`

每项记录 identity、classification、before/after snapshot、checksum、outcome/errorCode；snapshot 加密并限制访问，敏感 SQL 不进入普通日志或管理 UI。

| 分类 | 判定 | apply 行为 |
|---|---|---|
| convertible | 数据集身份、字段、指标、筛选和聚合可无损映射，结果 schema/checksum 对账通过 | 写入 v1 spec 与钉定引用，保留 before snapshot |
| legacy-read-only | 运行仍合法，但包含 v1 不支持的 MBQL/native 语义 | 保持旧读，禁止新编辑/复制/发布 |
| invalid | 数据源或字段缺失、payload 损坏、权限无法证明 | 不改写，记录稳定 errorCode，阻断引用它的新发布 |

## 4. S1/S2 路由与旧写约束

- S1 只能建立旧写 feature flag 和持续观测，默认保持开启；不得提前关闭。
- S2 重定向必须复用 `LegacyDataModelingRedirect` 模式，保留原 workspace/filter/archive 意图并可一轮发布回切。
- report-factory/metric-lens/nl2sql-eval 是否保留仅由实测调用和管理员需求决定，不以菜单不可见推断无人使用。
- 旧读在 S4 前持续可用；不可证明等价的 MBQL 永久允许保持 legacy-read-only。
- 任一阶段均不得近似转换、静默切 latest 或把 UNKNOWN 记为零。

## 5. 后续 Sprint 的 DoR

- [ ] 前一阶段门禁与持续观测窗口有真实证据。
- [ ] 三分类 fixture、结果等价和恢复路径可重复。
- [ ] 对 Screen、Card/public/embed、路由 resolver、迁移表逐符号执行 fresh impact。
- [ ] HIGH/CRITICAL 风险已向用户报告并完成范围裁剪。
- [ ] Expand/兼容/回滚契约、Chrome 95 UI 和集中 E2E 已进入对应 Sprint，而非借用 Sprint-94 状态。
