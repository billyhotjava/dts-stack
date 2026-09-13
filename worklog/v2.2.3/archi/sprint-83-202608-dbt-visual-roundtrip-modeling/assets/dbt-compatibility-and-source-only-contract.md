# dbt 兼容分级与 source-only ZIP 契约

**状态**：FROZEN_POLICY / CERTIFICATION_GAP
**确认日期**：2026-08-02
**对应决策**：D09、D10、D11、D13
**运行时前置项**：[`H83-01`](dbt-runtime-hotfix-prerequisite.md) 已产生候选证据，F0/T05 已登记 certified derivative；生产激活与真实物化待 F6。

## 1. 目的与边界

本契约把“能读取 dbt 包”“能导入治理模型”“能在 DTS 运行时物化”拆成三个独立能力，避免用一个“支持 dbt”结论掩盖版本、结构或运行时缺口。

- 这是现有 archive inspector、`ModelPackage` projection、ModelSpec import 和 `DbtExecutionGateway` 的统一能力投影，不新增 parser、模型台账、导入状态机或运行控制面。
- `STRUCTURE_VIEW_ONLY` 只是一种派生的界面能力，不得加入现有 `ConversionMode`、Implementation ownership 或生命周期状态。
- 具体 dbt Core、manifest schema、adapter 和数据源组合只有经过 golden fixture 与真实运行验证后才能标记为已认证。

## 2. 三轴兼容契约

```text
DbtCompatibilityView {
  inspection: SUPPORTED | UNSUPPORTED | UNKNOWN,
  importProjection: IMPORTABLE | STRUCTURE_VIEW_ONLY | BLOCKED,
  materialization: CERTIFIED | NOT_CERTIFIED | UNSUPPORTED,
  dbtCoreVersion?, manifestSchemaVersion?, adapterType?,
  adapterPackageVersion?, certificationProfileId?,
  issues[]
}
```

| 轴 | 判定含义 | 不能推出 |
|---|---|---|
| `inspection` | ZIP 可安全解压，项目或 artifact 可由规范化 seam 读取 | 不代表结构完整、可创建模型或可执行 |
| `importProjection` | 包内事实足以形成可信投影；`IMPORTABLE` 还要求依赖闭合并允许补齐业务语义 | 不代表当前 DTS runtime 可以物化 |
| `materialization` | 精确 Core + adapter package + adapter type + 数据源 + DTS image digest 已通过真实执行矩阵 | 不扩大其他版本、adapter 或数据源的兼容承诺 |

`issues[]` 复用 `assets/import-partial-result-contract.md` 的稳定字段：`code/stage/category/message/retryable/recoveryAction/correlationId`。兼容投影不单独落一套状态表；持久化时只能附着于既有 inspect/preview/run item 或 immutable evidence。

### 2.1 inspect → preview 可信交接（D13）

安全 inspect 成功时除规范化 `ModelPackage` 与 compatibility 外，必须返回 `inspectionProof` 和 `proofExpiresAt`。`inspectionProof` 是短时效、不可由客户端重算的签名凭据；签名载荷至少绑定：

- `proofVersion`、tenant identity、canonical actor identity；
- 规范化技术包的 canonicalization version 与 checksum；
- `issuedAt` 与 `expiresAt`，首期固定有效期为 30 分钟。

签名与验证必须复用平台托管的密钥/签名能力；禁止在 dbt 导入模块新增硬编码 secret、独立配置 secret 或第二套密钥台账。`inspectionProof` 只证明“该 tenant/actor 在有效期内 inspect 过这份不可变技术包”，不代表映射、选择、权限或 compatibility 一定可 apply。

preview 必须先验证 tenant、actor、expiry 和规范化技术包 checksum，再处理用户输入。客户端只允许在以下白名单区域补充信息：

- `context`：既有 WarehousePlan、domain、source 等租户内引用；
- `selection`：只能引用已 inspect 技术包内的节点，并继续满足依赖闭包；
- `semanticOverrides`：模型类型/分层、业务名称/定义、grain、字段 role/key、标准绑定和消费场景；字段引用必须指向已验证的技术字段。

SQL/Jinja、技术字段名和类型、依赖、dbt uniqueId/resourceType、project/config/materialization、artifact 内容与 checksum 均不在白名单，不能被 preview 请求覆盖。任何非白名单技术变更，即使客户端重算公开 checksum，也必须 fail-closed：

