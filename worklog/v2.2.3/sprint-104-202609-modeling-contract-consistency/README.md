# Sprint-104：通用建模契约与物化一致性整改

**版本**：v2.2.3  
**规划日期**：2026-09-06  
**状态**：IN_PROGRESS  
**类型**：缺陷整改与通用场景完善  
**目标**：同一模型在保存、草稿恢复、校验、物化和质量检查中遵循一致规则，用户无需添加假时间字段或假主键。

## 范围与来源

本 sprint 承接本次建模复审的五项 P1 不一致及能力边界核对，只设置一个 Feature：F1，拆分八个 Task。基线工作放在 F1/T01，不额外建立 Feature。
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
| UI | 模型工作台，`/modeling/models/{id}?activeStage=logical`；实际入口和 modelSpecId 参数由 T01 核验 | 保存、恢复、标准绑定、字段管理、加载方式、发布与物化，不新建菜单 |
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
| G2 | 实现及聚焦 review | PENDING | T02 首批契约源码已修复；见 it/evidence/T02-contract | T02–T07、T08 |
| G3 | 发布安全 | PENDING | T08 产出 release-plan；未授权部署 | T08 |
| G4 | 可运维性与 DoD | PENDING | [IT](it/README.md)；T08 产出 runbook | T08 |

## Feature 与执行顺序

| Feature | Task 数 | 优先级 | 状态 |
|---|---:|---|---|
| [F1-通用建模契约与物化一致性](features/F1-通用建模契约与物化一致性/README.md) | 8 | P1（T07 为 P2） | IN_PROGRESS |

建议顺序：T01 归因 → T07 能力契约冻结 → T02 → T05/T06/T03/T04 → T07 一致性验证 → T08。任务依赖优先于排序；不默认启用多代理。
统计：DRAFT=6，READY=0，IN_PROGRESS=2，DONE=0，BLOCKED=0。
现场基线/DoR 仍有缺口；用户已授权启动具有固定源码输入的 T01/T02 子项，其余任务仍保持 DRAFT。

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
| 实际物化与离线交付准备 | T08 | IT-01–IT-07 |

## 完成标准与非目标

- [ ] 五项 P1 修复均有 RED→GREEN 和当前版本证据；完整契约测试失败逐项闭环。
- [ ] UI、API、编译、dbt 检测及当前发布门禁结论一致；Chrome 95 验证未完成不得标 DONE。
- [ ] 重复物化按既有幂等/并发规则验收；现场证据与源码/构建证据分开。
- [ ] 旧模型可读取、不静默覆写；必要资源可离线交付，无开发目录依赖。
- 非目标：本轮不直接实现新输入方式、通用历史引擎、目录/菜单重构、生产数据修改、删除/归档整改或未经授权部署。

