# 领域、API 与迁移契约

## 1. 目标 ModelSpec

```json
{
  "id": "uuid",
  "planId": "uuid",
  "domainId": "uuid",
  "businessActivityRef": "optional-string",
  "modelType": "FACT|DIMENSION|SUMMARY|APPLICATION",
  "layer": "DWD|DWS|ADS",
  "name": "string",
  "description": "string|null；新 DIMENSION UI 将其作为维度定义必填，历史 v2 允许为空",
  "dimensionProfile": {
    "dimensionCode": "DIM_STABLE_CODE",
    "hierarchies": [{ "code": "string", "name": "string", "levels": [{ "fieldName": "string", "order": 1 }] }],
    "scdPolicy": {
      "type": "NONE|TYPE1|TYPE2",
      "effectiveFromField": "optional-string",
      "effectiveToField": "optional-string",
      "currentFlagField": "optional-string"
    },
    "reuseScope": "PLAN|DOMAIN|TENANT"
  },
  "grain": { "statement": "一行代表什么", "keys": ["field"] },
  "factShape": "TRANSACTION|PERIODIC_SNAPSHOT|ACCUMULATING_SNAPSHOT|null",
  "timeSemantics": { "type": "EVENT_TIME|SNAPSHOT_DATE|PERIOD|MILESTONE_DATES", "fields": ["field"] },
  "fields": [{ "name": "string", "dataType": "string", "nullable": true, "sourceFieldRef": "optional-string", "role": "KEY|ATTRIBUTE|TIME|MEASURE", "securityLevel": "optional-string" }],
  "sourceRefs": [{ "kind": "TABLE|DBT_MODEL|DATASET", "ref": "string", "layer": "ODS_RAW|ODS_STANDARDIZED|STG|DWD", "role": "PRIMARY|JOINED", "alias": "optional-string", "joinType": "INNER|LEFT|RIGHT|FULL|null", "joinExpression": "optional-string", "sortOrder": 0, "sourceBindingId": "uuid", "resolvedVersion": "string" }],
  "generationStrategy": { "type": "string", "reference": "optional-string" },
  "dependsOn": [{ "modelSpecId": "uuid", "revision": 3 }],
  "dimensionRefs": [{ "modelSpecId": "uuid", "revision": 3 }],
  "metricRefs": [{ "metricId": "string", "version": 2 }],
  "standardBindings": [{ "fieldName": "string", "standardElementId": "uuid", "standardElementVersion": 2, "referenceCode": "string", "referenceCodeVersion": 4, "measurementUnitId": "uuid", "measurementUnitVersion": 1 }],
  "implementationMode": "DESIGNER_GENERATED|DBT_MANAGED",
  "materialization": "table|view|incremental",
  "revision": 1
}
```

新契约没有 `objectId`。`businessActivityRef` 仅 FACT 可填写，且为空合法；旧 `processId` 由兼容层单向映射，禁止双写。`dimensionProfile` 仅 DIMENSION 可填写，当前是 Phase-B 目标契约，尚未进入 Java/TS/Schema/DB，不能据此声称运行态已实现。内部 `ModelEntity` 如仍被编译器或图查询消费，只能由 ModelSpec 投影生成，不提供独立 CRUD，也不能反向成为 ModelSpec 外键或发布门禁。`dependsOn` 沿用现有内核名，v2 将字符串 ID 扩展为带 revision 的引用；`dimensionRefs` 同样必须固定 `modelSpecId + revision`。兼容 reader 可读旧字符串，下一次有效编辑时显式确认并升级，禁止静默锁定当前最新版。

### 1.1 DIMENSION 字段归一

