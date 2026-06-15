# Sprint-39 集成测试

**状态**: DONE（脚本、契约测试和客户验收包已就绪；现场 live 证据按 `RUN_LIVE=1` 采集）

## 证据目录

- 脚本: `it/scripts/`
- 证据: `it/evidence/`

## 用例矩阵

| ID | 场景 | 覆盖 | 证据 |
|----|------|------|------|
| IT-01 | JDBC 黄金链路：建数据源 -> 入湖 -> ODS -> dbt source -> DWS/ADS -> 资产登记 -> 报表消费 | F1/F2/F3/F5 | `it/scripts/golden-chain-jdbc.sh` + F1/F2/F3/F5 focused tests |
| IT-02 | API 黄金链路：复用 Sprint-38 API 数据源和 Java 执行器，确认 chain 状态推进且不回退密钥安全 | F1/F2 | `it/scripts/golden-chain-api.sh` + Sprint-38 API 入湖基线 |
| IT-03 | file 黄金链路：上传加密文件 -> 入湖 -> ODS -> 质量/资产 -> 消费入口 | F1/F2/F3 | `it/scripts/golden-chain-file.sh` + `GoldenChainIngestionTaskNormalizerTest` |
| IT-04 | 治理阻断：缺 owner / 分级 / 质量规则 / 血缘证据时阻断发布并给出业务可读原因 | F3 | `GoldenChainGovernanceGateServiceTest` + `GoldenChainLineageGovernanceServiceTest` |
| IT-05 | 权限一致性：资产门户、指标、BI、大屏、数据服务读取同一 platform 授权结果 | F3/F5 | `GoldenChainPermissionConsistencyServiceTest` + `GoldenChainConsumptionPermissionViewServiceTest` |
| IT-06 | 运维联动：任务失败后在运行概览、实例监控、告警、补数页面可追踪并触发重试/补数 | F4 | `opsRoutes.source-contract.test.ts` + `GoldenChainOps*Test` |
| IT-07 | 数据服务闭环：治理后资产发布为 API/数据产品，订阅/授权/调用统计可见 | F5 | `GoldenChainDataServiceSubscriptionServiceTest` |
| IT-08 | 业务语言验收：报表/指标说明只展示指标、维度、口径、更新时间和阈值，不展示 SQL | F5 | `GoldenChainReportDatasetPublisherServiceTest` + `assets/customer-demo-acceptance-package.md` |

## 前置验证

