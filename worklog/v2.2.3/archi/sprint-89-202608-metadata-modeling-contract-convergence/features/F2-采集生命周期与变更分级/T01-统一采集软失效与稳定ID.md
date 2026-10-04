# T01: 统一采集软失效与稳定 ID

**优先级**: P0
**状态**: DONE
**依赖**: F1/T01；编码前对三个 sync service 与 resolver 做 GitNexus upstream impact

## 实现契约

### dataset/table

- PostgreSQL、JDBC、Inceptor 均按现有自然键 upsert。
- 完整快照中表消失：dataset.harvestStatus=`STALE`，保留 dataset/table/columns。
- 表重现：原 dataset/table 改回 `SYNCED`，snapshotTime 更新，ID 不变。
- JDBC 的遗留 PURGE 配置在兼容期强制降级为 MARK 并输出结构化 deprecation warning；不得由定时/例行同步调用 purge helper。

### column

- 将三处 `deleteByTable + recreate` 改为按 normalized name 原地 upsert。
- 新字段创建；已有字段更新技术属性但保留业务治理字段；缺失字段标记 `REMOVED`；重现恢复 `ACTIVE`。
- 默认字段列表只返回 ACTIVE；diff/历史视图可以读取 REMOVED。

### resolver

- source_id 非空且 harvestStatus=`SYNCED`：可解析。
- harvestStatus=`STALE`：`MISSING`。
- source_id 非空且 harvestStatus=NULL：`PROVIDER_ERROR/UNKNOWN`，要求重采集。
- source_id 为空的手工/遗留资产：沿现有 enabled/lifecycle/permission 规则，不强制采集状态。

## 预期文件

- `PostgresCatalogSyncService.java`
- `JdbcCatalogSyncService.java`
- `InceptorCatalogSyncService.java`
- `JpaCatalogSourceReferenceReadAdapter.java`
- column repository 的 ACTIVE/历史查询 seam（仅在确有消费时最小扩展）
- 聚焦的 stable identity / stale resolver tests

## RED→GREEN

- [x] 相同自然键持续命中原 dataset/table；column 原地更新并保留 ID
- [x] 消失/重现使用 `STALE/REMOVED → SYNCED/ACTIVE`，不重建原对象
- [x] 三个例行采集路径均不再调用 `deleteByTable` 或 `purgeDataset(dataset)`
- [x] 业务 tags/sensitiveTags/standard 在技术字段更新后不变
- [x] 只有完整快照成功后才执行软失效；采集异常直接返回失败
- [x] NULL/STALE/SYNCED/manual 四类 resolver 状态符合契约

### RED 证据（2026-08-10）

- 命令：`./mvnw -ntp -Dtest=CatalogColumnSyncServiceTest,SourceReferenceResolverAdapterTest,RoutineCatalogSyncSourceContractTest test`
- 结果：`BUILD FAILURE`，测试编译仅因 `CatalogColumnSyncService.synchronizeSnapshot(...)` 尚未实现而失败（2 处 `cannot find symbol`）。
- 判定：新增契约已有效锁定，进入 GREEN 实现。

### GREEN 证据（2026-08-10）

- 命令：`./mvnw -ntp -Dtest=CatalogColumnSyncServiceTest,SourceReferenceResolverAdapterTest,RoutineCatalogSyncSourceContractTest,JdbcClassificationLifecycleIT test`
- 结果：`BUILD SUCCESS`，15 tests，0 failures，0 errors，0 skipped；其中 JDBC 用 Testcontainers PostgreSQL 读取真实 JDBC metadata。
- 边界：未使用现场 PostgreSQL/Inceptor 授权样例，因此仅记为本地自动化验收，不等同现场 G4。

## 发布边界

不删除 purge helper、不执行生产回填；首轮只保证 routine path 不可达。若后续需要显式物理清理，必须另建有审批、预览、引用检查、审计和回滚的治理任务。
