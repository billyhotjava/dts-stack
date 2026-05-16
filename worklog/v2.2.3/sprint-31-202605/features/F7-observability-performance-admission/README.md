# F7: 观测、审计与性能准入

**优先级**: P1
**状态**: READY
**目标**: 给企业交付补上运行可观测、审计可追踪和性能边界，避免“功能跑通但现场不可承诺”。

## 任务

| Task | 内容 | 验收 |
|---|---|---|
| T01 | 黄金链路审计动作补齐 | 接入、预检、发布、注册、授权都有审计码 |
| T02 | 运行指标面板 | 展示接入任务、dbt run、语义模型 run、血缘写入成功率 |
| T03 | 大 CSV / 大表性能准入 | 100MB 样例通过；500MB/百万行给出明确结论 |
| T04 | 服务间调用失败诊断 | platform/ingestion/analytics service auth failure 可定位 |
| T05 | Sprint-31 IT 证据归档 | `it/evidence` 下沉淀命令、输出、截图或日志摘要 |

## 代码关注点

- audit filters / audit services
- `ExternalRunLogService`
- ingestion execution services
- dbt run result services
- platform/analytics service-auth logging
