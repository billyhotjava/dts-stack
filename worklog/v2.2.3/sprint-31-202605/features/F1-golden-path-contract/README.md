# F1: 黄金链路契约与端到端验收

**优先级**: P0
**状态**: READY
**目标**: 定义 DTS 企业级数据平台的唯一可交付主链路，让后续开发不再按模块孤岛验收。

## 任务

| Task | 内容 | 验收 |
|---|---|---|
| T01 | 定义黄金链路状态机：SOURCE、ODS_READY、DBT_READY、CATALOG_READY、SEMANTIC_READY、CONSUMABLE_READY | 状态机写入文档和 API 契约 |
| T02 | 设计黄金链路 smoke 脚本 | 能用一份样例从数据源跑到语义模型发布 |
| T03 | 明确 JDBC / 文件 / API 三类接入能力矩阵 | 页面和 README 都展示可用/不可用边界 |
| T04 | 建立主链路验收数据集 | 至少一个小型 JDBC 样例和一个文件样例 |
| T05 | 建立 Sprint-31 总体验收清单 | `it/README.md` 可逐项打勾 |

## 关键约束

- 先验收 JDBC 黄金链路，再补齐文件/API 的产品化差距。
- 所有后续 feature 都必须能回到黄金链路状态机。
