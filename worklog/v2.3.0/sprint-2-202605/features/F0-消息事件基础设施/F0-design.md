# F0 设计：消息事件基础设施（Kafka）

**版本**: 1.0
**状态**: spec 就绪
**更新**: 2026-04-18

## 1. 概览

### 1.1 目标

引入 Kafka 作为 S10 平台的**业务事件总线**和未来**流批一体的事件流源**，同时提供事件规范、客户端 starter、Outbox 模式参考实现，为 F1/F2/F3/F4 及未来的财务/PLM/ERP 对接提供统一基础。

### 1.2 设计红线

- **Kafka ≠ 万能锤**：仅用于 ① 跨服务业务事件、② 流批一体事件流；**不承担** K8s 多副本协调（那用 Redis / 无状态设计）、**不替代** 同步 RPC
- **不沿用 JMS / ActiveMQ 模型**：老架构的 Java message broker 是命令队列风格，与事件日志模型不同
- **强一致场景走 Outbox**：审批通过 → MDM 写库 这类"事务性事件"，必须 outbox 表 + 本地事务保证，不能直接 `producer.send()`
- **MVP 不上重型组件**：一期用 JSON + 手写信封；暂不引入 Schema Registry / Avro / Protobuf
- **单节点 KRaft 起步**：升级到 K8s 再扩多节点集群

### 1.3 本 Feature 不做

- Schema Registry / Avro / Protobuf
- 多集群 / 跨机房复制（MirrorMaker）
- Redis / IMDG 引入（另议）
- Prometheus/Grafana 接入（平台本身未就绪，后续另议）
- Flink / Spark 流处理（由 F4 及后续 Sprint 承担，F0 只保证 Kafka 能作为它们的 source）

### 1.4 依赖的已有 Shared Library

本 Feature **直接复用** `source/dts-common/.../SecurityLevelCatalog.java`（已成熟）：

- `PersonnelSecurityLevel`：`GENERAL / IMPORTANT / CORE`
- `DataSecurityLevel`：`PUBLIC / INTERNAL / SECRET / CONFIDENTIAL`（不含 TOP_SECRET）
- `maxDataLevelForPersonnel()` / `parseMaxDataLevel()`：法定规则与宽松解析

新 shared-kafka 模块（§8）**不重新定义**密级常量，通过 `com.yuzhi.dts.common.security.SecurityLevelCatalog` 引用；Kafka 信封的 `dataClassification` 字段用 `DataSecurityLevel.code()` 序列化。

---

## 2. 技术选型

| 组件 | 选型 | 版本 | 备注 |
|---|---|---|---|
| Kafka 服务端 | `apache/kafka` | **4.1.2** | KRaft only（4.x 已移除 ZK）；鲲鹏+麒麟已烟雾测试通过 |
| Java 客户端 | **Spring Kafka** | 3.3.x+ | 基于 Spring Boot 3 / Java 21，已通 Kafka 4.x 兼容验证 |
| Outbox 实现 | **应用层 outbox 表 + NOTIFY + 5s 兜底轮询** | - | 每服务独立表 |
| Kafka UI | `provectuslabs/kafka-ui` | latest | 开发/运维使用，不对业务用户开放；支持 arm64 |
| 架构 | 单节点 KRaft（broker + controller 共用） | - | 分区 3 / 副本 1；升级 K8s 后变更 |

---

## 3. 部署与目录

### 3.1 docker-compose 接入

`docker-compose.yml` 新增服务 `dts-kafka`：

```yaml
dts-kafka:
  platform: ${DTS_RUNTIME_PLATFORM:-linux/amd64}   # 鲲鹏部署设为 linux/arm64
  image: ${IMAGE_KAFKA}
  container_name: dts-kafka
  environment:
    KAFKA_NODE_ID: 1
    KAFKA_PROCESS_ROLES: broker,controller
    KAFKA_CONTROLLER_QUORUM_VOTERS: "1@dts-kafka:9093"
    KAFKA_LISTENERS: "PLAINTEXT://:9092,CONTROLLER://:9093"
    KAFKA_ADVERTISED_LISTENERS: "PLAINTEXT://dts-kafka:9092"
    KAFKA_CONTROLLER_LISTENER_NAMES: "CONTROLLER"
    KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: "CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT"
    KAFKA_INTER_BROKER_LISTENER_NAME: "PLAINTEXT"
    KAFKA_LOG_DIRS: "/var/lib/kafka/data"
    KAFKA_AUTO_CREATE_TOPICS_ENABLE: "false"     # 禁自动建 topic，全部显式声明
    KAFKA_LOG_RETENTION_HOURS: 720               # 30 天
    KAFKA_NUM_PARTITIONS: 3
    KAFKA_DEFAULT_REPLICATION_FACTOR: 1
    KAFKA_MIN_INSYNC_REPLICAS: 1
  volumes:
    - ./data/kafka:/var/lib/kafka/data
  networks: [dts-net]
  healthcheck:
    <<: *hc
    test: ["CMD-SHELL", "/opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list || exit 1"]

dts-kafka-ui:
  image: ${IMAGE_KAFKA_UI}
  container_name: dts-kafka-ui
  environment:
    KAFKA_CLUSTERS_0_NAME: "s10"
    KAFKA_CLUSTERS_0_BOOTSTRAPSERVERS: "dts-kafka:9092"
  depends_on:
    dts-kafka: { condition: service_healthy }
  networks: [dts-net]
```

