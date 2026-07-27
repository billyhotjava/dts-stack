# T01：建立后端契约、数据库、安全与 NFR 测试矩阵

**优先级**：P0
**状态**：DRAFT
**依赖**：F1～F4、F7

## 目标

用自动化测试关闭 compiler、Build/Publish Intent、candidate、pipeline、plan execution binding、DAG deployment、probe、publish、Catalog 和安全边界，在进入真实浏览器前形成稳定红绿门禁。

## 技术设计（Contract-first）

- **契约测试**：validate compatibility、SINGLE/BATCH Build Intent、active claim、服务端派生、P0 单目标、START_BUILD/retry/replacement、Publish Intent、role-aware candidate commands、legacy compatibility、聚合 scope 的 PlanExecutionBinding、两类 run purpose、RelationObservation、registration。
- **单元测试**：settings/capabilities、compiler golden files、state aggregation、idempotency/drift、error mapping。
- **PostgreSQL IT**：clean migration/rollback、CAS/unique/index、binding scope entry、run-purpose XOR、active OPERATIONAL_RUN、scheduled-open 幂等、100-entry publication 原子可见、Catalog/lineage 幂等。
- **Airflow contract IT**：RELEASE_BUILD/plan DAG 稳定 identity、共同 import 唯一 Python task factory、`max_active_runs=1`、原子 thin DAG 文件、parse/templateVersion/checksum/schedule 对账、UTC/IANA timezone、manual/CRON 正确落账、sync/probe 失败传播；静态检查不存在第二个 canonical Docker dbt runtime builder。
- **安全 IT**：domain duty resolver、Sprint-36/F3 `canPerform`、403、cross-tenant、同人批准/发布、auditor read-only、role/action/schedule/target/conf 注入、Airflow pairwise service identity + exact path allowlist、identifier injection。
- **secret IT**：Git/DAG/DagRun conf/XCom/API/DB/env/audit/log/evidence 扫描；host/container filesystem=tmpfs、runtime root 0700、profile 0600、固定路径派生/read-only/cleanup；symlink/path traversal、missing/denied/rotation、伪造 service header/token fail-closed。
- **NFR**：候选 101、100 并发 Build Intent、artifact 超限、Airflow accepted-then-timeout、restart recovery、DAG 数量/registration、CRON/手工并发、EXPLAIN、audit classified。
- **失败策略**：任何红灯阻止真实发布；不得通过禁用测试或预置成功数据绕过。

## 影响范围

- backend tests/resources
- Liquibase Testcontainers suite
- audit/security tests
- `it/evidence/it-01-*` 等机器结果

## 验证

- [ ] RED tests 在实现前可复现核心缺口。
- [ ] 所有适用 NFR 行有具体测试名/命令。
- [ ] clean DB 与升级 DB 两条路径。
- [ ] generic dbt sync 和旧 validate consumer 回归。
- [ ] 旧 lifecycle publication client 兼容委托且不产生双写。
- [ ] 连续发布 A/B 后 binding scope=A+B，cron 修改后 dagId 不变。
- [ ] 平台无 scheduler/nextRun 计算路径；actual 状态只读 Airflow。
- [ ] Sprint-36/F3 只有在实际 domain/migration/API/IT 存在并且发布链调用 `canPerform` 后才视为依赖完成。

## Definition of Done

- [ ] focused suite 0 failure/error。
- [ ] 测试不依赖执行顺序或手工数据库状态。
- [ ] 证据含命令、版本、时间和结果。
