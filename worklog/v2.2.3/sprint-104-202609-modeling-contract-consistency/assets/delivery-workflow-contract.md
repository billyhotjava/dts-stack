# F2 模型交付与资产治理统一契约

**状态：F2 历史流程基线（不代表整体验收通过）。** 2026-09-07 用户明确新边界后，本文件及派生 T09/T10 契约中的 W1–W4、模型页质量/资产维护及“发布交付后结束”要求由 [F3 边界契约](F3-modeling-data-boundary.md) 替代，接续责任见 [F2 修订表](../features/F2-模型交付与资产治理贯通/README.md)。下文保留历史实现规则与定位证据，不继续作为新页面验收标准。

未冲突的模型/目录/规则/serving owner、版本与 CAS、安全、幂等、证据失效和帮助能力继续复用；新增 DTO/迁移和准确副作用由 F3/T15 冻结。F3 尚未实施，本修订不宣称当前代码已解耦。

## 业务目标与边界

用户在模型工作台完成物化、质量检查、发布并确认交付结果，常见阻断可就地处理。资产目录负责持续治理，规则中心负责复杂规则维护，BI 负责分析使用。复用现有页面和服务，不新增菜单、平行模型台账或前端发布状态机。

物化成功、质量通过、模型发布、资产登记、分析准备分别取证；它们有关联但不互相冒充。分析准备失败不回滚已发布模型、不阻止已登记资产的治理，也不触发重新物化。保留现有阻断性质量政策，禁止自动绑定无效规则或用恒通过规则绕过门禁。

## 现状勘察账本（追加 C11–C18）

路径相对仓库根，行号为本次规划源码定位。之前对用户样例的诊断是历史观察，实施前仅刷新受影响基线，不重扫全仓。

| 编号 | 已确认事实 | 证据与扩展点 |
|---|---|---|
| C11 | serving 状态被直接翻译为目录状态 | `source/dts-platform-webapp/src/pages/data-modeling/prototype/modelServingSyncPresentation.ts:7`；`ModelAssetDeliveryResult.tsx:40` |
| C12 | 分析语义发布、查询数据集投影全部成功才标记同步成功 | `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/modeling/serving/CatalogModelSemanticSyncService.java:115`；`:126`、`:128`；永久错误分类 `:223` |
| C13 | 分析发布通过平台数据源 ID 找连接，缺失返回 400；已有按平台 ID 注册能力 | `source/dts-analytics/src/main/java/com/yuzhi/dts/analytics/web/rest/SemanticPublishResource.java:121`、`:137`、`:212`；`DatabaseResource.java:597`；后者要求用户会话，不能让服务令牌直接冒用 |
| C14 | 大屏选源时已有注册调用；注册返回 ID 不证明元数据已准备好 | `source/dts-platform-webapp/src/analytics/pages/screens/components/DatabaseIdPicker.tsx:72`；`DatabaseResource.java:597` 方法内部同步元数据异常被捕获 |
| C15 | 配置质量规则链接只有通用页面，资产身份放在 title 而非路由 | `source/dts-platform-webapp/src/pages/data-modeling/prototype/ModelReleaseWorkflowPanel.tsx:276` |
| C16 | 可复用批量 serving 查询与有版本保护的重试 | `source/dts-platform-webapp/src/api/modelSpecApi.ts:330`、`:336`、`:343` |
| C17 | 发布意图使用候选版本和幂等键；不能当作立即发布成功 | `source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/ModelPublicationIntentResource.java:52`；QUALITY_RUNNING 新请求返回 202，其他结果按当前协议返回 |
| C18 | 资产 PUT 更新多字段，不能直接当局部 PATCH；其服务仍为治理数据 owner | `source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/catalog/CatalogDatasetResource.java:601`；依次写 name/type/classification/owner/domain/物理定位/tags 等 |

先前样例：模型 `18147b30-e512-490b-a996-3ff292891a4b`（项目任务快照明细）、r2、目标 `biadmin.public.prjdemo_dwd_project_task_snapshot`；曾观察到已发布且物理资产存在，但分析端缺少平台数据源关联，出现 `ANALYTICS_SEMANTIC_PUBLISH_HTTP_400`。这是 F2 的回归来源，不是本轮重测结果，也不是新功能通过证据。

## 单一事实源与字段归属

