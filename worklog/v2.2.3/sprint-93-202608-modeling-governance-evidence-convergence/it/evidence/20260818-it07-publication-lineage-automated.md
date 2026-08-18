# IT-07 发布血缘自动化证据（2026-08-18）

## 验收边界

- 数据建模继续负责 ODS→DWD→DWS→ADS 的 dbt 转换、构建、测试和物化。
- Sprint-93 只在候选发布事务中消费已钉定的 ModelSpec、source binding、compiled SQL、物理观察和 Catalog 稳定列身份，写入既有表/字段血缘；不读取或加工 ODS 业务数据，也不执行 ODS→DWS SQL。
- 本证据是 PostgreSQL 集成测试证据，不替代部署后真实登录、API/UI 查询和 Chrome 集中 E2E。

## RED

新增 `dbtManagedDwdPublicationProjectsConfirmedOdsTableAndTransitiveColumnLineage` 后，发布事务未生成已确认 ODS→DWD 表级边，查询当前边时抛出 `EmptyResultDataAccessException`，证明旧实现只投影 ModelSpec 依赖，未消费已确认 ODS source binding。

## 实现

- `CandidatePublicationRepository` 在同一发布事务内：
  - 校验 ModelSpec 的 ODS sourceRef 与 `modeling_warehouse_plan_source` 中 `CONFIRMED / CATALOG_TABLE` 的 binding、version 和 Catalog 资产一致；
  - 写入既有 `catalog_dataset_lineage`，并把 candidate、dbt invocation、observedAt 和有效期作为证据；
  - 使用当前 revision、checksum、implementation revision 钉定的 MODEL/STG compiled SQL，保守组合 ODS→STG→DWD 字段映射；
  - 对 DWD→DWS→ADS 的 ModelSpec 依赖解析直接字段映射；多 ODS 来源存在歧义时不猜测物理字段边；
  - 重试复用同一逻辑边，回滚关闭表级和字段级 `valid_to`，历史边继续保留。
- 原 `DbtAssetSyncService` 的投影解析逻辑提取为唯一共享的 `DbtSqlProjectionParser`；没有新增第二套血缘 owner、parser 语义或表。

## GREEN

执行：

```text
./mvnw -DskipTests compile
./mvnw -Dtest=CandidatePublicationRepositoryIT#dbtManagedDwdPublicationProjectsConfirmedOdsTableAndTransitiveColumnLineage test
./mvnw -Dtest=DbtAssetSyncServiceTest,CandidatePublicationRepositoryIT test
```

结果：

- 编译通过。
- 新增 ODS→DWD 用例通过：当前表级边 1 条；字段边 2 条（`budget_no→budget_no`、`budget_amount_adjusted→budget_amount`），均为 `PARSED` 且指向同一 `dataset_lineage_id`。
- 重放发布不增加逻辑边；回滚后当前表/字段边均为 0，历史有效期表边 1 条、字段边 2 条。
- 共享解析和候选发布回归共 24 项，失败 0、错误 0、跳过 0。

## 结论

`PASS_AUTOMATED / FINAL_E2E_PENDING`。IT-07 的缺失写入能力已完成，最终 PASS 仍需在全部 Sprint-93 实现收口后，使用真实模型链验证 Catalog/API/UI 的 ODS→DWD→DWS→ADS 表级、字段级、来源、验证状态和时间有效期。