- 客户所说的 definition 复用 `description`，不增加第二个同义字段；新 DIMENSION UI 在创建和编辑草稿时要求填写，属性继续写入 `fields(role=ATTRIBUTE)`。
- “维度键”复用 `grain.keys`，并在 `IMPLEMENTATION_READY` 校验每个 key 唯一命中 `fields(role=KEY)`；不新增 `dimensionKey`。
- `dimensionCode` 是维度目录定位 ModelSpec 的稳定大写 ASCII 标识，必须匹配 `^[A-Z][A-Z0-9_]{0,63}$`，租户内按大小写不敏感规则唯一且创建后不可变；它不是数据行 JOIN 键。数据行的业务自然键由 `grain.keys` 表达；代理键属于 SQL/dbt 实现产物；码表值的 `standard_code` 由标准模块持有并通过 `standardBindings` 引用。
- `scdPolicy` 属于 `IMPLEMENTATION_READY`：`TYPE2` 必须引用生效起始、生效结束、当前标志三个已声明字段，`NONE/TYPE1` 不接收这些 TYPE2 专属引用。计划 `historyPolicy` 只提供默认建议，不静默改写已保存 revision。
- `reuseScope` 为 `PLAN/DOMAIN/TENANT`，但不放大权限；同计划引用可使用有权 revision，跨计划引用只允许已发布且可访问的 revision。
- `sourceRefs` 与 `generationStrategy` 是 inclusive OR。DRAFT 可两者皆空；进入实现前至少一个有效。两者同时存在时，前者是可追溯输入，后者描述生成方式，不存在优先级、覆盖或静默舍弃。

兼容边界：上述“新 UI 必填”和 Phase-B `IMPLEMENTATION_READY` 规则不得追溯性收紧现有 `contractVersion=2` 保存校验。历史 v2 DIMENSION 的 `description=null`、重复 grain key 或 grain/KEY 未闭合仍须可读、可重放；只有完成快照迁移、幂等重放和 checksum/ETag 兼容验证后，才能通过新 contract version 或独立实现门禁强制执行。

## 2. ModelType 不变量

| 类型 | 保存门禁 | 实现门禁 | 发布门禁 |
|---|---|---|---|
| DIMENSION → DWD | 新 UI：planId、domainId、implementationMode、名称、description（维度定义）、维度键说明；目标层由类型确定；Phase-B 再增加 dimensionCode | KEY 字段闭合、scdPolicy；ODS_RAW/ODS_STANDARDIZED/STG，或当前计划已确认的存量或外部管理 DWD sourceRefs；或 generationStrategy 至少一个（允许同时存在） | 标准、质量、权限、当前 revision 产物 |
| FACT → DWD | domainId、名称、grain.statement、grain.keys；目标层由类型确定；sourceRefs/dependsOn 可均为空 | factShape、timeSemantics；技术层 sourceRefs 或锁定 revision 的 FACT@DWD dependsOn 至少一个（同时存在时全部校验）；DIMENSION 另走 dimensionRefs | 标准、质量、依赖、权限、当前 revision 产物 |
| SUMMARY → DWS | domainId、名称、锁定 revision 的 DIMENSION/FACT@DWD 或 SUMMARY@DWS dependsOn、聚合粒度；目标层由类型确定 | 聚合表达式、刷新策略、上游 CURRENT 且无环 | 上游版本、质量、权限、当前 revision 产物 |
| APPLICATION → ADS | domainId、名称、锁定 revision 的任意合法 DWD/DWS/ADS 四类模型 dependsOn、消费场景；目标层由类型确定 | 输出字段、刷新策略/SLA、上游 CURRENT 且无环 | 上游版本、服务/报表权限、当前 revision 产物 |

`ODS_RAW`、`ODS_STANDARDIZED`、`STG` 不进入 `ModelSpec.layer` 新写枚举：前两者由接入/目录与 SourceBinding 持有，后者由 SQL/dbt/调度产物持有。历史 `layer=ODS|STG` 的专属分类/UI 尚待实现；目标行为是由兼容 reader 返回并标记只读，写拒绝复用现有 `MODEL_SPEC_LEGACY_READONLY`，不新增同义 code。

门禁返回结构统一为：

```json
{
  "decision": "PASS|BLOCKED|UNKNOWN",
  "blockers": [{ "code": "MODEL_GRAIN_REQUIRED", "field": "grain.keys", "repairRoute": "/modeling/models/{id}" }],
  "checkedRevision": 3,
  "checkedAt": "instant"
}
```

DIMENSION 的 DRAFT 保存和 `IMPLEMENTATION_READY` 必须分离：DRAFT 不因缺少来源、生成策略或 SCD 而被拒绝；实现门禁返回稳定 blocker 和 repairRoute，不通过修改 ModelSpec 状态或伪造默认字段制造完成。

### 2.1 Phase-B expand 兼容门

