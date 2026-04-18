# T05: Kafka UI 与基础监控

**优先级**: P1
**状态**: READY
**依赖**: T01

## 目标
引入一个轻量 Kafka UI（Kafdrop / Kowl 之一，气隙镜像），让运维/开发可以查看 topic、消息、consumer group、lag；基础指标接入 Prometheus/Grafana（如平台有），无则最小化日志。

## 技术设计
详细技术方案在 F0 brainstorming 阶段产出，本文件仅占位。

## 影响范围
- docker-compose 增加 UI 容器
- 如已有 Grafana 则新增 Kafka dashboard

## 验证
- [ ] 界面可访问，topic/lag 可见

## 完成标准
- [ ] UI 部署成功并纳入 admin 导航（可选）
