# T04: Adapter 单测与假客户端

**优先级**: P1  
**状态**: READY  
**依赖**: T01, T02, T03

## 目标

用假客户端隔离 OpenMetadata 远端依赖，覆盖 adapter 的配置、状态和错误处理。

## 范围

- 为 `OpenMetadataAdapter` 增加可注入 client 或测试替身。
- 覆盖 token/no-auth、连接配置、unsupported、远端失败、成功路径。
- 增加 fixture 覆盖 Addax nested writer 配置。
- 避免单测依赖真实 Docker 或 OpenMetadata server。

## 完成标准

- [ ] 单测可在无网络、无 Docker 环境运行。
- [ ] 关键分支覆盖率足以防止静默跳过回归。
- [ ] 假客户端不会进入生产 Bean。
- [ ] 测试命令记录到 `it/README.md`。
