# 数仓分层全局治理与模型选择设计

**日期**：2026-08-04

**状态**：DESIGN_APPROVED / IMPLEMENTATION_PENDING

**归属**：Sprint-84 F1（规划与建模概览真实化）

**影响模块**：`dts-platform`、`dts-platform-webapp`

## 1. 背景与问题

数据建模原型中的“数仓分层”页面提供新建入口，并以“系统”标识区分平台内置分层。当前生产实现只读取 Sprint-64 固定分层字典，随后把整个页面收敛为只读，导致原型要求的自定义分层新增、删除及模型选择能力丢失。

当前 DTS 暂未完成多租户能力，因此本设计采用**全局共享**作用域。系统分层继续承载稳定的建模与物化语义；用户新增的分层必须归属一个系统分层类型，不得发明新的执行层语义。

## 2. 目标与非目标

### 2.1 目标

1. 系统内置分层保持稳定、可查询、不可修改和不可删除。
2. 用户可以新增、查询和删除全局共享的自定义分层。
3. 自定义分层必须归属一个系统分层类型，并能在兼容的模型创建流程中选择。
4. ModelSpec 同时保存业务选择的自定义分层编码和既有 canonical 执行层，物化校验继续使用 canonical 执行层。
5. 删除存在活动模型引用的自定义分层时必须失败关闭，并返回可理解的冲突信息。
6. 新增、删除和删除拦截均由服务端产生公共审计证据。

### 2.2 非目标

- 不实现租户、建模空间或建设计划级隔离。
- 不允许新增第七种系统执行层或改变 Sprint-83 的 dbt 物化规则。
- 不提供系统分层编辑、删除或覆盖能力。
- 不实现自定义分层排序、批量导入、批量删除或层级嵌套。
- 不用浏览器缓存、前端数组或规划参数 JSON 充当权威台账。

## 3. 方案比较与决策

| 方案 | 说明 | 优点 | 风险 | 结论 |
|---|---|---|---|---|
| 自定义登记表叠加系统字典 | 系统分层保留在稳定代码契约中，仅持久化自定义分层；查询时合并 | 内置语义不可被配置破坏，迁移面小，删除规则清晰 | 查询需组合两个来源 | **采用** |
| 系统与自定义分层统一入库 | 通过 Liquibase 写入系统分层，所有分层使用同一表 | 查询和引用外键直接 | 系统语义可能被误改；升级、种子和运行时契约耦合 | 不采用 |
| 全局 JSON 参数 | 在参数配置中保存自定义分层数组 | 初始代码量小 | 缺少行级并发、引用约束、稳定审计和删除语义 | 不采用 |

决策：新增自定义分层 canonical owner；系统分层仍由 `Sprint64GovernanceContract` 提供。页面查询返回两类记录的统一投影，并通过 `builtin`、`deletable` 和 `disabledReason` 明确能力差异。

## 4. 领域模型与不变量

### 4.1 系统分层类型

沿用现有六个系统分层编码：

| 系统编码 | 类型 | 模型选择规则 |
|---|---|---|
| `ODS_RAW` | 原始接入层 | 当前 ModelSpec 不可选择，仅用于规划展示 |
| `ODS_STANDARDIZED` | 标准化接入层 | 当前 ModelSpec 不可选择，仅用于规划展示 |
| `STG` | 技术过渡层 | 当前 ModelSpec 不可选择，仅用于规划展示 |
| `DWD` | 明细事实/维度层 | 维度表、事实表可选择 |
| `DWS` | 汇总服务层 | 汇总表可选择 |
| `ADS` | 应用服务层 | 应用表可选择 |

### 4.2 自定义分层

新增全局表 `modeling_warehouse_layer`，只保存自定义分层：

| 字段 | 约束 | 说明 |
|---|---|---|
| `id` | UUID 主键 | 服务端生成 |
| `code` | `varchar(64)`，全局唯一 | 大写 ASCII，格式 `[A-Z][A-Z0-9_]{1,63}`；删除后不可复用 |
| `name` | `varchar(128)`，非空 | 中文或业务显示名称 |
| `system_layer_code` | `varchar(32)`，非空 | 必须属于六个系统分层编码 |
| `description` | `varchar(1000)`，可空 | 业务说明或加工责任 |
| `naming_prefix` | `varchar(64)`，可空 | 小写 ASCII 前缀；为空时不强制命名规则 |
| `status` | `ACTIVE/DELETED` | 删除采用逻辑删除，保留历史解析能力 |
| `version` | 整数 | 乐观锁与并发删除保护 |
| `created_by/created_date` | 非空 | 创建审计字段 |
| `last_modified_by/last_modified_date` | 非空 | 最近修改审计字段 |

