# T02: PgBouncer 连接池方案设计

**优先级**: P0
**状态**: READY
**依赖**: 无

## 目标
设计连接池中间件方案，解决多服务争抢 PostgreSQL 连接问题。

## 技术设计

### 现状
- 8 个服务各自维护 HikariCP 连接池，直连 PostgreSQL
- `max_connections=300`，无全局连接数管控
- 高负载下可能耗尽连接，导致服务不可用

### 设计要点
1. **PgBouncer 部署模式**: sidecar vs 独立容器 vs 每服务一个
2. **池化模式选择**: session pooling vs transaction pooling vs statement pooling
3. **各服务连接数规划**:
   - dts-platform: 核心业务服务，需求最大
   - dts-admin: 管理后台，需求较小
   - Keycloak: 登录高峰期需求大
   - Airflow: 调度密集期需求大
   - OpenMetadata: 元数据采集期需求大
4. **HikariCP 参数调优**: 各服务 minimumIdle / maximumPoolSize 规划
5. **监控指标**: 活跃连接数、等待队列、连接复用率
6. **故障处理**: PgBouncer 自身的高可用

### 需要回答的问题
- 各服务的并发峰值是多少？
- 是否有长事务场景（影响 transaction pooling 选择）？
- OpenMetadata / Airflow 等第三方组件是否兼容 PgBouncer？

## 影响范围
- `docker-compose.yml`: 新增 PgBouncer 服务
- 所有服务的 `application.yml`: JDBC URL 改指 PgBouncer
- `.env`: 新增 PgBouncer 配置变量

## 验证
- [ ] 包含各服务连接数分配矩阵
- [ ] 包含 PgBouncer 配置模板
- [ ] 包含第三方组件兼容性验证方案

## 完成标准
- [ ] 产出完整的连接池技术方案文档
