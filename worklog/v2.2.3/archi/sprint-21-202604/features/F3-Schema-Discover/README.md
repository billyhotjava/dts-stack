# F3: Schema Discover 探测服务

**优先级**: P0
**状态**: DONE

## 目标

提供工业级接入中心的核心能力：从数据源自动发现库、表、字段、主键、索引、增量字段候选和样例数据，为 ODS 生成和任务向导提供可信输入。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | JDBC 方言探测接口与元数据 DTO | P0 | DONE | F2 |
| T02 | 表清单、字段、类型、注释读取 | P0 | DONE | T01 |
| T03 | 主键、唯一键、索引读取 | P0 | DONE | T01 |
| T04 | 增量字段候选识别与采样预览 | P0 | DONE | T02-T03 |
| T05 | Discover 结果缓存、刷新与 drift 标记 | P1 | DONE | T02-T04 |

## 完成标准

- [ ] 支持至少 PostgreSQL、MySQL、Oracle、SQL Server、DM8 的基础元数据探测。
- [x] 能返回 schema/table/column/type/comment/nullable/default。
- [x] 能识别 primary key、unique key、普通索引。
- [x] 能根据字段名和类型推荐更新时间字段候选。
- [x] 能采样预览前 N 行，且敏感字段按权限脱敏。

## 当前落地

- 新增 `POST /api/infra/data-sources/{id}/schema-discover`，复用数据源权限边界和审计记录。
- 新增 Schema Discover DTO，覆盖库产品、schema、表、字段、主键、索引、增量字段候选、采样行和耗时。
- `JdbcCatalogSyncService` 增加 preview-only 探测方法，不写入 catalog 资产，为后续 ODS/dbt source 生成提供输入。
- 新增 `infra_schema_discover_cache`，支持按数据源和探测条件缓存 Discover 结果，并通过 `forceRefresh` 手动刷新。
- 刷新时会对比上一次缓存，返回新增表、删除表、字段类型/nullable 变化等 drift 摘要。
- 数据源页面新增“探测”操作，展示数据库产品、表清单、主键、增量候选和字段摘要。
- Schema 探测弹窗展示缓存/实时标记和 drift 详情。

## 待补

- 方言验证矩阵已落到 `it/evidence/dialect-validation-matrix.md`；仍需要在真实联调环境归档 PostgreSQL、MySQL、Oracle、SQL Server、DM8 的连接测试、Discover、ODS 预览和预检输出。