### 3.2 imgversion.conf

```
IMAGE_KAFKA=apache/kafka:4.1.2
IMAGE_KAFKA_UI=provectuslabs/kafka-ui:latest
```

### 3.3 数据与备份

- 数据目录：`${S10_ROOT}/data/kafka`（与 `data/pg` 同级，气隙部署可随宿主机一起备份）
- 备份：MVP 阶段不做专门备份，依赖 30 天 retention + outbox 表的最终一致；未来 K8s 上再考虑定期 snapshot

### 3.4 双架构支持

- 开发机（Mac / x86 Linux）：`DTS_RUNTIME_PLATFORM=linux/amd64`
- 生产（鲲鹏 + 麒麟）：`DTS_RUNTIME_PLATFORM=linux/arm64`
- `apache/kafka:4.1.2` 和 `provectuslabs/kafka-ui` 官方均提供 multi-arch manifest，无需定制镜像

---

## 4. 事件契约

### 4.1 Topic 命名规范

格式：**`{domain}.{entity}.{event}`**

| 字段 | 说明 | 允许值（示例） |
|---|---|---|
| `domain` | 业务域 | `mdm` / `approval` / `intake` / `ods` / `iam` |
| `entity` | 聚合根 | `project` / `dept` / `request` / `submission` |
| `event` | 过去式动词 | `created` / `updated` / `deleted` / `approved` / `rejected` / `submitted` |

规则：

- 全部小写 ASCII；分隔用 `.`
- `event` 动词一律过去式（事件是既成事实）
- 扩展版本号走 eventType 而非 topic 后缀（见 §4.4）

示例：`mdm.project.updated` / `approval.request.approved` / `intake.submission.created`

### 4.2 Payload 信封

所有消息 value 为 JSON，统一信封：

```json
{
  "eventId":            "550e8400-e29b-41d4-a716-446655440000",
  "eventType":          "mdm.project.updated",
  "source":             "dts-mdm",
  "occurredAt":         "2026-05-01T10:00:00.000Z",
  "schemaVersion":      "1.0",
  "dataClassification": "INTERNAL",
  "payload":            { /* 业务字段 */ }
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| `eventId` | UUID v4 | **幂等 key**，消费端去重凭据；生产端生成 |
| `eventType` | string | 冗余于 topic，便于消费端 filter / 审计日志 |
| `source` | string | 发出服务名（`dts-mdm` / `dts-approval` / …） |
| `occurredAt` | ISO-8601 UTC | 事件发生时间（业务时间，非落表时间） |
| `schemaVersion` | string | payload schema 版本，形如 `1.0` / `2.0` |
| `dataClassification` | enum string | 数据密级，取值 `PUBLIC / INTERNAL / SECRET / CONFIDENTIAL`（S10 不涉及 TOP_SECRET）；消费端可据此做 ACL / 审计 |
| `payload` | object | 业务内容，结构由 eventType 决定 |

Key 策略：消息 Kafka key 使用 `aggregate_id`（如 project code），保证同一聚合根的事件进入同一分区，顺序有保证。

### 4.3 幂等性

- 生产端：eventId 在 Outbox 表唯一约束（`UNIQUE`），不会重复发出
- 消费端：**业务层天然幂等**（UPSERT / `INSERT ... ON CONFLICT DO UPDATE`），幂等 key 从信封 `eventId` 派生；**不维护单独的 `processed_event` 表**
- 若业务表无法天然幂等（极少数），消费者可自行维护去重表，但为局部实现，不是全局规范

### 4.4 Schema 演进

- **additive only**：字段只加不改不删
- 需要破坏性变更时，发 **新 eventType**（例：`mdm.project.updated` → `mdm.project.updated.v2`，新 eventType 对应新 topic）
- 老消费者继续订阅旧 topic 直到全量迁移；迁移期生产者双写
- `schemaVersion` 字段用于同一 eventType 内的兼容变更追踪（如"这条消息来自 v1.3 生产者"）

---

## 5. Topic 默认参数

| 参数 | 默认值 | 备注 |
|---|---|---|
| 分区数 | 3 | 单节点先用 3；为扩多节点 & 消费端并行预留 |
| 副本因子 | 1 | 单节点硬约束 |
| `min.insync.replicas` | 1 | 同上 |
| 保留时长 | 30 天（`retention.ms=2592000000`） | 足以覆盖下游重放 |
| 清理策略 | `cleanup.policy=delete`（默认） | 状态快照类 topic 需显式改 `compact` |

禁止 auto create topic（`auto.create.topics.enable=false`），所有 topic 必须在各服务启动时显式声明 / 预创建。

### 5.1 Topic 预创建流程

每个服务在启动阶段调用 Spring Kafka 的 `NewTopic` bean 声明自己发出的 topic；shared starter 提供 `TopicDeclarator` 工具统一管理。

---

## 6. Outbox 模式

### 6.1 表结构

每个使用 outbox 的服务**独立建表**（不共享），表名 `outbox_event`，DDL 如下：

```sql
CREATE TABLE outbox_event (
  id              BIGSERIAL    PRIMARY KEY,
  event_id        UUID         NOT NULL UNIQUE,
  aggregate_type  VARCHAR(100) NOT NULL,
  aggregate_id    VARCHAR(100) NOT NULL,
  topic           VARCHAR(200) NOT NULL,
  event_type      VARCHAR(200) NOT NULL,
  payload         JSONB        NOT NULL,
  created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
  published_at    TIMESTAMPTZ  NULL,
  retry_count     INT          NOT NULL DEFAULT 0,
  last_error      TEXT         NULL
);

