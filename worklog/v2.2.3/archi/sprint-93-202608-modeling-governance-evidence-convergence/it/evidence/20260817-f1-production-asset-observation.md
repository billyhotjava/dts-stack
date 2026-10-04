# F1/T01 生产资产观察组件实施证据

**日期**：2026-08-17
**范围**：组件代码与定向单元/集成测试；未部署、未执行真实账号浏览器 E2E
**结论**：生产观察主干已接入，T01 保持 `IN_PROGRESS`，等待运行态 IT-01/02、500+ 统计对账及权限负向验收。

## 已接入的生产 owner

| Owner | 接入结果 |
|---|---|
| 模型候选发布/物化 | 发布事务登记唯一 `catalog_dataset` 后，通过薄 adapter 写 MATERIALIZATION observation；receipt 必须匹配 datasetId/AssetKey，失败不完成发布。 |
| dbt manifest 同步 | 对非 lifecycle owner 的稳定物理关系写 DBT_SYNC observation；lifecycle 管理模型由发布 owner 负责，避免双写。 |
| JDBC 技术元数据同步 | 在目录 dataset 与字段同步后写 JDBC_SYNC observation；身份冲突和观察拒绝 fail-closed。 |
| ingestion 来源/ODS 血缘 | 只对已验证成功执行写 INGESTION_EVENT observation；使用执行对应的 schema snapshot fingerprint，缺 sourceId 返回结构化错误。 |

ODS mapping 同步同时传递 `fieldSnapshotChecksum`，使 API 与表来源都能提供稳定 schema fingerprint。语义仓储 receipt 增加 asset identity，重复证据走幂等快路径；同一 AssetKey 绑定不同 resourceId 返回结构化冲突。

收口源码检索确认：生产 `ObservationCommand` 仅由既有 normalization owner、`CatalogPhysicalDatasetObservationAdapter` 和 `ModelPublicationAssetObservationAdapter` 构造；publication/materialization、dbt、JDBC catalog 与 ingestion 的调用点均进入这两个薄 adapter。

## 定向验证结果

| 模块/测试 | 结果 | 覆盖重点 |
|---|---:|---|
| dts-platform F1 相关单元测试 | 49 条通过 | admission、receipt identity、状态保护、dbt/JDBC/ingestion adapter、ODS fingerprint、发布失败回滚。 |
| `CatalogAssetSemanticStoreIT` | 4/4 通过 | identity、幂等、投影与统计。 |
| `JdbcClassificationLifecycleIT` | 1/1 通过 | JDBC 同步后的分类生命周期与 observation。 |
| `CandidatePublicationRepositoryIT` | 6/6 分项通过 | 唯一 dataset、字段/血缘、重放、回滚、100 条原子性，以及候选发布与通用 dbt writer 并发收敛。 |
| dts-ingestion `PlatformInfraClientTest` | 7/7 通过 | 精确执行 schema snapshots 进入 lineage payload；读取失败保持兼容。 |

候选发布 IT 的旧夹具已同步到当前 v2 schema：移除已退役 `object_id/process_id/spec_json`，并补齐非空 `warehouse_layer_code`。并发 loser 允许唯一键或 Hibernate 乐观锁两种等价竞态，待 winner 提交后由下一次幂等 dbt tick 收敛；最终强制断言只有一个 datasetId 且 artifact 绑定同一 ID。

## 已知非阻塞告警

- Maven 仍报告既有重复 compiler plugin 与 POI 4.1.2/5.3.0 convergence 警告。
- 测试上下文中未配置 `/opt/dts/upload`、Hive driver、biadmin 密码及外部 ingestion/analytics 服务，相关启动告警不影响本组数据库断言。
- JaCoCo 报告存在既有 stale execution data 警告；定向测试本身均有明确 Surefire 通过结果。
- 收口 `gitnexus_detect_changes(scope=unstaged)` 风险为 `MEDIUM`；当前工作区同时存在 analytics、permission、webapp 等其他未提交改动，检测共覆盖 46 个文件。F1 拥有文件未出现 HIGH/CRITICAL 新风险，报告映射到 ingestion `CreateTask` 的既有读取流程，未改其 API 契约。

## 尚未完成

1. 在重建后的真实运行实例执行 IT-01/02，核对首次物化的目录身份、语义投影和发布状态。
2. 用 500+ 资产验证 stats projection 与分页目录在同一 `asOf` 下对账。
3. 执行 xiezm 正向、部门角色越权负向和审计动作验收。
4. 所有功能完成后统一执行 Chrome 95/E2E；本次不提前重复执行。