| 对象 | 权威 owner | 页面规则 |
|---|---|---|
| 模型定义、字段、逻辑业务归属 | ModelSpec 与其草稿/修订 | 模型工作台编辑；目录中的“编辑模型”携带 modelSpecId 返回同一模型 |
| 物化与发布候选、质量快照 | 既有实现/候选/发布控制面 | 显示当前版本证据；不另存一份 UI 进度 |
| 资产负责人、资产说明、标签、治理配置 | 既有目录及治理服务 | 模型页复用治理表单片段和保存入口；目录再次打开能看到同一值 |
| 平台数据源连接 | 平台数据源控制面 | 分析端只维护关联及必要运行投影，凭据不经浏览器传递，不要求重复录入 |
| 分析语义与查询投影 | 既有 serving 与分析服务 | 单独显示准备结果；关联连接不自动授予用户数据访问权限 |

模型业务定义与资产治理说明不能隐式双向覆盖。显示名、业务域、数仓分层等现有重叠字段，T09 必须冻结“初次投影来源、后续可编辑 owner、再次发布是否覆盖”逐字段表；未冻结前 T12 不可实施保存。模型输出的 source/schema/table 身份不可在简化治理表单修改。

## 业务状态—界面—允许动作矩阵（共享测试用例 S01–S10）

| ID | 后端事实/前置条件 | 当前页面反馈与主操作 | 命令结果/失败恢复 |
|---|---|---|---|
| S01 | 当前候选无有效物化证据 | 待物化；“开始物化” | 走现有 build-intents；受理显示进行中，完成后重读，不把 202 当完成 |
| S02 | 物化或检查任务实际执行中 | 显示对应步骤、运行记录；不开放冲突操作 | 刷新/重开可恢复；只读刷新不启动新任务 |
| S03 | 物化成功，目标资产尚未登记 | 资产待登记或登记失败，明确失败步骤 | 由既有候选资产登记流程恢复；不得误报 ASSET_MISMATCH 或调用重新物化 |
| S04 | 资产已登记，阻断性质量政策要求规则但无有效绑定 | 质量检查“待配置”；“配置质量规则” | 就地维护当前输出资产的规则；其他资产规则只借用内容新建，规则保存/发布不等于质量通过 |
| S05 | 绑定有效，规则未执行/失败/证据过期 | 待检查、检查未通过或结果已过期；“执行检查”或查看失败记录 | 既有质量命令；真实失败结果可追查，修复后仅重跑必要检查 |
| S06 | 当前候选质量已通过且服务端允许发布 | “发布模型”，保留模型/候选版本 | 既有发布命令核验当前状态；并发变更拒绝旧请求并提示重新加载 |
| S07 | 旧入口发布意图受理记录（只读兼容） | 显示真实候选阶段，不生成新向导主动作 | 新向导 W3 用 quality、W4 用 publish；publish-intents 不接入下一步按钮；历史重放沿用原协议 |
| S08 | 模型已发布，目录存在，分析未就绪/失败 | 已发布、资产已登记；分析准备中/失败，给具体原因 | “重试分析准备”仅恢复 serving；已登记资产仍可维护 |
| S09 | 编辑修订与上次发布修订不同 | 分别标明当前草稿与已发布版本的结果 | 不将旧物化/质量成功当作新版本的发布凭证 |
| S10 | 读取失败、未启用分析、无权操作 | 读取失败/未启用/无权分别显示；隐藏或禁用对应动作并解释 | 不把未知当成功或未登记；服务端再次鉴权，404 不泄露其他租户对象 |

S06 与 S07 复用既有发布控制面：T09 必须核对“检查准备”和“确认发布”的命令边界，第三步不得在用户未确认第四步发布时自动发布。如果现有 publish-intents 将二者耦合，先在同一控制面固定向后兼容的阶段命令/停止边界，再开放向导；禁止仅在前端拆按钮而后台仍自动推进到底。不可用依赖/无规则等等待原因在只读聚合层表达；是否需要底层枚举扩展必须先证明现有结构不足。

## 统一只读视图契约（拟新增，T09 冻结后方可编码）

建议 `GET /api/modeling/model-specs/{id}/delivery-status`，列表批量版 `GET /api/modeling/model-specs/delivery-status?modelSpecIds=<逗号分隔UUID>`。保持现有 ApiResponse 包装，用户/租户来自服务端。已有 serving-sync API 保留兼容；不增加新的可写总进度台账。

