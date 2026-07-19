# T01：建立无业务对象的 ModelSpec 契约

**优先级**：P0
**状态**：DONE
**依赖**：F1-T02、F1-T03

## 目标与用户结果

用户选择模型类型后直接得到可编辑 DRAFT ModelSpec；保存、编译、运行和发布都不再先查询业务对象。

## 范围与不做

- 范围：ModelSpec v2 DTO、Schema、验证、API、表结构、revision 和兼容读。
- 不做：不新建 ModelEntity/语义对象替代物，不在一个 Task 完成旧数据迁移。

## 输入契约

公共字段：`planId`、`domainId`、`modelType`、`layer`、`name`、`implementationMode`；description、materialization 可选。`businessActivityRef` 仅 FACT 可选，其他 modelType 不接收。禁止新请求包含 objectId；兼容请求含 objectId 时只记录 legacyRef，不写新依赖。

## 输出产物

- ModelSpec v2 和 revision；
- model-type validation result；
- canonical `/api/modeling/model-specs` CRUD；
- v1/vNext/semantic 兼容读取映射。

## 详细设计

### 1. 架构裁决

- 新增 canonical `ModelSpecContract` / `ModelSpecApplicationService` / `ModelSpecRepository` / `ModelSpecResource`，但继续使用同一张 `modeling_model_spec` 主表；不新建 v2 模型主表。
- 不原地删改旧 `ModelingVNextContract.ModelSpec`；`/api/modeling/vnext/**` 降为 deprecated compatibility boundary，通过 `ModelSpecCompatibilityReader` 单向投影。
- 新路径为 `/api/modeling/model-specs`；旧 writer 不得修改 `contract_version=2` 的行。
- `ModelingDbtCompiler.compile` 影响面为 CRITICAL；本 Task 通过 `ModelSpecCompilerProjection` 适配，不直接重写 compiler。

### 2. canonical v2 契约

`CreateModelSpecCommand` 只接收 `planId/domainId/modelType/layer/name/implementationMode/idempotencyKey`，可选接收 `description/materialization/businessActivityRef/grain/factShape/timeSemantics/fields/sourceRefs/dependsOn/dimensionRefs/metricRefs/standardBindings/generationStrategy`。

- `dependsOn` 项是 `modelSpecId + revision`；`dimensionRefs` 同样带 revision；`metricRefs` 带 metricId + version。
- `standardBindings` 保留 fieldName/standardElementId/referenceCode/securityLevel，扩展标准版本及 measurementUnitId/version。
- `businessActivityRef` 仅 FACT 可选；其他类型携带时返回 422。
- 普通创建请求不接受 `objectId/processId/legacyRef/revision/checksum`；严格未知字段校验返回 `MODEL_SPEC_FIELD_NOT_ALLOWED`。
- `ModelSpecView` 由服务端增加 `contractVersion=2/id/status/revision/checksum/createdAt/updatedAt/compatibilityMode=CANONICAL`。

### 3. expand 数据库

1. 新 forward-only changeset 将 `object_id/process_id` 改为 nullable，保留列、FK 和索引到 F5 contract 阶段。
2. `modeling_model_spec` 增加 `contract_version/domain_id/business_activity_ref/description/fact_shape/grain_json/time_semantics/fields/source_refs/depends_on/dimension_refs/metric_refs/standard_bindings/generation_strategy/legacy_refs/current_checksum/idempotency_key/idempotency_request_hash/idempotency_response_snapshot`。
3. v2 行必须具备 plan/domain/checksum；增加 `(tenant_id, plan_id)` 复合 FK、domain FK、tenant+idempotency partial unique、tenant+plan+lower(name) v2 partial unique 与计划/分类/活动索引。
4. revision 表增加 `tenant_id/contract_version/snapshot_json/created_by`；v2 snapshot 只 INSERT，不使用旧 `ON CONFLICT DO UPDATE`。checksum 是排除 revision/checksum/timestamp 后 canonical JSON 的 SHA-256。

### 4. API、并发与兼容

1. POST 服务端生成 ID/revision/checksum；同 idempotency key+hash 返回不变快照，同 key 异内容返回 409。
2. PUT 以 `If-Match: "model-spec:{id}:{revision}:{checksum}"` 做 CAS；过期时返回 currentRevision/currentChecksum/currentEtag，不覆盖新版本。
3. canonical 创建须确认计划可编辑、domain 已是计划的 CONFIRMED 业务分类，并消费 F2-T02 的真实分类解析结果。
4. compatibility reader 优先读 v2 `snapshot_json`，否则读 v1 `spec_json`；v1 在 canonical 查询中标记 `LEGACY_READONLY`，v2 向旧 API 投影时 `objectId=null`，不伪造对象。
5. `releaseGate.registered` 改为模型身份有效 + 同租户计划存在 + domain 属于已确认分类，不再查询 business object。
6. contract 阶段只在零旧写、迁移对账一致且无消费者后删除 object_id/process_id FK/列；expand 阶段禁止 drop。

## 影响范围

- `ModelingVNextContract.java`
- `ModelingVNextApplicationService.java`
- `ModelingVNextResource.java`
- `modelingVnextContract.ts`、`modelingApi.ts`
- Modeling vNext JSON Schema/fixtures
- `modeling_model_spec` Liquibase changelog 与 revision 表

## 异常、权限与回滚

- plan/domain 不匹配：字段级 422，不创建空记录。
- revision 冲突：409 返回 currentRevision/checksum。
- 回滚通过 compatibility reader 和 feature flag 恢复 v1 读取；v2 写入记录不删除。

## 实施与测试设计

1. 先更新契约测试，证明无 objectId 的请求在旧实现失败。
2. 执行 GitNexus impact 后按 expand/application/contract 三段实现。
3. 补四类型、租户隔离、并发和 v1 兼容测试。
4. 运行 dts-platform 单元/集成测试和 webapp type/build。

## 验证证据

- `it/evidence/backend-contract/model-spec-v2.txt`
- `it/evidence/migration/model-spec-expand-contract.txt`
- `it/evidence/frontend/model-spec-type-contract.txt`

## 完成标准

- [x] 无 objectId 可完成创建、更新和读取。
- [x] 发布门禁不查询业务对象。
- [x] Java/TS/Schema 字段和枚举一致。
- [x] v1 数据可读且没有双写。
