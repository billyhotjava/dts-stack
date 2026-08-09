# Sprint-86 架构评审发现（Review Findings）

**评审日期**：2026-08-09

**评审对象**：Sprint-86 README、assets、F1～F5 Task 与 IT 计划

**评审性质**：文档阶段独立复核；未修改任何源码、schema、菜单或运行数据

**核实方式**：源码/Liquibase 逐处核对 + 当前环境 PostgreSQL 只读复核

**总体结论**：方向成立；RF-86-01 仍是 F2 分层契约的 BLOCKER，其余发现已通过文档修订缓解，但所有 PROPOSED/OPEN ADR 仍需用户/架构评审。

## 严重度与状态

| 字段 | 含义 |
|---|---|
| BLOCKER | 若基于当前错误/分裂事实冻结契约，实施期必然返工；对应 ADR 前必须关闭 |
| MAJOR | 关系、边界或影响缺口会使下一实施 Sprint 偏离目标 |
| MINOR | 不推翻方向，但必须补入画像、蓝图或迁移准入 |
| OPEN | 事实已确认，决策仍未冻结 |
| MITIGATED | 文档已纠正事实并绑定 owner/Task；不代表相关 ADR 已 ACCEPTED |

## 发现总表

| ID | 标题 | 严重度 | 阻塞/影响对象 | 状态 |
|---|---|---|---|---|
| RF-86-01 | DIM 在建模/dbt 与资产目录之间语义分裂 | BLOCKER | ADR-86-05、F2/T01 | OPEN |
| RF-86-02 | 本地与客户域规模口径不同，页面形态需自适应 | MINOR | ADR-86-09、F4/T01 | MITIGATED |
| RF-86-03 | 归域率 0/83 要求把批量归域提升为一等治理动作 | MAJOR | F4/T01 | MITIGATED |
| RF-86-04 | GitNexus 对当前 React 组件出现前端影响假阴性 | MAJOR | F4/T01、F5/T01 | MITIGATED |
| RF-86-05 | 自定义分层全量软删的首版范围未冻结 | MINOR | F2/T01、F5/T01 | MITIGATED |
| RF-86-06 | `/governance/subjects` 四 Tab 迁移 owner 原缺失 | MINOR | F1/T01、F4/T01 | MITIGATED |
| RF-86-07 | “数据架构主数据”与业务 MDM 概念冲突 | MAJOR | ADR-86-12、F1/T01/T02 | MITIGATED |
| RF-86-08 | 资产纳管范围扩大与统计 5000 扫描上限冲突 | MAJOR | ADR-86-04、ADR-86-16、F2/T01 | OPEN |
| RF-86-09 | 六种 Actor 角色 vs 三档权限粒度，I07 运行时不可强制 | MAJOR | ADR-86-08、ADR-86-17、F1/T01 | OPEN |
| RF-86-10 | F1/T02 与 F4/F5 在批量候选原子性上循环依赖 | MAJOR | F1/T02、F4/T01 | MITIGATED |
| RF-86-11 | 三套状态词汇表并存且无映射 | MINOR | 全部 Task 验收 | MITIGATED |
| RF-86-12 | IT-03 验收标准在“设计评审”与“四类证据”间歧义 | MINOR | IT-03 | MITIGATED |
| RF-86-13 | ADR 冻结结果的写回位置未定义 | MINOR | F1/T02、F5/T01 | MITIGATED |
| RF-86-14 | “自适应形态”把未知规模包装成设计决策 | MINOR | ADR-86-09、F4/T01 | MITIGATED |

> 第二轮评审（2026-08-09，全量读取 20 个文件 / 1254 行，非抽样）新增 RF-86-08～14。
> RF-86-08 是唯一可能推翻已 DIRECTION_CONFIRMED 的 ADR-86-04 的发现，优先级最高。

## RF-86-01｜DIM 跨上下文语义分裂 · BLOCKER · OPEN

### 事实

