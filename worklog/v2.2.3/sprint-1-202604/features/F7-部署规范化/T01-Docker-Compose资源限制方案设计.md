# T01: Docker Compose 资源限制方案设计

**优先级**: P2
**状态**: READY
**依赖**: 无

## 目标
为 Docker Compose 部署中的所有服务设计合理的资源限制，防止资源争抢和 OOM。

## 技术设计

### 现状
- 除 Elasticsearch（512m heap）外，所有服务无 CPU/Memory 限制
- 一个服务内存泄漏可以拖垮整机
- 无资源使用基线数据

### 设计要点
1. **资源基线采集**: 各服务在正常/峰值负载下的资源使用量
2. **限制策略**:
   - `deploy.resources.limits`: 硬上限
   - `deploy.resources.reservations`: 预留资源
3. **各服务资源规划**:
   - dts-platform: 业务核心，资源需求最大
   - dts-admin: 管理后台，中等需求
   - dts-ingestion: ETL 期间资源波动大
   - dts-analytics: BI 查询资源密集
   - PostgreSQL: 需要稳定的内存分配
   - Elasticsearch: 已有 heap 限制，需增加容器级限制
   - Keycloak: 登录高峰期资源需求
   - Airflow: 调度器 + worker 分别限制
4. **OOM 处理**: OOM-killer 优先级设置
5. **最小部署规格**: 平台运行的最低硬件要求文档

## 影响范围
- `docker-compose.yml`: 所有服务增加 deploy.resources
- `docker-compose-app.yml`: 同步更新
- 文档: 最小部署规格说明

## 验证
- [ ] 包含各服务资源限制配置表
- [ ] 包含最小部署硬件规格
- [ ] 包含资源基线采集方法

## 完成标准
- [ ] 产出完整的资源限制方案文档
