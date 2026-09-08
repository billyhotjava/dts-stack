# Sprint-104：通用建模契约与物化一致性整改

**版本**：v2.2.3  
**规划日期**：2026-09-06  
**状态**：IN_PROGRESS  
**类型**：缺陷整改与通用场景完善  
**目标**：用户能先设计 ODS→DWD→DWS→ADS，以“设计→实现配置→物化”完成建模；数据接入、资产形成与治理、分析准备在数据模块办理。各模块复用同一模型/资产身份和版本证据，不把所有约束堆在模型页。

**当前产品边界（2026-09-07）**：新增 [F3-全层建模与数据模块边界简化](features/F3-全层建模与数据模块边界简化/README.md)，以 [F3 契约](assets/F3-modeling-data-boundary.md) 为本轮增量规范。物化完成即完成本次建模；F2 中强制继续质量配置、发布交付及模型页治理的界面条件被替代，已有服务/版本/安全规则和历史证据保留。T15–T20 已进入实施及真实 Chrome 95 分项验收，尚未满足全部 DoD。

**手工测试入口**：[IT-01～IT-18 操作步骤、版本前提与结果填写表](it/手工验收用例-20260907.md)。2026-09-07 起由用户手工操作；研发补验分支单列，不以清单代替通过证据。

**最新实施状态**：F1/F2 已有主要实现，F3 的 SOURCE、逻辑依赖、结构物化、三步页面、接入目标绑定和数据接手代码已落地；正式包及真实 Chrome 95 分项验收进行中。规划内活动候选唯一约束已按冻结契约修复并通过真实PG回归；最终正式包9a5e7ce60正在部署复验，独立离线目标仍未指定，不能标记全部完成。见 [F3 运行记录](it/evidence/current-environment/f3-runtime-20260907.md)；旧记录按各自提交保留。

## 范围与来源

初始范围为五项 P1 不一致及能力边界核对，F1 含 T01–T08。2026-09-06 扩展 F2“模型交付与资产治理贯通”，新增 T09–T14；2026-09-07 按用户新要求新增 F3/T15–T20，全 Sprint 共 3 个 Feature、20 个 Task。F2 原有四步向导、质量闭环、目录 CAS 和精确分析注册源码及验证进展继续保留，F3 只接续其中与新模块边界冲突的部分。源码落地不计作验收完成，原进展见 [本轮实现与验证记录](it/evidence/current-environment/implementation-progress-20260907.md)。
计划复审已修正，已开始 T01 源码归因与 T02 契约整改；不代表部署或现场验收完成。仓库既有路径为 `worklog/v2.2.3`；用户消息中的 `workog` 按既有目录处理。

此前 `fefea6844`、`a33ba6b0a` 已处理普通明细时间必填、部分无键全量/全表聚合和已有 TYPE2 绑定保存；本 sprint 不把这些历史实现重复计为 DONE。
登记时 HEAD 为 `c96d4ff4b`；删除/归档相关改动不属于本 sprint。

## 架构决策记录（ADR）

| 决策 | 选择与约束 |
|---|---|
| 单一模型事实源 | 延续 ModelSpec、创作草稿、实现修订和发布候选 owner，不新建平行模型/质量/资产台账 |
| 分阶段规则 | 分开格式合法性、阶段完整性、治理策略；不以取消所有检查换取通用性 |
| 当前值优先 | 当前编辑→创作草稿→模型基线→新建默认；显式 FULL、[]、null 的语义由 T02 固定 |
| 键与业务时间 | 全量非维度模型可无键；维度和当前增量执行保持必要键；时间语义只约束适用场景 |
| 维度历史 | 保存历史配置不等于已实现 TYPE2 历史维护；真实能力由 T07 固定 |
| 权限与合规 | 不扩张权限范围，不改变租户、认证、密级、来源有效性和版本校验 |
| 存储与迁移 | 优先修正现有 DTO/Schema/JSON 配置和编译；未计划新表或改列。若证据要求迁移，先补设计，只允许前向 changeSet |
| 离线交付 | 不依赖开发目录挂载、不临时下载依赖、不手工修补容器；按用户授权从部署目录正式构建和定向更新 |
| F3 模块边界 | 模型设计/实现/物化归建模；数据接入、资产形成/质量治理/发布和分析准备归数据。当前版本真实物化成功即可完成建模，不等同资产可用或 PUBLISHED |
| F3 全层设计 | 在同一 ModelSpec 内补 ODS 逻辑类型与版本依赖；零接入可保存全链，不伪造物理来源；普通空表结构物化与数据加工结果分别记证 |
| F3 增量范围 | 首批 PostgreSQL 普通表，复用现有接入和目录；不新增 STG 建模、菜单、批量全链调度或新治理/发布 owner；技术缺口由 T15 先冻结 |

