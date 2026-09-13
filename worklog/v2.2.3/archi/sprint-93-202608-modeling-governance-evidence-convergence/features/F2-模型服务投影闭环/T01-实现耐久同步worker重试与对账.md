# T01：实现耐久同步 worker、重试与对账

**优先级**：P0
**状态**：COMPLETE / IT-04_PASS
**依赖**：F1/T01（最终观察写入），编码可并行

## 目标

把 serving projection 自身作为耐久工作队列，可靠同步语义模型与物理资产关系，并处理现有永久 pending。

## 技术设计（Contract-first）

- **输入契约**：claim `sync_status='SYNC_PENDING' OR ('SYNC_FAILED' AND next_sync_at<=now)`，limit 1～100，按 updated_at/modelSpecId 排序，使用 SKIP LOCKED。
- **处理契约**：解析 latestPublishedRef/servingRef → 校验 model/candidate/physicalAssetId/AssetKey/version → 调用 F1 observation/read seam → 更新关系/语义状态。
- **输出契约**：CAS 调用 `markSyncSucceeded`；异常调用 `markSyncFailed(errorCode,nextAttemptAt)`；超过 5 次进入不可自动重试状态并生成治理问题/审计。
- **重试契约**：1m、5m、15m、30m、60m；不可重试错误（身份歧义、旧 version、缺物理资产）直接问题化。
- **数据流**：scheduled worker → repository claim → sync service → F1 command boundary → CAS receipt；既有 outbox 继续作为对外事件和审计，不新建第二消息状态机。
- **错误路径**：解析失败、物理资产缺失、AssetKey 冲突、CAS miss、数据库临时错误分类；旧 version 视为 superseded，不覆盖新数据。
- **复用点**：账本 L08～L10、`CatalogModelServingProjectionRepository`、现有 scheduler/transaction 模式。

## UI 交互规格

- 模型工具栏显示当前 syncStatus 和 updatedAt。
- `SYNC_FAILED` 显示 errorCode、attempts、nextSyncAt；有权限用户触发受控重试，重复点击幂等。
- 资产详情展示 semantic model ref/candidate/observation，不把同步中当成功。

## 影响范围

dts-platform serving repository/service/worker、治理问题/审计接缝、聚焦测试；前端状态消费由 F5 完成。

## 验证（RED→GREEN）

- [ ] 两 worker 并发 claim 不重复处理。
- [x] 成功、临时失败、永久失败、CAS miss、旧 version 五条路径通过。
- [x] claim SQL 和参数校验保证单轮不超过 100。
- [x] 可控 Clock 覆盖分级退避；IT-04 记录真实失败和恢复时序。
- [x] 43 条现有 pending 经受控 worker 处理为 `SYNCED`，未通过 SQL 直接更新。

## Definition of Done

- [x] 生产代码存在 markSyncSucceeded/Failed 的真实调用方。
- [x] 运行手册、告警字段和终态审计完整；IT-04 故障注入已通过。
- [x] 终态失败进入治理问题与机器审计，无重复 consumer owner、无静默失败。
