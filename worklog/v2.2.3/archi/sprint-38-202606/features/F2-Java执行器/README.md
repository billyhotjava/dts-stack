# F2: Java执行器

**优先级**: P0
**状态**: IN_PROGRESS

## 目标

把死代码 SPI（`ApiHttpSourceConnector`/`SourceConnectorRegistry`/`ExecutionPlan`）落地为 dts-ingestion 进程内的真实 API 抓取执行器，整体替换 `buildApiDagSource` 的 385 行内嵌 Python，修复其全部逻辑缺陷。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | 执行器骨架与SPI接线 | P0 | DONE | F1-T02 |
| T02 | HTTP引擎 | P0 | DONE | T01 |
| T03 | 鉴权策略实现 | P0 | DONE | T02, F1-T02 |
| T04 | 分页与类型化游标 | P0 | DONE | T02 |
| T05 | raw落地与按资源事务 | P0 | IN_PROGRESS | T01 |
| T06 | 安全防护(SSRF/TLS/响应限制) | P0 | IN_PROGRESS | T02 |

## 完成标准

- [x] `SourceConnectorRegistry` 被真实调用，API 任务经 `ApiHttpSourceConnector.buildExecutionPlan` → 执行器执行（骨架接线完成；真实 HTTP/落地见 T02/T05）
- [ ] 旧内嵌 Python 的 13 项缺陷全部在 Java 侧修复并有单测（游标比较/单事务/分页提前停/SSRF/maxResponseBytes/TLS/initialValue/lookbackSeconds/限流）
- [ ] 单测覆盖 ≥80%（鉴权/分页/游标/落地各模块）