| 上下文 | 当前事实 |
|---|---|
| 建模 | `ModelSpecContract.Layer` 为 ODS/STG/DWD/DWS/ADS；`targetLayer(DIMENSION)` 返回 DWD |
| 规划 | Sprint-64 将 DWD 命名为“明细事实 / 维度层”，命名前缀同时包含 `dim_`、`fact_` |
| canonical 分层 | `WarehouseLayerApplicationService` 的系统映射没有 DIM |
| dbt 资产同步 | `resolveControlledLayer` 只识别 ODS/STG/DWD/DWS/ADS |
| 资产目录后端 | `CatalogAssetOverviewAggregator.KNOWN_LAYERS` 包含 DIM；`CatalogDataset.warehouseLayer` 是自由字符串，更新链只规范大小写，不做枚举校验 |
| 资产前端 | `assetPageShared.tsx` 展示 DIM 并纳入 `LAYER_ORDER` |

因此，原结论“DIM 仅由前端虚构、后端不存在”不成立。真正的问题是：**建模与 dbt 把维度模型放在 DWD，而资产目录后端/前端允许 DIM 作为独立展示值**。如果不先冻结归一化策略，同一张维度表会在模型、物化和资产统计中得到不同分层。

### 已修正文档

- README L14、能力边界、领域画像和 F2/T01 已改为“跨上下文语义分裂”。
- 候选方向改为：DIMENSION 模型继续使用 DWD；资产侧历史/外部 DIM 是否映射为 DWD、保留兼容展示或正式升格为 canonical layer，必须显式 ADR。
- ADR 未冻结前，不新增 DIM 写入链，不按 DIM 扩展 dbt/ModelSpec 枚举。

### 关闭条件

ADR-86-05 必须给出选择、兼容读写、现存值画像、回填/回滚、资产统计和模型 UI 验收。完成前 F2 分层轴保持 DRAFT。

## RF-86-02｜域规模口径影响组件形态 · MINOR · MITIGATED

### 事实

- 当前本地实测：4 个域节点（根 2、子 2）。
- 客户环境用户口径：约 10～50 个、两层。
- 两者属于不同环境，不构成数据矛盾；风险在于用单一规模提前冻结 UI。
- 现有 `DomainScopeNav` 已按数量阈值决定是否显示搜索能力，说明“树或 Table”不必是静态二选一。

### 缓解

领域画像并列两个口径；ADR-86-09 保持 PROPOSED；F4 要求层级关系不丢失，并按规模/宽度自适应：小规模可平铺，大规模显示搜索与树/分组。

## RF-86-03｜零归域率要求一等批量治理 · MAJOR · MITIGATED

### 事实

当前环境 83 个数据集、0 个已归域。现有 `DomainScopeNav` 已提供“全部资产”和“未归域”，所以“域导航是空壳”“批量归域是唯一有效路径”均过度推断。

### 正确推导

- 未归域资产必须始终可发现，不能把 domainId 非空作为资产准入条件。
- 资产工作台应同时保留全部资产、未归域、按域三条路径。
- 在归域率接近 0 的 onboarding 阶段，批量归域是首要治理动作；服务端必须定义权限、目标域状态、幂等、逐项部分失败、审计和重试契约。

### 缓解

领域画像、F4 操作走查和 [`data-model-relationships.md`](data-model-relationships.md) E2E-B 已按上述逻辑修订。

## RF-86-04｜前端影响分析出现假阴性 · MAJOR · MITIGATED

### 事实

当前 GitNexus 对 `DomainScopeNav`、`SubjectAreasPage` 的 upstream impact 返回 0，但源码存在明确 import/路由/E2E 引用。索引落后不能解释全部现象，因为索引提交本身已包含其中的 import。

这只证明**本次抽查的当前 React 组件存在假阴性**，不足以断言“所有 TypeScript 影响分析系统性失效”。

### 缓解

- G2 从 PASS 改为 GAP。
- `impact-baseline.md` 要求前端使用 GitNexus 流程/上下文 + scoped `rg` 交叉验证，不把 `impactedCount: 0` 当作无影响。
- F4/F5 在实施拆分前必须形成旧页面、Tab、按钮、路由、菜单种子和测试的实际引用清单。

