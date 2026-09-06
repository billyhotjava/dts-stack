# Sprint-104：通用建模契约与物化一致性整改

**版本**：v2.2.3  
**规划日期**：2026-09-06  
**状态**：IN_PROGRESS  
**类型**：缺陷整改与通用场景完善  
**目标**：同一模型在编辑、物化、质量检查、发布、资产治理和分析交付中遵循一致规则；用户无需假字段，能在模型页处理常见阻断，并看到与后端真实状态一致的结果。

## 范围与来源

初始范围为五项 P1 不一致及能力边界核对，F1 含 T01–T08。2026-09-06 按用户确认扩展 F2“模型交付与资产治理贯通”，新增 T09–T14；全 Sprint 共 2 个 Feature、14 个 Task。F1 既有进度和证据保留，F2 的 T09 进入契约核验，T10-A 已实施帮助上下文与字段说明迁移，T10-B1 已实施单主动作与创作门禁；完整四步页面及其余应用切片尚未实施。
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
| 离线交付 | 不依赖开发目录挂载、不临时下载依赖、不手工修补目标目录；部署另行授权 |

## 端到端契约链

完整字段与错误约束见 [Feature 契约](features/F1-通用建模契约与物化一致性/README.md)。

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

## Gate Registry

| Gate | 项目 | 状态 | 证据/待交付位置 | 未过对应 Task |
|---|---|---|---|---|
| G0 | 交付与登录/迁移基线 | GAP | [baseline](it/baseline.md)，尚未探测当前目标 | T01 |
| G0 | 领域与真实数据画像 | GAP | [domain-profile](assets/domain-profile.md)，数量和样本待实测 | T01 |
| G0 | 领域不变量 | PASS | 本文 ADR，计划不新增 owner/菜单/业务表；不代表代码验收 | — |
| G1 | 契约与 DoR | GAP | Feature K1–K6；阶段矩阵、草稿 CAS/存储细节待固定 | T01、T02 |
| G1 | 非功能预算 | GAP | [nfr-budget](assets/nfr-budget.md)，检查目标已列、未执行 | T01、T08 |
| G2 | F1 实现及单次聚焦 review | PASS（源码） | T02–T07 源码与专项测试归档；见 [source-test-summary](it/evidence/source-test-summary-20260906.md)。仅代表源码实现/review，不替代页面或运行验收 | T08 页面、部署与真实竖切片 |
| G3 | F1 发布安全 | PENDING | 正式双镜像及交付包已构建、当前测试环境已部署；离线安装与全部运行验收待完成 | T08 |
| G4 | F1 可运维性与 DoD | PENDING | [IT](it/README.md)；T08 产出 runbook | T08 |

## 当前源码测试证据（2026-09-06）

- 提交 `bd0670acc89e7dd1be82e0d12961ba7744f63ca2` 的部署目录 Docker Maven 专项为 99/99；工作台 Vitest 日志为 92/92。正式命令、首轮测试夹具修复原因和边界见 [源码专项归档](it/evidence/source-test-summary-20260906.md)。
- IT-04 ephemeral dbt 样例已通过，但其余 UI、Chrome 95、正式交付/部署、真实物化和质量未完成；不以源码证据替代 G0、G3 或 G4。

## Feature 与执行顺序

| Feature | Task 数 | 优先级 | 状态 |
|---|---:|---|---|
| [F1-通用建模契约与物化一致性](features/F1-通用建模契约与物化一致性/README.md) | 8 | P1（T07 为 P2） | IN_PROGRESS |
| [F2-模型交付与资产治理贯通](features/F2-模型交付与资产治理贯通/README.md) | 6 | P1 | IN_PROGRESS（T09 契约核验） |

建议顺序：T01 归因 → T07 能力契约冻结 → T02 → T05/T06/T03/T04 → T07 一致性验证 → T08。任务依赖优先于排序；不默认启用多代理。
统计：DRAFT=4，READY=0，IN_PROGRESS=10，DONE=0，BLOCKED=0。
F2 顺序：T09 基线/契约冻结 → T10 四步向导与统一交付视图 → T11 质量闭环、T12 资产维护、T13 分析恢复 → T14 集成验收；T11–T13 共享 T10 契约，默认单代理按依赖执行。F1/T08 与 F2/T14 共享环境/交付证据，不重复计算通过范围。

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

## 完成标准与非目标

- [ ] 五项 P1 修复均有 RED→GREEN 和当前版本证据；完整契约测试失败逐项闭环。
- [ ] UI、API、编译、dbt 检测及当前发布门禁结论一致；Chrome 95 验证未完成不得标 DONE。
- [ ] 重复物化按既有幂等/并发规则验收；现场证据与源码/构建证据分开。
- [ ] 旧模型可读取、不静默覆写；必要资源可离线交付，无开发目录依赖。
- [ ] F2 同一业务状态的前后端判定/允许操作一致；资产登记不再与分析准备混用，常见阻断可在模型面板处理。
- [ ] 质量/资产保存与分析重试有版本保护，旧证据不冒充当前成功，跨页返回保留身份与编辑上下文。
- 非目标：不实现新输入方式、通用历史引擎、全目录/菜单重做、平行控制面、生产数据修改、删除/归档整改或未经授权部署。F2 只收敛既有页面交互与交付流程。


## F2 扩展决策与开工门槛（2026-09-06）

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
| G2 | PENDING | T09 契约核验中；T10-A/T10-B1 已提交，完整四步页面与其余切片未实施；不得复用 F1 的源码 PASS | T10–T13 |
| G3 | PENDING | F2 预计包含分析端，正式包/必要迁移与离线验证未执行 | T14 |
| G4 | PENDING | IT-08–IT-18 与运行手册结果未执行 | T14 |

### 向导与生命周期补充（规划，未实施）

- 四步页面是工作阶段，ModelStatus/候选状态仍由各自现有控制面维护；进入某页不推进生命周期。
- 已具备证据可以跨步查看；回到上游编辑要按字段/实现/规则依赖重算下游资格；URL/浏览器返回不能绕过后端门禁。
- 每页一个主动作，移除重复流程卡片及并列全流程按钮；第三步完成后仍须在第四步确认发布。
- 截图中 CANCELLED 候选与历史目标表构建成功分开呈现，取消或失效候选不能被显示为当前可发布。
- 实施归属：T09 冻结 W/N/I 契约，T10 重构四步页面，T11 落 W3，T12/T13 落 W4，T14 验收 IT-15–IT-17。任务数保持 14，不另加重复任务。

### 页面简洁与帮助收敛

右上角既有“?”承载 W1–W4 规则解释和用法说明，按步骤定位；页面保留操作、状态和必要就地错误/阻断反馈。时间字段长说明作为明确迁移样例，细节见共享 H01–H04。T09/T10/T14 落实契约、界面/帮助和 IT-18 验收；Task 总数不变。

T10-A 已通过窄切片方案复审并开始帮助/说明迁移编码；T10-B 及 T11–T13 仍受各自业务契约缺口约束，不将帮助实现算作四步向导上线。
