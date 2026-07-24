# 业务维度、逻辑模型、实现与物理资产四层最小闭环设计

**日期**：2026-07-24
**状态**：待书面评审
**适用范围**：Sprint-67 F3-T02/T08/T09/T10、F6-T08
**替代边界**：替代此前“维度目录直接以 `DIMENSION ModelSpec` 同时承载概念维度、逻辑维度表和实现输入”的设计；旧实现和证据保留用于迁移与回归，不再作为目标架构。

## 1. 设计结论

采用增量式方案 A：

1. `DimensionDefinition` 是可独立登记、复用和退役的业务分析维度，不要求来源表。
2. `ModelSpecRevision` 只描述维度表、明细表、汇总表和应用表的逻辑结构。
3. `ModelImplementation` 只描述如何从既有物理资产、上游模型或受控生成器产生目标模型。
4. `MetadataObjectRevision` / `PhysicalAssetRevision` 只描述真实物理对象和版本，不拥有业务维度或逻辑模型语义。
5. 数据连接测试只证明通道可用；元数据同步、规划确认、实现绑定是三个独立动作。
6. API 不直接进入模型编译器。最小路径复用现有 API 采集能力，先生成并登记 ODS Landing 物理资产，再按统一物理资产输入参与建模。

本设计解决的是以下根因：

- “维度”既被当作分析视角，又被当作 DWD 表；
- “来源表”既被当作已有上游，又被误认为当前模型未来的目标表；
- 连接测试、元数据同步和加入规划被错误合并；
- 逻辑设计、SQL/dbt 实现和物理资产生命周期相互污染；
- 当前大抽屉在首次创建时同时要求概念、逻辑、实现和物理信息。

## 2. 明确不做

本批次不扩展为通用元数据平台，也不重写已有数据接入框架：

- 不实现 OpenAPI/Swagger 自动导入；
- 不新增 GraphQL、SOAP/XML；
- 不处理复杂 JSON 多实体自动拆表；
- 不实现 API 虚拟表或模型运行时直接调用 API；
- 不建立新的业务分类、标准、指标或 WarehousePlan；
- 不物理删除现有 ModelSpec、实现、资产或历史 ODS/STG 记录；
- 不进行全站视觉改版，只重构建模关键操作路径。

## 3. Canonical 对象与唯一所有者

| 层 | Canonical 对象 | 唯一负责 | 明确不负责 |
|---|---|---|---|
| 业务概念 | `DimensionDefinition` | 名称、定义、业务分类、负责人、复用范围、分析层级语义、系统编码 | 字段映射、来源表、目标层、SCD 实现、物理表 |
| 逻辑设计 | `ModelSpecRevision` | 模型类型、粒度、逻辑字段与角色、业务键、SCD 策略、维度引用、锁定 revision 的模型依赖 | 连接凭据、自由文本表名、SQL/dbt 产物、物理运行状态 |
| 数据实现 | `ModelImplementation` | 输入方式、输入引用、字段映射、增量/SCD 实现、目标定位、编译和测试产物 | 业务维度正文、元数据正文 |
| 物理与元数据 | `MetadataObjectRevision`、`PhysicalAssetRevision` | 连接、catalog/schema/table、字段快照、可用性、版本、运行血缘 | 模型粒度、业务定义、模型生命周期 |
| 规划确认 | `WarehousePlanSourceBinding` | 当前计划允许使用的已确认物理来源及版本 | 连接测试结果、模型目标表 |

所有对象使用不可变 ID 建立关系，revision 显式锁定。系统编码只用于客户识别和查询，由服务端生成且不可作为外键。名称修改不得改变对象 ID 或既有编码。

## 4. 最小关系模型

```text
DataConnection
  └─ metadata sync ─> MetadataObjectRevision
                         └─ confirm in plan ─> WarehousePlanSourceBinding

DimensionDefinition
  └─ instantiate ─> ModelSpecRevision
                       └─ realize ─> ModelImplementation
                                        ├─ PHYSICAL_ASSET
                                        ├─ UPSTREAM_MODEL
                                        └─ GENERATED
                                             └─ deploy ─> PhysicalAssetRevision
                                                              └─ lineage callback
```

关系类型必须显式区分：

- `DIMENSION_DEFINITION_REF`：维度表逻辑模型引用业务维度；
- `DIMENSION_MODEL_REF`：明细/汇总模型引用一致性维度模型；
- `MODEL_DEPENDENCY`：汇总、应用或派生模型引用上游模型 revision；
- `IMPLEMENTATION_INPUT`：实现引用规划物理资产、上游模型或生成器；
- `PHYSICAL_REALIZATION`：实现产物对应目标物理资产；
- `LINEAGE_EDGE`：运行后形成的来源到目标血缘。

