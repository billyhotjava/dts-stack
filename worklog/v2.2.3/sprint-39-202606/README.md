# Sprint-39: 结构化数据黄金链路与商业化闭环

**时间**: 2026-06
**状态**: DONE
**类型**: Product Foundation / Implementation（dts-platform + dts-ingestion + dts-metrics + dts-platform-webapp）
**目标**: 面向传统行业结构化数据客户，把现有数据接入、入湖、建模、治理、资产、权限、报表、数据服务和运维能力串成一条可验收的主链路，先打牢商业产品基础，再演进现代湖仓路线。

## 背景

当前 dts-stack 已经具备多个单点能力：数据源、入湖任务、API/JDBC/file 执行、dbt 建模、质量规则、资产目录、血缘、权限、数据服务、指标语义、大屏设计器和运维后端接口。但这些能力仍主要按模块存在，客户很难按一条标准链路完成验收。

本 sprint 不追求新增复杂湖仓概念，核心是把结构化数据场景跑成闭环：

`数据源 -> 入湖任务 -> ODS -> DWD/DWS/ADS -> 质量/血缘/资产登记 -> 权限/审批 -> 指标/报表/数据服务 -> 运维监控`

## 核心架构决策

| 决策点 | 结论 |
|--------|------|
| 客户主场景 | 传统行业结构化数据，优先 JDBC/API/file 入湖，不以半结构化/实时流/湖仓表格式为第一目标 |
| 产品主线 | 建立一条可验收黄金链路，而不是继续按中心菜单补点状功能 |
| 建模主线 | dbt 仍是当前主建模引擎，但用户侧表达为“建模方案/指标模型/数据集发布”，不暴露手工导入 |
| 治理策略 | 治理从“登记项”升级为“发布门禁”：owner、分级、质量、血缘、权限缺失时阻断发布或进入待治理状态 |
| 语义指标 | dts-metrics 只承接 DWS/ADS 上的业务语义和报表产物，不替代 DWD/DWS 治理建模 |
| 现代湖仓 | 本 sprint 不引入 Iceberg/Hudi/Delta、流批一体、成本优化、语义缓存等下一阶段能力 |

## Feature 列表

| ID | Feature | 优先级 | Task 数 | 状态 | 阶段目标 |
|----|---------|--------|---------|------|----------|
| F1 | 结构化数据黄金链路状态机 | P0 | 4 | DONE | 定义并落地跨模块主链路状态与验收证据 |
| F2 | 接入入湖到建模产品闭环 | P0 | 4 | DONE | 把数据源/入湖/ODS/dbt source/模型发布串成后台主线 |
| F3 | 治理资产权限硬门禁 | P0 | 4 | DONE | 让治理、资产、血缘、权限成为发布前硬约束 |
| F4 | 任务运维中心产品化 | P1 | 4 | DONE | 将已有 ops 后端能力接成客户可用运维工作台 |
| F5 | 业务消费闭环 | P1 | 4 | DONE | 把指标、报表、大屏、数据 API、数据产品统一到治理后资产 |

**统计**: READY=0, IN_PROGRESS=0, DONE=20, BLOCKED=0
**依赖顺序**: F1 -> F2 -> F3 -> F4/F5；F4 可在 F1 状态模型稳定后并行，F5 依赖 F2/F3 的发布门禁口径。

## 进度记录