```ts
type StepState = 'NOT_STARTED' | 'WAITING_INPUT' | 'RUNNING' | 'SUCCEEDED'
  | 'FAILED' | 'NOT_APPLICABLE' | 'UNKNOWN';
type StepKey = 'materialization' | 'quality' | 'publication' | 'catalog' | 'analysis';
interface DeliveryStatus {
  modelSpecId: string;
  modelRevision: number;
  publishedRevision: number | null;
  candidateId: string | null;
  candidateVersion: number | null;
  environment: string | null;
  implementationRevision: number | null;
  implementationChecksum: string | null;
  observedAt: string; // ISO-8601，只表示读取时间
  steps: Record<StepKey, {
    state: StepState;
    evidenceRevision: number | null;
    runId: string | null;
    matchesCurrentTarget: boolean; // 服务端核验环境/实现/输出及当前证据要求
    reasonCode: string | null;
    message: string;
    resourceId: string | null;
    updatedAt: string | null;
  }>;
  actions: Array<{
    code: string; // T09 冻结为枚举，映射既有业务命令
    enabled: boolean;
    reasonCode: string | null;
    targetId: string | null;
    expectedVersion: number | null;
  }>;
}
```

同一模型修订下可以有不同实现、环境和运行；不能只比较 modelRevision 判定证据有效。质量证据还必须核对输出身份、规则版本、政策要求及已有失效规则。禁止新增本地“通过缓存”替代现有证据判断。

数据流：现有列表/发布面板 → 批量/单项只读聚合 → 模型/候选/目录/serving 权威记录 → 同一 DTO。禁止 GET 修复关联或写库；前端只负责文案和布局，不重新根据字符串拼 allowedActions。命令端使用同一业务判定或等价的共享领域规则重新校验，按钮可用不能代替后端校验。

`resourceId` 等均为授权范围内标识；质量可覆盖多个输出时必须按资产展开，不能把一个资产的通过当全部通过。DTO 中单 resourceId 的适用边界、多输出明细、批量部分失败和并发读取版本一致性，由 T09 冻结补充结构和样例；因此该提案暂不标 READY。

## 既有写入口与待冻结缺口

| 契约 | 已确认内容 | T09 必须补齐的内容 |
|---|---|---|
| 物化 | POST `.../{id}/build-intents`；planId:string、environment:string；If-Match、Idempotency-Key | 复用 F1 固定的现有类型、错误码与重复执行约定 |
| 发布意图 | POST `.../{id}/publish-intents`；candidateId:UUID、reason:string；候选 If-Match、Idempotency-Key | QUALITY_RUNNING 的 202/200、后续发布命令与 actions 完整映射；不能把提交和发布混为一个成功提示 |
| 分析重试 | POST `.../{id}/serving-sync/retry`；If-Match 为 `"model-serving-sync:{modelSpecId}:{version}"` | 固定错误分类、最大次数、旧版本冲突、处理中重试行为 |
| 资产治理 | GET/PUT `/api/catalog/datasets/{id}`，当前 PUT 接收 CatalogDataset | 局部更新安全契约、版本保护及字段 owner；不能读旧全对象再覆盖他人变更 |
| 规则绑定和执行 | 复用治理规则/绑定/运行控制面；候选与 dataset 身份服务端解析 | 精确路由、请求/响应 DTO、规则版本与资产关联校验、版本冲突及运行幂等；未核实不编造现有 API |
| 分析接入 | POST 分析端 `/api/database/ensure-from-platform/{platformDataSourceId}`（现有用户会话入口）；POST `/api/semantic/publish`（现有服务入口） | 提取共享接入服务，在既有受信任发布边界接入；不能扩大整个 database API 的服务权限 |

## 页面操作与跨页约定

