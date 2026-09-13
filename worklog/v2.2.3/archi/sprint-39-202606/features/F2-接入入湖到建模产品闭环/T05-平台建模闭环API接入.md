# T05: 平台建模闭环 API 接入

**优先级**: P0
**状态**: DONE
**依赖**: T02/T03/T04

## 目标

把 F2 已完成的 ODS 到 dbt source、模型发布门禁、存量 dbt 迁移清单从纯 service 契约补成平台可调用 API，避免能力只停留在单测和文档里。

## 技术设计

- 新增 `GoldenChainModelingResource`，统一挂在 `/api/golden-chains/modeling` 下。
- `POST /ods-dbt-source-candidates`：输入 ODS 表和字段快照，返回 dbt source 候选、阻断原因和 `MODEL_READY` 阶段快照。
- `POST /model-release-decisions`：输入模型层级、dbt 运行证据、治理快照和语义契约，返回发布门禁结论、warning/blocker 和阶段快照。
- `POST /dbt-migration-inventory`：输入存量 dbt 资产快照，返回迁移风险统计和下一步动作。
- 三个 F2 契约服务注册为 Spring `@Service`，保持 controller thin，不在 API 层写入 dbt 文件或修改历史资产。

## 影响范围

- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/GoldenChainModelingResource.java`
- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/goldenchain/modeling/`
- `source/dts-platform/src/test/java/com/yuzhi/dts/platform/web/rest/GoldenChainModelingResourceTest.java`

## 验证

- [x] ODS 表快照可通过 API 返回可发布 dbt source YAML 和 `MODEL_READY` evidence。
- [x] 模型发布门禁可通过 API 返回 PROD/DEV/DEMO 的发布结论、warning/blocker 和 `RELEASE_READY` evidence。
- [x] 存量 dbt 资产可通过 API 返回高/中/低风险统计和迁移动作。

验证命令：

```bash
cd source/dts-platform
./mvnw -q -Dtest=GoldenChainModelingResourceTest test
```

## 完成标准

- [x] F2 建模闭环具备平台 API 验收入口，不再只能由 Java 单测直接 new service 验证。
