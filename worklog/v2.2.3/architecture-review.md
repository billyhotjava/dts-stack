# S10-Stack 架构评审报告

**日期**: 2026-04-01
**版本**: v2.2.2 (评审基准)
**评审人**: 架构师 (AI-assisted)

## 一、架构概览

S10-Stack 是企业级数据治理与分析平台，采用 Spring Boot 3.4.5 + Java 21 + PostgreSQL 17 + React/Vite 技术栈，通过 Docker Compose 编排 10+ 服务，覆盖数据接入、建模、治理、BI 全链路。

## 二、关键架构缺陷

### 1. 单点故障风险严重（可用性）

**问题**: 所有服务共享一个 PostgreSQL 17 实例（8 个数据库），无主从复制、无故障转移。

- PostgreSQL 挂掉 -> 全平台瘫痪（Keycloak、Airflow、OpenMetadata、业务服务全部不可用）
- `max_connections=300`，8 个服务争抢连接池，高负载下容易耗尽
- 无连接池中间件（如 PgBouncer），每个 Spring Boot 服务自行管理连接
- 无备份/恢复策略，无灾难恢复预案

### 2. 服务间通信缺乏异步机制（扩展性）

**问题**: 所有服务间通信均为同步 REST 调用（RestTemplate / Feign），无消息队列。

- dts-platform -> dts-ingestion -> Airflow -> Addax -> OpenMetadata: 整条链路同步阻塞
- Airflow 执行状态靠轮询获取（15 秒间隔），不是事件驱动
- 数据接入任务的创建、执行、回调全部走 HTTP，任一环节超时则链路断裂
- 没有死信队列、没有重试队列，失败任务的恢复依赖手动触发或简单重试计数器

### 3. 调度引擎不可扩展（性能瓶颈）

**问题**: Airflow 使用 LocalExecutor，所有任务在单节点串行/并行执行。

- 无法水平扩展 worker 节点
- DAG 数量增长后，调度器将成为瓶颈
- Addax 通过 DockerOperator 启动容器执行 ETL，但受限于单主机资源
- 没有资源隔离 -- 一个大型 ETL 任务可能耗尽主机 CPU/内存，影响所有其他服务

### 4. 安全架构存在明显薄弱环节

**4a - 服务间认证过于薄弱**:
- 服务间仅靠 `X-DTS-Service` 请求头认证，无签名、无 mTLS
- `/api/platform/**` 和 `/api/admin/platform/**` 端点允许无认证的服务间调用
- 一旦攻击者进入 Docker 网络，可冒充任意服务调用所有内部 API

**4b - 密钥管理不安全**:
- 所有密码、OAuth2 Client Secret、Fernet Key 明文存储在 `.env` 文件中
- 无密钥轮换机制
- 自签名证书无自动续期

**4c - Ranger 集成缺失**:
- `/services/dts-ranger/` 只有 `.gitkeep`，声称有细粒度数据访问控制但实际未实现
- 当前行级/列级策略仅在应用层实现，绕过应用直接查库则无保护

### 5. 可观测性严重不足

- 没有分布式链路追踪: 10+ 微服务之间的调用链无法追踪
- 没有集中式日志收集: 各服务日志分散在各自的 bind mount 目录下，无 ELK/Loki 聚合
- 指标收集不完整: Traefik 暴露了 Prometheus metrics，但无 Grafana 仪表盘、无应用级 metrics、无告警规则
- 无健康检查告警: PostgreSQL 和 Elasticsearch 有 healthcheck，但无外部告警

### 6. 数据治理链路存在断层

- 元数据驱动不完整: OpenMetadata 与 dbt、Airflow 的集成依赖 cron 同步（每小时），非实时
- 数据质量规则执行脱节: GovRule 在 dts-platform 中定义，实际执行依赖 dbt tests / OpenMetadata profiler，两者之间无统一编排
- 血缘追踪不完整: 只覆盖 dbt 模型和 OpenMetadata 采集范围，Addax 接入的 ODS 层血缘部分依赖手动注册
- 数据标准和指标中心: 大部分是 CRUD 操作，缺少与实际数据流的关联

### 7. Docker Compose 不适合生产级部署

- 无自愈能力: `restart: unless-stopped` 只能应对进程崩溃
- 无滚动更新: 升级任何服务需要停机
- 无资源限制: 除 Elasticsearch 外所有服务无 CPU/Memory limits
- 无水平扩展: 所有服务单副本运行

### 8. 前端架构碎片化

- 三个独立 webapp: admin-webapp、platform-webapp、analytics-webapp
- UI 库不统一: admin 和 platform 用 antd，analytics 用自研 UI 库
- Hetu BI 引擎嵌入方式粗暴: Traefik path rewrite 代理到 host.docker.internal:7778（已决定移除）
- 状态隔离: 三个 webapp 之间无法共享用户状态

## 三、优先级总结

| 优先级 | 缺陷 | 风险等级 | 改进难度 |
|--------|------|---------|---------|
| **P0** | 单点 PostgreSQL 无备份无容灾 | 致命 | 低 |
| **P0** | 密钥明文存储 + 弱服务间认证 | 高 | 中 |
| **P1** | 无可观测性（链路追踪/集中日志/告警） | 高 | 中 |
| **P1** | Airflow LocalExecutor 不可扩展 | 高 | 中 |
| **P1** | 服务间全同步通信无消息队列 | 高 | 高 |
| **P2** | Docker Compose 生产部署无资源限制 | 中 | 低 |
| **P2** | 数据治理链路断层 | 中 | 高 |
| **P2** | 前端碎片化 + Hetu 移除 | 中 | 中 |

> 注: 多租户架构暂不纳入本版本范围。