- 工作台 canonical route：`/data-modeling/dimensions/workbench?modelSpecId=<UUID>`。不新增菜单。旧 `/modeling/models/:id` 兼容重定向必须保留模型身份。
- 原单一发布面板方案更新为下文“四步建模向导”：一个工作区内四个独立步骤页，每页只负责当前子过程。原弹窗/面板中的组件按职责迁入第三、四步，移除重复动作入口。
- “配置质量规则”：当前步骤内维护当前资产规则，固定服务端解析出的模型输出资产。复杂规则编辑复用现有规则页；携带 modelSpecId、candidateId、datasetId、assetKey 和相对 returnTo。查询参数仅提供上下文，后端仍校验归属，不把参数当权限。
- “维护资产信息”：复用治理组件，以同一资产 ID 保存；未登记时显示原因与可用修复动作。不得用 semantic-model ID 冒充物理 dataset ID。
- 向导内部步骤页按下文导航矩阵跳转；离开向导仅用于复杂规则编辑或高级治理。返回后恢复原模型、步骤和候选，重新读取最新证据；未保存内容按既有离开保护处理。returnTo 限站内白名单，刷新/返回均可复现上下文。
- 空态解释缺什么；加载态不擦除已确认结果；错误态保留输入并给当前步骤修复动作；成功态必须来自命令结果加权威重读，不能靠本地把标签变绿。
- Chrome 95、1366×768、键盘焦点和面板滚动均为验收内容，底部操作不能被遮挡。

## 分析准备与存量恢复

在既有发布后的分析交付策略内自动补齐平台源关联，不依赖用户访问大屏页面。严格按平台源 ID 关联，禁止同名猜测；复用凭据与数据源政策。关联存在后确认目标表/字段、分析语义及查询投影，不把仅生成元数据占位记录当真实可分析。

准备在服务端可恢复流程执行，返回受理结果后展示进度；依赖未就绪等可恢复失败有界退避；鉴权/非法目标等错误明确人工修复，不对全部 400 无限重试。重复/并发重试不得产生重复连接、语义模型或查询投影；新修订使旧任务结果失效时不得回写新修订成功。

T13 在实施前审计既有唯一性与租户边界，必要时通过前向迁移补约束；必须先处理存量重复关系，不能先加唯一约束让升级失败。仅恢复选定模型的失败交付，不做全库注册、历史批量重跑或重新物化。未启用分析的部署明确显示不适用，不阻断模型发布；具体配置来源由 T09 固定。

## 交付约束

本次仅补规划。后续代码修改先在 `/opt/prod/s10/v2.2.3` commit/push，再在 `/opt/prod/s10/deploy` pull --ff-only、核对 SHA 后测试/编译/构建。无容器补丁、hotfix 镜像、开发挂载或离线临时下载。T14 记录源码、测试、构建、部署、页面验收各自状态。

## 四步建模向导（本次界面方案修订，优先于原“一个大面板”描述）

用户明确要求“一个子过程一个页面”。采用一个模型工作区、四个独立可寻址步骤页，共用模型身份/版本栏、步骤导航和草稿保护；不为四步增加四个侧栏菜单，也不要求跨数据建模、治理、BI 菜单来回找入口。

建议子页路由沿用 `/data-modeling/dimensions/workbench`，以 `modelSpecId=<UUID>&step=definition|implementation|verification|delivery` 定位步骤；这些是独立内容视图，不是把原长页面滚动到锚点。创建尚无 ID 时只开放 definition，首次保存取得 ID 后 replace URL，不能因刷新重复新建。候选上下文使用 candidateId，由后端校验归属。

| 步骤/页面 | 只解决的业务问题 | 主要内容 | 主操作及完成条件 |
|---|---|---|---|
| W1 模型设计 / definition | 模型要表达什么 | 名称、业务定义、类型、粒度、字段/标准等设计配置 | “保存并继续”：保存、完成本阶段必要校验后进入 W2；不完整草稿可暂存但不能推进 |
| W2 实现配置 / implementation | 如何生成目标数据 | 来源/SQL或可视化实现、目标、加载方式及必要依赖 | “提交实现并继续”：校验并生成确定实现版本后进入 W3；同版本已提交且无改动时仅“下一步”，不重复生成实现 |
| W3 构建与检查 / verification | 当前实现是否可交付 | 目标环境、物化任务、工程检测、当前输出规则绑定和治理质量结果 | 主操作随后端事实为“开始物化”“配置质量规则”“执行检查”之一；全部必需证据有效后为“下一步”，不自动发布 |
| W4 发布与交付 / delivery | 是否正式发布及交付结果 | 待发布版本/目标摘要、发布阻断、确认发布；发布后目录与分析结果 | 未发布且满足条件：“确认发布”；发布处理中仅显示进度；发布后处理当前失败交付步骤或“返回模型列表” |

