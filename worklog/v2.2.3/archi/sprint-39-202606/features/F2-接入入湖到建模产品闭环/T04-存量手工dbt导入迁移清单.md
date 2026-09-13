# T04: 存量手工 dbt 导入迁移清单

**优先级**: P1
**状态**: DONE
**依赖**: T03

## 目标

识别当前仍依赖手工 dbt 导入、手工注册或运行图缺失的资产，并给出迁移路径。

## 技术设计

- 新增 `GoldenChainDbtMigrationInventoryService`，将存量 dbt 资产快照转换为迁移清单。
- 标记：已纳入主链路、需补 source、需补资产、需补血缘、需补运行图、需人工确认。
- 对 PM 业务包输出迁移清单，不在本任务强制迁移全部历史模型。

## 影响范围

- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/goldenchain/modeling/`
- `source/dts-platform/src/test/java/com/yuzhi/dts/platform/service/goldenchain/modeling/`
- `worklog/v2.2.3/sprint-39-202606/assets/dbt-migration-inventory-pm.md`

## 验证

- [x] 至少对一个现有业务包产出迁移报告：`assets/dbt-migration-inventory-pm.md`。
- [x] 报告能定位缺失层级和下一步动作。
- [x] focused test 通过：`cd source/dts-platform && ./mvnw -q -Dtest=GoldenChainDbtMigrationInventoryServiceTest test`
- [x] F1+F2 组合测试通过：`cd source/dts-platform && ./mvnw -q -Dtest=GoldenChainContractTest,GoldenChainIngestionTaskNormalizerTest,GoldenChainOdsDbtSourceContractServiceTest,GoldenChainModelReleaseGateServiceTest,GoldenChainDbtMigrationInventoryServiceTest,GoldenChainQueryServiceTest,GoldenChainResourceTest,GoldenChainInstancePersistenceIT test`

## 完成标准

- [x] 存量手工导入不再是不可见风险。
