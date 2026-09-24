# v2.2.3 Sprint 队列

本文件是 v2.2.3 的 sprint 索引，2026-09-22 补建（此前缺失，导致 F12 等工作在 sprint 层不可见）。**各 sprint 的权威状态以其自身 README 为准**，本表只做索引与执行顺序；补建时按各 README 现有记载登记，未回溯重算历史统计。

## Sprint-102：数据质量自动化工作流闭环（202608）

**目录**：`worklog/v2.2.3/sprint-102-202608-data-quality-workflow-automation`
**时间盒**：2026-08-24 ～ 2026-09-11
**状态**：IN_PROGRESS
**目标**：把规则、运行策略、模型质量阶段、接入后验证、规则执行、问题处置和发布门禁收敛为同一条可追踪、可重试、可审计的数据质量工作流。

| Feature | 说明 |
|---|---|
| F0-交付基线与契约冻结 | G0 门槛 |
| F1-质量工作流账本与状态机 | 核心 |
| F2-运行策略与触发入口收敛 | |
| F3-建模与入湖事件接入 | |
| F4-重试问题与恢复闭环 | |
| F5-质量工作流前端 | |
| F6-集中验收与运维交接 | G3/G4 |

## Sprint-103：数据集成流程可视化与运行闭环（202608）

**目录**：`worklog/v2.2.3/sprint-103-202608-data-integration-flow-closure`
**时间**：2026-08-27 ～ 2026-09-30
**状态**：`PASS_WITH_ENV_NOTE` — 数据集成闭环已部署；独立编排入口退役变更已通过源码验收但未部署；Chrome 95 待现场复验。
**类型**：Architecture + UI Productization + Vertical Slice

| Feature | 说明 |
|---|---|
| F0-交付基线与契约冻结 | G0 |
| F1-任务配置与拓扑投影 | |
| F2-版本准入与调度发布 | |
| F3-任务级运行闭环 | |
| F4-治理迁移与集中验收 | G4 |

**已知风险**：Chrome 95 现场复验未完成；退役变更未部署。

## Sprint-104：通用建模契约与物化一致性整改（202609）

**目录**：`worklog/v2.2.3/sprint-104-202609-modeling-contract-consistency`
**状态**：IN_PROGRESS
**目标**：用户能先设计 ODS→DWD→DWS→ADS，以"设计→实现配置→物化"完成建模；数据接入、资产形成与治理、分析准备在数据模块办理；各模块复用同一模型/资产身份和版本证据。

| Feature | 优先级 | Task 数 | 状态 |
|---|---|---|---|
| F1-通用建模契约与物化一致性 | P1 | 8 | IN_PROGRESS |
| F2-模型交付与资产治理贯通 | P1 | 6 | IN_PROGRESS |
| F3-全层建模与数据模块边界简化 | P1 | 6 | IN_PROGRESS |
| F4-模型工作台性能与交付状态稳定性 | — | — | 见 sprint README §F4 |
| F5-建模状态语义与上游引用准入收敛 | — | — | 见 sprint README §F5 |
| F6-指标计算口径与资产BI协作闭环 | — | — | 见 sprint README §F6 |
| F7-首次建模初始化与菜单授权一致性 | — | — | 见 sprint README §F7 |
| F8-质量规则运行契约与失败反馈重构 | — | — | 见 sprint README §F8 |
| F9-部门公共层与ADS共享权限链收敛 | — | — | 见 sprint README §F9 |
| F10-菜单信息架构按实施主线收敛 | — | — | 见 sprint README §F10 |
| F11-权限模型统一与RBAC重构 | — | — | 见 sprint README §F11 |
| **F12-业务应用ZIP全链路导入** | **P1** | **8** | **IN_PROGRESS**（T01 DONE、T02 IN_PROGRESS、T03–T08 DRAFT） |
| **F13-发布质量处理与失败恢复** | **P1** | **9** | **IN_PROGRESS**（后端实施中；F13-T03、F14-T02/T03 已开工） |
| **F14-模型构建与失败恢复** | **P0** | **8** | **IN_PROGRESS**（后端实施中；F13-T03、F14-T02/T03 已开工） |
| **F15-发布与构建单线流程体验** | **P0** | **5** | **IN_PROGRESS**（T01 READY，T02–T05 DRAFT；去页签单线流程，F13-T05/F14-T06 作为阶段插槽） |

