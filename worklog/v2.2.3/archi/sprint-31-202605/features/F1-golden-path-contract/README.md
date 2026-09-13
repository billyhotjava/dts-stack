# F1: 黄金链路契约与端到端验收

**优先级**: P0
**状态**: DONE
**目标**: 定义 DTS 企业级数据平台的唯一可交付主链路，让后续开发不再按模块孤岛验收。

## 任务

| Task | 内容 | 验收 |
|---|---|---|
| T01 | 定义黄金链路状态机：SOURCE、ODS_READY、DBT_READY、CATALOG_READY、SEMANTIC_READY、CONSUMABLE_READY | DONE |
| T02 | 设计黄金链路 smoke 脚本 | DONE |
| T03 | 明确 JDBC / 文件 / API 三类接入能力矩阵 | DONE |
| T04 | 建立主链路验收数据集 | DONE |
| T05 | 建立 Sprint-31 总体验收清单 | DONE |

## 关键约束

- 先验收 JDBC 黄金链路，再补齐文件/API 的产品化差距。
- 所有后续 feature 都必须能回到黄金链路状态机。

## 交付说明

- API 契约：`GET /api/capabilities` 暴露 `goldenPath`。
- 状态机文档：`worklog/v2.2.3/sprint-31-202605/assets/golden-path-state-machine.md`
- Smoke 计划：`worklog/v2.2.3/sprint-31-202605/assets/golden-path-smoke-plan.md`
- Smoke 脚本：`worklog/v2.2.3/sprint-31-202605/it/scripts/golden-path-smoke.sh`
- 接入能力矩阵：`worklog/v2.2.3/sprint-31-202605/assets/connector-capability-matrix.md`
- 验收数据集：`worklog/v2.2.3/sprint-31-202605/assets/golden-path-fixtures.md`
