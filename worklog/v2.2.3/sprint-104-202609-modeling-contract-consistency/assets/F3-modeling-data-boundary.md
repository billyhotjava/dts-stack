# F3：全层建模与数据模块边界契约

**日期**：2026-09-07  
**状态**：DRAFT（产品边界已由用户明确；新增接口细节、迁移和运行基线由 T15 冻结，尚未实施）  
**适用任务**：F3/T15–T20；[Feature 与任务入口](../features/F3-全层建模与数据模块边界简化/README.md)。

## 1. 决策与替代范围

用户要求：ODS 到 DWD、DWS、ADS 可以一起设计；建模操作简化，不把所有约束集中在模型页；模型物化完成即完成建模，资产形成及后续治理在“数据”模块完成。

本契约替代 F2 中“模型向导必须经过质量配置、资产维护、分析准备才结束”的页面和完成条件。F2 已有模型版本、候选、目录 CAS、质量规则、分析重试和帮助能力继续复用；已有源码与验收记录保留。下列目标是待实施行为，不代表当前系统已经支持。

| 功能 | 界面归属与结束条件 | 必须保留的边界 |
|---|---|---|
| ODS/DWD/DWS/ADS 逻辑定义、字段与跨层映射 | 建模工作台；允许无接入任务、无物理来源时保存设计 | 用模型 ID/修订描述设计依赖，不伪造可用物理来源 |
| 执行配置与物化 | 建模工作台；当前版本、当前目标的真实物化成功即完成本次建模 | 空表建成与数据加工成功分别记证；旧成功不冒充新版本 |
| API、数据库、离线文件接入与写数 | 既有数据接入页面；绑定模型目标，独立显示运行结果 | 连接/字段映射只在接入执行需要时必填；不借绑定重建或覆盖模型 |
| 数据登记、资产负责人/说明、质量治理、对外发布 | “数据”模块既有目录/资产详情及治理页面 | 目录、规则和发布仍各自校验；不能因建模完成自动宣称资产可用 |
| 分析准备与失败恢复 | 从数据资产上下文进入既有分析交付能力 | 失败不撤销模型物化结果，不重复构建或创建连接 |

物化可以保留必要的技术元数据登记、表身份与血缘投影；它们是系统记录，不要求用户在模型页填写完整资产表单，也不等于治理、数据质量或分析准备完成。需要治理政策才能对外开放的数据，仍在数据模块满足政策后开放。

## 2. 最小用户流程与分阶段校验

```text
建模：设计（可先建全链草稿） → 实现配置 → 物化 → 建模完成
                                                     └─ 去数据管理（可选跳转）
数据：接入/写数、数据与资产登记、质量治理、发布/分析准备
```

上述数据工作按实际任务办理，不增加一套必须逐页走完的新向导。首批结构物化仅覆盖现有 PostgreSQL 目标上的普通表；视图、其他引擎及全新加载算法不在本 Feature 范围。已支持的原有物化方式保持兼容。

| 阶段 | 立即检查 | 延后到何处检查 |
|---|---|---|
| 暂存设计 | 身份/权限、字段格式、重复名称、已填写引用是否合法；支持未完成草稿 | 目标连接、接入配置、完整实现、数据质量、资产运营信息均不阻断暂存 |
| 完成设计并配置实现 | 必要字段/类型、模型层级与映射、版本引用、依赖循环 | 已定义而未落地的 ODS 不要求伪造 sourceBinding；连接在物化前确定 |
| 结构物化 | 当前模型/实现快照、目标连接与权限、字段类型/键/分区可执行、名称/既有表冲突 | 不执行源数据查询，不要求数据行或业务质量规则已通过 |
| 数据加工运行 | 来源已绑定、物理输入存在、实际加载策略和执行条件满足 | 运行结果不替代资产治理；来源缺失只阻断该运行 |
| 资产治理/发布 | 当前资产身份、负责人等适用元数据、实际质量/密级/发布政策 | 失败只限制对应资产操作，不将已成功建模改为失败 |