全局不变量：

1. 自定义编码不得与任一系统编码或其他历史自定义编码重复。
2. 系统分层不落入该表，也不接受写接口。
3. `DELETED` 分层不再出现在新建模型选项中，但历史模型仍可解析其名称和所属系统类型。
4. 自定义分层的 `system_layer_code` 创建后不可修改；需要变更时应创建新分层并迁移模型。

### 4.3 ModelSpec 引用

在 `modeling_model_spec` 当前头增加 `warehouse_layer_code`，并在 ModelSpec v2 snapshot 中增加同名字段：

- `layer`：继续保存现有 canonical 执行层，值仍为 `DWD/DWS/ADS`。
- `warehouseLayerCode`：保存用户选择的系统或自定义分层编码。
- 存量模型将 `warehouse_layer_code` 回填为当前 `layer`。
- 创建和更新模型时，服务端解析 `warehouseLayerCode`；若指向自定义分层，其 `system_layer_code` 必须等于模型类型推导出的 canonical `layer`。
- API 未传 `warehouseLayerCode` 时，为兼容旧调用方，服务端使用推导出的 canonical `layer`。

这样可以保留业务选择，又不改变 `ModelType → Layer → dbt` 的执行不变量。

## 5. API 契约

canonical 资源路径使用 `/api/modeling/warehouse-layers`，不继续把新增写能力放入带 Sprint 编号的资源：

| 方法 | 路径 | 行为 |
|---|---|---|
| `GET` | `/api/modeling/warehouse-layers` | 返回系统分层与活动自定义分层的合并列表 |
| `POST` | `/api/modeling/warehouse-layers` | 创建全局自定义分层 |
| `DELETE` | `/api/modeling/warehouse-layers/{code}` | 逻辑删除无活动模型引用的自定义分层 |

现有 `GET /api/governance/sprint64/warehouse-layers` 暂时保留兼容，只返回系统字典；生产数据建模页面迁移到 canonical 新资源。

### 5.1 创建请求

```json
{
  "code": "FIN_DETAIL",
  "name": "财务明细层",
  "systemLayerCode": "DWD",
  "description": "承载财务域可复用明细模型",
  "namingPrefix": "fin_dwd_"
}
```

成功返回 `201` 和创建后的统一分层投影。重复编码返回 `409 WAREHOUSE_LAYER_CODE_CONFLICT`；非法编码或系统类型返回稳定的 `400` 字段错误；权限不足返回 `403`。

### 5.2 删除规则

1. 系统分层删除返回 `409 WAREHOUSE_LAYER_BUILTIN_PROTECTED`。
2. 不存在或已删除的自定义分层返回 `404 WAREHOUSE_LAYER_NOT_FOUND`。
3. 存在非 `ARCHIVED` ModelSpec 引用时返回 `409 WAREHOUSE_LAYER_IN_USE`，响应包含引用数量，不返回未经授权的模型详情。
4. 删除成功返回 `204`，并写入逻辑删除状态与严格审计。

## 6. 页面与交互

数仓分层页面恢复原型的维护结构：

1. 顶部保留刷新，并增加“新建数仓分层”表单或弹窗。
2. 表单字段为分层编码、分层名称、所属系统类型、说明、命名前缀。
3. 列表继续展示分层编码、名称、系统类型、加工责任/说明、命名前缀和要求，同时增加“来源/操作”语义。
4. 系统行显示“系统”标识，删除按钮不渲染，并展示“平台内置分层不可删除”的原因。
5. 自定义行显示“自定义”标识和删除按钮；删除前二次确认，处理中禁用重复提交。
6. 创建、删除成功后重新读取服务端权威列表；失败保留表单或当前列表，并展示稳定错误原因。
7. 页面覆盖 loading、empty、permission、validation、conflict、retry 和 success 状态，不回退演示数据。

模型创建/编辑页面按模型类型筛选可选项，并显示“自定义名称（编码）/ 所属系统类型”。不可用于当前 ModelSpec 的 ODS/STG 自定义分层只在规划页显示，不出现在模型选择框中。

## 7. 数据流

### 7.1 新增分层

```text
用户提交表单
  → Web Resource 校验权限与请求体
  → WarehouseLayerService 校验系统类型和全局编码唯一性
  → Repository 插入 ACTIVE 自定义分层
  → 公共审计 outbox 严格写入
  → 返回统一投影
  → 页面刷新权威列表
```

