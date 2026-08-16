# 运行手册草案（Gate G4）

**状态**：PENDING；F2/T01 与 F6/T02 实施后补齐真实指标、日志样例和告警阈值。

## 1. 核心健康信号

| 信号 | 期望 | 异常判据 | 初步处置 |
|---|---|---|---|
| 语义投影覆盖率 | 稳定目录资产均有 projection 或明确 issue | 连续两轮覆盖率下降 | 停 backfill，跑 preview/差异报告 |
| 服务同步 pending age | P95 <60s | 任一 `SYNC_PENDING` >5m | 查 outbox/consumer/correlationId，触发受控重试 |
| 服务同步失败 | 正常为 0 | 5 分钟内失败 ≥5 或单资产 5 次失败 | 停该资产重试，保留 dead-letter，核对 version/AssetKey |
| orphan model serving ref | 0 | physicalAssetId 不存在或 AssetKey 不匹配 | 禁止发布推进，运行 reconciliation |
| 质量证据缺失/过期 | 发布前为 0 | QUALITY_PASSED 候选缺 pinned run | 阻塞发布，重新触发治理质量 |
| 字段血缘覆盖 | 目标链路 >0 且与 manifest 一致 | 表级有边但字段级长期 0 | 查导入 job 的 skip reason，不生成伪边 |
| 概览/目录统计差 | 0 | 同一 asOf total/domain/status 不一致 | 回滚读切换，运行 stats reconcile |

## 2. 必需日志字段

`correlationId`、`tenantId`（兼容字段）、`assetType`、`assetKey`、`resourceId/datasetId`、`modelSpecId`、`candidateId/version`、`projectionVersion`、`evidenceRef`、`qualityRunId`、`attempt`、`outcomeCode`、`errorCode`、`actor`。

## 3. 常见故障剧本

### A. 服务投影一直 SYNC_PENDING

1. 按 modelSpecId 查询 projection version、attempt 和 nextSyncAt。
2. 查同 correlationId 的 `MODELING_CATALOG_PROJECTION` outbox。
3. 验证 physicalAssetId 和 CatalogAssetKey 能解析到唯一 dataset。
4. 若 version 已过期，标记旧事件 superseded；不得覆盖当前 ref。
5. 若可重试，按退避策略执行；第五次后进入 dead-letter 并生成问题单。

### B. 目录有资产但无语义状态

1. 运行 reconciliation preview，不直接 observe。
2. 确认 relationType、producer、evidence、domain/layer 是否可唯一解析。
3. 可解析项分批 apply；不可解析项保留 issue。
4. 核对 stats projection 与目录总数。

### C. 模型工程测试通过但发布被质量阻塞

1. 区分工程证据与治理质量证据。
2. 核对 ruleVersion、binding 的 datasetId、run 终态和 finishedAt。
3. 过期/失败时创建新 run；禁止修改历史 run 或把 dbt test 当作替代。

### D. OpenMetadata 不可用

1. DTS 目录继续从 `catalog_dataset` 返回资产。
2. UI 标记技术元数据同步异常，不改变资产 publication/lifecycle。
3. 恢复后按稳定 AssetKey 重放同步，不新建 dataset。

## 4. 待实施补充

- 实际日志查询命令和 dashboard。
- 指标采集实现及告警规则。
- dead-letter 操作入口和审计动作。
- 目标环境容量与值班 owner。
