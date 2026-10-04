# F2: 数据库接入自动建 ODS 收敛

**优先级**: P0  
**状态**: DONE
**依赖**: F1

## 目标

把现有数据库接入自动建表能力收敛到 ODS 契约 v1：源业务字段原样搬运，统一追加 `_dts_*` 技术字段，并保证 Addax、Airflow、execution 追踪一致。

## Task 列表

| ID | Task | 优先级 | 状态 |
|---|---|---|---|
| T01 | TargetTableProvisioner 契约改造 | P0 | DONE |
| T02 | Addax Job 技术字段注入重构 | P0 | DONE |
| T03 | 显式列选择类型保真修复 | P0 | DONE |
| T04 | full_refresh 与增量建表策略收敛 | P1 | DONE |
| T05 | 数据库接入回归测试 | P0 | DONE |

## 完成标准

- [x] 自动建 ODS 时源字段和 `_dts_*` 字段稳定存在，字段定义来源优先为 schema snapshot。
- [x] Addax 写入不会因技术字段导致列数不匹配。
- [x] 显式列选择仍能通过元数据获取真实类型。
- [x] full_refresh 和 incremental 的建表行为可解释、可测试。
