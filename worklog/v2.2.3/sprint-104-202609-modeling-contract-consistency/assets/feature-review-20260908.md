# Sprint-104 三个 Feature 复审记录（2026-09-08）

**性质**：只读源码与规划复审，不是测试、构建、部署或页面验收。
**范围**：F1/F2/F3 共 20 个 Task 文档、F3 契约、`c96d4ff4b..1ad474167`（78 个提交）中对应的 dts-platform / dts-platform-webapp / dts-analytics 源码。
**方法**：按 domain-dts 不变量与各 Task 契约逐项对照代码；路径均相对仓库根。未运行任何测试或编译，结论只代表静态复核。

## 结论摘要

| 严重度 | 数量 | 摘要 |
|---|---:|---|
| HIGH | 3 | 再次发布覆盖人工治理字段（F2/T12）；F3 与同日编码方向冲突；HEAD 之后 25 个提交无测试证据 |
| MEDIUM | 6 | PUT 强制 If-Match 未登记为兼容性变更；交付状态错误码误标；T13 归并冲突分支；键一致性存量兼容无证据；F3 K33 漏列 DDL-only 执行缺口；状态口径不一致 |
| LOW | 4 | T03 集合 B 定义不一致；wizard 可编辑与 STALE 并存；K31 与 K1 冲突；IT-19–24 未进手工用例 |

F1 源码与契约基本对齐；F2 有两处与自身契约直接冲突；F3 是规划但与当天编码相互矛盾。

## 一、代码缺陷

### R01 HIGH · F2/T12 · 再次发布覆盖人工维护的 owner/description

- 位置：`source/dts-platform/src/main/java/com/yuzhi/dts/platform/repository/modeling/CandidatePublicationRepository.java:788-806`
- 现象：原生 `update catalog_dataset set ... owner = ?, ... description = ?` 无条件写入当前操作者与模型描述，`where id = ?` 无任何保护条件。
- 违反：T12 契约“再次发布不得覆盖人工维护字段”、IT-11 走查“再次发布检查人工字段未被覆盖”、F3 K36/T19“owner/description 等人工值不被再次物化或接入覆盖”。
- 附带：该 SQL 不递增 `catalog_dataset.version`，`PATCH .../governance-summary` 的 ETag CAS 无法感知这次覆盖；同文件 `:324` 归档更新同样绕过 version。
- 建议：发布投影只写模型投影字段（name/domain/source/schema/table/tags/layer/lifecycle/classification），owner/description 仅在原值为空时补写；或按 T09 owner 表显式区分投影字段与人工字段，并让原生更新一并 `version = version + 1`。

### R02 MEDIUM · F2/T12 · 全量 PUT 变为破坏性接口且未登记

- 位置：`source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/catalog/CatalogDatasetResource.java:603-613`、`:818-822`
- 现象：`PUT /api/catalog/datasets/{id}` 缺 If-Match 返回 428。前端 `platformApi.ts` 的 `updateDataset` 已改签名，但仓内无调用方；dts-admin 审计规则登记了该 PUT（`20251230-03_refine_catalog_explore_audit_rules.xml:73`），外部或脚本调用方会直接失败。
- T12 契约写明有意为之，但 `assets/release-plan.md` 只登记了 `20260906-01` 迁移，没有登记 API 兼容性变更与受影响调用方清单。
- 建议：release-plan 增加“接口兼容性”一节，列出 PUT 语义变化与迁移指引；确认无外部调用方后再发布。

### R03 MEDIUM · F2/T10 · 交付状态把“尚未开始”标成“证据过期”

- 位置：`source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/modeling/ModelDeliveryStatusQueryService.java:141-146`（catalog）、`:149-171`（analysis）
- 现象：当前候选已构建但尚未发布或尚未登记资产时，`matched=false`，步骤返回 `UNKNOWN` 且 reasonCode 为 `MODEL_DELIVERY_EVIDENCE_STALE`。前端将 UNKNOWN 显示为“暂无当前证据”，掩盖了错误码本身错误。
- 违反：T10“读取不到与读取失败必须区分，不能均回 NOT_REGISTERED”；S01–S10 fixtures 按 reasonCode 断言。
- 关联 LOW：同文件 `:213` `verificationEditable = current || verification != null` 与 `:222` 的 STALE 原因码可同时成立，自相矛盾。
- 建议：candidate 当前但未发布时 catalog/analysis 返回 `NOT_STARTED` 与 `MODEL_DELIVERY_PUBLICATION_REQUIRED` 之类的前置原因码；STALE 仅在 `!current` 时使用。

### R04 MEDIUM · F2/T13 · 旧数据湖归并的冲突分支会让外层事务失败

