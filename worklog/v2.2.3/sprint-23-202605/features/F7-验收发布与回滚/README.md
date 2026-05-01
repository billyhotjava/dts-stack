# F7: 验收、发布与回滚

**优先级**: P0  
**状态**: IN_PROGRESS

## 目标

为资产主目录切换提供可验证、可回滚、可诊断的发布路径，避免资产门户主数据切换造成现场不可控。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | 迁移前数据检查和风险报告 | P0 | DONE | F1-F3 |
| T02 | smoke 脚本：OM sync、mapping、asset API、portal、lineage、quality | P0 | DONE | F4-F6 |
| T03 | feature flag 与旧路径回退 | P0 | DONE | F4,F5 |
| T04 | 发布说明、升级步骤和回滚步骤 | P0 | IN_PROGRESS | T01-T03 |
| T05 | 验收证据模板和现场 Runbook | P0 | DONE | T02 |

## 完成标准

- [x] 发布前可输出资产映射统计和异常清单。
- [x] smoke 能验证同步、映射、资产列表、详情、质量、血缘。
- [x] 有开关可回退旧 `catalog_dataset` 主路径。
- [x] 回滚不删除 OpenMetadata 数据，不丢失 DTS 治理扩展。
- [x] 验收证据包含截图、API 输出、SQL 统计和残余风险。
