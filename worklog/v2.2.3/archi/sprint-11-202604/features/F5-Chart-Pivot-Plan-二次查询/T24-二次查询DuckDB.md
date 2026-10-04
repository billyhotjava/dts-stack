# T24: 二次查询（DuckDB 临时视图）

**优先级**: P1
**状态**: READY
**依赖**: F4

## 目标

"Query This Result"：把当前查询的结果集注册为临时视图（session 级），用户可对其继续写 SQL。使用 DuckDB 内嵌引擎，不污染底层数据源。

## 技术设计

### 核心流程

1. 用户在结果区点 `Query This Result`
2. 后端 `SqlResultStreamService.createTempView(executionId)`：
   - 从 `query_execution_chunk` 读全量行
   - 写入 DuckDB 的内存数据库：`CREATE TEMP TABLE result_<id> AS SELECT ...`
   - 返回临时视图名（如 `result_a1b2c3d4`）
3. 前端自动开新 Tab：
   - datasourceId = `virtual:duckdb-{sessionId}`
   - 初始 SQL = `SELECT * FROM result_a1b2c3d4 LIMIT 100`
4. 用户可对该视图自由写 SQL；执行路由到 DuckDB

### DuckDB 集成

```xml
<dependency>
  <groupId>org.duckdb</groupId>
  <artifactId>duckdb_jdbc</artifactId>
  <version>1.0.0</version>
</dependency>
```

- 每用户 session 一个 DuckDB 实例（JVM 内进程）
- key = `userId + tabId` 或 `sessionId`
- 连接池管理：`ConcurrentMap<String, Connection>`

### 资源回收

- 临时视图 30 分钟不活跃自动清理（定时任务 + 最近访问时间戳）
- 用户关闭引用 Tab 立即清理
- JVM OOM 防护：单实例上限 256MB，超限拒绝新视图

### 权限

- 只有创建该临时视图的用户可访问
- 跨用户、跨 session 隔离

### 新端点

| 方法 | 路径 | 作用 |
|---|---|---|
| `POST` | `/api/sql/v2/temp-views` | 从 executionId 创建临时视图，返回视图名 |
| `POST` | `/api/sql/v2/temp-views/{name}/query` | 对该视图执行 SQL |
| `DELETE` | `/api/sql/v2/temp-views/{name}` | 显式销毁 |

### 审计

- `SQL_TEMP_VIEW_CREATE` / `SQL_SUBQUERY_EXECUTE`

### 回退方案

若 DuckDB JNI 在目标部署环境不稳定（如 Alpine glibc 兼容问题），回退到 PostgreSQL 临时表：
- 用 `CREATE TEMP TABLE` 写入平台数据库
- 性能次于 DuckDB 但运维稳定

## 影响范围

- 新增 DuckDB 依赖（需验证部署环境兼容性）
- 新增 `SqlResultStreamService`
- 新增 `SqlIdeResource` 相关端点
- 新增前端"Query This Result"按钮 + 新 Tab 触发逻辑

## 验证

- [ ] 创建临时视图成功
- [ ] 对视图写 SQL（含 JOIN、聚合、窗口函数）正确执行
- [ ] 30 分钟不活跃自动清理
- [ ] 关闭 Tab 立即清理
- [ ] 跨用户隔离
- [ ] 审计正确
- [ ] DuckDB 兼容性验证（含目标 Linux 环境）

## 完成标准

- [ ] DuckDB 方案通过
- [ ] 若不通过，回退 PostgreSQL 并文档化
