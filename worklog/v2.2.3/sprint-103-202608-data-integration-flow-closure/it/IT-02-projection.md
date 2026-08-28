# IT-02：DRAFT/ACTIVE 拓扑投影

检查日期：2026-08-28。判定：`PASS_SOURCE_DEPLOYED`。

## 已验证

- `IngestionFlowProjectionServiceTest` 6 项通过；拓扑从 task/revision canonical design 生成，不持久化 React Flow 坐标或第二套业务 DSL。
- design、validation 和 topology 均携带同一 plan checksum；前端只读展示 DRAFT/ACTIVE 版本。
- 前端 source contract 明确禁止 localStorage/自由画布成为执行事实源。
- 当前 7 个 active 任务均为线性单任务 EL，未发现实施可编辑多作业 DAG 的数据依据。

## 保留门禁

未对现有任务执行发布，因此运行态 revision/DAG checksum 的业务样本核对留待安全金丝雀。