- 2026-06-14: 完成 F1-T01。`dts-platform` 新增黄金链路契约代码，覆盖阶段顺序、阶段状态、阻断码、阶段快照字段和阻断校验；focused test 通过 `./mvnw -q -Dtest=GoldenChainContractTest test`。
- 2026-06-14: 完成 F1-T02。`dts-platform` 新增 `golden_chain_instance` / `golden_chain_stage_snapshot` 轻量模型、Repository 与 Liquibase changelog；focused test 通过 `./mvnw -q -Dtest=GoldenChainContractTest,GoldenChainInstancePersistenceIT test`。
- 2026-06-14: 完成 F1-T03。`dts-platform` 新增统一只读聚合接口 `/api/golden-chains`，返回链路列表、详情、阶段状态、失败原因和业务可读下一步动作；focused test 通过 `./mvnw -q -Dtest=GoldenChainContractTest,GoldenChainQueryServiceTest,GoldenChainResourceTest,GoldenChainInstancePersistenceIT test`。
- 2026-06-14: 完成 F1-T04。新增 `it/scripts/golden-chain-*.sh` 和 `it/evidence/README.md`，覆盖 JDBC/API/file 与治理/权限/运维证据采集；验证通过 `bash -n worklog/v2.2.3/sprint-39-202606/it/scripts/golden-chain-*.sh` 和 dry-run。
- 2026-06-14: 完成 F2-T01。新增 `GoldenChainIngestionTaskNormalizer` / `GoldenChainIngestionTaskView`，将 JDBC/API/file 入湖任务统一映射到 INGESTION 阶段、ODS 输出、checkpoint 和 execution evidence；focused test 通过 `./mvnw -q -Dtest=GoldenChainIngestionTaskNormalizerTest test`。
- 2026-06-14: 完成 F2-T02。新增 `GoldenChainOdsDbtSourceContractService`，将 ODS 字段快照生成 dbt source 候选配置，并在缺 owner、缺字段快照或 STG 暴露时返回 `BLOCKED_MODEL`；focused test 通过 `./mvnw -q -Dtest=GoldenChainOdsDbtSourceContractServiceTest test`。
- 2026-06-14: 完成 F2-T03。新增 `GoldenChainModelReleaseGateService`，模型发布前校验 dbt compile/test/build、DWD 主键/标准码、DWS/ADS 粒度、schema contract、质量、血缘和分级分类；focused test 通过 `./mvnw -q -Dtest=GoldenChainModelReleaseGateServiceTest test`。
- 2026-06-14: 完成 F2-T04。新增 `GoldenChainDbtMigrationInventoryService` 和 PM 业务包迁移清单 `assets/dbt-migration-inventory-pm.md`，将存量手工 dbt 导入风险显性化；focused test 通过 `./mvnw -q -Dtest=GoldenChainDbtMigrationInventoryServiceTest test`。
- 2026-06-14: 完成 F3-T01。新增 `GoldenChainAssetIdentityResolver`，固化资产身份优先级并将无法解析或重复资产标记为待治理；focused test 通过 `./mvnw -q -Dtest=GoldenChainAssetIdentityResolverTest test`。
- 2026-06-14: 完成 F3-T02。新增 `GoldenChainGovernanceGateService`，将 owner、分级分类、质量规则/结果和 DWD/DWS/ADS 关键治理字段变成发布硬门禁；focused test 通过 `./mvnw -q -Dtest=GoldenChainGovernanceGateServiceTest test`。
- 2026-06-14: 完成 F3-T03。新增 `GoldenChainLineageGovernanceService`，将血缘解析失败、缺 source/target 或缺 evidenceRef 统一转为 `PENDING_GOVERNANCE`；focused test 通过 `./mvnw -q -Dtest=GoldenChainLineageGovernanceServiceTest test`。
- 2026-06-14: 完成 F3-T04。新增 `GoldenChainPermissionConsistencyService`，统一资产门户、metrics、BI、大屏、API 服务和数据产品的 platform policy/RLS/masking 快照一致性；focused test 通过 `./mvnw -q -Dtest=GoldenChainPermissionConsistencyServiceTest test`。
- 2026-06-14: 完成 F4-T01。`dts-platform-webapp` 补齐任务运维中心四个菜单路径到真实页面的 static route 和 dynamic override；source-contract test 通过 `pnpm exec tsx src/routes/sections/dashboard/opsRoutes.source-contract.test.ts`。
- 2026-06-14: 完成 F4-T02。新增 `GoldenChainOpsWorkCenterService`，按 `chainKey/taskId` 关联任务实例、告警和补数动作；focused test 通过 `./mvnw -q -Dtest=GoldenChainOpsWorkCenterServiceTest test`。
- 2026-06-14: 完成 F4-T03。新增 `GoldenChainOpsErrorClassifier`，把连接、凭据、质量、模型、血缘、权限和系统异常映射为可恢复动作；focused test 通过 `./mvnw -q -Dtest=GoldenChainOpsErrorClassifierTest test`。
- 2026-06-14: 完成 F4-T04。新增 `GoldenChainOpsImpactService`，将链路最近运行、失败次数、MTTR、补数状态、日志入口和受影响资产/报表/API 纳入运维视角；focused test 通过 `./mvnw -q -Dtest=GoldenChainOpsImpactServiceTest test`。
- 2026-06-14: 完成 F5-T01。新增 `GoldenChainReportDatasetPublisherService`，将治理后 DWS/ADS 资产发布为 BI Dataset 与指标入口，并阻断无权限和 SQL/dbt 说明泄漏；focused test 通过 `./mvnw -q -Dtest=GoldenChainReportDatasetPublisherServiceTest test`。
- 2026-06-14: 完成 F5-T02。新增 `GoldenChainDataServiceSubscriptionService`，让数据 API/数据产品具备订阅状态、secretRef 授权、调用统计和依赖资产视图；focused test 通过 `./mvnw -q -Dtest=GoldenChainDataServiceSubscriptionServiceTest test`。
- 2026-06-14: 完成 F5-T03。新增 `GoldenChainConsumptionPermissionViewService`，统一资产门户、指标、BI、大屏、API 服务和数据产品的消费权限视图；focused test 通过 `./mvnw -q -Dtest=GoldenChainConsumptionPermissionViewServiceTest test`。
- 2026-06-14: 完成 F5-T04。新增 `GoldenChainCustomerScenarioPackageService` 和客户验收包 `assets/customer-demo-acceptance-package.md`，输出传统行业结构化数据客户可读的闭环验收材料；focused test 通过 `./mvnw -q -Dtest=GoldenChainCustomerScenarioPackageServiceTest test`。
- 2026-06-14: Sprint-39 全量 focused 验证通过：黄金链路 Java 测试组合、运维路由契约、IT 脚本语法检查和 `dts-platform-webapp` 生产构建均通过。

