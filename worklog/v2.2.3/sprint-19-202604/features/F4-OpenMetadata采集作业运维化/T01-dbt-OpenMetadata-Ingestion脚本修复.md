# T01: dbt/OpenMetadata Ingestion 脚本修复

**优先级**: P0  
**状态**: READY  
**依赖**: F1

## 目标

修复 `dts-openmetadata-ingestion` 当前因 token 为空直接跳过的行为，让采集脚本可控、可诊断。

## 范围

- 调整 `services/dts-openmetadata/ingestion/run-dbt-ingestion.sh` 的 token/no-auth 判断。
- no-auth 模式必须显式开启。
- 输出配置路径、认证模式、目标 OpenMetadata URL。
- 保持敏感信息脱敏。

## 完成标准

- [ ] token 为空且未允许 no-auth 时脚本失败并给出原因。
- [ ] no-auth 模式日志明确显示。
- [ ] 成功执行时 OpenMetadata ingestion 配置被实际调用。
- [ ] 退出码能区分成功、跳过和失败。
