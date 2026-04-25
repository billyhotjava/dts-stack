# T04: 增量 cursor 与 checkpoint

**优先级**: P0
**状态**: DRAFT
**依赖**: T02, F3/T03

## 目标

支持 API 增量同步的 cursor/checkpoint 机制，并保证失败恢复和重跑语义明确。

## 范围

- 支持 timestamp cursor、numeric cursor、opaque page token。
- 定义 checkpoint 保存时机：page 成功、batch 成功、task 成功。
- 支持 lookback window 和 late arriving records。
- 支持手工 reset checkpoint 和指定时间回补。

## 完成标准

- [ ] checkpoint 原子更新，不因失败误推进。
- [ ] 增量参数能注入 query/body/header。
- [ ] 手工重跑不会破坏当前水位。
- [ ] execution history 展示 before/after checkpoint。