身份、租户隔离、敏感数据访问、必要密级与写入权限始终在实际读写命令校验。声明的类型/键/分区不得静默丢弃。不以恒通过规则、前端隐藏错误或改标签实现“简化”。F1 的字段标准与执行规则由 T15 逐项归入以上阶段，不能一刀切取消，也不能全部保留为草稿必填。

## 3. 契约链与扩展草案

下表区分现有接口与拟扩展内容。后者在 T15 完成 JSON 样例、错误码、数据库约束及 fixtures 前不得视为 FROZEN，也不得直接用于开工。

| 编号 | 既有 owner/接口 | F3 输入、输出及边界 |
|---|---|---|
| K31 | ModelSpecContract、ModelSpecApplicationService、SnapshotCodec、三端 Schema；`POST /api/modeling/model-specs`、`PUT .../{id}/definition` 与既有草稿入口 | 拟增 `modelType: SOURCE`、固定 `layer: ODS` 表示贴源模型，不把 ODS 塞入 FACT；复用 `fields`、模型身份、ETag/修订。源连接与接入任务可空；ODS 不套用事实/维度专有必填。具体枚举及存储 CHECK 的兼容方案由 T15 冻结 |
| K32 | 既有 `dependsOn: ModelRevisionRef[]`、字段映射、依赖校验与编译投影 | 下游可引用 `{modelSpecId: UUID, revision: integer}` 的 ODS 设计版本；保存不要求上游已物化/发布。复用现有字段映射表达式；T15 固定逻辑输入到运行物理来源的解析协议。`sourceRefs` 继续表示真实已登记来源，不假造 CONFIRMED/AVAILABLE |
| K33 | `POST /api/modeling/model-specs/{id}/build-intents`；ModelBuildIntentResource、现有实现/候选/运行 owner | 请求保留 `{planId:string,environment:string}`、If-Match、Idempotency-Key；拟增可选 `buildMode: SCHEMA_ONLY | DATA_BUILD`。旧请求保持当前执行语义；新页面明确选择“创建表结构”或原有“生成数据”能力。SCHEMA_ONLY 只根据已声明输出结构生成 DDL，不查询上游、写业务数据或自动发布资产 |
| K34 | `GET .../{id}/delivery-status`、ModelDeliveryStatusResource 与既有只读聚合 | 拟增 `modelingResult:{state:NOT_MATERIALIZED|RUNNING|SUCCEEDED|FAILED|STALE,modelRevision:number,modelChecksum:string,implementationRevision:number|null,environment:string,runId:string|null,buildMode:string|null,outputs:array}`。outputs 必须沿用真实目标定位及结构核验证据；完整 shape 由 T15 对齐当前 DTO。结果由运行/当前快照派生，不新增持久化总进度状态机，不把 SUCCEEDED 写成 PUBLISHED |
| K35 | IngestionTaskProxyResource、IngestionTaskService、TargetTableProvisioner、API landing；既有接入创建/更新/执行命令 | 接入目标绑定需携带模型 ID/修订和精确目标身份；服务解析源/目标字段、差异并验证。新增绑定字段的嵌入位置、类型、版本并发和各接入适配器由 T15 冻结。没有物理表时提示先物化；模型型目标禁止继承文件接入的隐式 DROP/重建行为 |
| K36 | CatalogAssetType/CatalogAssetKey、catalog_dataset、目录/质量/发布/serving owner | 数据模块使用既有 dataset 身份；`PATCH /api/catalog/datasets/{id}/governance-summary` 继续使用 owner/description、If-Match 和目录 CAS。接手数据/治理不新建同表第二资产；自动技术投影和用户维护字段分别由原 owner 管理 |

### 当前完成证据

“建模完成”必须同时满足：本次物化运行成功；结果属于当前模型与实现版本、环境和目标；实际表结构与声明一致；多输出中本次请求要求的全部输出均成功。零行不构成结构物化失败；部分成功、取消、失败或版本过期均不得显示完成。DATA_BUILD 成功沿用其实际执行与结果要求，不能拿 SCHEMA_ONLY 的证据替代。

