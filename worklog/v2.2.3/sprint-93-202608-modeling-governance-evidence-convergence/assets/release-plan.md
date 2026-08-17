# 发布安全计划（Gate G3）

**当前状态**：READY_FOR_REHEARSAL；代码和开关已具备，真实发布/回滚演练与 IT-12 完成前 G3 仍为 GAP。
**变更类型**：兼容扩展、耐久消费者、受控数据补齐、兼容 UI 读切换。
**风险等级**：HIGH；涉及资产消费资格、模型发布证据和存量语义状态，但不删除历史事实。
**发布负责人/回滚负责人**：xiezm / 平台运维。

## 1. 不变量和影响半径

- `catalog_dataset` 继续拥有物理目录身份，`catalog_asset_semantic_projection` 只承载正交状态；不新建平行资产台账。
- 同一 ModelSpec 二次物化只新增 revision/candidate/attempt/observation，不新增 ModelSpec 或稳定 AssetKey。
- 工程质量与治理数据质量分别判定；旧候选快照、质量 run、血缘和审计只追加，不原地改写。
- OpenMetadata 是技术元数据同步边界，不是 DTS 资产身份的唯一来源；其故障不得删除或隐藏 DTS 资产。
- 读路径最多对当前页执行两次批量状态查询；带运营状态筛选的候选集合硬上限为 5000。

## 2. Expand → Migrate → Contract

| 阶段 | 本 Sprint 动作 | 兼容性/退出条件 |
|---|---|---|
| Expand | 部署兼容 DTO、QualityEvidence Port、语义同步 worker、状态批量读、审计动作和受控迁移 API | 旧字段/路由仍保留；数据库无 destructive change |
| Shadow/Observe | 保持既有资产登记 owner；开启 worker，观察 `SYNC_PENDING/SYNC_FAILED/SYNCED` 和差异日志 | 不通过 SQL 改业务状态；终态失败进入治理通知和审计 |
| Backfill | preview 后按 20 → 100 → ≤500 批次 apply；每批使用 previewHash、batchId 和 correlationId | 歧义、缺 producer/evidence、状态漂移 fail-closed |
| Read switch | `DTS_PLATFORM_CATALOG_ASSET_STATUS_VIEW_ENABLED=true` 后，概览/目录/详情读取统一状态 | 同一 asOf、分页、筛选和旧查询参数回归通过 |
| Contract | 本 Sprint 不删除旧字段、旧路由、旧事实或历史兼容读 | 需另行 ADR、观测窗口和批准 |

## 3. 独立开关

| 开关/控制面 | 默认 | 关闭后的行为 | 用途 |
|---|---:|---|---|
| `DTS_MODELING_CATALOG_SEMANTIC_SYNC_ENABLED` | `true` | worker 不启动；投影事实保留，可从 UI 受控重试 | 停止模型到 Analytics 的自动同步 |
| `DTS_MODELING_CATALOG_SEMANTIC_SYNC_DELAY_MS` | `30000` | 仅改变轮询周期 | 降低下游压力，不改变重试预算 |
| `DTS_PLATFORM_CATALOG_ASSET_STATUS_VIEW_ENABLED` | `true` | 状态聚合 bean 不加载，目录回到兼容基础字段 | 独立回滚资产状态读切换 |
| normalization apply | 无自动任务 | 未显式调用就不会写入 | Backfill 天然为人工批次开关 |

两个布尔开关必须能独立切换：可以保留 worker 而关闭新读视图，也可以保留统一读视图而暂停 worker。

## 4. 发布顺序

1. 冻结发布窗口并登记 `commit/image/actor/startedAt`；导出下列只读基线：dataset/projection 数、服务同步状态、质量证据、表/字段血缘、统计 asOf。
2. 部署 dts-platform（审计字典、后端兼容扩展）；暂设 `DTS_MODELING_CATALOG_SEMANTIC_SYNC_ENABLED=false`、`DTS_PLATFORM_CATALOG_ASSET_STATUS_VIEW_ENABLED=false`。
3. 验证 Liquibase、健康检查和旧 API；确认旧目录/详情仍可用。
4. 开启 semantic sync，观察至少两个 30 秒周期；终态失败必须出现错误码、attempt、correlationId、治理通知和审计。
5. 执行 normalization preview；首批 20 apply 后立即对账并 rollback/reapply，随后才允许 100、≤500。
6. 开启 asset status view；验证默认 10 条分页、运营筛选、概览 asOf、详情和模型深链。
7. 部署 dts-platform-webapp；以 xiezm 从真实菜单完成 IT-01～13。
8. 所有 NO-GO 条件均未命中后才扩大使用范围并登记 `result=PASS`。

## 5. 回滚顺序

1. 回滚 dts-platform-webapp 到保留的前一镜像；后端扩展字段仍兼容。
2. 设置 `DTS_PLATFORM_CATALOG_ASSET_STATUS_VIEW_ENABLED=false`，重建单一 dts-platform 容器，验证旧目录读路径。
3. 设置 `DTS_MODELING_CATALOG_SEMANTIC_SYNC_ENABLED=false`；不删除 pending/failed/synced、outbox 或 servingRef。
4. 对本次 batchId 调用 rollback；若 projectionVersion 漂移，停止并转人工，禁止强删。
5. 保留 candidate、quality run、physical observation、lineage 和审计历史；恢复后使用新请求/新 run/新 attempt 重试。

## 6. 数据迁移安全

- `preview.limit`、`apply.limit` 仅允许 1～500；501 必须失败。
- apply 只接受与当前数据一致的 previewHash，且只处理 `UNIQUE_RESOLUTION`。
- producer、evidence、稳定 AssetKey 任一缺失都不推断回填；产生明确 issue。
- rollback 仅删除该 batch 创建且 version 未漂移的 projection，并重新打开对应 issue。
- apply/rollback 分别写 `CATALOG_ASSET_SEMANTIC_BACKFILL_APPLY/ROLLBACK` 严格审计。
- 禁止 truncate、全表 update、直接把 serving 状态改为成功或覆盖人工治理字段。

## 7. NO-GO 条件

- 同一 AssetKey 对应多个 datasetId，或首次/二次物化导致稳定模型/资产数量增加。
- previewHash 漂移、歧义/缺证据资产仍被 apply、回滚覆盖后续用户修改。
- 旧 projection version 能覆盖新 servingRef，或 `SYNC_PENDING` 被显示为已上线。
- 工程质量仍被标为治理数据质量通过；候选缺少钉定 ruleVersion/binding/run/checksum。
- OpenMetadata 故障导致 DTS 目录 5xx、资产消失或本地血缘被删除。
- 概览、目录、详情在同一 asOf 无法对账，或分页/运营筛选出现 N+1/越界扫描。
- 缺失分类审计、correlationId、回滚证据、Chrome 95 或 xiezm 真实操作证据。

## 8. 演练记录（IT-12）

| 字段 | 当前值 |
|---|---|
| releaseId | `PENDING_FINAL_E2E` |
| commit / images | 待集中 E2E 前冻结 |
| migration batches | 待执行首批 20 的 apply/rollback/reapply |
| flags before/after | 待记录两个独立开关 |
| startedAt / rolledBackAt / recoveredAt | 待记录 |
| actor | xiezm |
| result | PENDING |

真实值、API/SQL 输出、截图和日志统一登记在 `it/README.md`；未完成回滚演练前不得把 G3 标为 PASS。
