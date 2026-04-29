# F3: 血缘注册与标签治理

**优先级**: P0
**状态**: DONE
**依赖**: F1, F2

## 目标

把接入任务中的真实源表、目标表、owner/domain/tags 转换为 OpenMetadata 可消费的血缘和治理元数据。

## Task 列表

| ID | Task | 优先级 | 状态 |
|---|---|---|---|
| T01 | Stream/Mapping 提取器 | P0 | DONE |
| T02 | Source/Target FQN Builder | P0 | DONE |
| T03 | Owner/Domain/Tags 写入策略 | P1 | DONE |
| T04 | 血缘注册回归测试 | P1 | DONE |

## 完成标准

- [x] 不再向 `registerLineage` 传入空 streams。
- [x] 源表和目标表 FQN 规则可配置、可测试、可诊断。
- [x] owner/domain/tags 暂不写入 OpenMetadata 时显式回传 `not_supported`。
- [x] 血缘写入失败有结构化结果。