## RF-86-05｜自定义分层全量软删 · MINOR · MITIGATED

### 事实与含义

`modeling_warehouse_layer` 当前 25 条全部为 DELETED、ACTIVE 为 0，当前 canonical 投影实际依赖内置代码。该事实不否定 ADR-86-03“分层与业务树正交”，也不应阻塞已经 ACCEPTED 的正交关系。

### 缓解

风险已改挂 F2/F5：首版是否开放自定义分层，以及 25 条 DELETED 记录保留为回滚锚点还是后续清理，必须在实施范围/迁移 ADR 中明确。

## RF-86-06｜旧 Subject 页面能力承接 owner 缺失 · MINOR · MITIGATED

`/governance/subjects` 的四个 Tab 为建模范围、数据集市、主题域信息、治理概览。原文多处提问但没有前置依赖和 owner。

当前分工：

1. F1/T01 冻结数据集市/主题域的架构 owner。
2. F1/T02 冻结集市→主题域及与计划/应用模型的关系。
3. F4/T01 为四个 Tab、按钮和深链逐项确定目标页面与兼容路由。
4. F5/T01 冻结观测期与 Contract 门禁。

## RF-86-07｜架构字典与业务 MDM 混称 · MAJOR · MITIGATED

### 事实

Sprint 初稿把业务分类、数据域、过程、分层、集市和主题域统称“数据架构主数据”。这会与通常意义上的人员、组织、项目、物料等业务主数据混淆。仓库已有 `dts-admin /api/mdm` 人员/组织同步入口，但没有证据证明通用 MDM 对象、金记录、匹配合并、审核和版本能力已经完整存在。

### 缓解

- Sprint 正文和 F1 改用“数据架构元数据/架构字典”。
- ADR-86-12 冻结边界：MDM 是平级独立 owner，可引用架构字典并通过 DIMENSION ModelSpec 形成分析投影。
- [`data-model-relationships.md`](data-model-relationships.md) E2E-D 只定义接口关系；通用 MDM 实现转入后续独立 Sprint，不与 Sprint-86 的架构字典控制面冲突。

## RF-86-08｜资产纳管范围扩大与统计扫描上限冲突 · MAJOR · OPEN

### 事实（代码自陈述限制）

`service/catalog/CatalogAssetPortalService.java:500-509` 注释：

```
// 已知限制（非本次引入，但影响不止于显示）：
// hidden 按当前取数窗口观测，而窗口大小随 offset 变化……波动幅度不是「小幅」而是可达数千。
// 更要紧的是：overview() 用这个 total 作为扫描循环的终止条件，因此它同时影响 truncated
// 是否被正确判定——不是纯展示字段。
// ……与「每次地图加载已跑两轮全量扫描」的成本问题直接冲突。
// 故记录为已知限制。若要修，应连同该成本问题一并设计（例如缓存 domainStats）。
```

配套事实：

| 事实 | 证据 |
|---|---|
| 统计扫描上限 5000 条，分页 200，最多 25 页 | `CatalogAssetPortalService.java:60`、`:132-133` |
| `withStats` 走同一路径 | `CatalogDomainResource.java:166` → `domainStats` → `overview` |
| 触顶后前端计数降级为 `≥N` | `DomainScopeNav.tsx` `countText` |
| 每次资产地图加载已跑两轮全量扫描 | 同上注释 |

### 冲突

ADR-86-04 把纳管范围从“已治理资产”扩大到“所有稳定可寻址的真实关系”——外部源表、持久化 STG、失败构建残留关系全部进入台账。资产总量显著上升，5000 上限只会更早触顶，且 `truncated` 判定本身已被记为不可靠。

三项连带影响当前均未登记：

1. `domain-profile.md` 画像只记 83 条，未记 5000 上限与两轮扫描的既有成本
2. F4 要设计域导航形态，但域计数在客户规模下是不可靠近似值，蓝图无从验收
3. G1 非功能预算标 PENDING 推给实施 Sprint——但这不是优化问题：若客户资产为万级，`withStats` 实时聚合方案本身要换（缓存 / 物化统计表 / 增量维护），换法会反向约束 F2 的状态机设计（治理状态若参与统计则必须可索引）

