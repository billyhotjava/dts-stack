# T02: MDM 引用层（替代硬编码）

**优先级**: P0
**状态**: READY
**依赖**: T01

## 目标
把 ODS/STG 里所有 project / dept / subsystem 等主数据字段改为引用 MDM 的稳定 code；所有枚举字段改为引用 MDM 字典 `standard_code`。

## 技术设计
详细技术方案在 F4 brainstorming 阶段产出，本文件仅占位。

## 影响范围
- 新增 STG 层或重做 ODS 清洗
- 未匹配值的降级策略（放入 quarantine 表 + 告警）

## 验证
- [ ] 未匹配率 <1%，且所有未匹配值有清单

## 完成标准
- [ ] MDM 引用模型全量接入