### 7.2 模型选择

```text
页面读取分层列表并按 ModelType 筛选
  → 用户选择 warehouseLayerCode
  → ModelSpec 写服务解析分层
  → 校验 custom.systemLayerCode == ModelSpecContract.targetLayer(modelType)
  → 当前头与 revision snapshot 同时保存 warehouseLayerCode
  → 后续 StageGate/dbt 仍消费 canonical layer
```

### 7.3 删除分层

```text
用户确认删除自定义分层
  → 服务端锁定/校验 ACTIVE 记录
  → 查询活动 ModelSpec 引用
  → 有引用：409 + 严格拒绝审计
  → 无引用：状态改为 DELETED + 成功审计
  → 页面刷新权威列表
```

## 8. 权限、审计与错误处理

- 读取沿用数据建模查看权限；新增和删除沿用数据建模维护权限，不新增临时角色。
- 审计动作登记为 `MODELING_WAREHOUSE_LAYER_CREATE` 和 `MODELING_WAREHOUSE_LAYER_DELETE`。
- 写操作使用可写事务；审计失败时业务写入回滚，不产生“已成功但无审计”的分层。
- 日志和错误响应不得包含访问令牌、用户凭据或完整模型快照。
- 删除冲突只返回计数和稳定错误码，避免通过分层管理越权枚举模型。

## 9. 迁移、兼容与回滚

采用 expand/rollback-safe 方式：

1. 新增自定义分层表和 `modeling_model_spec.warehouse_layer_code` 可空列。
2. 将存量 ModelSpec 的新列回填为当前 canonical `layer`，再添加非空约束。
3. 后端先兼容未传新字段的旧调用方；前端随后切换到新列表和写接口。
4. 不删除旧 Sprint-64 GET，避免其他只读消费者立即失效。
5. 回滚应用时保留新增表和列，不执行破坏性 schema 回滚；旧版本忽略新增字段。

## 10. 测试与验收

实施必须按 RED → GREEN 顺序覆盖：

### 10.1 后端

- 系统与自定义分层合并、稳定排序和来源标识。
- 创建成功、重复编码、与系统编码冲突、非法系统类型和权限拒绝。
- 系统分层不可删、自定义分层无引用可删、有活动引用返回 409、历史删除编码不可复用。
- ModelSpec 创建/更新保存 `warehouseLayerCode`；系统类型不匹配时拒绝；旧请求兼容回退 canonical layer。
- 严格审计成功/失败事务语义和数据库迁移校验。

### 10.2 前端

- 原型要求的新建表单和自定义删除操作可见。
- 系统行不可删除，自定义行可删除并有确认、busy 和错误恢复。
- 模型类型只展示兼容的系统/自定义分层。
- 页面不再显示“整个系统分层字典只读”的错误结论。
- Chrome 95 构建兼容检查通过。

### 10.3 交付分层

- 代码/契约：聚焦测试、模块测试和构建通过。
- 数据库：Liquibase 在目标 PostgreSQL 成功应用，存量 ModelSpec 回填数量可核对。
- 部署：仅重建和替换 `dts-platform`、`dts-platform-webapp`，保留回滚锚点。
- 真实验收：从真实菜单进入数仓分层，创建自定义 DWD 分层，在维度表或事实表中选择，验证保存后回显；活动引用时删除被拦截，解除引用后删除成功，并核对公共审计。

## 11. Sprint-84 文档同步范围

实现时同步更新：

1. Sprint-84 主 README 的端到端契约、F1 状态、Gate 与完成标准。
2. F1/T01、F1/T02、F1/T03 的现行文件和 owner 描述，删除旧 `PlanningWorkspace`/WarehousePlan 错误引用。
3. 页面能力矩阵、按钮组件矩阵和原型符合性评审。
4. IT-84-01 增加“创建 → 模型选择 → 引用删除拦截 → 解除引用后删除 → 审计”的真实旅程。
5. `sprint-queue.md` 中 Sprint-83/84 的过期状态；不得把本切片代码完成写成真实 E2E 完成。

## 12. 验收结论边界

本设计完成后，只有以下证据同时成立才可把数仓分层能力标记为 DONE：

- canonical owner 与数据库迁移完成；
- 页面新增、选择和删除闭环通过；
- 系统分层保护、引用冲突和公共审计通过；
- 当前代码重新构建、部署并完成真实认证 UI/API 验收。

任何单独的页面截图、组件测试、API 存在或旧镜像健康均不能替代上述闭环。
