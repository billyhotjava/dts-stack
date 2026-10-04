# T03: 认证与 no-auth 策略收敛

**优先级**: P0
**状态**: DONE
**依赖**: 无

## 目标

统一 OpenMetadata server、ingestion 容器、`dts-platform`、`dts-ingestion` 对 token 和 no-auth 的配置语义。

## 范围

- 梳理 `OPENMETADATA_AUTH_TOKEN`、`OPENMETADATA_ALLOW_NO_AUTH` 和应用侧 token 配置。
- 明确 dev、single、prod 三类部署模式的默认值。
- 启动或采集前输出当前认证模式，不打印敏感 token。
- 避免 token 为空时无声跳过采集。

## 完成标准

- [ ] token 模式和 no-auth 模式互斥或优先级明确。
- [ ] token 为空且未开启 no-auth 时返回可诊断失败。
- [ ] no-auth 模式必须显式配置。
- [ ] 文档和日志不泄露 token。
