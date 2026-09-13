# F5: 业务消费闭环

**优先级**: P1
**状态**: DONE

## 目标

把治理后的 DWS/ADS 资产发布到指标、BI、大屏、API 和数据产品，使用业务语言让客户完成报表和服务消费。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | 报表数据集发布和指标入口 | P1 | DONE | F2-T03,F3-T04 |
| T02 | 数据服务订阅与 API 授权运营 | P1 | DONE | F3-T04 |
| T03 | 大屏/BI 消费资产权限统一 | P1 | DONE | T01 |
| T04 | 客户场景验收包与演示数据 | P1 | DONE | T01,T02,T03 |

## 完成标准

- [x] 业务用户通过指标、维度、报表、API、数据产品消费治理后的资产。
- [x] 默认说明不暴露 SQL、dbt 文件、内部表名。
- [x] 消费侧权限和资产门户一致。

## 进度记录

- 2026-06-14: T01 已完成。新增 `GoldenChainReportDatasetPublisherService`，将 DWS/ADS 发布门禁、权限一致性和业务字段合同转换为 BI Dataset 与指标入口，阻断无权限和 SQL/dbt 说明泄漏；验证：`./mvnw -q -Dtest=GoldenChainReportDatasetPublisherServiceTest test`。
- 2026-06-14: T02 已完成。新增 `GoldenChainDataServiceSubscriptionService`，统一数据服务 schema、消费方式、SLA、刷新频率、secretRef token、订阅审批和调用统计；验证：`./mvnw -q -Dtest=GoldenChainDataServiceSubscriptionServiceTest test`。
- 2026-06-14: T03 已完成。新增 `GoldenChainConsumptionPermissionViewService`，要求资产门户、指标、BI、大屏、API、数据产品六个消费面权限快照齐全并复用同一 platform policy/RLS/masking；验证：`./mvnw -q -Dtest=GoldenChainConsumptionPermissionViewServiceTest test`。
- 2026-06-14: T04 已完成。新增 `GoldenChainCustomerScenarioPackageService` 和客户验收包 `assets/customer-demo-acceptance-package.md`，覆盖客户可读步骤、证据清单、业务指标、失败恢复和无 SQL/dbt 说明泄漏；验证：`./mvnw -q -Dtest=GoldenChainCustomerScenarioPackageServiceTest test`。