## 完成标准

- [x] 新建一条“黄金链路实例”可从数据源创建一路追踪到报表/API/数据产品消费。
- [x] JDBC/API/file 至少各保留一个结构化数据验收样例；Sprint-38 API 入湖证据作为 API 路径基线，不回退。
- [x] ODS 到 DWD/DWS/ADS 的 dbt 产物不再依赖手工导入作为主流程，至少具备受控生成/注册/发布入口。
- [x] 发布到指标、报表、大屏、数据服务前必须通过 owner、分级分类、质量、血缘、权限门禁。
- [x] 任务运维中心 4 个菜单入口有真实页面或明确路由，能查看运行概览、实例、告警、补数。
- [x] 业务消费层展示客户语言：指标、维度、数据集、API、数据产品，不暴露底层 SQL/dbt 文件作为默认表达。
- [x] IT 证据覆盖黄金链路、治理阻断、运维联动、消费权限一致性，落在 `it/evidence/`。

## 非目标

- 不在本 sprint 引入新的湖仓存储格式、分布式查询优化或实时流处理。
- 不重构 IAM/Keycloak 主体系，只收口资产权限消费边界。
- 不替换 Airflow/Addax/dbt/OpenMetadata，只把现有能力纳入主链路和门禁。
- 不让 dts-metrics 直接拥有平台资产、权限、RLS、审计或 dbt 发布事实源。
- 不一次性完成所有历史资产迁移；本 sprint 先提供迁移清单、状态标记和关键路径样例。

## 相关材料

- 能力契约: `assets/product-capability-contract.md`
- IT 计划: `it/README.md`
- Sprint-38 API 入湖基线: `worklog/v2.2.3/sprint-38-202606/README.md`
- Sprint-35b metrics 硬化: `worklog/v2.2.3/sprint-35b-202606/README.md`
