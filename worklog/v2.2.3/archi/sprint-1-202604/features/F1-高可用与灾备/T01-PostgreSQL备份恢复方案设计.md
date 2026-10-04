# T01: PostgreSQL 备份恢复方案设计

**优先级**: P0
**状态**: READY
**依赖**: 无

## 目标
设计 PostgreSQL 定时备份与灾难恢复方案，确保数据可恢复性。

## 技术设计

### 现状
- 单实例 PostgreSQL 17.6，承载 8 个数据库（dts_platform, dts_admin, dts_common, dts_analytics, dts_keycloak, openmetadata_db, airflow, dts_ranger）
- 无任何备份机制，主机故障 = 数据全部丢失
- 数据存储在 `./services/dts-pg/data` bind mount

### 设计要点
1. **备份策略选型**: pg_dump（逻辑备份） vs pg_basebackup（物理备份） vs pgBackRest
2. **备份频率**: 全量备份 + 增量备份的周期规划
3. **RPO/RTO 目标**: 根据业务需求定义可接受的数据丢失和恢复时间
4. **存储方案**: 本地 + 异机（MinIO / NFS），考虑离线环境约束
5. **恢复验证**: 定期恢复演练方案
6. **WAL 归档**: 是否启用 WAL 归档实现 PITR（Point-in-Time Recovery）
7. **自动化**: cron 定时备份脚本 + 备份状态监控告警

### 需要回答的问题
- 业务可接受的最大数据丢失量（RPO）是多少？
- 业务可接受的最大恢复时间（RTO）是多少？
- 备份存储空间预算？
- 是否有异地存储条件（离线环境下的异机备份）？

## 影响范围
- `docker-compose.yml`: PostgreSQL 服务配置（WAL 归档参数）
- `services/dts-pg/`: 备份脚本和配置
- 新增: 备份 cron job 或 sidecar 容器

## 验证
- [ ] 方案文档包含 RPO/RTO 定义
- [ ] 包含至少两种备份工具的选型对比
- [ ] 包含恢复流程 SOP
- [ ] 包含离线环境适配方案

## 完成标准
- [ ] 产出完整的备份恢复技术方案文档