物理外键不是逻辑维度引用的前置条件，数仓目标表也不得成为自身实现输入。

## 5. 三种最小实现输入

`ModelImplementation` 首期只接受以下互斥的主实现方式；一种方式可以包含多个同类输入：

| 输入方式 | 引用对象 | 适用场景 |
|---|---|---|
| `PHYSICAL_ASSET` | 当前计划已确认且 CURRENT 的 `MetadataObjectRevision` / `PhysicalAssetRevision` | 数据库同步表、文件 Landing 表、API Landing 表、存量受管表 |
| `UPSTREAM_MODEL` | 锁定 revision 的 `ModelSpecRevision` | 模型派生、汇总表、应用表 |
| `GENERATED` | 受控生成器定义 | 日期维度、受控 Seed |

不允许自由文本实现方式。现有 `sourceRefs`、`dependsOn`、`generationStrategy` 在兼容期继续可读，写入时由适配层转换为上述实现输入；完成迁移和零旧写证明后再决定是否删除旧字段。

## 6. 模型类型与门禁

| 对象/类型 | DRAFT | DESIGNED | IMPLEMENTATION_READY |
|---|---|---|---|
| `DimensionDefinition` | 名称、定义、业务分类 | 负责人、复用范围、层级语义确认 | 不适用 |
| `DIMENSION` | 可无来源保存 | 必须引用 `DimensionDefinition`，完成粒度、业务键、属性和 SCD 逻辑策略 | `PHYSICAL_ASSET`、`UPSTREAM_MODEL` 或 `GENERATED` 之一有效 |
| `FACT` | 可无来源保存 | 业务事件、粒度、字段、时间语义和维度引用闭合 | `PHYSICAL_ASSET` 或 `UPSTREAM_MODEL` 有效 |
| `SUMMARY` | 可保存最小草稿 | 聚合粒度、指标/字段和上游语义完整 | 只允许合法且无环的 `UPSTREAM_MODEL` |
| `APPLICATION` | 可保存最小草稿 | 消费场景和输出契约完整 | 只允许合法且无环的 `UPSTREAM_MODEL` |

四类目标层继续固定：

- `DIMENSION`、`FACT` → DWD；
- `SUMMARY` → DWS；
- `APPLICATION` → ADS；
- ODS_RAW、ODS_STANDARDIZED、STG 继续归数据接入和技术实现，不成为第五类 ModelSpec。

## 7. API-only 来源的最小落地

首期不在建模模块新增 `APIResourceRevision`。API 接入按下列路径产生统一物理资产：

```text
API Connection
  → ApiIngestionTaskRevision
  → sample/live run
  → ODS Landing table
  → register MetadataObjectRevision(origin=API, ingestionTaskRevision=...)
  → confirm WarehousePlanSourceBinding
  → ModelImplementation(inputMode=PHYSICAL_ASSET)
```

规则：

1. API 连接测试成功不自动加入计划。
2. API 采集任务至少完成一次受控试跑，Landing 表和字段真实存在后才能登记为可建模物理资产。
3. Landing 元数据保留 API 采集任务 revision、checkpoint 和来源类型，用于反查和运行血缘。
4. API 响应结构漂移时不得静默改写已锁定元数据 revision；实现进入 STALE 并引导重新试跑、登记新 revision 和更新映射。
5. 建模模块不感知认证、分页和 HTTP 细节，只消费 Landing 资产版本。

## 8. 前端信息架构

### 8.1 维度目录

`/modeling/dimensions` 只管理 `DimensionDefinition`：

- 列表：名称、系统编码、业务分类、状态、被多少逻辑模型使用、更新时间；
- 创建：名称、定义、业务分类、负责人和可选层级语义；
- 主动作：`登记业务维度`；
- 行动作：`创建维度表`、`查看引用`、`编辑`、`退役`；
- 不显示来源、目标层、SCD 物理字段、物化或 SQL/dbt。

### 8.2 轻量新建模型

新建抽屉只收集：

- 模型类型；
- 模型名称和用途；
- 业务分类；
- 创建 DIMENSION 时选择 `DimensionDefinition`。

保存后创建 DRAFT 并进入模型详情。抽屉不再承担字段、来源、实现和发布配置。

### 8.3 三阶段模型详情

详情页按状态提供三个阶段，不在同一长表单混排：

1. `逻辑设计`：粒度、字段、业务键、维度引用、SCD 逻辑策略；
2. `数据实现`：选择物理资产、上游模型或生成器，维护字段映射和实现参数；
3. `物理资产`：目标库表、编译/测试、部署状态、资产与血缘。