- 位置：`source/dts-analytics/src/main/java/com/yuzhi/dts/analytics/service/PlatformAnalyticsDatabaseRegistrationService.java`，`ensureDataLakeDatabase`（`@Transactional`）→ `adoptLegacyDataLake` → `PlatformAnalyticsDatabaseBindingWriter.insert`（REQUIRES_NEW）。
- 现象：`candidate` 在外层持久化上下文中已被 `setTenantId/setPlatformDataSourceId` 置脏，内层唯一冲突返回赢家后，外层提交时仍会 flush 这条脏实体并再次撞 `uk_analytics_database_tenant_platform_source`。
- 影响：单实例（T13-A 边界）概率低；多实例或并发启动时必现，与 T13 契约“并发重试……无重复连接”不符。
- 建议：冲突分支 `entityManager.detach(candidate)` 或改为先查后更新的原生 CAS；`hasUnresolvedLegacyBinding`/`adoptLegacyDataLake` 的 `findAll()` 全表扫描可改为按 details_json 精确查询（LOW）。

### R05 MEDIUM · F1/T02、T04 · 键一致性对存量模型没有兼容证据

- 位置：`ModelSpecStageGateService.validateImplementationKeyContract`（`:689-719`）、`ModelingDbtCompiler.validateGrainKeyContract`（`:761-778`）、`ModelSpecCompilerProjection.java:424`（去掉 `.distinct()`）。
- 现象：grain.keys 与 KEY 字段集合必须严格相等且无重复。前端保存时 grain.keys 由 KEY 字段派生（`modelWorkbenchService.ts:993,1009`），新模型一致；历史通过导入或旧 API 写入、两者不一致的模型进入 IMPLEMENTATION_READY 或编译会被新错误码阻断。
- 缺口：T01 domain-profile“复合键重复/空值、旧 implementationPolicy 与当前 settings 差异”至今未实测，Sprint 完成标准“旧模型可读取、不静默覆写”通过，“不因规则冲突被误拦截”无证据。
- 建议：T01 补一次只读存量扫描（grain.keys 与 KEY 不一致、重复键的模型数），结果为 0 则关闭；否则在阶段门禁给出修复路径而非仅阻断。

### R06 LOW · F1/T03 · 集合 B 两端定义不一致

- 位置：`ModelSpecStageGateService.java:266`（`standardBindings(view)` 按 fieldName 建 map，丢掉 fieldName 为空的绑定）与 `GovernanceModelSpecStandardEvidenceAdapter.java:22-30`（遍历全部 bindings）。
- 影响：fieldName 为空但带标准引用的畸形绑定，一端认为 B 为空不调证据服务，另一端会评估。契约要求 B 唯一定义。

### R07 LOW · F2/T10 · 读侧每次全量列计划

- 位置：`ModelReleaseCandidateApplicationService.authorizeRead`（`:1587-1596`）调用 `planReadAccess.listPlans()` 全量列表后线性查找。交付状态列表页每行一次；计划数少时可接受，记录为性能提示。

## 二、规划缺陷

### R08 HIGH · F3 与同日编码方向冲突

- F3 契约（`assets/F3-modeling-data-boundary.md`）以 `f0d081b90`（09-07 09:32）为账本基线，宣布模型页 W3 质量配置、W4 治理入口由数据模块接手；T18 要求“移走 W3/W4 中业务质量规则和资产/分析编辑入口到数据上下文”。
- 同日 13:26 的 `264ce64ac` 与 `it/20260907-target-quality-entry-fix.md` 仍在扩建模型页“配置质量规则”面板（`ModelTargetQualityPanel.tsx` 281 行、`ModelMaterializationActions.tsx`）；`it/手工验收用例-20260907.md` IT-09 要求“质量入口优先使用 W3”。
- 结果：IT-09（W3 质量闭环）与 IT-21（模型页无资产治理表单）是互斥通过标准。README 称 F3 契约“为本轮增量规范”，却没冻结当前包按哪一套验收。
- 建议：用户拍板其一；若维持 F2 为本轮交付，F3 README 与 Sprint README 的“被替代”措辞改为“下一轮替代”，并冻结 IT-09/IT-21 的生效版本。

### R09 MEDIUM · F3 K33 漏列 DDL-only 执行缺口

- 现有执行链只有 dbt：`ModelImplementationExecutionPlanner` → `ModelingDbtCompiler` → Airflow `dts_release_build_*` dbt build。dbt 无法在上游关系不存在时“只建空表、不查上游”，DWS→DWD→ODS 的结构物化还有依赖顺序问题。
- F3 第 6 节列的四项缺口（模型契约、发布副作用、接入行为、资产身份）没有“SCHEMA_ONLY 的执行引擎与多层顺序”；T17 又写“复用现有 ExecutionPlanner/DbtCompiler/候选运行”，二者不可兼得。
- 建议：T15 增加第五项缺口，明确 SCHEMA_ONLY 走独立 DDL 执行（JDBC 建表并复用物理表 observation 核验）还是 dbt `--empty`/占位上游方案，并给出多层顺序规则。