**F12 统计**：DRAFT=6，IN_PROGRESS=1，DONE=1，BLOCKED=0。估算 31 人日，无人员容量或工期承诺。
**F12 执行顺序**：T02 主路径 → T08 建模重置/同包重导 → T03 包契约与生产 → T04/T05 领域适配（T06 运行基础先于 T04/T05 真实写入集成）→ T07 分片验收。单代理实施。
**F12 关键决策**：Q1–Q6 见[架构与包契约](sprint-104-202609-modeling-contract-consistency/features/F12-业务应用ZIP全链路导入/架构与包契约.md)；Q7 跨服务传输复用 `AnalyticsSemanticPublishClient`，不新建出站客户端；Q8 领域载荷须按目标契约实测对照修订后方可冻结 Schema。
**F12 已知风险**：
- 下游三类载荷与 dts-analytics 目标契约不同构（SCREEN 需整体重做），G1 契约链为 GAP，T04/T05 在对齐前不得转 READY。
- 重置能力未实现：43 个 PJM 草稿删除被追加写保护拒绝，用户同规划手工重导需求仍未闭环（T08）。
- Chrome 95、1366×768/768 窄屏真实浏览器验收未执行。

**F13/F14 联合统计**：共16Task；READY=2、DRAFT=11、IN_PROGRESS=3、DONE=0、BLOCKED=0。每个Feature各8Task，T01 READY；F13-T03、F14-T02/T03 IN_PROGRESS，其余DRAFT。F13原11人日估算失效，M0后重估联合开发/测试/交付容量，无工期承诺。
**联合执行顺序**：F13-T01与F14-T01分别核对后完成M0 → M1构建到质量的最早集成切片 → M2并发与故障恢复 → M3统一界面 → M4同批制品与双环境验收。详见[联合设计](sprint-104-202609-modeling-contract-consistency/assests/F13-F14-联合设计与实施顺序.md)。2026-09-24 已按用户确认启动两个Feature首批后端，见[实施记录](sprint-104-202609-modeling-contract-consistency/assests/F13-F14-首批编码与测试记录.md)。
**职责**：F14负责构建前检查、执行、关系核验与结果回写恢复；F13负责构建后的资产登记与发布质量处理。复用现有派发、身份、台账和页面，不另建调度器。F13自身缺陷仍由F13修复，不转移到F14后计完成。
**设计入口**：[F13](sprint-104-202609-modeling-contract-consistency/assests/F13-release-quality-reconcile-contract.md)、[F14](sprint-104-202609-modeling-contract-consistency/assests/F14-模型构建与失败恢复设计.md)。保留原refresh使候选失效语义；新增立即检查与构建恢复命令按M0确定。登记根因先真实复现，不预设候选版本变化必然失败。
**验证**：原F13-IT-01–21、F14-IT-01–20保留；新增F13 16项、F14 17项单元用例及[12项联合系统场景](sprint-104-202609-modeling-contract-consistency/it/F13-F14-系统测试用例.md)，ST细化IT，不重复累计覆盖。首批源码与定向测试证据单独见实施记录，整体IT/ST仍NOT_RUN；正式包、部署、页面验收未执行。G0/G1=GAP，G2/G3/G4=PENDING。
**2026-09-24架构修订**：统一写入职责和持久交接；恢复operation隔离原失败任务；质量轮次绑定构建/规则/runId；复用平台ADVISORY/BLOCKING，不修改现场配置，保留已通过候选的冻结策略。M2完成T06后端，M3完成其页面联调。
**主要风险**：终态恢复的合法转换、运行文件保留、唤醒与领取并发、原发起人撤权、Java/Airflow版本兼容和在途操作回退；由两个T01冻结，不凭空放宽。评审流程重构另行登记，不在本次范围。

**F4–F11 说明**：sprint README 的「Feature 与执行顺序」表历史上只登记 F1–F3，F4–F11 状态分散在各自章节。本次补建队列时按现状索引，不回溯补全，避免制造未经核对的状态。