顶部步骤条只用于导航和状态，不再重复渲染一排可执行流程卡片。取消截图中的“保存草稿/校验/提交实现/发布与物化”四动作并列及顶部重复“重新物化”按钮。

每页一个底部操作区：主按钮最多一个；“上一步”和编辑页“暂存草稿”为次按钮；离开入口固定；低频重建/取消任务/历史记录收进有明确名称的更多操作或详情。字段即时校验就近显示，正式阶段校验随主操作执行；需要主动重新校验时作为次操作，不与推进按钮争主次。W3/W4 内嵌治理表单的保存属于局部提交，编辑期间页面推进按钮禁用并说明需先保存/取消。

用户点击步骤条只导航，不隐式保存、提交实现、构建或发布。点击“保存并继续/提交实现并继续”才按明确文案执行对应命令；部分成功时保留已保存版本，显示失败环节，重入复用成功结果。既有并发/幂等约束不能由页面连发多个请求替代。

## 生命周期与页面位置分离

源码事实：`ModelSpecContract.java:371` 的 ModelStatus 有 DRAFT、DESIGNING、VALIDATING、READY_TO_PUBLISH、PUBLISHED、ARCHIVED；`ModelLifecycleContract.java:78` 的候选 DeliveryStatus 另含 BUILDING、BUILT、QUALITY_RUNNING、QUALITY_PASSED、CANCELLED、STALE 等执行状态。`ModelPublicationQualityReconciler.java:114`–`:145` 展示质量结果/治理证据协调逻辑。枚举存在不证明所有迁移路径当前都可达，T09 必须核对实际命令与 guard。

四个步骤页是用户工作的阶段，不能直接等同于四个数据库状态。生命周期对用户的目标展示分组如下，保留既有 owner/历史枚举，不仅为配合向导重建台账：

| 生命周期展示 | 含义及进入条件 | 编辑/推进规则 |
|---|---|---|
| 草稿/设计中 | 对应 DRAFT/DESIGNING 的实际业务状态 | W1/W2 编辑；离开暂存不构成校验、实现提交或发布 |
| 验证中 | 当前版本已进入必要检查；不以页面位于 W3 推断 | 当前执行快照不可原位修改；无规则显示“待配置”，不伪装运行中 |
| 待发布 | 当前实现/环境的必需构建与质量证据均有效、现有门禁允许 | W4 可确认发布；返回上游修改后重新评估资格 |
| 已发布 | 服务端发布提交成功，有当前发布引用 | W4 展示交付结果；编辑通过既有草稿/新修订流程，原发布版本保持有效，直到新发布按既有替换规则提交 |
| 已归档 | 现有归档命令成功 | 各步可按权限查历史，普通保存/构建/发布禁用；本轮不新增恢复归档流程 |

“待发布”是目标展示语义，T09 明确它与 READY_TO_PUBLISH、候选 QUALITY_PASSED/APPROVED 和当前部署政策的实际映射；不能因为任意一个枚举同名就放行。若存在 REVIEW_PENDING/REJECTED/PARTIAL/ROLLED_BACK 等历史或适用分支，在 W4 展示真实原因/结果，沿用既有命令处理，不新增审批角色或把这些分支默认为成功。

## 导航权限与状态迁移矩阵（N01–N08）

