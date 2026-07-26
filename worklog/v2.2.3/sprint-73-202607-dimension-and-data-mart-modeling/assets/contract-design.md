# Sprint-73 契约设计

## 1. DataMart

### 1.1 API DTO

```ts
type DataMartStatus = "DRAFT" | "CURRENT" | "RETIRED";

type CreateDataMartCommand = {
  code: string;             // ^[A-Z][A-Z0-9_]{1,63}$
  name: string;             // 1..128
  purpose: string;          // 1..2000
  ownerId: string;          // 人员目录账号
  domainIds: string[];      // UUID，去重后 1..100
  idempotencyKey: string;
};

type UpdateDataMartCommand = Omit<CreateDataMartCommand, "code" | "idempotencyKey">;

type DataMartView = {
  id: string;
  code: string;
  name: string;
  purpose: string;
  ownerId: string;
  domainIds: string[];
  status: DataMartStatus;
  revision: number;
  checksum: string;
  usageCount: number;
  createdAt: string;
  updatedAt: string;
};
```

### 1.2 REST

| Method | Path | 行为 |
|--------|------|------|
| GET | `/api/modeling/data-marts?offset=0&limit=10&keyword=&domainId=&status=` | 当前租户内按分类、状态和关键词检索；默认 10、最大 100 |
| POST | `/api/modeling/data-marts` | body 中 `idempotencyKey` 必填；同 key 不同 payload 返回 409 |
| GET | `/api/modeling/data-marts/{id}` | 返回当前 head 和 ETag |
| PUT | `/api/modeling/data-marts/{id}` | `If-Match` 必填；缺少返回 428，CAS 冲突返回 409 |
| POST | `/api/modeling/data-marts/{id}/confirm` | DRAFT→CURRENT；至少一个可见 ACTIVE 分类 |
| POST | `/api/modeling/data-marts/{id}/retire` | CURRENT→RETIRED；被活动计划/模型引用时返回 409 和引用摘要 |

### 1.3 数据表

`modeling_data_mart`

- `id uuid PK`
- `tenant_id varchar(128) NOT NULL`
- `code varchar(64) NOT NULL`
- `name varchar(256) NOT NULL`
- `purpose text NOT NULL`
- `owner_id varchar(128) NOT NULL`
- `status varchar(16) NOT NULL`
- `revision int NOT NULL`
- `current_checksum varchar(64) NOT NULL`
- `idempotency_key varchar(128) NOT NULL`
- 审计字段
- `UK (tenant_id, lower(code))`
- `UK (tenant_id, id)`
- `UK (tenant_id, idempotency_key)`

`modeling_data_mart_revision`

- `id uuid PK`
- `tenant_id, data_mart_id, revision`
- 当前 revision/status/checksum + 完整 `snapshot_json`
- `content_checksum`
- `snapshot_json`
- `UK (tenant_id, data_mart_id, revision)`

`modeling_data_mart_domain`

- `id uuid PK`，`tenant_id, data_mart_id, domain_id`
- `UK (tenant_id, data_mart_id, domain_id)`
- FK 到 DataMart head；`domain_id` 由 `CatalogDomainResolutionPort` 校验可见性和 ACTIVE 状态

`modeling_warehouse_plan_data_mart`

- `id uuid PK`
- `tenant_id, plan_id, data_mart_id`
- `UK (tenant_id, plan_id, data_mart_id)`
- 仅 CURRENT DataMart 可写入；计划侧使用独立 `data_marts_version` 做 CAS

## 2. DimensionDefinition 扩展

```ts
type DimensionDefinitionScopeType = "DOMAIN" | "DATA_MART";

type DimensionAttribute = {
  code: string;                 // ^[A-Z][A-Z0-9_]{0,63}$
  name: string;
  definition: string;
  primaryKey: boolean;
  standardRef?: string | null;
  standardVersion?: string | null;
  order: number;
};

type CreateDimensionDefinitionCommandV2 = {
  domainId: string;
  scopeType: DimensionDefinitionScopeType;
  dataMartId?: string | null;   // DATA_MART 时必填，且 mart 必须关联 domainId
  name: string;
  definition: string;
  ownerId: string;
  reuseScope: "PLAN" | "DOMAIN" | "TENANT";
  attributes: DimensionAttribute[];
  hierarchies: DimensionDefinitionHierarchy[];
  idempotencyKey: string;
};
```

数据库扩展：

