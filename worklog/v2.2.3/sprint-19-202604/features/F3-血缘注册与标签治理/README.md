# F3: 血缘注册与标签治理

**优先级**: P0  
**状态**: READY  
**依赖**: F1, F2

## 目标

把接入任务中的真实源表、目标表、owner/domain/tags 转换为 OpenMetadata 可消费的血缘和治理元数据。

## Task 列表

| ID | Task | 优先级 | 状态 |
|---|---|---|---|
| T01 | Stream/Mapping 提取器 | P0 | READY |
| T02 | Source/Target FQN Builder | P0 | READY |
| T03 | Owner/Domain/Tags 写入策略 | P1 | READY |
| T04 | 血缘注册回归测试 | P1 | READY |

## 完成标准

- [ ] 不再向 `registerLineage` 传入空 streams。
- [ ] 源表和目标表 FQN 规则可配置、可测试、可诊断。
- [ ] owner/domain/tags 至少完成表级处理；不支持的维度有明确说明。
- [ ] 血缘写入失败有 task execution 级别的可追踪信息。
