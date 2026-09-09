# F5：建模状态语义与上游引用准入收敛

**优先级**：P0  **状态**：DRAFT  **日期**：2026-09-09

## 目标与现场归因

用户在选择上游模型时就能看出该上游当前能否被引用；保存、提交实现和发布被拒时，界面直接说明是哪个上游、缺什么条件。设计阶段继续允许引用同规划内未发布模型，不因收紧校验而阻断 DWD/DWS 联合开发。

本 Feature 来源于 2026-09-09 对建模版本与引用关系的源码复审。复审结论：分阶段准入规则本身已经存在且方向正确，缺陷集中在**状态表达**与**拒绝信息**两处，不是"校验时机排错了"。三条独立状态轴中只有一条对用户可见：

| 状态轴 | 载体 | 取值 | 用户可见 |
|---|---|---|---|
| 模型设计状态 | `ModelSpecContract.ModelStatus`:373 | DRAFT / DESIGNING / VALIDATING / READY_TO_PUBLISH / PUBLISHED / ARCHIVED | 是 |
| 实现状态 | `ModelLifecycleContract.ImplementationView.status`:771，裸 String | "ACTIVE" 等 | 否 |
| 交付状态 | `ModelLifecycleContract.DeliveryStatus` | 15 值状态机 | 候选页可见 |

`DESIGNING`、`VALIDATING`、`READY_TO_PUBLISH` 三值在 `dts-platform/src/main` 内**零写入点**（账本 C27）。因此界面上的"草稿"是 `DRAFT` 一个值被迫承载"设计未完成 / 实现已提交 / 已物化"的全部语义，用户无法据此判断上游是否可用。决定可用性的是第二条轴，而该轴既未枚举化、也未投影到任何面向选择器的读接口。

不把本 Feature 理解为"放宽或收紧校验"。三阶段准入强度维持现状，只改状态表达、可用性投影与拒绝信息精度。

## 冻结契约与复用边界

### 三阶段准入矩阵冻结

下表为冻结结果，本轮不改变任何一格的判定强度。

| 阶段 | 落点 | 现行规则 | 本轮动作 |
|---|---|---|---|
| 设计 | `ModelSpecApplicationService.validateReferenceSet`:1796 | 同规划内可引用任意修订（含未发布）；跨规划要求被钉修订快照为 PUBLISHED | 保持，不收紧 |
| 实现保存/提交 | `ModelImplementationInputPolicy.validateUpstream`:132 | 六元组全等 + 上游实现须 ACTIVE | 保持强度，改结果结构 |
| 物化计划 | `ModelImplementationDependencyService.resolvePlan`:342 | 缺当前实现即拒，带 modelSpecId/revision 明细 | 作为明细精度的基准形状 |
| 发布预检 | `ModelReleaseCandidatePreflightService.validateEdges`:305 | 钉住修订漂移且该修订快照非 PUBLISHED 则阻断；同规划未发布上游按 AUTO_DEPENDENCY 纳入同批次 | 补归档分支 |

发布阶段的 `upstream.status()` 取自**被钉修订的快照**（`ModelSpecApplicationService`:1521 读 `findRevision` 后 `compatibilityReader.read`），不是模型当前状态；该语义正确，不在本轮更改。

### 契约变更

| 类型 | 契约 | 变更 |
|---|---|---|
| 枚举 | `ModelLifecycleContract.ImplementationStatus` | 新增；`ImplementationView.status` 由 String 改为该枚举，取值集合以现网实际写入值为准，由 T25 冻结 |
| 结果 | `ModelImplementationInputPolicy.ValidationResult`:249 | 由 `(boolean, String)` 扩为携带 `modelSpecId`、`reason`、`expected`/`actual` 的结构 |
| REST | `POST /api/modeling/model-specs/upstream-availability` | 新增只读批量投影；请求 `{modelSpecIds: UUID[], ownerModelSpecId: UUID}`，响应 `ApiResponse<List<UpstreamAvailabilityView>>` |
| DTO | `UpstreamAvailabilityView` | `{modelSpecId UUID, revision int, checksum string, implementationState enum, implementationRevision int|null, dbtUniqueId string|null, selectable boolean, blockReason string|null}` |
| 记录 | `ModelSpecApplicationService.DependencyNode`:2216 | 追加 `implementationState`、`implementationRevision`，供已选上游区分"设计草稿"与"实现已提交" |
| 数据 | 无 | 不新增表、列、索引、事件；实现状态仍由 `modeling_model_implementation` 承载 |
| 迁移 | 无 | 枚举化为应用层类型变更，不改列类型。若 T25 发现现网存在枚举外取值，先补设计再决定是否需要前向 changeSet |

`implementationState` 取值：`NONE`（无实现记录）/ `DRAFT`（有实现但非 ACTIVE）/ `ACTIVE`（可被引用）/ `UNKNOWN`（越权或不可读）。

### 复用与禁止

