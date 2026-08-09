# F1/T02 关键关系、批量候选与二次物化决策包

**状态**：APPROVED

**形成日期**：2026-08-09

**评审入口**：IT-03、IT-07（NFR-86-04～08/12）

**评审人**：xiezm（已登记，兼任全部 Accountable roles）

本文件记录已冻结的关系语义；不授权修改源码、schema 或运行数据。ADR 的权威状态仍写入
[`decision-register.md`](decision-register.md)。

## 1. 已批准结论摘要

| ADR | 已批准选择 | 关键约束 | 当前状态 |
|---|---|---|---|
| ADR-86-13 | 语义模型资产与物理资产分离，通过 immutable revision/candidate/observation 关联 | ModelSpec 发布不等于物理表存在；物理 locator 只解析为既有 `CatalogAssetType + CatalogAssetKey` | ACCEPTED |
| ADR-86-14 | 数据集市单业务分类；APPLICATION 必须选择数据集市与主题域 | 兼容期保留现有关联表并在服务层强制单归属；主题域必须属于所选集市 | ACCEPTED |
| ADR-86-15 | 依赖为 DAG；候选创建全有或全无；运行可逐项失败；二次物化新增 attempt/observation | 服务端不得静默排除无资格模型；跨计划只允许固定已发布 revision | ACCEPTED |

## 2. 关系基数与稳定引用

| 关系 | 目标基数 | 稳定引用 | 写 owner | 失败规则 |
|---|---:|---|---|---|
| 业务分类 → 数据域 | 1:n | `CatalogDomain.id`；根为分类、子为域 | 平台数据架构 | 子域无父、父非根或成环时拒绝 |
| 数据域 → 业务过程 | 1:n | `businessProcessId: UUID` | 平台数据架构 | 过程与模型域不一致时拒绝 |
| 业务分类 → 数据集市 | 1:n | 现有关联行在兼容期保留；每个 `martId` 只能有一个 ACTIVE 分类 | 平台数据架构 | 0 个或多于 1 个分类时不得进入 CURRENT/新计划 baseline |
| 数据集市 → 主题域 | 1:n | `subjectDomain.martId` | 平台数据架构 | 主题域与模型所选集市不一致时拒绝 |
| 数仓计划 → 数据域/集市 | n:m 建设范围 | 计划 baseline revision | 数据建模规划 | 只允许 CURRENT 字典对象；父子不一致时拒绝确认 |
| 数仓计划 → ModelSpec | 1:n | `planId + modelSpecId` | 数据建模 | 模型上下文不包含于已确认 baseline 时拒绝提交 |
| ModelSpec → Revision | 1:n | `modelSpecId + revision + checksum` | 数据建模 | 发布链不得引用可变 head |
| Revision → Candidate Entry | n:m | entry 固定 revision/checksum/implementation | 发布控制面 | revision 漂移或实现缺失时整个候选创建失败 |
| Candidate Entry → Dispatch/Observation | 1:n | `candidateEntryId + attempt`；observation 追加 | 物化控制面 | 重试不得覆盖旧 attempt/observation |
| ModelSpec → 语义模型资产 | 1:0..1 当前 serving 投影 | 既有 `SEMANTIC_MODEL` asset key | 建模投影 | 未发布 revision 不得生成 latest-published |
| Physical Observation → 物理资产 | n:1 | 既有 `CatalogAssetType + CatalogAssetKey` | 数据资产 | locator 不完整/冲突时保持 FAILED/UNKNOWN，不伪造资产 |
| 业务主数据 → DIMENSION | 1:n 分析投影 | 主数据对象/版本引用；后续 MDM Sprint 定义 | MDM + 建模 | 本 Sprint 只保留边界，不创建 MDM 台账 |

## 3. ModelSpec 业务上下文矩阵

| 模型类型 | 技术层 | 必填上下文 | 禁止/兼容规则 |
|---|---|---|---|
| DIMENSION | DWD | `planId`、`dataDomainId`、DimensionDefinition ID/revision | 不新增 DIM canonical layer；跨域共享仅消费已确认的一致性维度定义 |
| FACT | DWD | `planId`、`dataDomainId`、`businessProcessId` | 业务过程必须属于所选数据域；自由文本 `business_activity_ref` 只作兼容展示 |
| SUMMARY | DWS | `planId`、`dataDomainId`、至少一个固定上游 revision | 不得依赖 ADS；跨计划上游必须是已发布固定 revision |
| APPLICATION | ADS | `planId`、`dataDomainId`、`dataMartId`、`subjectDomainId`、至少一个固定上游 revision | 主题域必须属于所选集市；分类、域、集市不一致时拒绝提交 |