### R10 LOW · F3 K31 与 F1 K1 冲突

- F1 README K1 仍写“modelType 四类”，F3 K31 拟增 `SOURCE`；F3 已列为缺口 1，但 F1 README 与 `model-spec-v2.schema.json`、TS 契约（C20）未标注将被扩展。建议 F1 K1 加一句“F3/T15 冻结后扩展”。

## 三、文档与流程

### R11 HIGH · HEAD 之后的改动没有测试证据

- Sprint README“最新编码状态 `32b7e1309`、44/44”之后又有 25 个产品提交，直至 `1ad474167`。
- 09-07 五份修复记录（`it/20260907-authoring-version-chain-fix.md`、`delivery-status-read-transaction-fix.md`、`materialization-finalize-and-logs-fix.md`、`offline-runtime-upgrade-fix.md`、`target-quality-entry-fix.md`）全部标注“未运行测试、未编译”，涉及事务边界（NOT_SUPPORTED）、草稿基线 CAS SQL（`DbtImplementationDraftRepository.alignAuthoringBase`）、Airflow 工厂收尾顺序、升级脚本。
- 以下 12 个提交在 worklog 内零引用：`2cb2a0958 4e850f7d1 151b2558d 43dec1eee 2e815ddad df0384d02 5c9b4ef94 b8038e7a3 264ce64ac 43c1055b1 701fb81d6 01830c7d7`。
- README“当前运行镜像 296”与 IT 记录中已部署 `d156c7f36`、`17f7738d6`、`fe2d2562a` 矛盾。
- 与 F1/F2 DoD 的 RED→GREEN 要求不符。建议：在部署目录对 HEAD 跑一次 Java 专项 + Vitest + 前端构建并归档；README 的“最新编码状态”“当前运行镜像”改为引用单一事实源（`formal-validation-and-delivery-evidence-20260907.md`）。

### R12 MEDIUM · 状态口径不一致

| 文档 | 说法 |
|---|---|
| `assets/T09-contract-freeze.md:90` | T10–T14 保持 DRAFT |
| `features/F2/README.md` Task 表 | T10–T13 IN_PROGRESS |
| `it/evidence/current-environment/goal-code-gap-review-20260906.md` | T11/T12/T14 DRAFT |
| Sprint README 统计 | IN_PROGRESS=14 |
| Sprint README Gate G3 | “正式双镜像” |
| T14 / release-plan | 三镜像 + Airflow extra 运行文件 |

建议：以 Feature README 的 Task 表为唯一状态源，其余文档只写日期快照并标注“见 Feature 表”。

### R13 LOW · 验收用例缺口

- `it/手工验收用例-20260907.md` 只到 IT-18；`it/README.md` 已规划 IT-19–IT-24 但未进手工清单。
- F3 说“冲突的旧页面断言由新 IT 替代”，未列出哪些 IT-09/IT-15/IT-18 断言作废。

## 四、核对过且未发现问题

- T04：`dts_unique_combination` 由 `DbtConfigService.ensureWorkspaceBootstrap` 启动时写入，部署目录 `services/dts-dbt/macros/dts_unique_combination.sql` 已存在；编译产物对多键生成组合唯一性、单键生成 unique、无键不生成。
- T03：R 只检查策略必绑缺失，B 覆盖全部已声明绑定，NONE 不再提前 return；与契约一致。
- T06：`normalizeModelDraftImplementation` 不再用 capabilities 默认值替换显式 FULL/[]；`implementationConfiguration` 区分未设置与显式值。
- T02/T10-B2：`validateCreate` 不再要求业务归属，`validateDeliverableCreate` 保留；前端移除创建期归属校验与后端一致。
- T12：`catalog_dataset.version` 的 `@Version`（primitive long）对既有 `findById → save` 与 `new + setId + save` 路径无副作用；前端按 `catalog-dataset:{id}:{version}` 拼 If-Match，与 GET 返回体一致。
- T10：`ModelDeliveryStatusQueryService` 与 `ModelReleaseCandidateApplicationService` 新增读方法均为 `readOnly`，`workspaceForCurrentModel` 不创建候选；`technicalAuthorized=true` 与既有 `ModelAuthoringDraftResource` 行为一致，非本轮新增。
- 交付状态读取 500 的 NOT_SUPPORTED 事务隔离修复方向正确（见 R11，尚未运行测试）。

## 五、建议处理顺序

1. R01：修 `CandidatePublicationRepository` 发布投影对 owner/description 的覆盖，并让原生更新递增 version。
2. R11：部署目录对 HEAD 跑专项测试与构建，归档日志；同步 README 状态行。
3. R08：用户拍板 IT-09 与 IT-21 哪一套为本轮验收标准，同步 F2/F3 README 与手工用例。
4. R03、R04、R05：随下一批修复一起处理，各补一条回归用例。
5. R02、R12、R13：文档修订，不阻塞代码。