当前 `ModelSpecSnapshotCodec` 以包含 null 的 canonical JSON 计算 checksum、ETag 和 idempotency request hash。实现 `dimensionProfile` 前必须先写并观察兼容 RED，至少覆盖：

1. 历史 v2 snapshot 不含 `dimensionProfile` 仍可读取；
2. 部署新代码后历史 checksum/ETag 不漂移；
3. 相同 idempotency key 与原请求仍重放原响应快照，不误报内容冲突；
4. 缺少 `dimensionProfile` 的历史 DIMENSION 只投影为待完善，不静默生成 code/SCD/层级并宣称门禁通过。

只有上述 RED 被最小兼容实现转绿后，才允许执行 additive schema/DTO/codec expand；禁止批量重算历史 checksum 来掩盖不兼容。

## 3. WarehousePlan 基线

保留 `domainBindings`，其用户语言是业务分类。`processBindings` 从基线必填项移除；来源无需再映射到业务对象或业务过程。

目标基线条件：

```text
CATEGORY_SCOPE_CONFIRMED
AND PLANNING_POLICY_CONFIRMED
AND (SOURCE_INVENTORY_CONFIRMED OR CONCEPTUAL_DESIGN_ALLOWED)
```

`CONCEPTUAL_DESIGN_ALLOWED` 只控制 WarehousePlan 的提前设计投影，不得反向收紧 canonical ModelSpec DRAFT：DIMENSION 仍可先做概念设计，FACT 具备粒度即可保存无输入草稿。进入实现前，DIMENSION 必须满足有效来源或 generationStrategy，FACT 必须满足有效 `sourceRefs OR dependsOn`；组合输入全部参与校验。

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
- `MODEL_SPEC_TYPE_LAYER_MISMATCH`
- `MODEL_SPEC_INPUT_KIND_NOT_ALLOWED`
- `MODEL_SPEC_UPSTREAM_LAYER_NOT_ALLOWED`
- `MODEL_SPEC_FACT_INPUT_REQUIRED`
- `MODEL_SPEC_DIMENSION_INPUT_REQUIRED`

上述 blocker 在 DRAFT/IMPLEMENTATION/RELEASE 使用同一 code，不因页面不同改名。`MODEL_SPEC_TYPE_LAYER_MISMATCH`、`MODEL_SPEC_INPUT_KIND_NOT_ALLOWED` 和 `MODEL_SPEC_UPSTREAM_LAYER_NOT_ALLOWED` 已与实现统一；不得保留无 `SPEC` 前缀的同义 code。前端下拉过滤不是安全边界。ODS 技术入口和历史 ODS/STG 专属迁移分类/UI 尚待实现；完成分类后兼容写拒绝复用现有 `MODEL_SPEC_LEGACY_READONLY`。

## 4. API 目标面

| 能力 | 目标 API | 旧 API 处置 |
|---|---|---|
| 计划台账、计划头与基线 | `/api/modeling/warehouse-plans`（list/get/create/PATCH/archive/baseline） | canonical 保留；前端不得复用旧项目空间 `/api/modeling/plans` |
| 业务分类 | `/api/catalog/domains` | 复用，不再另建 semantic subject domain |
| 维度目录 | `/api/modeling/model-specs?modelType=DIMENSION` | 替代 semantic/modeling business objects |
| 四类表 | `/api/modeling/model-specs` | 从 vNext 路径提升为 canonical |
| 模型门禁 | `/api/modeling/model-specs/{id}/release-gate` | 替代对象存在性门禁 |
| 产物/运行/血缘 | `/api/modeling/model-specs/{id}/artifacts|runs|lineage` | 从 `/api/semantic/models/*` 迁入 |
| 指标 | `/api/governance/indicators` / dts-metrics owner API | semantic metric 只做迁移来源 |

兼容期 `/api/modeling/vnext` 可继续存在，但新前端只消费一组 canonical 客户端。`/api/semantic/business-objects` 和 `/api/modeling/vnext/business-objects` 切换为只读/410 写入拒绝，并记录调用方。

计划头修改和归档使用 `If-Match: "plan-head:{version}"`。PATCH 只接受 `name/objective/scope/ownerId/ownerDepartmentId`；`code/onboardingMode/lifecycleStatus/tenantId/version` 不允许由前端回写。canonical 规划不提供前端物理删除，归档是默认退出动作。

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
