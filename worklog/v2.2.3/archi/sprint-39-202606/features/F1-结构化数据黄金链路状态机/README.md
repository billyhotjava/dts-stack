# F1: 结构化数据黄金链路状态机

**优先级**: P0
**状态**: DONE

## 目标

定义并落地跨数据源、入湖、建模、治理、资产、权限、消费、运维的黄金链路状态机，让客户可以按一条主链路验收产品。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | 黄金链路状态机与验收契约 | P0 | DONE | - |
| T02 | 链路实例与阶段快照模型 | P0 | DONE | T01 |
| T03 | 统一链路聚合接口 | P0 | DONE | T02 |
| T04 | 端到端证据采集脚本 | P0 | DONE | T03 |

## 完成标准

- [x] 每个链路阶段有明确状态、失败分类、负责人和证据字段。
- [x] 前端不需要跨多个中心自行拼接链路状态。
- [x] IT 可按 chainId 或等价键追踪完整链路。

## 进度记录

- 2026-06-14: T01 已完成。代码落点：`source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/goldenchain/`；验证：`./mvnw -q -Dtest=GoldenChainContractTest test`。
- 2026-06-14: T02 已完成。代码落点：`source/dts-platform/src/main/java/com/yuzhi/dts/platform/domain/goldenchain/`、`source/dts-platform/src/main/java/com/yuzhi/dts/platform/repository/goldenchain/`、`source/dts-platform/src/main/resources/config/liquibase/changelog/20260614_01_golden_chain_instance.xml`；验证：`./mvnw -q -Dtest=GoldenChainContractTest,GoldenChainInstancePersistenceIT test`。
- 2026-06-14: T03 已完成。代码落点：`source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/goldenchain/GoldenChainQueryService.java`、`source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/goldenchain/dto/`、`source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/GoldenChainResource.java`；验证：`./mvnw -q -Dtest=GoldenChainContractTest,GoldenChainQueryServiceTest,GoldenChainResourceTest,GoldenChainInstancePersistenceIT test`。
- 2026-06-14: T04 已完成。脚本落点：`worklog/v2.2.3/sprint-39-202606/it/scripts/golden-chain-*.sh`；证据模板：`worklog/v2.2.3/sprint-39-202606/it/evidence/README.md`；验证：`bash -n` + dry-run。