模型修订或影响物化的实现改变后，仅该版本的完成资格重新评估；历史成功保留。修改资产负责人、规则或分析连接不触发重新物化。既有 PUBLISHED 模型的编辑、新草稿、历史发布和数据模块发布继续遵守原有版本规则；新增 SOURCE 修订的可引用状态与物化后编辑语义由 T15 明确。

### 明确的错误与恢复

- 非法字段/依赖循环/缺失版本：沿用结构化 FieldIssue，保存用户输入；具体 code 由 T15 固定，不默默替换为最新版本。
- 缺少目标权限/连接：阻断物化，保留草稿；缺少接入绑定只阻断 DATA_BUILD/接入运行。
- 已有表同名而身份或结构不符：返回结构差异与冲突，禁止自动 DROP、覆盖或把已有表直接记作本次成功。
- 超时/重复点击：按原幂等协议返回已有运行或冲突；必须重新读取运行，不盲目重建。
- 目录/分析读取失败：建模页仍根据物化证据显示真实结果；数据入口显示失败并可独立恢复，不用“未登记”替代读取错误。

## 4. UI 落点

复用 `/data-modeling/dimensions/workbench?modelSpecId={id}`，保留三个工作步骤的既有 `definition`、`implementation`、`verification` 深链，展示为“模型设计 / 实现配置 / 物化”。每个内容页只显示当前任务需要的字段，一个主动作；ODS 不显示事实形态、维度历史和指标配置。规则解释进入右上角已有帮助，当前错误保留就地提示。

成功页只保留模型/版本、目标表、实际结果及“返回模型列表”，另给次操作“去数据管理”；不出现负责人、规则配置、资产发布和分析连接表单。不因用户没有继续到数据模块而将模型标记未完成。

“数据”入口复用 `/catalog/search?view=table` 及 AssetLedgerView/既有详情，治理和分析沿用原有页面。精确聚焦参数/详情路由由 K36 返回的身份与 detailRoute 解析，T15 冻结，不拼造一个尚不存在的 datasetId 路由。尚未登记时以物化目标身份进入既有数据登记动作；无记录与读取失败分开。旧 `step=delivery` 要能回看历史交付，并明确导航到对应数据操作，不能强制执行发布，也不能丢失 modelSpecId/候选/环境。

| 四态 | 建模页面 | 数据页面 |
|---|---|---|
| 空 | 可新建 ODS 和下游草稿；尚未物化 | 未登记/暂无数据，提供当前适用动作 |
| 加载 | 当前步骤/运行加载，禁止重复提交，保留输入 | 加载精确资产/运行，不以空表单覆盖 |
| 错误 | 定位字段、结构差异或本次运行；重试遵循 allowedActions | 接入/治理/分析分别报告，保留用户修改 |
| 成功 | 当前版本物化完成，可结束建模 | 显示各环节自己的真实结果，不反写模型完成状态 |

## 5. Context Ledger C19–C26

复核基线：开发目录 HEAD `f0d081b90`，2026-09-07；本次只读源码与既有规划，无运行证明。此前审查已完成链路定位，后续只更新发生变化的条目。

