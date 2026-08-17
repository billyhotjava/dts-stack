# 非功能预算（Gate G1）

**依据**：`assets/domain-profile.md` 的本地实测 + Sprint-86 已批准 NFR + DTS 领域不变量
**适用范围**：资产观察/投影、模型服务同步、质量证据查询、血缘写入、资产概览/目录/详情和存量迁移。

| 维度 | 预算 | 适应度函数（可执行） | 归属 Task | 状态 |
|---|---|---|---|---|
| 观察批量 | 单次 1～500；501 返回 400 | `CatalogAssetObservationResourceTest` 边界断言 | F1/T01 | 已有/须回归 |
| 观察幂等 | 相同 asset/evidence 重放不得新增第二 projection；版本只在事实变化时增加 | repository 并发 IT + 唯一约束断言 | F1/T01 | MODULE_VERIFIED |
| 存量迁移 | 每批 ≤500；必须 previewHash + batchId；漂移返回 409 | migration IT 覆盖 preview/apply/replay/rollback/drift | F1/T02 | MODULE_VERIFIED / REAL_BATCH_PENDING |
| 查询效率 | 目录一页资产补齐语义状态不得 N+1；单页额外查询 ≤2 | Testcontainers 查询计数；批量按 `(asset_type,asset_key)` 查询 | F5/T01 | MODULE_VERIFIED |
| 概览正确性 | 不再受 200 行扫描上限影响；stats 与目录同一 asOf 可对账 | 500+ 资产 IT，断言 overview total == stats total == list total | F1/T01、F5/T01 | MODULE_VERIFIED / 500+ IT_PENDING |
| 服务同步延迟 | outbox READY 后 60 秒内进入 SYNCED 或 SYNC_FAILED | scheduler/consumer IT 使用可控 Clock，断言 deadline | F2/T01 | GAP |
| 服务同步重试 | 最多 5 次，指数退避 1m/5m/15m/30m/60m；旧 version CAS 失败不得重试覆盖 | 故障注入 IT + repository CAS 断言 | F2/T01 | MODULE_VERIFIED / FAULT_IT_PENDING |
| 服务同步批量 | 单轮最多 100 条，避免长事务 | consumer 单测断言 limit；100+ 队列 IT | F2/T01 | BOUND_VERIFIED / 100+ IT_PENDING |
| 质量证据查询 | 一个候选的质量证据查询 SQL ≤8，禁止逐 rule N+1 | Testcontainers 查询计数断言 | F3/T01 | GAP |
| 质量证据新鲜度 | `maxAgeSeconds` 来自发布策略；缺失/运行中/失败/过期均 fail-closed | 参数化单测覆盖每种 violation | F3/T01 | MODULE_VERIFIED |
| 质量幂等 | 同一 run 引用重放不新增 run；重试必须产生新的 runId | 集成测试断言 run 数量和 command snapshot | F3/T02 | MODULE_VERIFIED / REAL_RUN_PENDING |
| 血缘写入 | 相同来源+证据+边重放幂等；不得覆盖人工 VERIFIED | Sprint-90 guard IT + model publication IT | F4/T01 | MODULE_VERIFIED / REAL_FIELD_PENDING |
| 页面延迟 | 本地测试量级下目录/概览/详情 P95 ≤2s；不得增加首屏 API 数量超过 1 个批量语义请求 | Playwright performance + Network 请求计数 | F5/T01 | 待目标环境校准 |
| 兼容性 | Chrome 95；禁止新增第三方前端依赖 | legacy build + Chrome95 smoke + package diff | F5/T01、F6/T01 | LEGACY_BUILD_PASS / CHROME95_PENDING |
| 审计 | observation/reconcile/backfill/sync retry/quality gate 全部分类且含 actor/correlationId/assetKey/candidateId | 审计 IT 断言动作非“未分类” | F1～F3、F6/T02 | CATALOG_PASS / RUNTIME_IT_PENDING |
| 权限 | 全局写仅 ADMIN/OP_ADMIN/INST_DATA_OWNER；部门越权 fail-closed | MockMvc 正/负向测试 + xiezm 浏览器走查 | F1/T01、F6/T01 | 部分已有 |
| 可用性 | OM 不可用时目录/详情仍返回 DTS 资产，不得 5xx | 停用 OM stub/故障注入 IT | F4/T01 | MODULE_VERIFIED / FAULT_IT_PENDING |
| 租户 | N/A：当前产品未建立多租户；仍保留既有 tenant 字段兼容，不新增租户 UI | 静态契约断言无新 tenant selector | 各 Task | N/A |

## 未达标项处置

| 缺口 | 影响 | 处置 | 关联 Task |
|---|---|---|---|
| 客户真实量级未取得 | P95、批次和索引阈值只能按本地基线 | F0 记录目标环境画像，超出预算时回写本表 | F0/T01 |
| 字段血缘为 0 | 无法验证写入吞吐和查询正确性 | 先取得真实 manifest 与跳过原因，不降低验收标准 | F4/T01、F6/T01 |
| Chrome 95 未运行 | UI 兼容性无法最终判定 | Legacy 产物已构建；集中 E2E 执行 Chrome 95 回归 | F6/T01 |
| 服务投影已全部 SYNCED 但缺故障延迟样本 | 无法据成功静态快照证明 60 秒收敛预算 | F2 在集中 E2E 注入可恢复故障并记录 deadline | F2/T01、F6/T01 |

DoD 时必须逐条回跑适应度函数；“代码中已处理”不是证据。
