# T01: Kafka 集群接入 docker-compose

**优先级**: P0
**状态**: READY
**依赖**: 无

## 目标
将 Kafka（单节点 KRaft 模式）纳入 docker-compose，与其他服务共网络；镜像固化到 `imgversion.conf`，支持气隙部署。

## 技术设计
详细技术方案在 F0 brainstorming 阶段产出，本文件仅占位。

## 影响范围
- `docker-compose.yml` / `docker-compose.dev.yml`
- `imgversion.conf` 增加 Kafka 镜像
- `init.sh` / `dev-up.sh` 可能需要新增 topic 预创建

## 验证
- [ ] 服务启动后可 producer/consumer 通连
- [ ] 容器重启后消息和位点持久化

## 完成标准
- [ ] 开发/气隙两种部署形态均可运行
