# Sprint-93 运行手册（Gate G4）

**状态**：IMPLEMENTED / REHEARSAL_PENDING；健康信号、阈值和处置步骤已固定，真实故障注入与非实现者演练完成前 G4 仍为 GAP。
**Owner**：xiezm（所级数据管理员）；平台运维负责容器、数据库和告警。
**风险**：HIGH；错误操作可能造成消费资格误判，但历史 candidate/run/observation/lineage/audit 不允许硬删除。

## 1. 正常运行基线

- 语义同步 worker 每 30 秒最多 claim 50 条，数据库 claim 硬上限 100，lease 2 分钟。
- 临时失败退避为 1/5/15/30/60 分钟；第五次重试后仍失败（第六次失败）或永久错误进入终态 `SYNC_FAILED + next_sync_at IS NULL`。
- 终态失败同时出现在治理通知和 `MODELING_SEMANTIC_SYNC_TERMINAL_FAILURE` 审计；人工重试使用强 ETag/CAS 并写 `MODELING_SEMANTIC_SYNC_RETRY`。
- 资产目录默认 10 条/页；页面状态聚合只做 semantic store + serving projection 两次批量读取；运营筛选候选最多 5000。
- normalization preview/apply 每批最多 500；推荐 20 → 100 → 500，永不自动 apply。

## 2. 快速健康检查

以下命令只读。API 调用使用已登录会话，不在文档或 shell history 中写明文密码。

```bash
docker compose -f docker-compose-app.yml ps dts-platform dts-platform-webapp dts-analytics dts-openmetadata
curl -fsS "${DTS_BASE_URL}/management/health"
curl -fsS -H "Cookie: ${DTS_SESSION_COOKIE}" "${DTS_BASE_URL}/api/catalog/assets-v2?page=0&size=10"
curl -fsS -H "Cookie: ${DTS_SESSION_COOKIE}" "${DTS_BASE_URL}/api/catalog/asset-normalization-migrations/semantic-projection/preview?limit=20"
```

数据库状态（容器读取既有数据库环境变量）：

```bash
docker exec -i v223-dts-pg-1 sh -lc 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1 -c "
select sync_status,
       (next_sync_at is null) as terminal,
       count(*) as rows,
       max(sync_attempts) as max_attempts,
       max(updated_at) as last_update
  from modeling_catalog_model_serving_projection
 group by sync_status, (next_sync_at is null)
 order by sync_status, terminal;
"'
```

## 3. 告警规则

| 告警 | 阈值 | 级别 | 含义 | 首要动作 |
|---|---|---|---|---|
| SemanticPendingAge | 任一 `SYNC_PENDING` 超过 5 分钟；部署后抑制 2 分钟 | P1 | worker 未运行、lease 卡住或 Analytics 不可达 | 查 worker 开关、日志和下游健康；不要 SQL 改状态 |
| SemanticTerminalFailure | 任一 `SYNC_FAILED AND next_sync_at IS NULL` 新增 | P1 | 永久错误或五次重试耗尽 | 从治理通知进入模型页，按 errorCode 修复后受控重试 |
| SemanticFailureBurst | 5 分钟 ≥5 个失败，或单资产 10 分钟 ≥3 次 | P1 | 下游/契约系统性故障 | 暂停 worker，保留队列和审计，检查 Analytics |
| OrphanServingRef | 任一 servingRef 的 physicalAssetId/AssetKey 无唯一 dataset | P0 | 资产身份断链，可能误服务 | 禁止继续发布，运行 reconciliation |
| GovernanceEvidenceGap | `QUALITY_PASSED` 候选缺 pinned run/checksum，任一即告警 | P0 | 发布结论不可证明 | 阻塞发布，新建质量 run；不改历史快照 |
| FieldLineageGap | 已有 VERIFIED 表边但字段边连续 2 次同步仍为 0 | P2 | manifest 映射缺失/跳过 | 查 skip reason 和 schema drift，不生成伪边 |
| CatalogCountDrift | 同一 asOf 概览 total 与目录/统计差异 >0 | P1 | 读投影或分页事实不一致 | 关闭 status view，运行 stats reconcile |
| OpenMetadataDegraded | 3 次连续同步失败 | P2 | 技术元数据缓存不可用 | 保持 DTS 资产可查，恢复后按 AssetKey 重放 |

同一 assetKey/errorCode 在 10 分钟内聚合为一个告警；终态失败必须保留一条治理通知，不因告警抑制而丢失。

## 4. 必需日志与审计字段

日志至少包含：`correlationId`、`tenantId`、`assetKey`、`modelSpecId`、`projectionVersion`、`attempt`、`errorCode`、`nextAttemptAt`。发布/质量/物化链另需：`candidateId/version`、`qualityRunId`、`evidenceRef`、`outcomeCode`、`actor`。

关键检索：

```bash
docker compose -f docker-compose-app.yml logs --since=30m dts-platform \
  | rg 'Catalog semantic sync|MODELING_SEMANTIC_SYNC|correlationId|projectionVersion'
docker compose -f docker-compose-app.yml logs --since=30m dts-analytics \
  | rg 'semantic|publish|error|correlation'
```

