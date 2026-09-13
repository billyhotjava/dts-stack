# T03: Owner/Domain/Tags 写入策略

**优先级**: P1
**状态**: DONE
**依赖**: T01, T02

## 目标

处理任务 schema 中已经存在的 `lineage.owner/domain/tags`，避免治理元数据被悄悄丢弃。

## 范围

- 梳理 OpenMetadata 对 owner、domain、tags 的 API 要求。
- 明确支持表级写入，列级写入是否延期。
- 对不存在的 tag/domain 选择自动创建、跳过或报错策略。
- 将处理结果写入日志或执行审计。

## 完成标准

- [ ] 表级 owner/domain/tags 有明确实现或明确不支持说明。
- [ ] 不存在的治理对象处理策略可配置或文档化。
- [ ] 不因 tag 写入失败破坏主接入任务，除非强一致开关开启。
- [ ] 用户配置的治理字段不再无声丢弃。