| 条件 | HTTP | 稳定 code | 恢复动作 |
|---|---:|---|---|
| 签名、tenant、actor、checksum、proofVersion 不匹配，或 payload 被篡改 | 409 | `DBT_IMPORT_INSPECTION_PROOF_INVALID` | `REUPLOAD` 后重新 inspect |
| 当前时间已超过 `expiresAt` | 410 | `DBT_IMPORT_INSPECTION_PROOF_EXPIRED` | `REUPLOAD` 后重新 inspect |

判定顺序固定为先验证签名及其绑定字段，再读取签名覆盖的 expiry；只有签名有效且主体/技术包匹配的 proof 才能返回 EXPIRED，其余统一为 INVALID，避免客户端用未认证 payload 选择错误分支。

inspect 不新增数据库表或持久化会话；原 ZIP 继续只存在于受控临时区。preview 验证成功后，才把规范化技术包、映射、选择、语义补充与 checksum 固定到既有 import run/item。proof、ZIP、SQL/Jinja、secret 与签名密钥正文不得进入普通日志或公共审计。

## 3. 当前运行时事实与认证状态

| 事实 | 当前观测 | 结论 |
|---|---|---|
| 构建声明 | `builds/dts-dbt/Dockerfile` 固定 `dbt-postgres==1.10.0` | 只证明 adapter 直接依赖声明 |
| 镜像实际 Core | `dts-dbt:1.10.0` 返回 `dbt-core 2.0.0-alpha.5` | 镜像标签不能作为 Core 版本证明 |
| 镜像传递依赖 | `dbt-adapters 1.24.5`、`dbt-common 1.38.0` | 当前依赖未形成可复现的精确认证集合 |
| 首期 materialization adapter 候选 | PostgreSQL | `NOT_CERTIFIED`；MySQL、达梦及其他 adapter 为 `UNSUPPORTED`，不得出现在可选列表 |

运行时认证使用独立的 **RT-01 PostgreSQL runtime profile**，不再由 FX-01～05 导入/安全 fixture 代替。首个 `CERTIFIED` profile 必须同时具备：

1. 精确锁定 dbt Core、adapter 及传递依赖，镜像构建可复现；
2. 同一候选 image digest 内 `python -m pip check` 与 `dbt --version` 通过，依赖清单和 digest 已归档；
3. 对声明的 PostgreSQL 版本完成 parse/compile/build/run、manifest/catalog/run_results/relation evidence 验证；
4. 未认证 digest、版本/adapter 漂移和未声明 PostgreSQL 版本稳定 fail-closed；
5. 发布、回滚与审计证据满足 [`H83-01`](dbt-runtime-hotfix-prerequisite.md)。

### 3.1 RT-01 的四类独立证据动作

| 阶段 | 目的与通过条件 | 能够声明 | 不能推出 |
|---|---|---|---|
| 候选工程证据 | H83-01 对一个精确 Core + adapter + transitive lock + PostgreSQL 版本 + image digest 完成可复现构建、`pip check`、`dbt --version`、parse/compile/build/run、artifact/relation 和回滚验证 | 形成不可变候选和原始 RT-01 证据，状态为 `READY_FOR_CERTIFICATION` | 不能把产品 profile 从 `NOT_CERTIFIED` 变为 `CERTIFIED`，也不代表客户包兼容 |
| 产品认证登记 | F0/T05 独立审查 H83-01 证据、重新核对 fail-closed/回滚边界并登记 `certificationProfileId` | 只有该精确 runtime profile 可从 `NOT_CERTIFIED` 变为 `CERTIFIED` | 不扩大其他 digest、adapter、数据库版本或客户项目的范围 |
| 客户兼容声明 | 获得授权且脱敏的客户包/版本画像后，对声明范围单独执行兼容矩阵并记录来源、版本、限制和证据 | 只对证据覆盖的客户组合发布兼容说明 | 不能把工程 golden project 或相邻版本外推为客户兼容 |
| 编码后回归 | Sprint-83 运行控制面、Gateway、artifact 回写等编码完成后，以已认证的同一 RT-01 profile 重跑真实 PostgreSQL 主路径、失败门禁和回滚路径 | 证明本次代码变更未破坏已认证 profile | 不能替代首次工程准入，也不能扩大客户兼容范围 |

RT-01 的候选修复与原始证据由独立 H83-01 产生，可先于 Sprint-83 功能编码完成；F0/T05 是唯一认证状态 owner；编码后回归是对应发布切片的 DONE 门禁。三类动作必须分别留存证据，不能互相冒充。