| 场景 | 是否能跳页面 | 能否编辑/执行 | 页面与后端的一致要求 |
|---|---|---|---|
| N01 首次建模 | 默认 W1→W2→W3→W4；只在本阶段条件满足后推进 | 后续步骤无有效前置输入时只读解释或锁定，给出回到缺失步骤入口 | URL 直接访问不绕过校验；后端拒绝缺前置条件的命令 |
| N02 回看已有步骤 | 可直接点击任何已具备可查看内容的步骤 | 只读导航不写库、不改变生命周期 | 步骤可查看不等于其操作可执行；未来步骤可看阻断摘要，但不开放写动作 |
| N03 返回上游修改 | 无冲突执行时可以回到 W1/W2 | 修改通过同一草稿/修订 owner；保存后重算后续资格 | 用户看到哪些结果需重做及原因；历史运行留存，不误用旧成功 |
| N04 向后跨步 | 已有当前版本有效前置证据时可直接进入 W3/W4，无需重复点前两步 | 构建/发布命令仍核验所有依赖，而非检查“曾访问过某页” | 不能通过点击下一步补造阶段完成状态 |
| N05 执行中/发布中 | 可离开、回看、刷新；页面不应成为运行持有者 | 锁定本次候选的输入和冲突动作；新草稿是否允许由既有能力决定，不承诺原位修改 | 再进页面绑定原任务；取消/重试按后端 allowedActions，不把离页当取消 |
| N06 已发布模型再编辑 | 可直接打开 W1/W2 的新草稿/修订上下文 | 既有发布不可原位覆写；新候选重新取有效证据 | 同时标明正在编辑版本和当前发布版本；不静默撤销当前发布 |
| N07 深链/浏览器前进后退/外部返回 | 保留 modelSpecId、step、候选上下文 | 无效/已删除/不属于该模型的上下文不自动切换为其他模型 | 提示失效并提供安全入口；明确用户 step 不被轮询自动强制跳走，缺省入口才选择推荐步骤 |
| N08 未保存修改 | 可选择保存草稿后离开、放弃或留在当前页 | 暂存失败留在当前页并保留输入；不把暂存当实现提交 | 刷新/关闭采用浏览器和既有草稿保护能力；仅持久化成功的内容保证跨会话恢复 |

后端只读聚合视图新增拟定结构（T09 完整冻结）：`wizard.recommendedStep: WizardStep`；`wizard.pages: Array<{step:WizardStep, canView:boolean, canEdit:boolean, reasonCode:string|null}>`；`actions` 增加 `step:WizardStep`、`primary:boolean`，其中 `WizardStep='definition'|'implementation'|'verification'|'delivery'`。每步主动作最多一个；页面完成标识由权威阶段/证据派生，不由前端访问历史生成。所有写命令仍重新校验相同领域规则；不让“只读 canEdit=false”成为唯一安全保护。

## 上游变更与结果有效性（I01–I06）

| 变更 | 下游处理 | 用户反馈与最小恢复 |
|---|---|---|
| I01 改字段/粒度/SQL/来源/影响执行的实现 | 保存形成新快照；依赖旧快照的提交/构建/质量/发布资格重新评估 | 明确需重新提交实现、构建或检查；旧表存在不代表新版本物化成功 |
| I02 改环境、目标或加载策略 | 旧环境/目标证据不用于新目标 | 回 W2/W3 处理，不自动删旧表或改旧发布引用 |
| I03 只改规则绑定/规则版本/治理质量政策 | 重做受影响的治理检查与发布门禁；工程构建是否可复用由证据依赖判定 | 不默认重新物化；任何执行型新规则按实际能力处理 |
| I04 只改资产负责人/资产说明等治理字段 | 按字段 owner 保留有效构建结果 | 只刷新治理结果；若密级/授权政策变化，重新校验相关访问/发布条件，不归为普通元数据 |
| I05 只修复分析关联/连接或重试分析 | 只重跑受影响的分析准备 | 发布与物化记录保持；成功仍验证目标实际可用 |
| I06 候选取消/失效或旧任务晚到 | 取消候选不恢复可发布资格；物理构建历史单独保留 | 截图场景应显示“目标表曾构建成功；当前候选已取消”，由后端给出创建/选择有效候选的下一步，而非泛化为已完成 |

以上是目标失效语义；T09 明确实际 checksum/revision、环境、目标、规则版本、政策与候选依赖，形成共享 I01–I06 fixtures。不能前端只清空标签而后端仍接受旧证据，也不能为了简单对所有治理字段修改一律要求重物化。

截图中“实现投影尚未生成”与“历史构建已成功”也应分开解释：投影视图不可用不直接等于实现不存在；W2 按真实创作能力给出可编辑视图、代码只读视图或明确限制。具体能否转换/编辑由既有能力接口决定，本轮不借向导扩张历史模型编辑能力。

## 页面简洁与右上角帮助（H01–H04）

**用户约束**：W1–W4 不常驻展示教程、规则解释、实现原理或重复的操作说明。页面保留字段名称、输入控件、步骤/实际状态和必要动作；说明性内容统一进入右上角既有“?”帮助，不以大量 tooltip、提示框、字段旁问号或折叠说明块替代。