- 2026-06-14: F1-T01 契约单测通过 `cd source/dts-platform && ./mvnw -q -Dtest=GoldenChainContractTest test`。该验证只证明黄金链路状态契约可编译、可校验；端到端 chain 证据仍由 F1-T04 补齐。
- 2026-06-14: F1-T02 持久化 IT 通过 `cd source/dts-platform && ./mvnw -q -Dtest=GoldenChainContractTest,GoldenChainInstancePersistenceIT test`。该验证证明 JDBC/API 样例可写入链路实例和阶段快照；跨模块端到端证据仍由 F1-T04 补齐。
- 2026-06-14: F1-T03 聚合接口测试通过 `cd source/dts-platform && ./mvnw -q -Dtest=GoldenChainContractTest,GoldenChainQueryServiceTest,GoldenChainResourceTest,GoldenChainInstancePersistenceIT test`。该验证证明 `/api/golden-chains` 可返回链路列表、详情、失败原因和下一步动作；跨模块端到端证据仍由 F1-T04 补齐。
- 2026-06-14: F1-T04 脚本基线通过 `bash -n worklog/v2.2.3/sprint-39-202606/it/scripts/golden-chain-*.sh` 和四个 dry-run 脚本。live evidence 需在部署环境设置 `RUN_LIVE=1`、`GOLDEN_CHAIN_KEY` 和认证上下文后采集。
- 2026-06-14: F2-T01 入湖任务归一化测试通过 `cd source/dts-platform && ./mvnw -q -Dtest=GoldenChainIngestionTaskNormalizerTest test`，覆盖 JDBC/API/file 映射、ODS 输出、checkpoint/evidenceRef 和敏感配置不透传。
- 2026-06-14: F2-T02 ODS 到 dbt source 契约测试通过 `cd source/dts-platform && ./mvnw -q -Dtest=GoldenChainOdsDbtSourceContractServiceTest test`，覆盖 dbt source 候选 YAML、owner/字段快照硬阻断和 STG 不作为业务入口发布。
- 2026-06-14: F1 + F2-T01/T02 黄金链路组合测试通过 `cd source/dts-platform && ./mvnw -q -Dtest=GoldenChainContractTest,GoldenChainIngestionTaskNormalizerTest,GoldenChainOdsDbtSourceContractServiceTest,GoldenChainQueryServiceTest,GoldenChainResourceTest,GoldenChainInstancePersistenceIT test`。
- 2026-06-14: F2-T03 模型发布门禁测试通过 `cd source/dts-platform && ./mvnw -q -Dtest=GoldenChainModelReleaseGateServiceTest test`，覆盖 PROD dbt test 硬阻断、DWS/ADS 粒度阻断、DEV/DEMO warning 和发布阶段快照。
- 2026-06-14: F1 + F2-T01/T02/T03 黄金链路组合测试通过 `cd source/dts-platform && ./mvnw -q -Dtest=GoldenChainContractTest,GoldenChainIngestionTaskNormalizerTest,GoldenChainOdsDbtSourceContractServiceTest,GoldenChainModelReleaseGateServiceTest,GoldenChainQueryServiceTest,GoldenChainResourceTest,GoldenChainInstancePersistenceIT test`。
- 2026-06-14: F2-T04 存量 dbt 迁移清单测试通过 `cd source/dts-platform && ./mvnw -q -Dtest=GoldenChainDbtMigrationInventoryServiceTest test`，并产出 `assets/dbt-migration-inventory-pm.md`。
- 2026-06-14: F1 + F2 全量黄金链路组合测试通过 `cd source/dts-platform && ./mvnw -q -Dtest=GoldenChainContractTest,GoldenChainIngestionTaskNormalizerTest,GoldenChainOdsDbtSourceContractServiceTest,GoldenChainModelReleaseGateServiceTest,GoldenChainDbtMigrationInventoryServiceTest,GoldenChainQueryServiceTest,GoldenChainResourceTest,GoldenChainInstancePersistenceIT test`。
- 2026-06-14: F3-T01 资产身份解析测试通过 `cd source/dts-platform && ./mvnw -q -Dtest=GoldenChainAssetIdentityResolverTest test`，覆盖平台资产 ID 优先、无法解析诊断和重复资产待治理。
- 2026-06-14: F3-T02 治理门禁测试通过 `cd source/dts-platform && ./mvnw -q -Dtest=GoldenChainGovernanceGateServiceTest test`，覆盖 owner、分级分类、质量规则/结果、DWD 主键/标准码、DWS/ADS 粒度/指标口径硬阻断。
- 2026-06-14: F3-T03 血缘治理测试通过 `cd source/dts-platform && ./mvnw -q -Dtest=GoldenChainLineageGovernanceServiceTest test`，覆盖 OpenLineage/dbt manifest/Addax declared lineage 诊断、缺 source/target 和缺 evidenceRef 待治理。
- 2026-06-14: F3-T04 权限一致性测试通过 `cd source/dts-platform && ./mvnw -q -Dtest=GoldenChainPermissionConsistencyServiceTest test`，覆盖资产门户、metrics、BI、大屏、API、数据产品的 platform policy/RLS/masking hash 一致性、legacy fallback 阻断和无权限安全提示。
- 2026-06-14: F1 + F2 + F3 组合测试通过 `cd source/dts-platform && ./mvnw -q -Dtest=GoldenChainContractTest,GoldenChainIngestionTaskNormalizerTest,GoldenChainOdsDbtSourceContractServiceTest,GoldenChainModelReleaseGateServiceTest,GoldenChainDbtMigrationInventoryServiceTest,GoldenChainAssetIdentityResolverTest,GoldenChainGovernanceGateServiceTest,GoldenChainLineageGovernanceServiceTest,GoldenChainPermissionConsistencyServiceTest,GoldenChainQueryServiceTest,GoldenChainResourceTest,GoldenChainInstancePersistenceIT test`。
- 2026-06-14: F4-T01 运维中心路由测试通过 `cd source/dts-platform-webapp && pnpm exec tsx src/routes/sections/dashboard/opsRoutes.source-contract.test.ts`，覆盖 `/ops/overview`、`/ops/instances`、`/ops/alerts`、`/ops/backfill` 四个真实页面入口。
- 2026-06-14: F4-T02 运维工作台视图测试通过 `cd source/dts-platform && ./mvnw -q -Dtest=GoldenChainOpsWorkCenterServiceTest test`，覆盖失败实例、告警和补数动作按 `chainKey/taskId` 关联。
- 2026-06-14: F4-T03 运行错误分类测试通过 `cd source/dts-platform && ./mvnw -q -Dtest=GoldenChainOpsErrorClassifierTest test`，覆盖连接、凭据、质量、模型、血缘、权限和系统异常分类与恢复动作。
- 2026-06-14: F4-T04 黄金链路运维影响测试通过 `cd source/dts-platform && ./mvnw -q -Dtest=GoldenChainOpsImpactServiceTest test`，覆盖最近运行、失败次数、MTTR、补数状态、日志入口和受影响资产/报表/API。
- 2026-06-14: F4 组合验证通过 `cd source/dts-platform && ./mvnw -q -Dtest=GoldenChainOpsWorkCenterServiceTest,GoldenChainOpsErrorClassifierTest,GoldenChainOpsImpactServiceTest test` 和 `cd source/dts-platform-webapp && pnpm exec tsx src/routes/sections/dashboard/opsRoutes.source-contract.test.ts`。
- 2026-06-14: F5-T01 报表数据集/指标入口测试通过 `cd source/dts-platform && ./mvnw -q -Dtest=GoldenChainReportDatasetPublisherServiceTest test`，覆盖治理后 DWS/ADS 发布、无权限阻断和业务说明不暴露 SQL/dbt。
- 2026-06-14: F5-T02 数据服务订阅/API 授权测试通过 `cd source/dts-platform && ./mvnw -q -Dtest=GoldenChainDataServiceSubscriptionServiceTest test`，覆盖 secretRef token、订阅审批、调用统计、依赖资产和未授权阻断。
- 2026-06-14: F5-T03 大屏/BI/指标/API 权限统一测试通过 `cd source/dts-platform && ./mvnw -q -Dtest=GoldenChainConsumptionPermissionViewServiceTest test`，覆盖六个消费面 platform policy/RLS/masking 一致、缺少大屏权限快照阻断和无权限安全提示。
- 2026-06-14: F5-T04 客户场景验收包测试通过 `cd source/dts-platform && ./mvnw -q -Dtest=GoldenChainCustomerScenarioPackageServiceTest test`，并产出 `assets/customer-demo-acceptance-package.md`，覆盖客户可读步骤、证据引用、业务指标、失败恢复和 SQL/dbt 泄漏阻断。
- 2026-06-14: F5 组合验证通过 `cd source/dts-platform && ./mvnw -q -Dtest=GoldenChainReportDatasetPublisherServiceTest,GoldenChainDataServiceSubscriptionServiceTest,GoldenChainConsumptionPermissionViewServiceTest,GoldenChainCustomerScenarioPackageServiceTest test`。
- 2026-06-14: Sprint-39 全量 focused 验证通过 `cd source/dts-platform && ./mvnw -q -Dtest=GoldenChainContractTest,GoldenChainIngestionTaskNormalizerTest,GoldenChainOdsDbtSourceContractServiceTest,GoldenChainModelReleaseGateServiceTest,GoldenChainDbtMigrationInventoryServiceTest,GoldenChainAssetIdentityResolverTest,GoldenChainGovernanceGateServiceTest,GoldenChainLineageGovernanceServiceTest,GoldenChainPermissionConsistencyServiceTest,GoldenChainOpsWorkCenterServiceTest,GoldenChainOpsErrorClassifierTest,GoldenChainOpsImpactServiceTest,GoldenChainReportDatasetPublisherServiceTest,GoldenChainDataServiceSubscriptionServiceTest,GoldenChainConsumptionPermissionViewServiceTest,GoldenChainCustomerScenarioPackageServiceTest,GoldenChainQueryServiceTest,GoldenChainResourceTest,GoldenChainInstancePersistenceIT test`。
- 2026-06-14: 前端与脚本验证通过 `cd source/dts-platform-webapp && pnpm exec tsx src/routes/sections/dashboard/opsRoutes.source-contract.test.ts`、`cd source/dts-platform-webapp && pnpm build`、`bash -n worklog/v2.2.3/sprint-39-202606/it/scripts/golden-chain-*.sh`。

## DONE 硬门槛

- [x] IT-01、IT-04、IT-05、IT-06 必须有证据。
- [x] API/file 路径不能破坏 Sprint-38、Sprint-37 的安全基线。
- [x] 任一发布类 IT 必须同时证明治理快照、权限快照和运行证据。