| 编号 | 已确认事实 | 仓库相对路径与扩展点 |
|---|---|---|
| C19 | 创建贴源表固定禁用 | `source/dts-platform-webapp/src/pages/data-modeling/prototype/ModelWorkbenchCreateMenu.tsx:13` |
| C20 | ODS 被排除在四类目标之外；前端同样固定类型/层级 | `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/modeling/ModelSpecContract.java:1762`；`source/dts-platform-webapp/src/features/modeling/contracts/modelSpecV2Contract.ts:72` |
| C21 | 已选择的物理来源要求确认、版本匹配且可解析 | `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/modeling/ModelSpecSourceValidationAdapter.java:230`；保留 sourceRefs 真实语义，扩展逻辑依赖 |
| C22 | 普通任务保存可同步 ODS 映射；API 登记另要求成功落地证据 | `source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/IngestionTaskProxyResource.java:179`；`service/etl/OdsTableMappingSyncService.java:244`（同 Java 包根） |
| C23 | 接入执行触发建表；API landing 创建目标；文件 landing 存在重建分支 | `source/dts-ingestion/src/main/java/com/yuzhi/dts/ingestion/service/IngestionTaskService.java:1204`；`service/etl/api/ApiRawLandingService.java:64`、`service/etl/TargetTableProvisioner.java:142`（同 Java 包根） |
| C24 | F2 已有只读交付 DTO、资产局部更新 CAS、精确分析恢复协议 | [T10 DTO](T10-delivery-status-contract.md)、[T12 CAS](T12-asset-governance-contract.md)、[T13 注册](T13-analysis-registration-contract.md)；复用不重写 |
| C25 | 原发布/质量/目录投影存在执行耦合，不能仅删除 W4 或改完成标签 | [T09 冻结 A/B](T09-contract-freeze.md)；T15 列出命令副作用，T17 实现物化结束边界 |
| C26 | 数据资产台账已有唯一承载页 | `source/dts-platform-webapp/src/pages/catalog/LegacyAssetLedgerRedirect.tsx:4` 收敛 `/catalog/search?view=table`；`DataSearchPage.tsx:478` 复用 AssetLedgerView |

## 6. 开工前必须关闭的技术问题

1. **模型契约**：SOURCE 枚举、现有数据库 CHECK/修订快照、导入导出格式和逻辑字段引用如何向后兼容；T15 固定精确 schema 和前向迁移，不允许创建第二套 ODS 模型台账。
2. **物化与发布副作用**：现有 build/publish/quality/目录投影哪些可复用、哪些必须分开；T15 列出准确 service/guard 与事件，T17 实施。不得将质量失败改成通过，也不得自动授予资产访问权限。
3. **接入对已有表的行为**：API 的 raw_record 及技术列、文件重建策略与声明结构如何匹配；T15 固定支持范围和差异拒绝行为，T19 落地。仅支持现有适配器可表达的映射，不新增通用 API 展平引擎。
4. **资产接手身份**：无目录记录时如何复用现有登记命令，已有记录如何精确定位；T15 固定 URI/DTO/唯一性，T19 验证幂等，不以同名猜测匹配。

这些是实现前待核实项，不重新询问已经明确的产品边界。T15 尚未完成、目标基线未通过，因此 T15–T20 全部登记 DRAFT；需要超出普通表、现有接入和现有目录范围时另报问题，不静默扩展。

## 7. 范围内非功能与交付约束

| 约束 | 可执行检查/责任 |
|---|---|
| 每步一个主动作；无非本阶段必填 | T18/T20 页面走查与交互断言，覆盖 Chrome 95、1366×768、键盘/刷新/返回 |
| 状态 GET 无写副作用；不依赖治理/分析成功 | T17/T20 注入目录/分析故障，验证运行成功仍可读取、无额外写命令 |
| 重复物化/接入不重复建表或登记 | T17/T19/T20 同幂等键重放、两窗口、超时后重入；校验目标/记录/运行数量及数据保持 |
| 图规模、轮询、超时和重试有界 | 沿用现有配置；T15 记录实际参数与图边界 fixtures；不引入全图轮询、无界重试或全仓性能项目 |
| 数据安全与兼容 | T17/T19/T20 验证已有表冲突拒绝、schema-only 不写数、旧请求/旧模型/旧发布不变；必要迁移走前向 changeSet |
| 正式交付 | T20 复用 F1/T08、F2/T14 的部署目录和交付机制，只补本 Feature 差异；SHA、镜像、包校验和、离线验收分别留证 |

非目标：不重做菜单/资产目录，不新增审批角色、来源类型、STG 建模、全新历史引擎、跨引擎 DDL 或一键全链批量调度；不迁移/重跑真实业务数据。四层可共同设计，执行仍按单模型与已支持依赖规则推进。