已批准的兼容扩展方向：

- ModelSpec head、不可变 revision snapshot、导入/导出 schema 与 candidate entry 同批增加
  `businessProcessId`、`subjectDomainId`；旧自由文本字段先保留。
- 数据集市/主题域遗留 `tenant_id` 继续物理保留。API 不接受客户端 tenant；服务端统一写平台 scope，UI 不展示租户选择器。
- `modeling_data_mart_domain` 在 Expand 阶段保留，先通过 service guard 强制每个 mart 一个根分类；清洗后再增加
  `mart_id` 单值约束。是否最终改为显式外键列留到 Contract 阶段另批审批。

## 4. ADR-86-13：语义模型与物理资产映射

### 4.1 两种身份

```text
SemanticModelRef = modelSpecId + publishedRevision
PhysicalRelationRef = dataSourceId + databaseName + schemaName + relationName + relationType
CatalogAsset identity = existing CatalogAssetType + CatalogAssetKey(PhysicalRelationRef)
```

- `SemanticModelRef` 表示逻辑/语义设计版本，不承诺数据库对象存在。
- `PhysicalRelationRef` 由成功或可核验的 observation 提供，必须通过既有 `CatalogAssetKey` 规范化；禁止新增
  “模型物化资产 ID”。
- candidate entry、dispatch、observation 保存关联证据；CatalogDataset 只保存/投影资产事实，不反向成为模型 revision。

### 4.2 冲突、重命名、撤销与重放

| 场景 | 已批准行为 | 禁止行为 |
|---|---|---|
| 同一 locator 被多次运行/多个渠道观测 | 幂等解析同一 CatalogAssetKey，追加或刷新观测证据 | 创建第二个资产身份 |
| 同一 locator 同时声明不兼容 relationType | 标记 `IDENTITY_CONFLICT`，阻断 serving 投影并进入治理队列 | 任选一个类型覆盖 |
| 物理表重命名 | 新 locator 形成新 asset key，并登记 `RENAMED_FROM` 关系；旧资产进入 DEPRECATED/RETIRED 流程 | 原地改写历史 asset key |
| 回滚/撤销发布 | 撤销 serving 指针或 publication 状态，保留 revision、candidate、observation 和资产历史 | 级联删除 CatalogDataset |
| observation 重放 | 以 observation/evidence 幂等键去重；结果一致则返回 replayed | 重放产生重复资产或覆盖旧证据 |
| 物理关系消失 | discovery=MISSING、serving=STALE/FAILED；消费资格 BLOCKED | 自动改成“未发布”或删除治理信息 |

## 5. ADR-86-14：集市、主题域与计划范围

1. 一个数据集市恰好属于一个业务分类；一个业务分类可有多个数据集市。
2. 一个主题域恰好属于一个数据集市；主题域不能直接挂数据域。
3. 数仓计划可选择多个数据域和多个数据集市，只表达建设范围，不取得字典写权限。
4. APPLICATION ModelSpec 必须固定 `dataMartId + subjectDomainId`，且 `subjectDomain.martId == dataMartId`。
5. ModelSpec 的 owning `dataDomainId` 继续必填；其根分类必须与数据集市所属分类一致。
6. 历史多归属 mart 先进入迁移异常清单：0 归属补录，1 归属直接确认，>1 归属由产品 owner 选择唯一值；不得自动取第一条。

## 6. ADR-86-15：DAG、批量候选和二次物化

### 6.1 依赖图规则

- 图节点是不可变 ModelSpec revision，不是可变 head 或物理表名。
- 合法方向为 DWD → DWS → ADS；同层依赖必须显式且不形成环。
- 同计划未 serving 的上游 revision 自动进入候选闭包；已 serving 且版本匹配的上游作为外部已满足依赖，不重复派发。
- 跨计划依赖只允许显式固定的 PUBLISHED/SERVING revision；不得自动把另一个计划的可变 head 纳入候选。
- 循环、版本漂移、越界或缺失实现均在候选创建前失败关闭。

### 6.2 候选原子性

```text
多选 root models
  → 服务端预检并返回 root + dependency closure + blockers
  → 用户显式取消无资格 root，重新提交
  → 原子创建一个 immutable ReleaseCandidate
  → 拓扑派发；运行结果允许逐项 SUCCESS/FAILED/SKIPPED_DEPENDENCY_FAILED/CANCELLED
```