CREATE INDEX idx_outbox_pending
  ON outbox_event (created_at)
  WHERE published_at IS NULL;
```

- `payload` 存完整信封（不是 payload 片段），投递器直接 `producer.send(topic, aggregate_id, payload)`
- `published_at IS NULL` 的行为"待投递"；索引只覆盖这部分行，保持小而快

### 6.2 写入规约

业务操作在**同一本地事务**内：

```java
@Transactional
public void updateProject(ProjectChange change) {
    projectRepo.save(change.toEntity());          // 业务落库
    outboxRepo.insert(buildEnvelope(change));     // 同一事务写 outbox
    // 事务提交后触发 Postgres NOTIFY（下文）
}
```

### 6.3 投递器

触发方式：**Postgres `NOTIFY` + 5 秒兜底轮询**

- 写入 outbox 行后，trigger 发 `NOTIFY outbox_event_new`
- 投递器线程 `LISTEN outbox_event_new`，收到通知立即扫描 `published_at IS NULL` 行
- **同时**每 5 秒兜底扫一次（防 NOTIFY 丢失）
- 扫到行 → `KafkaTemplate.send(topic, aggregate_id, payload)` → 成功后 `UPDATE published_at = now()`
- 失败 → `retry_count += 1`，写 `last_error`；指数退避重试（见 §7.1）

### 6.4 Trigger 与 Schema 管理

- outbox 表 DDL、`AFTER INSERT` trigger（发 `NOTIFY outbox_event_new`）均由 shared starter 附带的 Flyway migration 提供（`V1__outbox_event.sql`）
- 业务服务只要开启 `s10.kafka.outbox.enabled=true`，启动时自动执行 migration；无需业务方手写 DDL

### 6.5 清理

`published_at IS NOT NULL` 超过 7 天的行由 starter 内置定时 job 物理删除，保留近期可查。清理策略在 shared starter 里可配置关闭（如合规要求永久保留）。

---

## 7. 运维与鲁棒性

### 7.1 消费端重试

shared starter 提供默认 `DefaultErrorHandler`：

- 重试策略：指数退避 `1s → 2s → 4s → 8s → 16s → 30s`（上限 30s）
- 最大重试：5 次
- 超限 → 消息投递到 DLQ topic：`{原topic}.dlq`（例 `mdm.project.updated.dlq`）
- 业务方可按需覆盖（特定异常快速失败、特定异常不重试）

### 7.2 DLQ 处理

- DLQ topic 保留 30 天
- shared starter 提供 `@DlqListener` 注解样例，把 DLQ 消息落到业务表 + 告警
- 定时 job（MVP 里每 10 分钟一次）统计各 DLQ topic 的消息数，超阈值发邮件

### 7.3 告警

MVP 只做**日志 + 邮件**：

- DLQ 消息数超阈值 → 日志 + 邮件通知运维/业务管理员
- Kafka 连接失败 → 日志
- 复用现有的邮件通道（来自 admin 或 platform 的 JavaMail 配置）

未来 Prometheus/Grafana 引入时接入：

- Kafka JMX exporter
- `outbox_event` 表的 `published_at IS NULL` 行数作为 lag 指标
- 消费组 lag、DLQ 消息数作为告警源

### 7.4 Kafka UI

- 部署：`dts-kafka-ui` 容器，bootstrap 指向 `dts-kafka:9092`
- 访问：通过 dts-proxy 反向代理到 `/kafka-ui/*`
- 权限：仅开发 / 运维访问；MVP 阶段用基础 HTTP Auth（Traefik 中间件），未来 K8s 上改 OIDC 对接 Keycloak
- 不对业务用户开放

---

## 8. 客户端 Starter 设计

### 8.1 模块位置

新增 `source/dts-shared-kafka`（Java 21 / Spring Boot 3 library 风格），发布为内部 Maven artifact。业务服务 `pom.xml` 依赖：

```xml
<dependency>
  <groupId>com.yuzhi.dts</groupId>
  <artifactId>dts-shared-kafka</artifactId>
  <version>${dts.version}</version>
</dependency>
```

### 8.2 提供能力

| 能力 | 说明 |
|---|---|
| `KafkaAutoConfiguration` | 自动装配 producer / consumer，读 `application.yml` 的 `s10.kafka.*` |
| `EventEnvelopeSerializer` | 生成 / 解析 信封（自动填 eventId / source / occurredAt） |
| `OutboxService` | `enqueue(topic, eventType, aggregateType, aggregateId, payload)` |
| `OutboxPublisher` | `NOTIFY + 5s` 兜底的投递器（Spring 启动后自动运行） |
| `TopicDeclarator` | 声明并预创建 topic（业务方在 `@Configuration` 里 `@Bean NewTopic` 即可） |
| `@DlqListener` | 便捷 DLQ 消费注解 |

### 8.3 配置样例

```yaml
s10:
  kafka:
    bootstrap-servers: dts-kafka:9092
    service-name: dts-mdm
    outbox:
      enabled: true
      poll-interval-ms: 5000
      cleanup-after-days: 7
    consumer:
      max-retries: 5
      backoff-initial-ms: 1000
      backoff-max-ms: 30000
```

### 8.4 hello-world demo

shared-kafka 仓库自带一个 `sample/` 子模块，含：

- producer 端：`OutboxService.enqueue(...)`
- consumer 端：`@KafkaListener` + 业务层 UPSERT
- 配套集成测试：启动 embedded kafka + Postgres testcontainer，断言事件发出后消费端完成 UPSERT

业务方在 F1 实施时直接参考此 sample，不再重复发明。

---

## 9. Task 映射

| Task | 对应 spec 章节 |
|---|---|
| T01 Kafka 接入 docker-compose | §3 |
| T02 业务事件规范 | §4, §5 |
| T03 客户端 starter | §8 |
| T04 Outbox 模式 | §6 |
| T05 Kafka UI 与监控 | §7 |

---

## 10. 验收清单

### 10.1 基础设施

- [ ] `apache/kafka:4.1.2` 单节点 KRaft 在 docker-compose 中启停通过（amd64 / arm64）
- [ ] `provectuslabs/kafka-ui` 可访问，能看到 topic / 消息 / consumer group
- [ ] 数据目录 `data/kafka` 持久化，容器重启无数据丢失

### 10.2 规范与文档

- [ ] `docs/kafka-event-contract.md` 发布，包含 Topic 命名 / 信封 / Schema 演进 / Topic 默认参数
- [ ] `imgversion.conf` 新增 `IMAGE_KAFKA` 和 `IMAGE_KAFKA_UI`

### 10.3 Starter

- [ ] `source/dts-shared-kafka` 模块可构建、可被其他服务依赖
- [ ] sample 模块集成测试通过（producer / consumer / outbox 全链路）
- [ ] 至少 1 个真实业务服务（F1 的 dts-approval）通过 starter 发出 / 消费事件

### 10.4 Outbox

- [ ] `outbox_event` DDL + 投递器代码纳入 shared starter
- [ ] NOTIFY + 5s 兜底双触发机制有单测证明健壮性（故意丢 NOTIFY → 兜底轮询仍能送达）

### 10.5 运维

- [ ] DLQ topic 自动生成、DLQ listener 示例可用
- [ ] DLQ 消息数超阈值的邮件告警 job 上线
- [ ] Kafka UI 通过 dts-proxy 对外暴露，有基础认证

---

## 11. 变更记录

| 日期 | 变更 | 作者 |
|---|---|---|
| 2026-04-18 | v1.0 初稿，基于 brainstorming session 输出 | brainstorm |
