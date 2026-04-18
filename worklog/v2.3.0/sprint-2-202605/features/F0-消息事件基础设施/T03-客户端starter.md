# T03: Kafka 客户端 starter 与接入规范

**优先级**: P0
**状态**: READY
**依赖**: T02

## 目标
封装一个 shared starter（基于 Spring Kafka 或 spring-cloud-stream），提供 Producer/Consumer 统一配置、tracing、错误处理、DLQ，供后续 dts-approval / dts-mdm / dts-intake 等服务直接依赖。

## 技术设计
详细技术方案在 F0 brainstorming 阶段产出，本文件仅占位。

## 影响范围
- 新增 `source/dts-shared-kafka`（或类似）
- 所有业务服务的 `pom.xml` 引入

## 验证
- [ ] 一个 sample 服务通过 starter 发送/消费事件，链路追踪、DLQ 正常

## 完成标准
- [ ] starter 发布并提供一个 hello-world demo
