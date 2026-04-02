# T01: Airflow Executor 升级方案设计

**优先级**: P1
**状态**: READY
**依赖**: 无

## 目标
设计 Airflow 从 LocalExecutor 升级到分布式 Executor 的技术方案。

## 技术设计

### 现状
- Airflow 2.9.3 使用 LocalExecutor
- 所有任务在单节点执行，无法水平扩展
- DAG 数量增长后调度器成为瓶颈
- Addax ETL 任务通过 DockerOperator 启动，受限于单主机资源

### 设计要点
1. **Executor 选型对比**:
   - CeleryExecutor: 需引入 Redis/RabbitMQ 作为 broker
   - KubernetesExecutor: 每个任务一个 Pod，天然隔离（需 K8s）
   - CeleryKubernetesExecutor: 混合模式
2. **Docker Compose 阶段方案**: CeleryExecutor + Redis
3. **K8s 阶段方案**: KubernetesExecutor
4. **Worker 扩展**: 多 worker 节点部署拓扑
5. **任务队列**: 不同类型任务（ETL / dbt / 元数据采集）分配不同队列
6. **Flower 监控**: Celery worker 监控面板
7. **DAG 分发**: 多 worker 节点的 DAG 文件同步方案
8. **向后兼容**: 现有 DAG 代码无需修改

### 需要回答的问题
- K8s 迁移的时间线是什么？
- 是否可以接受引入 Redis 作为 Celery broker？
- 当前 DAG 数量和并发任务峰值？

## 影响范围
- `docker-compose.yml`: 新增 Redis、Celery worker、Flower 服务
- Airflow 配置: executor 类型、broker URL
- DAG 文件分发机制

## 验证
- [ ] 包含 LocalExecutor / CeleryExecutor / KubernetesExecutor 对比矩阵
- [ ] 包含分阶段迁移路线图
- [ ] 包含 DAG 文件分发方案

## 完成标准
- [ ] 产出完整的 Executor 升级技术方案文档
