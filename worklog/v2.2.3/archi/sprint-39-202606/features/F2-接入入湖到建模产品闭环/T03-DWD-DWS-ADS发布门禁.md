# T03: DWD/DWS/ADS 发布门禁

**优先级**: P0
**状态**: DONE
**依赖**: T02

## 目标

把 dbt compile/test/build、schema contract、质量、血缘和治理快照纳入模型发布门禁。

## 技术设计

- 新增 `GoldenChainModelReleaseGateService`，把模型发布门禁抽成可测试契约。
- PROD 环境将 dbt compile/test/build 失败作为硬阻断；DEV/DEMO 保留 warning。
- DWD 发布检查主键、标准码映射、schema contract、质量、血缘和分级分类。
- DWS/ADS 发布检查粒度一致性、权限可消费性、schema contract、质量、血缘和分级分类。
- 发布结果映射黄金链路 `RELEASE_READY` 或阻断在 `MODEL_READY` / `GOVERNANCE_READY`。

## 影响范围

- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/goldenchain/modeling/`
- `source/dts-platform/src/test/java/com/yuzhi/dts/platform/service/goldenchain/modeling/`

## 验证

- [x] dbt test 失败时 prod 发布阻断。
- [x] 粒度不一致时 DWS/ADS 发布阻断。
- [x] DEV/DEMO dbt 失败保留 warning，不按 PROD 硬阻断。
- [x] focused test 通过：`cd source/dts-platform && ./mvnw -q -Dtest=GoldenChainModelReleaseGateServiceTest test`
- [x] 黄金链路组合测试通过：`cd source/dts-platform && ./mvnw -q -Dtest=GoldenChainContractTest,GoldenChainIngestionTaskNormalizerTest,GoldenChainOdsDbtSourceContractServiceTest,GoldenChainModelReleaseGateServiceTest,GoldenChainQueryServiceTest,GoldenChainResourceTest,GoldenChainInstancePersistenceIT test`

## 完成标准

- [x] 模型发布从工程动作升级为产品门禁。