认证完成前允许 `inspection=SUPPORTED` 或 `importProjection=IMPORTABLE`，但只要 `materialization!=CERTIFIED`，发布/物化门禁就必须返回 `DBT_RUNTIME_NOT_CERTIFIED`，不得降级执行。

## 4. source-only ZIP 静态能力

source-only 指包含 `dbt_project.yml`、源 SQL 和可选 schema/source YAML，但不包含可信 `target/manifest.json` 的 ZIP。

本节政策保持冻结，不属于 H83-01 热修复范围。source-only parser/projection 的实现可以作为后续 Sprint-83 切片交付；在实现与对应 FX 验证完成前，只能保持现有能力或返回明确 `STRUCTURE_VIEW_ONLY/BLOCKED`，不得通过猜测字段、执行不受信 SQL 或人工重建技术结构提前放开 apply。

### 4.1 资源处理矩阵

| dbt 资源 | 冻结处理政策 | 是否独立创建 ModelSpec |
|---|---|---|
| 普通 `model` | 只有可信完整字段契约、完整 literal 依赖闭包和已确认业务语义同时成立时才为 `IMPORTABLE` | 是，apply 后为 `ModelSpec DRAFT + DBT_MANAGED Implementation Revision` |
| STG 标记 model | `TECHNICAL_ONLY`，作为必要技术依赖保存 | 否 |
| `ephemeral` model | `TECHNICAL_ONLY`，不得冒充物理资产 | 否 |
| macro | `TECHNICAL_ONLY`；若可能改变结构或依赖，则阻断调用模型及其下游 | 否 |
| literal `source()` | 作为 `SourceNode`，必须映射到既有来源资产 | 否；也不自动创建来源主数据 |
| test | 作为技术/质量证据；source-only 当前未解析时不得宣称已导入 | 否 |
| seed/snapshot/analysis/exposure/metric/semantic model 等 | 首期不作为业务候选；若被候选依赖且无法验证则阻断受影响闭包 | 否 |
| 外部 package | 首期不联网下载；ZIP 内缺失或依赖无法闭合时阻断受影响闭包 | 否 |

### 4.2 可信结构与人工补全

- source-only 的可信完整字段契约必须来自同一规范化 parser seam 读取的 schema YAML，并同时满足：model 显式声明 `contract.enforced=true`；每个 column 都有非空且模型内唯一的 `name` 与显式 `data_type`。字段携带 `DECLARED` provenance；description、constraints/tests 可作为证据，但不得自动变成业务键或可空性结论。
- 只有普通 `columns:`、缺少 enforced contract、任一字段缺 `data_type` 或字段重名时，只能形成 `STRUCTURE_VIEW_ONLY` 的声明文档投影，不能成为 `IMPORTABLE`。artifact-rich 的可信度仍按固定 manifest/catalog provenance 单独判定。
- 当前 source adapter 尚未把 schema YAML columns 投影到普通 model；F1/T02 只冻结并保留唯一 `ModelPackage` seam，具体 source-only 扩展由 P1/F3/T06 在该 seam 内实现，不得另建 YAML parser 或第二套投影。
- 无可信完整字段契约时返回 `SOURCE_FIELDS_UNVERIFIED`；界面可按证据显示 `STRUCTURE_VIEW_ONLY`，但 apply 门禁为 `BLOCKED`。首期不允许用户手工重建整张外部技术表结构。
- 用户只可补充模型类型、分层、业务名称/定义、grain、业务键/字段角色、标准绑定、数据域、来源资产映射和消费场景；保存为 `USER_CONFIRMED` provenance。
- 用户不能确认或覆盖动态引用真实指向、macro 展开结果、缺失 package、未知编译字段或不存在的来源资产。

### 4.3 必须 fail-closed 的依赖

以下情况阻断当前 model 及其全部下游，且不能通过“继续导入”人工豁免：

- 非 literal `ref()` / `source()`；
- 自定义 macro、Jinja 控制流、`var()`、`env_var()` 或 `adapter.dispatch()` 可能改变结构或依赖；
- package/model/source 缺失，或依赖闭包无法证明完整；
- 字段结构未知，或 grain/key 引用了非可信字段；
- tenant、plan、domain、source revision 校验失败。

稳定恢复语义如下；这些 source-only 前置问题均不能通过同一个 frozen preview 的 retry 改变，修复后必须重新 inspect/preview：