| ID | 内容 | 产品落点 | 验收要求 |
|---|---|---|---|
| H01 | 字段含义、适用范围、选择后行为、不适用情况、操作教程 | 右上角“?”对应步骤的帮助主题 | 页面无常驻长说明，帮助包含完整规则和用法 |
| H02 | 必填、当前输入错误、权限/版本冲突、当前阻断、执行结果、必要操作确认 | 字段附近或当前任务状态区，简短且可操作 | 不要求用户读帮助才知道这次为什么不能继续；不能因精简删掉真实错误与后果 |
| H03 | W1–W4 的帮助上下文 | 同一个全局 HelpCenter 按 pathname + step 定位章节；无效 step 回退模型帮助 | 打开帮助不跳离模型、不执行保存；关闭恢复焦点，未保存输入和步骤不丢失 |
| H04 | 完整手册/搜索/离线 | 复用既有帮助中心及本地主题资源，随正式前端包交付 | 无外网依赖；帮助规则与能力/门禁契约一致，不另写一套推荐值或阶段条件 |

### 具体迁移样例：时间字段

| 位置 | 调整前 | 调整后 |
|---|---|---|
| 模型字段区 | “仅选择有业务时间含义的字段；勾选会设置字段作用为‘时间’，不会转换字段类型或数据。普通明细无需选择。” | 移除该常驻说明，保留“时间字段”、选项及当前真实校验结果 |
| 右上角帮助 → 对应步骤 → 时间字段 | 当前未确认有此完整说明 | 增加下列说明，操作时按当前步骤定位帮助 |

帮助正文拟定：

- 选择表示业务发生时间的字段，例如订单时间或记账日期。
- 选择后，字段作用会设为“时间”；字段的数据类型和已有数据不会改变。
- 普通明细模型可以不选择。需要时间语义的场景，以当前模型适用的校验规则为准。

页面无可选字段时保留必要的短空态与“管理字段”修复入口（是否可跳转遵循 N01–N08），不把空态写成教程。普通明细不得因帮助内容迁移重新变成时间必填。

### 已确认复用点与实施责任

- 样例原文：`source/dts-platform-webapp/src/pages/data-modeling/prototype/ModelImplementationBindingFields.tsx:438`；相邻 ValidationMessage 是实际校验结果，不能一起移除。
- 现有右上角入口：`source/dts-platform-webapp/src/features/help-center/HelpCenter.tsx` 的 `HelpCenter()` 使用 `CircleHelp`、`aria-label="打开帮助"` 和 Sheet；现有上下文只按 pathname 与帮助页 topic 解析，四步同一路径不能直接沿用而忽略 step。
- 主题与手册：`source/dts-platform-webapp/src/features/help-center/helpTopics.ts` 的 HELP_TOPICS/resolveHelpTopic、`HelpCenterPage.tsx`。扩展现有上下文解析和主题内容，不新增帮助后台、业务菜单或权限体系。
- T09 冻结四步→主题/章节映射，以及说明迁移清单和 H01–H04 fixtures；主题 ID、深链和旧路径兼容在开工前确定。
- T10 同时完成页面说明迁出和帮助内容迁入；T11–T13 的规则/治理/分析说明遵循相同边界；T14 执行 IT-18 验收。只删除页面文案而未交付可访问的帮助，不算完成。

## T09 本次核验结论与剩余范围

详见 [T09 契约核验记录](T09-contract-freeze.md) 和 [共享预期用例](delivery-contract-fixtures.json)。用例为 SPEC_ONLY，尚未被应用测试执行。

- W3 使用既有候选 `/quality`、`/governance-quality/runs`，W4 明确使用 `/publish`；无需为向导新建发布控制面。部分质量启动失败须先重读已发生的状态，再选择恢复命令。
- 修正此前“任意勾选已发布规则再绑定”的假设：现有规则与资产一对一，W3 管理当前输出资产规则；跨资产借用仅创建新规则，不移动原绑定或增加候选私有绑定。
- W4 常用资产维护范围固定为负责人 owner、资产说明 description；高级治理仍复用原页。局部保存/并发保护未冻结，禁止直接用简化表单调用现有多字段 PUT。
- 帮助四步映射已固定为 model-definition / model-implementation / model-verification / model-delivery，旧入口兼容，未知 step 回退 model-center。
- 资产/规则并发、多输出 DTO、分析关联唯一性与当前浏览器/运行基线仍为 GAP；不据此将实施任务标 READY。