代码注释已写明“若要修，应连同该成本问题一并设计”，但 F1～F5 无任何 Task 覆盖。

### 建议动作

1. 新增 **ADR-86-16｜资产统计架构**，与 ADR-86-04 同批冻结；未冻结前 F2 不得宣称纳管矩阵完备
2. `domain-profile.md` §3 补入上限与扫描成本事实，§7 补“客户资产量级”为 NFR 输入
3. Gate G1 非功能预算由 PENDING 改 **GAP**：部分 NFR 是架构决策输入，不能整体推迟
4. F2/T01 DoD 增加：纳管范围扩大后的统计口径与规模上限已评估

### 关闭条件

ADR-86-16 给出：统计口径（实时/缓存/物化）、规模上限、触顶降级的产品表达、以及与 ADR-86-04 纳管范围的联合验收。

## RF-86-09｜六种角色 vs 三档权限，I07 运行时不可强制 · MAJOR · OPEN

### 事实

| 位置 | 内容 |
|---|---|
| `capability-boundary.md` Actors | 6 种角色，各有不同可维护范围（架构管理员 / 建模 / 资产治理 / 指标 / 质量 / 主数据） |
| `domain-profile.md` §6 | “现有权限粒度仅 read/write/export｜已知缺口” |
| `domain-profile.md` §2 I07 | “架构字典只有一个写 owner，消费者不得复制 CRUD”（DTS 领域硬约束） |

三者未连接。现有权限模型无法按实体类型区分写权限，**I07 在运行时无法强制**，只能靠 UI 约定与代码自觉。

### 影响

这正是 Sprint-86 要解决的原始问题（两套 `catalog_domain` CRUD）的成因类型。若 ADR-86-08 把架构字典独立为控制面，而权限层拦不住其他模块写 `catalog_domain`，“独立”只存在于页面层，下一次仍会长出第二套 CRUD。

### 建议动作

1. 新增 **ADR-86-17｜架构字典写权限强制方式**：在现有 read/write/export 粒度下，用什么机制保证单一写 owner（服务层 port 收口 / 独立角色 / 审计后置检测），或显式接受“仅约定级”并记为具名风险
2. `domain-profile.md` I07 行补注运行时强制缺口
3. `capability-boundary.md` Actors 表补注当前权限粒度约束
4. F1/T01 DoD 增加：每个写 owner 的强制手段已明确，不只停留在文档约定

## RF-86-10｜F1/T02 与 F4/F5 循环依赖 · MAJOR · MITIGATED

### 事实

- `data-model-relationships.md` E2E-A 第 5 步原文：「原子失败或显式排除策略由 F4/F5 冻结」
- 但 ADR-86-15（模型依赖与重复物化）在 F1/T02 冻结，批量候选原子性是依赖闭包语义的一部分：部分失败若允许继续，闭包可能不完整，不变量 9 的 DAG 约束无法保证
- `F4/T01` 依赖声明为 `F1/T01、F1/T02、F2/T01、F3/T01`

即 **F1/T02 等 F4，F4 等 F1/T02**。按讨论顺序 F1→F2/F3→F4→F5 会在 F1/T02 卡住。

### 缓解

原子性策略上收至 F1/T02（属关系语义，非页面交互）；F4 只负责其交互呈现与失败可见性。E2E-A 第 5 步、F1/T02 DoD、F4/T01 已相应修订。

## RF-86-11｜三套状态词汇表并存 · MINOR · MITIGATED

### 事实

| 文件 | 词汇表 |
|---|---|
| `decision-register.md` | ACCEPTED / DIRECTION_CONFIRMED / PROPOSED / OPEN |
| `data-model-relationships.md` | CONFIRMED / TARGET_CONFIRMED / PROPOSED / GAP |
| `review-findings.md` | OPEN / MITIGATED |

