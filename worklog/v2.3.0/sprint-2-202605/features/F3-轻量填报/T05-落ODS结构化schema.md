# T05: 落 ODS 结构化 schema

**优先级**: P0
**状态**: READY
**依赖**: T04

## 目标
填报数据不再写入 `ods_*_v2`（varchar 大杂烩），而是写入新的 `ods_intake_*` 结构化表（字段类型正确、外键引用 MDM、时间戳规范）。

## 技术设计
详细技术方案在 F3 brainstorming 阶段产出，本文件仅占位。

## 影响范围
- 新增 `ods_intake_*` 表族
- 与 F4 的 ODS 范式化目标表合流

## 验证
- [ ] 同一条业务数据，Excel 路径和填报路径生成的 DWD 记录一致

## 完成标准
- [ ] 落地 schema 经 dbt 团队 review
