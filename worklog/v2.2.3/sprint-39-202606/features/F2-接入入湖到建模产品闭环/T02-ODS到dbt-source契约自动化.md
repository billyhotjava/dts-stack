# T02: ODS 到 dbt source 契约自动化

**优先级**: P0
**状态**: DONE
**依赖**: T01

## 目标

让 ODS 表进入 dbt source 注册/生成流程，减少手工导入模型和手工补 source 配置。

## 技术设计

- 新增 `GoldenChainOdsDbtSourceContractService`，把 ODS 表快照转换为 dbt source 候选配置。
- 候选产物包含源系统、ODS schema/table、owner、刷新频率、字段快照和黄金链路证据引用。
- 缺 owner 或字段快照时返回 `BLOCKED_MODEL`，不能进入发布。
- STG schema/table 仅允许作为 dbt 内部层或诊断层，不能作为普通业务 source 发布。

## 影响范围

- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/goldenchain/modeling/`
- `source/dts-platform/src/test/java/com/yuzhi/dts/platform/service/goldenchain/modeling/`
- `worklog/v2.2.3/sprint-39-202606/it/README.md`

## 验证

- [x] 新增 ODS 表后能生成 dbt source 候选配置。
- [x] 缺字段快照或 owner 时不能进入发布。
- [x] STG 不能作为普通业务 source 发布。
- [x] focused test 通过：`cd source/dts-platform && ./mvnw -q -Dtest=GoldenChainOdsDbtSourceContractServiceTest test`
- [x] 黄金链路组合测试通过：`cd source/dts-platform && ./mvnw -q -Dtest=GoldenChainContractTest,GoldenChainIngestionTaskNormalizerTest,GoldenChainOdsDbtSourceContractServiceTest,GoldenChainQueryServiceTest,GoldenChainResourceTest,GoldenChainInstancePersistenceIT test`

## 完成标准

- [x] ODS 到 dbt source 不再依赖人工复制文件作为默认流程。