## 端到端契约链

F1 字段与错误约束见 [Feature 契约](features/F1-通用建模契约与物化一致性/README.md)；本轮 ODS、结构物化、完成判定和数据交接的增量链见 [F3 K31–K36](assets/F3-modeling-data-boundary.md#3-契约链与扩展草案)。下表保留既有入口，不表示旧阶段必填全部继续前置。

| 层 | 现有落点 | 本 sprint 约束 |
|---|---|---|
| UI | 模型工作台，`/data-modeling/dimensions/workbench?modelSpecId={id}`；旧 `/modeling/models/{id}` 由 F2/T12 修复兼容跳转 | 保存、恢复、标准绑定、字段管理、加载方式、发布与物化，不新建菜单 |
| 模型 | `POST /api/modeling/model-specs`；`PUT /api/modeling/model-specs/{id}` | 完整字段和阶段规则对齐；保留 If-Match |
| 草稿 | `GET .../{id}/authoring-context`；`POST .../{id}/authoring-drafts`；`PUT .../{id}/authoring-drafts/{draftId}`；`POST .../{id}/authoring-drafts/{draftId}/validate|commit` | 完整 snapshot 往返，保留原有草稿版本/CAS 请求 |
| 阶段与实现 | `GET .../{id}/stage-gates`；`GET .../implementation/capabilities`；`PUT .../{id}/implementation` | 覆盖策略、输入/加载能力一致，保留模型与实现双版本 |
| 编译与检测 | `POST .../{id}/lifecycle/compile|tests` | 完整复合键质量产物，无键不生成假唯一性规则 |
| 物化 | `POST .../{id}/build-intents` | If-Match、Idempotency-Key；请求 `{planId:string,environment:string}` |
| Service | ModelSpecContract/StageGateService、创作草稿服务、InputPolicy、ExecutionPlanner、DbtCompiler | 在既有职责边界修复，不新增替代入口 |
| 数据 | `modeling_model_spec` 及既有修订；`modeling_model_implementation.settings_json` 及实现修订 | 保留当前模型/实现身份和历史；草稿具体表列在 T01 记录，不先写迁移 |
| 运行 | 既有 dbt/PostgreSQL、质量与发布候选控制面 | 成功必须绑定当前 revision/checksum，不用旧证据代替 |

## 现状勘察账本（Context Ledger）

以下来自已完成的源码复审；实施只复用账本，文件变化时补充差异，不重新全仓扫描。路径均相对仓库根。

| 编号 | 已确认事实 | 源码证据 |
|---|---|---|
| C01 | Java 草稿更新过滤部分完整上下文要求；前端更新使用完整模型校验；Schema 缺业务过程/主题域字段 | `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/modeling/ModelSpecContract.java:1470`；`source/dts-platform-webapp/src/features/modeling/contracts/modelSpecV2Contract.ts:1025`；`source/dts-platform/src/main/resources/config/modeling/model-spec-v2.schema.json:1446` |
| C02 | 按策略选必绑字段后，专业证据适配器又遍历全字段要求绑定 | `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/modeling/ModelSpecStageGateService.java:250`；`source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/modeling/GovernanceModelSpecStandardEvidenceAdapter.java:29` |
| C03 | 复合键编译为首列 unique；已用当前编译类复现 | `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/modeling/ModelingDbtCompiler.java:831`、`:872`；[复审证据](assets/review-evidence.md) |
| C04 | 草稿恢复只取 scdType，保存从基线取绑定/层级 | `source/dts-platform-webapp/src/pages/data-modeling/prototype/services/modelWorkbenchService.ts:523`、`:1041` |
| C05 | FULL 和显式空分区可能触发旧 policy 回退 | 同文件 `:459`–`:468` |
| C06 | 完整 Node 契约测试 37 项，24 通过、13 失败；不等于 13 个独立缺陷 | [复审证据](assets/review-evidence.md)，本次规划未重新运行 |
| C07 | 现有模型、草稿、实现、阶段接口可复用 | `source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/ModelSpecResource.java:44`、`:135`、`:200`；`ModelLifecycleResource.java:75`、`:116`、`:171`；`ModelAuthoringDraftResource.java:43`、`:81`、`:118` |
| C08 | 当前实现以 settings_json 保存且有修订；模型台账已存在 | `source/dts-platform/src/main/java/com/yuzhi/dts/platform/repository/modeling/ModelLifecycleRepository.java:196`；`ModelSpecRepository.java:65` |
| C09 | 历史专项回归和构建不能代替当前部署/浏览器/迁移证明 | [基线](it/baseline.md) |
| C10 | 输入方式按类型固定；执行计划明确不支持 SNAPSHOT，配置历史元数据不等于运行支持 | `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/modeling/ModelImplementationInputPolicy.java:203`；`ModelImplementationExecutionPlanner.java:146` |

F2 账本 C11–C18 见 [原交付契约](assets/delivery-workflow-contract.md)；2026-09-07 新增 [C19–C26](assets/F3-modeling-data-boundary.md#5-context-ledger-c19c26)，记录 ODS 禁建、接入建表、真实来源校验、发布耦合和数据目录落点。T15–T20 复用账本，仅对受影响变化补充证据。

## Gate Registry

| Gate | 项目 | 状态 | 证据/待交付位置 | 未过对应 Task |
|---|---|---|---|---|
| G0 | 交付与登录/迁移基线 | GAP | [baseline](it/baseline.md)，尚未探测当前目标 | T01 |
| G0 | 领域与真实数据画像 | GAP | [domain-profile](assets/domain-profile.md)，数量和样本待实测 | T01 |
| G0 | 领域不变量 | PASS | 本文 ADR，计划不新增 owner/菜单/业务表；不代表代码验收 | — |
| G1 | 契约与 DoR | GAP | Feature K1–K6；阶段矩阵、草稿 CAS/存储细节待固定 | T01、T02 |
| G1 | 非功能预算 | GAP | [nfr-budget](assets/nfr-budget.md)，检查目标已列、未执行 | T01、T08 |
| G2 | F1 实现及单次聚焦 review | PASS（源码） | T02–T07 源码与专项测试归档；见 [source-test-summary](it/evidence/source-test-summary-20260906.md)。仅代表源码实现/review，不替代页面或运行验收 | T08 页面、部署与真实竖切片 |
| G3 | F1 发布安全 | PENDING | 历史交付已部署；本轮含 platform/analytics/ingestion/webapp 四镜像，版本与阶段以整改验证为准 | T08 |
| G4 | F1 可运维性与 DoD | PENDING | [IT](it/README.md)；T08 产出 runbook | T08 |
| G0 | F3 空目标/无来源/接入与数据接手基线 | PASS | 登录、无来源四层设计、连续空表和文件写数已实际验证；独立离线仍由T20跟踪 | T15 |
| G1 | F3 边界、精确契约与兼容方案 | PASS | [F3 K31–K36、四项技术缺口与非功能检查](assets/F3-modeling-data-boundary.md)；已实施 DTO 见冻结章节；2026-09-08 候选用途/占用/迁移增量已冻结并通过隔离验证 | T15 |
| G2 | F3 实现与聚焦 review | PENDING | T16–T19 已实施，整改源码与178项专项测试通过；真实主线待部署复验 | T16–T19 |
| G3 | F3 正式交付与安全恢复 | PENDING | 复用 F1/T08、F2/T14 机制，只补本次差异 | T20 |
| G4 | F3 模块边界与全层真实验收 | PENDING | IT-19/21/23 有分项证据，IT-20/22 完整主线及 IT-24 独立离线待完成 | T20 |

## 当前源码测试证据（2026-09-06）

- 提交 `bd0670acc89e7dd1be82e0d12961ba7744f63ca2` 的部署目录 Docker Maven 专项为 99/99；工作台 Vitest 日志为 92/92。正式命令、首轮测试夹具修复原因和边界见 [源码专项归档](it/evidence/source-test-summary-20260906.md)。
- IT-04 ephemeral dbt 样例已通过，但其余 UI、Chrome 95、正式交付/部署、真实物化和质量未完成；不以源码证据替代 G0、G3 或 G4。

## Feature 与执行顺序

| Feature | Task 数 | 优先级 | 状态 |
|---|---:|---|---|
| [F1-通用建模契约与物化一致性](features/F1-通用建模契约与物化一致性/README.md) | 8 | P1（T07 为 P2） | IN_PROGRESS |
| [F2-模型交付与资产治理贯通](features/F2-模型交付与资产治理贯通/README.md) | 6 | P1 | IN_PROGRESS（T10–T13 源码集成，正式验证待执行） |
| [F3-全层建模与数据模块边界简化](features/F3-全层建模与数据模块边界简化/README.md) | 6 | P1 | IN_PROGRESS（T15–T20，实施及分项验收） |

建议顺序：T01 归因 → T07 能力契约冻结 → T02 → T05/T06/T03/T04 → T07 一致性验证 → T08。任务依赖优先于排序；不默认启用多代理。
统计：DRAFT=0，READY=0，IN_PROGRESS=19，DONE=1，BLOCKED=0。
F2 顺序：T09 基线/契约冻结 → T10 四步向导与统一交付视图 → T11 质量闭环、T12 资产维护、T13 分析恢复 → T14 集成验收；T10–T13 的源码现已集成并正式测试，T14 正在补齐镜像、迁移、离线及 Chrome 验收证据。F1/T08 与 F2/T14 共享环境/交付证据，不重复计算通过范围。

F3 顺序：T15 冻结新边界/副作用 → T16 ODS 与逻辑引用 → T17 结构物化/完成证据 → T18 三步页面 → T19 接入绑定与数据接手 → T20 验收。F2 以上顺序是已有切片记录，冲突页面不再按旧标准扩建；F3 的收敛实施不重复开发 F2 已有 owner。

T01–T08 均在推进中：源码专项、IT-04真实dbt、历史草稿主要页面分支已留证；正式构建及测试环境部署完成。无时间明细物化、其余场景、Chrome 95和离线安装尚未全部通过，禁止登记Sprint DONE。

## 追溯矩阵

| 需求 | Task | 验收 |
|---|---|---|
| 环境、13 项失败归因 | T01 | baseline；IT-01 |
| 合法字段/阶段统一 | T02 | IT-01、IT-07 |
| 标准覆盖范围一致 | T03 | IT-05 |
| 复合键唯一性 | T04 | IT-04 |
| 草稿历史/层级完整 | T05 | IT-02 |
| FULL/[] 不回退 | T06 | IT-03 |
| 能力边界真实 | T07 | IT-06 |
| 实际物化与离线交付准备 | F1/T08 | IT-01–IT-07 |
| 业务状态、接口、页面动作同一契约 | F2/T09、T10 | IT-08、IT-12、IT-15–IT-17；共享 S/N/I fixtures |
| 正确目标的规则配置与检查 | F2/T11 | IT-09、IT-10 |
| 资产治理同一 owner，模型导航保留身份 | F2/T12 | IT-11、IT-12 |
| 分析自动准备及独立失败恢复 | F2/T13 | IT-08、IT-13 |
| F2 页面/运行/离线交付一致 | F2/T14 | IT-08–IT-18 |
| 零接入任务完成 ODS 到 ADS 的逻辑设计 | F3/T15、T16 | IT-19 |
| 无数据结构物化及当前版本建模完成 | F3/T17 | IT-20 |
| 三步页面、约束按阶段出现、物化后可结束 | F3/T18 | IT-21 |
| 接入绑定同一 ODS、数据模块形成并维护资产 | F3/T19 | IT-22 |
| 治理失败独立恢复及旧流程兼容 | F3/T17–T19 | IT-23 |
| F3 正式包、部署、Chrome95 与离线差异验收 | F3/T20 | IT-24 |

## 完成标准与非目标

- [ ] 五项 P1 修复均有 RED→GREEN 和当前版本证据；完整契约测试失败逐项闭环。
- [ ] UI、API、编译、dbt 检测及当前发布门禁结论一致；Chrome 95 验证未完成不得标 DONE。
- [ ] 重复物化按既有幂等/并发规则验收；现场证据与源码/构建证据分开。
- [ ] 旧模型可读取、不静默覆写；必要资源可离线交付，无开发目录依赖。
- [ ] 前后端同一业务状态/允许操作一致；F2 的质量/资产/分析能力按 F3 边界落到数据模块，资产登记不与分析准备混用。
- [ ] 质量/资产保存与分析重试有版本保护，旧证据不冒充当前成功，跨页返回保留身份与编辑上下文。
- [ ] 无接入可保存四层设计及版本引用；结构物化核对真实目标，零行不阻断建模完成，空表不冒充数据就绪。
- [ ] 三步建模不要求资产运营信息；数据模块写数/治理失败不改物化结果；IT-19–IT-24 留存实际证据。
- 非目标：不实现新输入方式、通用历史引擎、全目录/菜单重做、平行控制面、生产数据修改、删除/归档整改或未经授权部署。F2 只收敛既有页面交互与交付流程。


## F2 扩展决策与开工门槛（2026-09-06 历史基线）

本节记录既有实现来由。2026-09-07 后与模块分工/建模结束条件冲突的规则由 [F3 契约](assets/F3-modeling-data-boundary.md) 替代；原四步及模型页治理要求不再作为 F3 验收标准。版本、安全、单主动作、帮助、身份和原服务复用约束继续有效。

唯一详细规范为 [模型交付与资产治理统一契约](assets/delivery-workflow-contract.md)，包含 Context Ledger C11–C18 与 S01–S10 状态/操作矩阵；下游只查账本中与变更相关的差异。

| 决策 | 业务与界面必须同时遵循的约束 |
|---|---|
| 连续交付入口 | W1 设计→W2 实现→W3 构建检查→W4 发布交付，每步独立页面、一个主操作；后端提供事实/导航权限/允许动作 |
| 结果分离 | 物化、质量、发布、目录登记、分析准备分别取证；分析失败不回滚发布、不妨碍已有资产维护 |
| 上下文不丢失 | 常见规则绑定/治理维护在面板完成；复杂编辑进入已有页面并返回原模型/候选/目标 |
| 写入 owner 不变 | 规则、资产、模型分别由现有服务写入；复用表单不代表复制数据或覆盖未编辑字段 |
| 分析准备无隐藏前置页 | 复用平台源接入能力，在既有服务交付边界恢复；不要求先访问大屏选源 |
| 授权边界 | 延续当前菜单授权约定及独立认证/租户/来源/密级/版本保护，不增加细粒度发布角色门槛 |
| 存储 | F1 的无新增表结论不自动覆盖 F2；唯一性/并发若需迁移，T09/T13 先冻结前向兼容方案 |

| Gate | F2 状态 | 交付物/缺口 | Task |
|---|---|---|---|
| G0 | GAP | 复用 baseline，追加 F2 分析源/质量/登录当前基线；历史观察不当本轮实测 | T09 |
| G1 | GAP | 共享契约已定业务矩阵；精确质量命令、多输出 DTO、字段 owner/CAS 与 NFR 参数待冻结 | T09；T13 接入唯一性 |
| G2 | PENDING | T09 契约、T10 四步向导/统一交付聚合、T11 质量闭环、T12 资产 CAS、T13 精确分析注册主要代码已落地；目录 `canMaintain` 与分析失败映射已在 `3c8`/`c275` 专项通过但尚未部署。`32b7e1309` 分步编辑/权限/转换回归 44/44、帮助主题 6/6 通过；前端正式构建与后续发布验收分别记录，不复用 F1 的源码 PASS | T10–T13 |
| G3 | PENDING | F2 预计包含分析端，正式包/必要迁移与离线验证未执行 | T14 |
| G4 | PENDING | IT-08–IT-18 与运行手册结果未执行 | T14 |

### 向导与生命周期补充（已落地的源码规则，现场验收待执行）

- 四步页面是工作阶段，ModelStatus/候选状态仍由各自现有控制面维护；进入某页不推进生命周期。
- 已具备证据可以跨步查看；回到上游编辑要按字段/实现/规则依赖重算下游资格；URL/浏览器返回不能绕过后端门禁。
- 每页一个主动作，移除重复流程卡片及并列全流程按钮；第三步完成后仍须在第四步确认发布。
- 截图中 CANCELLED 候选与历史目标表构建成功分开呈现，取消或失效候选不能被显示为当前可发布。
- 实施归属：T09 冻结 W/N/I 契约，T10 重构四步页面，T11 落 W3，T12/T13 落 W4，T14 验收 IT-15–IT-17。任务数保持 14，不另加重复任务。

### 页面简洁与帮助收敛

右上角既有“?”承载 W1–W4 规则解释和用法说明，按步骤定位；页面保留操作、状态和必要就地错误/阻断反馈。时间字段长说明作为明确迁移样例，细节见共享 H01–H04。T09/T10/T14 落实契约、界面/帮助和 IT-18 验收；Task 总数不变。

以下 T10-A/T10-B2/T13-A 段落为 2026-09-06 历史切片记录。其后 T10 四步向导与统一交付聚合、T11 质量闭环、T12 资产治理 CAS 和 T13 精确分析注册已继续实现；该历史记录当时的运行容器为 `296605b39f98`，尚未包含 `3c8`、`c275` 或 `375ac377f` 的变更。

T10-B2 已接入首次“保存设计并继续”：业务定义与实现配置分开提交，后续进入既有实现编辑入口。完整四阶段向导已在后续源码切片实现；正式测试、构建、新包部署与浏览器验收仍待完成，进展和历史验证边界见 [T10-B2 验证](it/evidence/T10-B2-definition/verification.md)。

## 2026-09-08 整改执行基线

当前整改按 [审查整改契约](assets/review-remediation-20260908.md) 执行。F3 三步建模是现行规则，历史四步记录不作为模型完成前置。20 项任务均在实施/验收中，未达到 DONE；当前运行受测版本及未通过分支以 IT 记录为准。

## F4 性能稳定性扩展（2026-09-08）

| Feature | Task数 | 优先级 | 状态 |
|---|---:|---|---|
| [F4-模型工作台性能与交付状态稳定性](features/F4-模型工作台性能与交付状态稳定性/README.md) | 4 | P0 | IN_PROGRESS |

### 现状勘察账本 F4 增量
| # | 事实 | 证据 |
|---|---|---|
| F4-1 | 页面10行并发状态请求且任一失败清空整页 | ModelWorkbenchCatalogList.tsx:272 |
| F4-2 | 聚合持有只读事务、质量上下文挂起再申请新事务 | ModelDeliveryStatusQueryService.java:43；CandidateQualityRuleContextService.java:55；DefaultDestinationSyncService.java:164 |
| F4-3 | 首屏等待9组列表/编辑辅助数据 | modelWorkbenchService.ts:568 |
| F4-4 | 现场10连接占满/30s超时、8个闲置事务、浏览器17条首屏全部状态失败 | it/evidence/F4-performance-20260908.md |

| 需求 | Task | 证据 |
|---|---|---|
| 消除连接饥饿 | F4/T21 | IT-25 |
| 单行隔离/限制并发 | F4/T22 | IT-26 |
| 首屏不等辅助数据 | F4/T23 | IT-27 |
| 真实运行与交付验证 | F4/T24 | IT-28 |

F4 Gate：G0=PASS_WITH_GAPS（既有正式环境和登录已实证，新制品性能待测）；G1=PASS（F4契约和非功能预算）；G2/G3/G4=PENDING，由T21–T24跟踪。顺序：T21/T22 → T23 → T24，单代理执行。