| code | retryable | recoveryAction | 恢复要求 |
|---|---|---|---|
| `SOURCE_FIELDS_UNVERIFIED` | false | `REUPLOAD` | 补齐 enforced schema contract 或上传 artifact-rich ZIP |
| `SOURCE_DEPENDENCY_DYNAMIC` | false | `REUPLOAD` | 改为可静态证明的 literal 依赖或上传 artifact-rich ZIP |
| `SOURCE_MACRO_DEPENDENCY_UNVERIFIED` | false | `REUPLOAD` | 消除隐藏依赖或上传可证明依赖的 artifact-rich ZIP |
| `SOURCE_PACKAGE_MISSING` | false | `INCLUDE_DEPENDENCY` | 把依赖纳入获授权 ZIP；首期不联网下载 |
| `SOURCE_SEMANTICS_INCOMPLETE` | false | `COMPLETE_MAPPING` | 补齐允许的业务语义后重新 preview |
| `DBT_RUNTIME_NOT_CERTIFIED` | false | `CONTACT_ADMIN` | 由管理员完成 runtime profile 认证，不能用户降级执行 |

错误响应必须给出受影响 uniqueId 与下游闭包。retry 只处理 apply 已启动后、前置条件未变化且被判定可重试的逐项执行/持久化失败。

### 4.4 部分成功与阶段副作用

- archive 根结构非法、项目为空、重复模型、恶意 ZIP 或超安全预算时，整包 inspect 失败，不产生 candidate。
- 模型级问题只阻断该模型及其下游；preview 可同时包含 importable 与 blocked 闭包，但 BLOCKED 项不得进入 `selectedUniqueIds`。用户只选择完整可导入闭包时，若所有选中项成功，attempt 状态为 `SUCCESS`，不是 `PARTIAL`。
- `PARTIAL` 只用于 apply 已启动后，选中的合格项发生逐项事务、持久化、审计等运行失败而形成成功/失败混合结果；统计范围仍只包含 selected items。
- inspect 不创建 ModelSpec、Implementation、Catalog 或运行任务；preview 只冻结既有 import run/item、映射、issues、projection 与 checksum。
- apply 只处理被选中且完整可导入的依赖闭包；每个成功项创建 canonical DRAFT、`DBT_MANAGED` Implementation revision、immutable artifact 和审计结果。
- BLOCKED/FAILED 项只保留结果与审计，不创建 ModelSpec/Implementation/Catalog；Catalog 时点继续遵循 D08。

## 5. 所有权硬约束

外部 ZIP 导入始终是 `DBT_MANAGED`。静态 SQL 是否属于设计器安全子集，只影响可视化/诊断能力，不能把导入模型静默归类为 `DESIGNER_GENERATED`。现有 classifier 的 conversion capability 与 Implementation ownership 必须在 F1/T02、F3/T02 中解耦并用 fixture 固定。

## 6. 分切片 READY 与 DONE 证据

- [ ] **S1/S2 READY**：只要求对应的 F0/T02 工程 parser/fixture 与契约证据；S2 至少归档 FX-01 artifact-rich、基础 blocked 分支和 FX-05 malicious。不得要求 H83-01、source-only 或编码后回归先完成。
- [ ] **S3 READY**：H83-01 已产生不可变候选镜像及原始 RT-01 证据，F0/T05 已完成审查、登记 `certificationProfileId` 并把精确 PostgreSQL profile 从 `NOT_CERTIFIED` 转为 `CERTIFIED`；未认证 runtime、漂移版本/adapter 继续稳定 fail-closed。
- [ ] **S4 READY**：FX-02 同时覆盖“enforced contract + 完整 name/data_type”“非 enforced/缺 data_type”“无字段”三支；FX-03 覆盖动态依赖、自定义 macro 隐藏依赖与缺失 package，并证明阻断调用模型及其下游。
- [ ] **对应切片 DONE**：编码完成后由 F6/T01 消费该切片的工程 fixture；S3 还必须用同一已认证 profile 完成独立 RT-01 回归。回归不能首次创建或扩大认证范围。
- [ ] **S4 DONE**：BLOCKED 项不可选择，只有选中项运行失败才产生 PARTIAL；importable source-only apply 后 ownership 仍为 `DBT_MANAGED`。
- [ ] **客户兼容声明**：只覆盖获授权客户画像的实测组合；没有客户包时明确保持“未声明”，不影响上述工程 READY/DONE。
