# F2: DTS 训练快照导出服务/API

**优先级**: P0  
**状态**: READY

## 目标

在 DTS 中提供正式训练快照导出能力，从 dbt 产出的训练模型生成 CSV snapshot package。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | 后端快照导出 API 合约 | P0 | READY | F1 |
| T02 | CSV 快照包生成服务 | P0 | READY | T01 |
| T03 | DTS 前端导出入口与状态展示 | P1 | READY | T02 |

## 完成标准

- [ ] DTS API 能根据模型名/快照 ID 导出 snapshot package。
- [ ] 导出产物包含五件套，且 `data.csv` 来自治理后的 DWD 模型。
- [ ] 导出任务可追踪输出路径、行数、字段数和质量状态。