三套部分重叠、无映射。实际后果：F1/T02 DoD 写「ADR-86-13/14/15 均形成明确结论」，“明确结论”对应哪个词无定义，验收时各说各话。

该问题与 Sprint-86 正在治理的“同一实体多种叫法”属同一类，只是发生在文档元层面。

### 缓解

`decision-register.md` 增加统一状态词汇表与三套映射，见该文件“状态词汇表”一节。

## RF-86-12｜IT-03 验收标准歧义 · MINOR · MITIGATED

`data-model-relationships.md` §7 要求「评审证据必须同时覆盖 UI 选择、API 请求/响应、数据库关联和审计记录」，读起来是要真实执行；而 `it/baseline.md` 声明「IT-03 是关系/证据设计评审，不冒充已执行 E2E」。两者冲突，IT-03 无法判定通过。

### 缓解

§7 与 `it/README.md` 均补入限定：Sprint-86 验收**用例设计的完备性**；四类证据是下一实施 Sprint 的执行要求。

## RF-86-13｜冻结结果写回位置未定义 · MINOR · MITIGATED

F1/T02 有 10 条冻结项，但未说明冻结后写到哪：改 `decision-register` 的 ADR 状态，还是改 `data-model-relationships` 的状态列？两处都改则无同步机制。这是 RF-86-11 的直接后果。

### 缓解

`decision-register.md` “冻结写回规则”一节定义单一写回位置与同步要求。

## RF-86-14｜“自适应形态”把未知包装成决策 · MINOR · MITIGATED

第一轮建议 F4「显式声明按哪个口径设计，并给出另一口径的降级形态」，第二版改为「自适应树/分组，阈值由客户画像与可用性验收冻结」。

自适应需实现两套形态加阈值判断，成本高于任选其一，而客户域数至今未知（`domain-profile.md` §7 第一条仍未决）。更务实是先取一个口径设计，把“实际远超此范围则降级”记为具名风险。

另：`DomainScopeNav.tsx:33` 已有既有阈值 `SEARCH_THRESHOLD = 8`，F4 若做阈值判断应复用而非另立第二个常量。

### 缓解

`capability-boundary.md` UI surfaces 与 F4/T01 已改为“单口径设计 + 降级风险 + 复用既有阈值”。

## 两个缺陷的独立处理边界

下列现网缺陷与 ADR 语义无直接关系，但“纯缺陷均可绕过冻结”过于宽泛：

| 缺陷 | 当前证据 | 允许的提前处理方式 |
|---|---|---|
| 重置静默清域 | `DatasetsPage` 的 active filter 计数漏掉 domain/layer，reset 又会清除二者 | 另立具名 hotfix，仅修计数/重置一致性 |
| 窄屏域不可达 | 资产概览/台账侧栏在 lg 以下 `collapsedWidth=0` | 另立具名 hotfix，仅补可达入口 |

任何提前 hotfix 均需用户单独批准，不得改变菜单、路由、字段或业务语义，并需 source-contract、Chrome 95 和真实浏览器验证。术语改名、概览去侧栏、页面重构仍受 Sprint-86 冻结门槛约束。

## 评审边界

- 本评审未修改任何源码、schema、菜单种子或运行数据。
- 数据库复核为只读查询，执行于 `v223-dts-pg-1` 的 `dts_platform`。
- 第一轮证据对应 HEAD `f7989e480`；第二轮（RF-86-08～14）已在 HEAD `c096b6fd4` 上重新核对。
  `c096b6fd4`（catalog 四页 IA 重构）改动了 `AssetOverviewPage.tsx`，该文件行号已随之更新；
  `DatasetsPage.tsx:505/437-439/601`、`DomainScopeNav.tsx:33/236/248`、`assetPageShared.tsx:77/82` 经复核未漂移。
- 两个已确认 UI 缺陷（重置静默清域、窄屏域不可达）在 `c096b6fd4` 后**仍然存在**，未被该次重构修复。
- 实施前仍须重新核对全部行号。
- 本轮已补关系级事实，但没有替代实施前逐符号 impact、客户数据画像、迁移 dry-run 或真实浏览器验收。