唯一主动作依次为：

```text
保存逻辑设计 → 配置数据实现 → 验证实现 → 生成并发布
```

### 8.4 来源修复和安全返回

- 数据库连接尚未同步具体表：跳转元数据同步；
- API 尚未生成 Landing 表：跳转 API 采集任务并执行试跑；
- 跳转携带受控 `returnTo`、`planId`、`modelSpecId`，完成后返回原模型的数据实现阶段；
- 来源版本漂移：保留逻辑设计，数据实现显示 STALE 和重新选择入口；
- 加载失败不得伪装为空列表，草稿未绑定来源时不得阻止逻辑保存。

## 9. Expand / Migrate / Contract 迁移

采用非破坏式迁移：

1. 新增 `DimensionDefinition` 和 ModelSpec 稳定引用，旧 DIMENSION revision 保持可读。
2. 按现有 DIMENSION 的名称、定义、业务分类和系统编码生成或复用 `DimensionDefinition`；迁移表保存 old modelSpecId → dimensionDefinitionId 映射。
3. 粒度、字段、SCD 逻辑策略保留在 ModelSpec；来源、生成策略、目标实现配置投影到 `ModelImplementation`。
4. 旧 API/旧深链通过适配器返回新读模型，禁止产生新的混合写入。
5. dry-run 输出总数、可自动迁移数、冲突数、孤儿数和原因；重复执行保持幂等。
6. 回滚只关闭新写和恢复旧读适配，不删除已生成的新对象或物理资产。

迁移验收要求：

- 迁移前后 ModelSpec、revision、字段和已发布产物计数一致；
- 零孤儿关系、零跨租户/跨部门扩大权限；
- 旧链接可定位到新维度定义或原模型；
- 删除或退役业务维度不级联删除模型、实现或物理资产。

## 10. 稳定错误码

- `DIMENSION_DEFINITION_REQUIRED`
- `DIMENSION_DEFINITION_NOT_CURRENT`
- `MODEL_IMPLEMENTATION_INPUT_REQUIRED`
- `MODEL_IMPLEMENTATION_INPUT_KIND_NOT_ALLOWED`
- `MODEL_IMPLEMENTATION_INPUT_STALE`
- `MODEL_IMPLEMENTATION_SELF_REFERENCE`
- `API_LANDING_NOT_READY`
- `PHYSICAL_ASSET_NOT_CONFIRMED`

页面展示业务说明和修复动作，但不得自行推导另一套门禁。

## 11. 验证门禁

### G1 契约测试

- 无连接登记业务维度；
- 无来源保存 DIMENSION/FACT 逻辑草稿；
- 实现阶段三种输入方式的允许/禁止矩阵；
- SUMMARY/APPLICATION 禁止直接物理来源；
- 过期 revision、循环、自引用、跨计划和跨部门 fail closed。

### G2 迁移测试

- 空库升级、存量 DIMENSION dry-run、幂等重跑和回滚；
- 迁移前后计数与 checksum 对账；
- 旧深链、旧 API 和历史 revision 保持可读。

### G3 前端契约

- 维度目录不出现来源和实现字段；
- 新建抽屉只提交最小 DRAFT；
- 三阶段页面字段和主动作互斥；
- API 未落地、来源漂移、网络失败和安全返回可恢复；
- 系统编码和稳定引用不可手工编辑。

### G4 真实联动 Journey

1. 无来源登记业务维度，创建逻辑维度表并用 `GENERATED` 物化日期维度；
2. 数据库表完成连接、元数据同步、规划确认、模型实现和目标资产登记；
3. API 完成试跑、Landing 表登记、规划确认、模型实现、目标资产和全链路血缘；
4. FACT → SUMMARY → APPLICATION 使用锁定模型 revision，漂移后正确阻塞并可修复。

### G5 一次性最终验证

完成整个功能批次后集中执行：

- 一次后端完整契约/集成测试；
- 一次前端 production build；
- 一次真实 Chrome 95 + Spring Security + API + PostgreSQL E2E；
- 一次 GitNexus `detect_changes` 范围审计。

Mock API 只能证明布局和交互，不得作为真实 E2E 或 Sprint DONE 证据。

## 12. 实施顺序

```text
T08 四层契约、DimensionDefinition、兼容迁移
  → T09 三种实现输入、API Landing 资产接入、物化回写
    → T10 轻量新建与三阶段模型详情
      → F6-T08 真实迁移、物化和 Chrome95 端到端验收
```

任何阶段发现对象所有权重新混入 ModelSpec，应停止后续实现并回到本设计修正，不用页面文案掩盖契约冲突。