- 复用既有 owner：实现读取仍走 `ModelLifecycleRepository.findImplementation`；依赖图仍由 `ModelSpecApplicationService.dependencyGraph` 产出；不新建模型/实现/依赖台账（domain-dts A4）。
- 新增端点是**只读投影**，形状对齐既有批量查询 `getModelServingSyncStatuses`（`modelSpecApi.ts`:337），不承担任何写入。
- 不动 `ModelSpecContract.ModelSpecView`：111 个文件引用（账本 C33），加字段属高风险且无必要，可用性走独立投影。
- 不扩权限、不改密级与租户判定、不新增菜单或页面（domain-dts 红线 4）。
- 不改变 dbt 编译、隔离项目组装与物化执行链路，那部分由 `fa920a05b` 关闭。

## UI/UX 与四态

入口沿用 `/data-modeling/dimensions/workbench`，实现配置步骤内的既有「上游模型」选择区（`ModelImplementationBindingFields.tsx`:302），以及设计步骤的「上游模型设计」区（`ModelLogicalDependencies.tsx`:30）。不新增页面、路由或菜单。

两个区的语义必须分开呈现，这是本 Feature 的核心界面产出：

- **设计步骤**：继续列出同规划内全部合规上游，含未发布模型，不按实现状态过滤。副标题保留"设计版本 rN"。允许选中无实现的上游，这是联合建模的正常路径。
- **实现步骤**：每个候选行追加可用性标记。`ACTIVE` 可勾选；`NONE`/`DRAFT` 行置灰不可勾选，并就地说明"上游尚未提交实现，需先完成其实现配置"；`UNKNOWN` 置灰并说明无读取权限。
- **已选上游变化**：已保存但当前不可用的上游，区分三种提示，取代现有单一的"当前不可作为上游"文案：上游实现已撤回、上游设计版本已前进、上游不可读。

四态：空为"当前暂无可引用的上游模型"；加载时候选区独立骨架，不阻塞其余表单；错误时展示后端返回的具名原因与上游名称，保留已填写内容；成功时勾选状态与锁定版本一并回显。

走查：进入工作台 → 打开一个 DWS 模型 → 设计步骤勾选未发布 DWD 上游并保存 → 进入实现步骤，确认该上游标为不可选且原因可读 → 为该 DWD 完成实现提交 → 返回 DWS 实现步骤，确认该上游转为可选 → 勾选并提交实现 → 物化。另注入：上游实现撤回、上游设计版本前进、越权上游三条失败路径。

Chrome 95 下不得引入新语法或新 API 依赖。

## Task 与依赖

| Task | 优先级 | 状态 | 依赖 |
|---|---|---|---|
| T25 状态语义与准入矩阵冻结 | P0 | DRAFT | 无 |
| T26 上游可用性投影与批量查询 | P0 | DRAFT | T25 |
| T27 实现状态枚举化与准入结果结构化 | P0 | DRAFT | T25 |
| T28 上游选择器可用性与依赖变化展示 | P0 | DRAFT | T26、T27 |
| T29 六元组直发与归档上游准入补齐 | P1 | DRAFT | T27 |
| T30 正式构建、Chrome 验收与三维证据 | P0 | DRAFT | T25–T29 |

顺序：T25 → T26/T27 并行 → T28 → T29 → T30。单代理执行，不启用多代理。

## DoR 与 Gate

- [x] 三阶段准入规则已按源码逐条核对并记录行号，见账本 C27–C34。
- [x] 契约已钉死：新增端点方法/路径/请求响应字段、枚举取值、记录追加字段均已写明。
- [x] 竖切片贯通：选择器 → 批量投影端点 → `ModelLifecycleRepository` → 现有实现表，无 TBD 层。
- [x] UI 落点具名：两个既有区块，不新增页面。
- [x] 影响分析：`ModelImplementationInputPolicy` 上游 1 个直接调用方，风险 LOW；`ModelSpecView` 111 文件引用，已据此排除加字段方案；`DependencyNode` 4 处消费，扩展成本可控。
- [ ] `ImplementationView.status` 枚举化涉及 31 个文件、27 处字符串比较，T25 未冻结取值集合前不得开工。
- [ ] 现网实现状态实际取值分布未采样，T25 完成前 T27 保持 DRAFT。

Gate：G0 复用 Sprint-104 既有基线与登录证据，本 Feature 不新增环境前提；G1 待 T25 关闭上述两项后转 PASS；G2/G3/G4 由 T30 跟踪。

## 完成标准

- [ ] 用户在实现步骤能直接看出每个上游能否引用，不再出现"选完保存才报错"。
- [ ] 保存、提交、物化、发布四处拒绝均指明上游标识与缺失条件，不再返回无主体的单一错误码。
- [ ] 设计阶段引用同规划未发布上游的能力未被削弱，联合建模走查通过。
- [ ] 前端按六字段契约直发上游输入，后端不再依赖保存期补齐锁定。
- [ ] 归档上游在依赖修订未漂移时不再静默通过发布预检。
- [ ] IT-29–IT-32 留存真实证据；未执行阶段不标 DONE。

## 非目标

不重做模型设计状态机，不启用三个零写入枚举值（处置由 T25 决定为保留或删除，二选一并记 ADR）；不改物化执行与 dbt 隔离项目组装；不引入新输入方式；不做跨规划引用策略变更；不新增角色或权限粒度；不改动生产数据。
