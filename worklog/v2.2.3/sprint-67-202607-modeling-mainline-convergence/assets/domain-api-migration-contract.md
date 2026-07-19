# 领域、API 与迁移契约

## 1. 目标 ModelSpec

```json
{
  "id": "uuid",
  "planId": "uuid",
  "domainId": "uuid",
  "businessActivityRef": "optional-string",
  "modelType": "FACT|DIMENSION|SUMMARY|APPLICATION",
  "layer": "ODS|STG|DWD|DWS|ADS",
  "name": "string",
  "description": "string",
  "grain": { "statement": "一行代表什么", "keys": ["field"] },
  "factShape": "TRANSACTION|PERIODIC_SNAPSHOT|ACCUMULATING_SNAPSHOT|null",
  "timeSemantics": { "type": "EVENT_TIME|SNAPSHOT_DATE|PERIOD|MILESTONE_DATES", "fields": ["field"] },
  "fields": [{ "name": "string", "type": "string", "nullable": true, "sourceFieldRef": "optional-string", "role": "KEY|ATTRIBUTE|TIME|MEASURE", "securityLevel": "optional-string" }],
  "sourceRefs": [{ "kind": "TABLE|DBT_MODEL|DATASET", "ref": "string", "layer": "ODS", "role": "PRIMARY|JOINED", "alias": "optional-string", "joinType": "INNER|LEFT|RIGHT|FULL|null", "joinExpression": "optional-string", "sortOrder": 0, "legacyRawRole": "optional-string" }],
  "dependsOn": [{ "modelSpecId": "uuid", "revision": 3 }],
  "dimensionRefs": ["model-spec-id"],
  "metricRefs": ["metric-id@version"],
  "standardBindings": [{ "fieldName": "string", "standardElementId": "uuid", "standardElementVersion": 2, "referenceCode": "string", "referenceCodeVersion": 4, "measurementUnitId": "uuid", "measurementUnitVersion": 1 }],
  "implementationMode": "DESIGNER_GENERATED|DBT_MANAGED",
  "materialization": "table|view|incremental",
  "revision": 1,
  "legacyRef": "optional-string"
}
```

新契约没有 `objectId`。`businessActivityRef` 仅 FACT 可填写，且为空合法；旧 `processId` 由兼容层单向映射，禁止双写。内部 `ModelEntity` 如仍被编译器或图查询消费，只能由 ModelSpec 投影生成，不提供独立 CRUD，也不能反向成为 ModelSpec 外键或发布门禁。`dependsOn` 沿用现有内核名，v2 将字符串 ID 扩展为带 revision 的引用；兼容 reader 可读旧字符串，下一次有效编辑时显式确认并升级，禁止静默锁定当前最新版。

## 2. ModelType 不变量

| 类型 | 保存门禁 | 实现门禁 | 发布门禁 |
|---|---|---|---|
| DIMENSION | domainId、名称、维度键说明 | 稳定键；来源或生成策略二选一 | 标准、质量、权限 |
| FACT | domainId、名称、grain.statement、grain.keys、sourceRefs | factShape、timeSemantics、来源字段映射 | 标准、质量、依赖、权限 |
| SUMMARY | domainId、名称、dependsOn、聚合粒度 | 聚合表达式、刷新策略 | 上游版本、质量、权限 |
| APPLICATION | domainId、名称、dependsOn、消费场景 | 输出字段和刷新策略 | 上游版本、服务/报表权限 |

门禁返回结构统一为：

```json
{
  "decision": "PASS|BLOCKED|UNKNOWN",
  "blockers": [{ "code": "MODEL_GRAIN_REQUIRED", "field": "grain.keys", "repairRoute": "/modeling/models/{id}" }],
  "checkedRevision": 3,
  "checkedAt": "instant"
}
```

## 3. WarehousePlan 基线

保留 `domainBindings`，其用户语言是业务分类。`processBindings` 从基线必填项移除；来源无需再映射到业务对象或业务过程。

目标基线条件：

```text
CATEGORY_SCOPE_CONFIRMED
AND PLANNING_POLICY_CONFIRMED
AND (SOURCE_INVENTORY_CONFIRMED OR CONCEPTUAL_DESIGN_ALLOWED)
```