- `modeling_dimension_definition.scope_type varchar(16) NOT NULL DEFAULT 'DOMAIN'`
- `modeling_dimension_definition.data_mart_id uuid NULL`
- `modeling_dimension_definition.attributes_json jsonb NOT NULL DEFAULT '[]'`
- revision 表增加同名快照字段
- CHECK：`DOMAIN → data_mart_id IS NULL`；`DATA_MART → data_mart_id IS NOT NULL`
- 现有数据统一回填 `scope_type='DOMAIN'`、`attributes_json=[]`

属性规则：

- CURRENT 维度必须至少一个 `primaryKey=true` 属性；
- 同一维度属性 code 唯一，order 唯一；
- 层级 level 引用 attribute code，不直接引用物理字段；
- 标准引用以 `standardRef + standardVersion` 快照保存；维度表字段标准仍由 ModelSpec 标准绑定契约管理。

## 3. DIMENSION ModelSpec 扩展

### 3.1 创建

```ts
type CreateDimensionModelSpecCommand = {
  planId: string;
  domainId: string;
  dataMartId?: string | null;
  modelType: "DIMENSION";
  name: string;                         // 业务显示名
  description?: string | null;
  dimensionDefinitionRef: {
    dimensionDefinitionId: string;
    revision: number;
  };
  variantCode?: string | null;          // 只有显式多实现时填写
  idempotencyKey: string;
};
```

唯一活动实现：

- 默认唯一键语义：`tenantId + planId + dimensionDefinitionId + coalesce(dataMartId, '-') + coalesce(variantCode, 'DEFAULT')`
- DRAFT/DESIGNING/VALIDATING/READY_TO_PUBLISH/PUBLISHED 均视为活动；ARCHIVED 不占位
- 冲突返回 `409 MODEL_SPEC_DIMENSION_VARIANT_CONFLICT`，响应含 `existingModelSpecId/repairRoute`

### 3.2 字段与实现

```ts
type ModelSpecFieldV3 = ModelSpecField & {
  dimensionAttributeCode?: string | null;
  redundant?: boolean;
  redundancySourceRef?: string | null;
};

type DimensionImplementationPolicy = {
  physicalName: string;
  loadStrategy: "FULL" | "INCREMENTAL" | "SNAPSHOT";
  retentionDays: number | null;         // null=由存储平台策略管理；显式值 >=0
  partitionFields: string[];
};
```

`scdPolicy` 继续由 `dimensionProfile` 拥有；它描述维度历史处理，不与物理数据保留期限混在一个对象中。

规则：

- 草稿保存不要求 `sourceRefs/dependsOn/generationStrategy`；
- 进入 IMPLEMENTATION_READY 前三者至少一个有效；
- 每个 CURRENT 维度属性必须映射到一个物理字段，允许额外技术字段；
- `partitionFields` 必须引用现有字段；
- TYPE2 必须具备 effectiveFrom/effectiveTo/currentFlag 字段且非空；
- `physicalName` 只经计划命名策略校验，不与中文 `name` 混用。

## 4. 计划业务范围

```ts
type WarehousePlanDataMartBaseline = {
  planId: string;
  dataMartIds: string[];
  version: number;
};
```

- CONFIRMED 集市的全部 `domainIds` 必须是计划中可见的业务分类；
- 若尚未确认集市，DOMAIN 维度建模不阻断；
- DATA_MART 范围维度与 APPLICATION 模型必须选择当前计划已 CONFIRMED 的集市。

## 5. 错误码

| 状态码 | code | 用户修复动作 |
|--------|------|--------------|
| 400 | `DATA_MART_DOMAIN_REQUIRED` | 至少关联一个业务分类 |
| 403 | `DATA_MART_SCOPE_FORBIDDEN` | 返回可访问分类或联系管理员 |
| 409 | `DATA_MART_IN_USE` | 查看引用后取消归档 |
| 409 | `MODEL_SPEC_DIMENSION_VARIANT_CONFLICT` | 打开已有维度表或填写明确 variantCode |
| 409 | `DATA_MART_REVISION_CONFLICT` | 加载最新 revision，保留当前输入 |
| 422 | `DIMENSION_ATTRIBUTE_MAPPING_INCOMPLETE` | 返回字段设计 Tab |
| 422 | `MODEL_SPEC_DIMENSION_INPUT_REQUIRED` | 返回逻辑设计或数据实现选择来源/生成策略 |
| 422 | `MODEL_SPEC_PHYSICAL_NAME_INVALID` | 展示命名规则 |
| 422 | `MODEL_SPEC_DIMENSION_SCD_TYPE2_FIELDS_REQUIRED` | 返回历史处理区并定位缺失字段 |
