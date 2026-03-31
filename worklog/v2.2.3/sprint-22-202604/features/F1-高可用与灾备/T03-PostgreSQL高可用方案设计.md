# T03: PostgreSQL 高可用方案设计

**优先级**: P1
**状态**: READY
**依赖**: T01

## 目标
设计 PostgreSQL 主从复制和自动故障转移方案，消除数据库单点故障。

## 技术设计

### 现状
- 单实例 PostgreSQL，无副本
- 数据库故障 = 全平台不可用

### 设计要点
1. **方案选型对比**:
   - Patroni + etcd: 自动 failover，社区活跃
   - repmgr: 轻量，适合简单场景
   - pg_auto_failover: Citus 出品，配置简单
2. **复制模式**: 同步复制 vs 异步复制（权衡一致性和性能）
3. **部署拓扑**: 一主一从 vs 一主两从
4. **VIP/DNS 切换**: 故障转移后客户端如何自动感知新主节点
5. **与 PgBouncer 集成**: PgBouncer 如何感知主从切换
6. **Docker Compose 适配**: 如何在 Compose 中编排多 PG 实例
7. **K8s 迁移考虑**: 方案需兼顾未来 K8s 部署（CloudNativePG Operator）

### 需要回答的问题
- 当前部署是否有多节点条件？
- 对数据一致性的要求（RPO=0 需要同步复制）？
- 是否有 etcd/ZooKeeper 等分布式协调服务？

## 影响范围
- `docker-compose.yml`: PostgreSQL 服务重构为多实例
- `.env`: 新增主从配置变量
- 所有服务连接配置: 支持多主机 JDBC URL 或 VIP

## 验证
- [ ] 包含至少三种方案的选型对比矩阵
- [ ] 包含故障转移流程图和时序图
- [ ] 包含 Docker Compose 和 K8s 两种部署模式的设计

## 完成标准
- [ ] 产出完整的高可用技术方案文档
