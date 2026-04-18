# F0: 消息事件基础设施（Kafka）

**优先级**: P0
**状态**: READY
**依赖**: Sprint-1 完成
**设计阶段**: spec 就绪（见 `F0-design.md`）

## 目标

在 S10 平台引入 Kafka 作为**业务事件总线**和**流批一体的事件流源**，同时定义事件规范与 outbox 模式，为后续 F1/F2/F3/F4 的事件发布订阅和流处理提供统一基础。

## 设计红线（brainstorm 已对齐）

- **Kafka ≠ 万能锤**：仅用于 ① 跨服务业务事件、② 流批一体事件流；**不承担** K8s 多副本协调（那用 Redis / 无状态设计）、**不替代** 同步 RPC
- **不沿用 JMS / ActiveMQ 模型**：老架构的 Java message broker 是命令队列风格，与事件日志模型不同
- **强一致场景走 Outbox**：审批通过 → MDM 写库 这种"事务性事件"，必须 outbox 表 + 本地事务保证，不能直接 producer.send
- **MVP 不上重型组件**：一期用 JSON + 手写 payload schema；暂不引入 Schema Registry / Avro
- **单节点 KRaft 起步**：开发/离线气隙用单节点（KRaft 模式，无需 Zookeeper）；生产再扩集群
- **topic 命名规范**：`{domain}.{entity}.{event}` 例 `mdm.project.updated` / `approval.request.approved`

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | [Kafka 集群接入 docker-compose](T01-Kafka接入docker-compose.md) | P0 | READY | - |
| T02 | [业务事件规范](T02-业务事件规范.md) | P0 | READY | T01 |
| T03 | [Kafka 客户端 starter 与接入规范](T03-客户端starter.md) | P0 | READY | T02 |
| T04 | [Outbox 模式示范实现](T04-Outbox模式.md) | P0 | READY | T03 |
| T05 | [Kafka UI 与基础监控](T05-KafkaUI与监控.md) | P1 | READY | T01 |

## 完成标准

- [ ] Kafka 单节点 KRaft 集成到 docker-compose，开发/气隙环境可独立启停
- [ ] 事件规范文档（topic 命名、payload 结构、幂等 key、版本演进）发布
- [ ] 各 Java 服务可通过统一 starter 注入 KafkaTemplate / Consumer，最小样例跑通
- [ ] Outbox 表结构 + 投递器样例（审批场景用）完成，有 IT 证据
- [ ] Kafka UI 可访问，至少能看到 topic 列表、消息、消费者位点
- [ ] 明确**不纳入本 Feature 范围**：Schema Registry、多集群、Redis / IMDG（另议）
