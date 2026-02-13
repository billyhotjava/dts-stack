# DTS v2.2.0 P3 回归清单（Addax/Airbyte/实时/隔离/血缘）

## 1. 连接器兼容回归（P3-QA-001）
- 目标：同一任务在 Addax/Airbyte 下的语义一致（FULL/INCREMENTAL/CDC）。
- 步骤：
  - 通过 `GET /api/ingestion/connectors/capabilities` 核对能力矩阵。
  - 准备同一源表，分别触发 Addax 与 Airbyte 任务。
  - 对比目标表行数、主键去重、增量水位推进结果。
- 通过标准：
  - FULL 场景：结果一致。
  - INCREMENTAL 场景：增量区间一致。
  - CDC 场景：延迟与一致性满足预期阈值。

## 2. 实时链路稳定性回归（P3-QA-002）
- 目标：实时状态接口可持续输出 checkpoint/lag/throughput。
- 步骤：
  - 连续轮询 `GET /api/ingestion/tasks/{id}/realtime-status`。
  - 观察 `lastHeartbeatAt` 是否持续刷新。
  - 观察 `lag` 与 `backlog` 是否可回落。
- 通过标准：
  - 24h 内无不可恢复中断。
  - 心跳、checkpoint、吞吐指标连续可读。

## 3. 跨项目隔离回归（P3-QA-003）
- 目标：项目上下文（`X-Active-Dept`）生效，跨项目不可读写。
- 步骤：
  - 使用项目 A 上下文创建 QueryDataset 与 BI Link。
  - 在项目 B 上下文调用：
    - `GET /api/sql/query-datasets`
    - `GET /api/reports`
    - `POST /api/reports`（绑定项目 A 的 QueryDatasetId）
- 通过标准：
  - 项目 B 不可见项目 A 数据集。
  - 项目 B 绑定项目 A 数据集时返回拒绝。

## 4. 血缘正确性回归（P3-QA-004）
- 目标：跨模块血缘页面可从节点反查上下游关系。
- 步骤：
  - 调用 `GET /api/catalog/lineage/impact?datasetId=...&direction=BOTH&depth=3`。
  - 检查返回中的 `nodes/edges` 字段：
    - `layer`
    - `upstreamAssetType/downstreamAssetType`
    - `direction`
    - `projectName`
- 通过标准：
  - 节点与边数量与预期一致。
  - 关系方向与资产类型标注正确。

## 5. 前端验收点
- `LineagePage`：支持方向、深度、项目名过滤，节点/边表格稳定展示。
- `TransformCreatePage`：连接器能力标签与模式禁用逻辑正确。
- `TransformDetailPage`：实时状态卡片可展示 connector/checkpoint/lag。