不得在日志中打印 SQL、凭据、连接串、样例敏感值或完整未脱敏 payload。

## 5. 故障处置

### A. `SYNC_PENDING` 超过 5 分钟

1. 检查 `DTS_MODELING_CATALOG_SEMANTIC_SYNC_ENABLED` 和两个容器健康；确认 worker 日志每 30 秒有 tick 或批次结果。
2. 查询该 modelSpecId 的 status API，记录 ETag、projectionVersion、attempt、nextSyncAt 和 correlationId。
3. 核对 servingRef、physicalAssetId 与稳定 AssetKey；缺失/冲突属于永久错误，先修身份证据。
4. Analytics 恢复后等待到期重试；只有 `SYNC_FAILED` 才由有权限用户使用当前 ETag 触发受控重试。
5. 禁止直接 update `sync_status='SYNCED'`，禁止删除 projection 或 servingRef。

### B. 终态语义同步失败

1. 在治理通知确认 `SEMANTIC_DELIVERY` 问题和对应模型修复深链。
2. 按 `terminalReason` 区分永久契约错误与重试耗尽；修复身份、字段、聚合方式或下游可用性。
3. 重新 GET status 获取强 ETag，再 POST `/api/modeling/model-specs/{id}/serving-sync/retry`；重复点击必须幂等。
4. 核对新 version、`SYNC_PENDING → SYNCED`、人工重试审计和旧 worker CAS miss。

### C. 目录有资产但缺语义状态

1. 调用 preview，不直接 apply；记录 totalPending、previewHash、resolutionStatus/reasonCodes。
2. 仅对 producer/evidence/AssetKey 唯一的行按 20 条批次 apply，并记录 X-Correlation-Id。
3. apply 后对账 dataset/projection/stats/issue；随后 rollback/reapply 验证 batch 边界。
4. previewHash 或 projectionVersion 漂移立即停止，转人工；禁止全表猜测回填。

### D. 工程测试通过但治理质量阻塞

1. 在发布面板分别检查工程验证与治理数据质量；dbt test 不能替代 GovQualityRun。
2. 核对 ruleVersion、binding、datasetId、runId、finishedAt、maxAge 和 checksum。
3. 失败/过期时触发新 run；确认新 runId，旧 candidate snapshot 不变。
4. 只有策略要求的两类证据都通过，才允许 self-publish/评审发布。

### E. OpenMetadata 不可用

1. 确认 DTS `/api/catalog/assets-v2`、资产详情和本地血缘仍返回 200；资产数量不得下降。
2. 记录 OM 同步错误，但不得修改 publication/lifecycle/eligibility 或删除 cache 映射。
3. OM 恢复后按稳定 AssetKey 重放；核对 datasetId 不变且不会重复建资产。

### F. 概览、目录和详情不一致

1. 记录各响应 `asOf`、筛选条件、page/size 和同一 AssetKey。
2. 关闭 `DTS_PLATFORM_CATALOG_ASSET_STATUS_VIEW_ENABLED` 进行兼容读对照；不清缓存、不改业务数据。
3. 运行 stats reconcile，核对 projection 新鲜度和状态批量读是否缺失。
4. 若运营筛选候选达到 5000，缩小筛选范围并按容量事件处理，禁止提升为无上限扫描。

## 6. 容量和失败预算

| 项目 | 预算 |
|---|---:|
| worker 单轮 claim | 50（仓储硬上限 100） |
| worker lease | 2 分钟 |
| worker 周期 | 30 秒 |
| 自动重试 | 1/5/15/30/60 分钟，之后终态 |
| observation/backfill 单批 | 1～500 |
| status read | 当前页两次批量查询，不允许逐行 N+1 |
| 运营筛选候选 | ≤5000 |
| 目录默认分页 | 10 条/页，服务端 0-based |

批次处理超过 30 秒、数据库连接池持续高于 80%、或候选集合命中 5000 上限时，停止扩大批量，先降低 worker/迁移速率并分析查询计划。

## 7. 回滚与恢复确认

- 按 `assets/release-plan.md` 的前端 → read switch → consumer → 本批 backfill 顺序执行。
- 回滚后验证旧 API 200、资产总数不变、历史 candidate/run/observation/lineage/audit 均存在。
- 恢复后 worker 必须从耐久状态继续，不重复创建 ModelSpec、dataset 或稳定 AssetKey。
- 演练结果登记 IT-12：commit、image、batchId、开关、时间、actor、日志和结果。

## 8. 禁止操作

- 禁止 truncate/清库、全表无条件 update、删除投影/outbox/历史证据。
- 禁止手工把 PENDING/FAILED 改成 SYNCED、把 PUBLISHED 冒充 ONLINE。
- 禁止用 dbt build/test 代替治理质量，或修改旧质量 run/候选快照。
- 禁止在 OM 故障时删除 DTS 资产或人工 VERIFIED 血缘。
- 禁止跳过 previewHash、batchId、ETag/CAS、权限 guard 或审计。

最终集中 E2E 完成故障注入和非实现者照本演练后，才将本文件状态和 Gate G4 更新为 PASS。
