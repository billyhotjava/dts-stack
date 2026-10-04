# T02: Metadata Source 与 Fallback Reason

**优先级**: P1
**状态**: DONE
**依赖**: T01

## 目标

让平台 API 能表达元数据来自 OpenMetadata、本地 catalog，还是 OpenMetadata 失败后的回退。

## 范围

- 在相关 DTO 中增加或复用 metadata source 字段。
- 区分 disabled、not found、auth failed、remote error、local fallback。
- 保持前端兼容；新增字段不破坏旧页面。
- 日志和响应不泄露内部堆栈。

## 完成标准

- [x] API 调用方能判断数据来源。
- [x] OpenMetadata 查询失败不再被包装成普通空结果。
- [x] 本地回退有明确 reason。
- [x] 单测覆盖主要状态。

## 实现记录

- `OpenMetadataResult`、`OpenMetadataSummary`、`OpenMetadataTablePage`、`OpenMetadataLineageResult`、`OpenMetadataQualityResult`、`OpenMetadataQualitySummary` 增加 `metadataSource` 与 `fallbackReason`。
- `metadataSource` 统一取值：`openmetadata`、`catalog`、`disabled`。
- 数据集元数据查询与批量查询在 OpenMetadata disabled/not-found 时回退本地 catalog，并保留 `OpenMetadata: ...` 原因。
- 本地 catalog 明细和列表统一返回 `metadataSource=catalog`。
- `OpenMetadataServiceTest` 覆盖坏 FQN pattern 和 source/fallback 透传。
