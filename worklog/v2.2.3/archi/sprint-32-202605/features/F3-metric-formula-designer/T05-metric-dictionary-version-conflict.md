# T05: 指标字典、版本和冲突检测

**优先级**: P1
**状态**: READY
**依赖**: T04

## 目标

维护指标字典、版本状态和口径冲突，避免同名同码指标漂移。

## 技术设计

- 指标 code + version 为稳定主键。
- 检测同 code 不同公式、同名不同口径、单位/格式冲突。
- 发布态指标只允许新版本，不允许原地修改。

## 影响范围

- `source/dts-metrics` metric dictionary/version service
- `source/dts-metrics-webapp` metric asset page

## 验证

- [ ] 已发布指标修改生成新版本。
- [ ] 冲突诊断能回到具体 graph node。

## 完成标准

- [ ] 指标字典和画布使用同一个指标事实源。