`CONCEPTUAL_DESIGN_ALLOWED` 只允许创建/编辑候选 DIMENSION ModelSpec；进入实现前仍必须满足来源或生成策略。

退役 blocker：

- `DOMAIN_PROCESS_CONFIRMATION_INCOMPLETE`
- `SOURCE_BUSINESS_MAPPING_INCOMPLETE`
- `MODEL_PROCESS_REQUIRED`
- `MODEL_OBJECT_REQUIRED`

新增 blocker：

- `CATEGORY_SCOPE_INCOMPLETE`
- `PLANNING_POLICY_INCOMPLETE`
- `MODEL_SOURCE_REQUIRED`
- `MODEL_GRAIN_REQUIRED`
- `MODEL_DIMENSION_KEY_REQUIRED`
- `MODEL_UPSTREAM_REQUIRED`
- `MODEL_REVISION_DRIFT`

## 4. API 目标面

| 能力 | 目标 API | 旧 API 处置 |
|---|---|---|
| 计划与基线 | `/api/modeling/warehouse-plans` | 保留并改门禁 |
| 业务分类 | `/api/catalog/domains` | 复用，不再另建 semantic subject domain |
| 维度目录 | `/api/modeling/model-specs?modelType=DIMENSION` | 替代 semantic/modeling business objects |
| 四类表 | `/api/modeling/model-specs` | 从 vNext 路径提升为 canonical |
| 模型门禁 | `/api/modeling/model-specs/{id}/release-gate` | 替代对象存在性门禁 |
| 产物/运行/血缘 | `/api/modeling/model-specs/{id}/artifacts|runs|lineage` | 从 `/api/semantic/models/*` 迁入 |
| 指标 | `/api/governance/indicators` / dts-metrics owner API | semantic metric 只做迁移来源 |

兼容期 `/api/modeling/vnext` 可继续存在，但新前端只消费一组 canonical 客户端。`/api/semantic/business-objects` 和 `/api/modeling/vnext/business-objects` 切换为只读/410 写入拒绝，并记录调用方。

## 5. 数据迁移

### 5.1 Dry-run 输出

每条旧对象必须输出：旧 ID、分类结果、目标 modelSpecId、字段映射、冲突、是否可自动迁移、校验和。结果分为 `AUTO_DIMENSION`、`AUTO_FACT_MERGE`、`MANUAL_SPLIT`、`ARCHIVE_ONLY`。

迁移报告必须把识别类别、准备状态和处置动作分开。缺失 catalog domain 时状态为 `NEEDS_CLASSIFICATION`；事实对象无法唯一定位目标模型时为 `NEEDS_TARGET`；两者都不得返回可执行自动动作。旧映射没有 alias 或无法从 tableRole 无歧义还原 joinType 时保留空值及 `legacyRawRole` 并进入冲突清单，禁止根据名称伪造。

### 5.2 迁移顺序

1. 快照 `semantic_business_object`、`modeling_business_object` 及引用表；
2. 生成稳定 `legacyRef → modelSpecId` 映射；
3. 创建/补全 ModelSpec，幂等重跑不新增重复记录；
4. 迁移维度、来源、粒度、标准和依赖引用；
5. 迁移产物、运行、评审和发布引用；
6. 对比行数、引用数、校验和和孤儿记录；
7. 冻结旧写入，观察调用审计；
8. 达到退出条件后删除旧菜单/API/表，未达到则保持只读。

### 5.3 回滚

回滚只切换读取路由和写入开关，不删除已迁移 ModelSpec。所有迁移映射保留，旧表快照只读。若出现目标冲突，停止该租户批次，不以最后写入胜出。

## 6. 权限与审计

- 浏览业务分类、维度和模型继续执行数据域/部门权限过滤；
- 创建模型要求计划编辑权限和目标 domainId 可写权限；
- 迁移和冻结动作要求平台管理员权限，并记录租户、操作者、批次和校验结果；
- 旧深链兼容不得扩大原用户可见范围；
- 失败响应不得暴露其他租户的对象、模型或映射 ID。
