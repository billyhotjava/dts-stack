# T02: Source/Target FQN Builder

**优先级**: P0
**状态**: DONE
**依赖**: T01, F1

## 目标

统一写入 OpenMetadata 时的 source/target FQN 生成规则，避免平台侧读路径和 ingestion 写路径不一致。

## 范围

- 复用或抽取 FQN pattern 解析逻辑。
- 支持 service、database、schema、table 占位符。
- 处理 schema 缺失、大小写、转义和非法字符。
- 输出 candidate 和最终命中 FQN 的诊断信息。

## 完成标准

- [ ] ingestion 写路径和 platform 读路径 pattern 语义一致。
- [ ] 缺 schema 的数据库不会生成双点或空段 FQN。
- [ ] 单测覆盖坏 pattern 和多 pattern 候选。
- [ ] 日志能帮助定位 OpenMetadata 未命中。