- 创建候选采用**全有或全无**：任何 root/闭包节点无资格，服务端不落候选、不派发。
- UI 可以提供“排除无资格模型”，但必须由用户确认后以新请求提交；服务端不得静默排除。
- 候选一旦锁定，entries、revision、checksum、implementation 和依赖闭包不可变；变化需 replacement candidate。
- 运行允许部分失败；下游依赖失败时标记 `SKIPPED_DEPENDENCY_FAILED`，不得继续执行并冒充成功。

### 6.3 二次物化与并发

- 同一 candidate/revision 的重试或主动二次物化产生新的 `attempt` 与 observation；历史只读保留。
- 新 ModelSpec revision 必须进入新 candidate/replacement entry，不能挂到旧 entry。
- `Idempotency-Key` 相同且 request hash 相同返回 replayed；key 相同而 payload 不同返回冲突。
- 同一 `plan + environment` 同时最多一个 active candidate；同一 candidate entry 同时最多一个 active attempt。
- cancel 先进入 `CANCEL_REQUESTED`，由执行器收敛为 `CANCELLED`；取消不删除历史证据。

## 7. NFR 已批准设计值（运行验证转 Sprint-87）

| 项目 | 已批准设计值 | Fitness function |
|---|---:|---|
| 根模型批量上限 `BATCH_MAX` | 100 | 100 成功；101 返回 422，零候选/派发 |
| 候选闭包节点 `DAG_NODE_MAX` | 500 | 500 节点可拓扑；501 具名失败 |
| 候选闭包边 `DAG_EDGE_MAX` | 2,000 | 边界内无重复；超限零派发 |
| DAG 最大深度 | 20 | 深度 20 可执行；21 失败并返回路径 |
| 候选预检/创建 API P95 | ≤ 5 秒（500 节点设计量） | 基准 IT 输出 P95；超限失败 |
| 前端轮询间隔 | 5 秒；后台/隐藏页退避到 30 秒 | fake timer/Playwright 断言，不产生并发轮询 |
| 无进展判定 | 10 分钟无 heartbeat → STALE；不自动判 FAILED | 故障注入后 10 分钟内具名可见 |
| 单次运行默认总时限 | 120 分钟，可由环境配置但必须有上限 | 配置/超时 IT 断言无无限运行 |
| 取消 | 5 秒内受理；5 分钟内进入终态或明确 `CANCEL_FAILED` | 取消 IT 断言状态与审计 |

这些数值是缺少客户批量画像时由 xiezm 接受的**产品设计容量**，不冒充生产容量证明；Sprint-87 F0 取得客户画像后须执行对应 fitness functions，超出设计容量则回到架构评审。

## 8. 拟新增/复用错误码

| 错误码 | 含义 | HTTP/状态语义 |
|---|---|---|
| `MODEL_DEPENDENCY_CYCLE` | 依赖成环 | 422，返回循环路径 |
| `MODEL_DEPENDENCY_SCOPE_VIOLATION` | 跨计划引用未固定已发布 revision | 422 |
| `MODEL_DEPENDENCY_LIMIT_EXCEEDED` | 节点、边或深度超限 | 422，返回实际/上限 |
| `MODEL_RELEASE_CANDIDATE_INELIGIBLE_ENTRY` | root 或闭包节点无资格 | 422，零候选 |
| `MODEL_RELEASE_CANDIDATE_ACTIVE_EXISTS` | 同计划/环境已有 active candidate | 409，返回现有 candidateId |
| `MODEL_MATERIALIZATION_ATTEMPT_ACTIVE` | 同 entry 已有 active attempt | 409 |
| `MODEL_PHYSICAL_RELATION_IDENTITY_CONFLICT` | locator/type 冲突 | 409，阻断资产投影 |

复用现有 candidate version conflict、stale、idempotency 与 scope-empty 错误；不得为同一语义新增第二套错误码。

## 9. IT-03 通过清单

- [x] 接受 ADR-86-13 的双身份与 locator 规则。
- [x] 接受 ADR-86-14 的单分类集市与 APPLICATION 主题域必填规则。
- [x] 接受 ADR-86-15 的候选全有或全无、运行逐项失败及二次物化规则。
- [x] 接受 §7 的批量/DAG/超时设计值。
- [x] 确认 E2E-A～C 的用例设计完备；E2E-D 只移交 MDM Sprint。
- [x] 确认本评审不代表真实代码、迁移、构建或 E2E 已执行。
