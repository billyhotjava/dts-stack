# T01: BusinessObject 与 ModelSpec 契约

**优先级**: P0
**状态**: DONE
**依赖**: 无

## 目标

定义业务对象、数仓规划、字段标准绑定和模型规格的跨前后端契约，避免业务对象被误解为表。

## 技术设计

新增契约至少包含：

- `BusinessObject`: `id/code/name/objectKind/processId/businessKey/grain/sourceRefs/status/implementationMode`
- `WarehousePlan`: `id/domainId/processId/layer/modelingMode/targetGrain/status`
- `StandardBinding`: `objectId/fieldName/standardElementId/referenceCode/securityLevel`
- `ModelSpec`: `id/objectId/processId/layer/modelType/implementationMode/grain/sourceRefs/dimensions/metrics/materialization/revision`
- `DbtArtifact`: `modelSpecId/path/artifactType/contentChecksum/gitRevision/dbtUniqueId/status`

## 影响范围

- 新增前端类型与纯函数契约测试。
- 新增后端 DTO/record 与 JSON schema。
- 更新 `assets/modeling-vnext-architecture.md` 和 API 矩阵。

## 验证

- [x] TypeScript 与 Java DTO 字段名称、可空性和枚举值一致。
- [x] 不允许无粒度的 DWD 设计器模型进入编译。
- [x] 设计器模式与 dbt 原生模式不能互相隐式覆盖。

## 完成标准

- [x] 契约测试先 RED 后 GREEN。
- [x] 生成一份 PJM ModelSpec JSON fixture，供后续前端/API/dbt 测试复用。
