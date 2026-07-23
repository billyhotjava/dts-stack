# T07：建立模型类型与分层依赖矩阵并收敛 ODS 入口

**优先级**：P0
**状态**：IN_PROGRESS
**依赖**：F2-T02、F2-T03、F3-T01、F3-T06、F6-T02

## 目标与用户结果

用户创建模型时只选择业务语义类型，不再自行组合容易冲突的“模型类型 + 目标数仓分层”。系统依据类型明确目标层、允许的上游证据和阶段门禁：

- `ODS_RAW`、`ODS_STANDARDIZED`、`STG` 是数据接入/技术处理层，不属于维度、明细、汇总、应用四类业务模型；
- `DIMENSION`、`FACT` 的目标层固定为 `DWD`；
- `SUMMARY` 的目标层固定为 `DWS`；
- `APPLICATION` 的目标层固定为 `ADS`。

因此，“创建 ODS”不从四类模型表单开始。已有 ODS 表通过连接、元数据同步和 WarehousePlan 来源盘点登记；尚未产生的 ODS 表通过接入映射/同步任务创建。完成 ETL/ELT 不是保存 DWD 逻辑草稿的前置条件，但进入实现前必须具备本 Task 规定的有效上游证据。

## 问题来源

2026-07-23 人工测试发现，四类模型新建表单仍可把当前模型的目标层选为 ODS，同时来源区又要求选择 ODS 表。这产生了两个相互冲突的问题：

1. 用户无法判断所选 ODS 表是“正在设计的目标表”还是“已经存在的上游输入”；
2. `modelType` 与 `layer` 可形成 `FACT + ODS`、`SUMMARY + DWD` 等无效组合，后续编译、血缘和发布只能再猜测其真实语义。

F3-T06 已拆分“目标模型”和“上游输入”，本 Task 继续冻结模型类型、目标层、上游层和实现方式之间的依赖矩阵，消除剩余歧义。

## Canonical 类型与分层矩阵

| 产品对象 | 所有者/入口 | 是否为四类 ModelSpec | 目标层 | 允许的直接物理上游 `sourceRefs` | 允许的上游模型 `dependsOn` | `generationStrategy` |
|---|---|---:|---|---|---|---|
| ODS 原始接入 | 数据集成、元数据同步、来源盘点 | 否 | `ODS_RAW` | 外部源表/文件/API | 不适用 | 由接入任务持有，不写入四类 ModelSpec |
| ODS 标准化接入 | 数据集成、清洗映射、来源盘点 | 否 | `ODS_STANDARDIZED` | `ODS_RAW` 或外部源 | 不适用 | 由接入/转换任务持有 |
| STG 临时处理 | SQL/dbt/调度技术节点 | 否 | `STG` | `ODS_RAW`、`ODS_STANDARDIZED` | 不适用 | 由实现产物持有，不进入业务模型台账 |
| 维度表 | 维度目录 | 是，`DIMENSION` | `DWD` | `ODS_RAW`、`ODS_STANDARDIZED`、`STG`，或当前计划已确认的存量或外部管理 `DWD` 物理资产 | 当前契约不以 `dependsOn` 替代维度输入 | 可与 `sourceRefs` inclusive OR；用于公共码表、人工 Seed 等受控生成 |
| 明细表 | 模型中心 | 是，`FACT` | `DWD` | `ODS_RAW`、`ODS_STANDARDIZED`、`STG`，或当前计划已确认的存量或外部管理 `DWD` 物理资产 | 仅锁定 revision 的 `FACT@DWD`；维度通过 `dimensionRefs` 引用，不进入 `dependsOn` | 不作为 FACT 输入豁免 |
| 汇总表 | 模型中心 | 是，`SUMMARY` | `DWS` | 不作为 canonical 创建输入 | 锁定 revision 的 `DIMENSION/FACT@DWD` 或 `SUMMARY@DWS` | 不适用 |
| 应用表 | 模型中心 | 是，`APPLICATION` | `ADS` | 不作为 canonical 创建输入 | 锁定 revision 的任意合法 `DWD/DWS/ADS` 四类 ModelSpec | 不适用 |

约束说明：

1. 四类模型的目标层由 `modelType` 自动确定并只读展示，提交时服务端再次校验，不能靠构造 API 绕过。
2. `sourceRefs.layer` 表达上游输入层，不得回写或推导当前模型的目标层。
3. 同层派生能力继续保留：FACT 可依赖 `FACT@DWD`，SUMMARY 可依赖 `SUMMARY@DWS`，APPLICATION 可依赖 `APPLICATION@ADS`；所有同层依赖都必须锁定 revision、CURRENT、无环并有权访问。
4. `DIMENSION@DWD` 通过 `dimensionRefs` 参与分析维度关系，不作为 FACT 的 `dependsOn`；SUMMARY 可在 `dependsOn` 中消费 DIMENSION/FACT@DWD 或 SUMMARY@DWS，APPLICATION 可消费任意合法 DWD/DWS/ADS 四类模型。
5. 旧来源若只有模糊 `ODS` 标签，不自动猜成 `ODS_RAW` 或 `ODS_STANDARDIZED`；来源盘点必须显式分类后才能作为实现证据。
6. `generationStrategy` 只解决 DIMENSION 的受控生成，不把 ODS/STG 重新包装成业务 ModelSpec。
7. `sourceRefs.layer=DWD` 只表示当前计划来源盘点中已确认的存量或外部管理物理资产；若上游已有 canonical ModelSpec，必须优先使用锁定 revision 的 `dependsOn`/`dimensionRefs`，不得用物理表引用规避版本和血缘门禁。

## 分阶段依赖门禁

| 模型类型 | `DRAFT_SAVE` | `IMPLEMENTATION_READY` | `RELEASE_READY` |
|---|---|---|---|
| DIMENSION → DWD | planId、已确认 domainId、名称、定义、维度键；目标层必须为 DWD；输入和生成策略可空 | KEY 字段闭合、SCD；有效 `ODS/STG/DWD sourceRefs` 或有效 `generationStrategy` 至少一种，组合时全部校验 | 实现门禁 + 当前 revision 的标准、质量、权限、产物和血缘证据 |
| FACT → DWD | planId、已确认 domainId、名称、grain；目标层必须为 DWD；`sourceRefs/dependsOn` 可均为空 | factShape、timeSemantics；有效 `ODS/STG/DWD sourceRefs` 或锁定 revision 的 FACT@DWD `dependsOn` 至少一种，组合时全部校验；DIMENSION 使用 `dimensionRefs` | 实现门禁 + 当前 revision 的标准、质量、权限、产物、依赖和血缘证据 |
| SUMMARY → DWS | planId、已确认 domainId、名称、聚合粒度、至少一个锁定 revision 的 DIMENSION/FACT@DWD 或 SUMMARY@DWS `dependsOn`；目标层必须为 DWS | 聚合表达式、刷新策略、上游 CURRENT 且无环；允许 SUMMARY@DWS 同层派生 | 实现门禁 + 当前 revision 的质量、权限、产物、依赖和血缘证据 |
| APPLICATION → ADS | planId、已确认 domainId、名称、消费场景、输出粒度、至少一个锁定 revision 的合法 DWD/DWS/ADS 四类模型 `dependsOn`；目标层必须为 ADS | 输出字段、刷新策略/SLA、上游 CURRENT 且无环；允许 APPLICATION@ADS 同层派生 | 实现门禁 + 服务/报表权限、质量、产物、依赖和血缘证据 |

所有阶段都先验证 `modelType → targetLayer` 不变量。发布阶段不得用已过期 revision 的构建、测试或上游证据替代当前 revision。

## 稳定 blocker 与修复入口

| blocker | 触发条件 | repairRoute |
|---|---|---|
| `MODEL_SPEC_TYPE_LAYER_MISMATCH` | 四类模型目标层与冻结矩阵不一致，或试图以 ODS/STG 作为四类模型目标层 | 当前模型设计页；目标层由系统按类型修正；ODS 建设入口为后续 UI 收敛项 |
| `MODEL_SPEC_INPUT_KIND_NOT_ALLOWED` | 当前类型填写了不接受的 `sourceRefs/dependsOn/generationStrategy` | 当前模型“上游输入”区域 |
| `MODEL_SPEC_UPSTREAM_LAYER_NOT_ALLOWED` | `dependsOn` 上游层违反矩阵，或 DWD 反向依赖 DWS/ADS | 当前模型“上游输入”区域 |
| `MODEL_SPEC_DEPENDENCY_INVALID` | `dependsOn` 未锁定明确 revision | 上游模型选择器 |
| `MODEL_SPEC_UPSTREAM_EVIDENCE` | 已锁定 revision 不再 CURRENT/可访问 | 依赖状态与版本升级操作 |
| `MODEL_SPEC_FACT_INPUT_REQUIRED` | FACT 进入实现时 `sourceRefs/dependsOn` 均无有效证据 | 当前模型“上游输入”区域 |
| `MODEL_SPEC_DIMENSION_INPUT_REQUIRED` | DIMENSION 进入实现时来源和生成策略均无有效证据 | 当前维度“来源与生成方式”区域 |

blocker code 是 API、页面诊断和 E2E 的稳定契约。页面可以翻译客户语言，但不得按页面自造另一套 code。

服务端按以下顺序返回首要 blocker，保证同一请求在创建、详情和发布页得到一致诊断：业务模型类型/目标层不匹配 → 输入种类不允许 → 上游 revision 缺失/漂移 → 上游层不允许 → 类型专属输入缺失。其余 blocker 仍可作为完整诊断列表返回。

当前代码已使用上表 `MODEL_SPEC_*` code；不得再增加无 `SPEC` 前缀的同义 code。ODS 技术入口导航、历史 ODS/STG 专属迁移分类及其只读 UI 仍是本 Task 后续实现项，不得把设计目标描述为当前运行态已具备；兼容写拒绝完成分类后复用现有 `MODEL_SPEC_LEGACY_READONLY`。

## ODS/STG 入口与所有权

### 已有物理表

```text
数据连接
  → 元数据同步
  → 选择具体 Schema/表
  → 标记 ODS_RAW / ODS_STANDARDIZED（不能只写模糊 ODS）
  → 加入 WarehousePlan 来源盘点并确认
  → 作为 DWD DIMENSION/FACT 的 sourceRefs 候选
```

### 尚未产生的 ODS 表

```text
数据连接
  → 接入映射/同步任务
  → 目标技术层 ODS_RAW 或 ODS_STANDARDIZED
  → 执行并完成元数据同步
  → 加入 WarehousePlan 来源盘点并确认
  → 作为 DWD 模型实现输入
```

STG 由 SQL/dbt/调度实现按需生成并随产物追踪，不提供独立业务模型创建按钮，也不进入维度/四类表台账。

## 兼容与迁移

1. 目标兼容行为：历史 `ModelSpec(layer=ODS|STG)` 支持 list/get、详情、审计、血缘和导出，并统一显示“旧技术层模型（只读）”；专属分类和 UI 尚待实现。
2. 新 create 已由 `MODEL_SPEC_TYPE_LAYER_MISMATCH` 禁止 ODS/STG；历史记录完成技术层分类后，update、进入实现和发布应复用现有 `MODEL_SPEC_LEGACY_READONLY`，不得通过下一次编辑静默改成 DWD。
3. 提供显式迁移建议：
   - 有真实物理表：迁为目录资产 + WarehousePlan SourceBinding；
   - 只有 SQL/dbt 技术节点：迁为实现产物/血缘节点；
   - 同时包含明确业务粒度：人工拆分为技术来源与 DWD ModelSpec，禁止自动猜测。
4. 迁移 dry-run、映射报告、校验和及回滚证据未齐前不物理删除旧记录。
5. 历史模糊 `layer=ODS` 保持原值只读；迁移时必须人工确认 RAW/STANDARDIZED，不做静默默认。

## 页面要求

1. 四类模型表单选择 `modelType` 后自动展示只读“目标数仓分层”，不再提供 ODS/STG 选项。
2. 若用户要建设 ODS，页面提供唯一的“前往数据接入”入口，并保留安全 `returnTo + planId`。
3. 上游区域随类型变化：
   - DIMENSION：规划物理来源 / 受控生成方式；
   - FACT：规划物理来源 / 锁定 revision 的 FACT@DWD 上游模型；DIMENSION 另走 `dimensionRefs`；
   - SUMMARY：锁定 revision 的 DIMENSION/FACT@DWD 或 SUMMARY@DWS 上游模型；
   - APPLICATION：锁定 revision 的任意合法 DWD/DWS/ADS 四类上游模型。
4. 选择器只展示矩阵允许且当前可访问、版本可验证的候选；服务端仍独立验证，不能把下拉过滤当安全边界。
5. 空态必须解释“草稿能否保存、实现前缺什么、去哪里补”，不得只显示“请选择来源”。
6. ODS 技术入口和历史 ODS/STG 专属只读/迁移 UI 当前尚待实现，验收前不得按文档目标宣称页面已存在。

## 影响范围

- `dts-platform` ModelSpec 类型/分层不变量、stage gate、依赖解析、兼容 reader 和稳定 blocker；
- `dts-platform-webapp` 创建/详情表单、目标层只读投影、按类型过滤上游和 ODS 修复导航；
- 数据集成、元数据目录、WarehousePlan SourceBinding 与实现产物的所有权说明；
- Sprint-67 API 契约、页面矩阵、人工 E2E 和发布 Go/No-Go。

## 验证矩阵

| 场景 | 预期 |
|---|---|
| 新建 DIMENSION/FACT | 目标层自动为 DWD 且只读 |
| 新建 SUMMARY | 目标层自动为 DWS，可选 revision-pinned、CURRENT、无环的 DIMENSION/FACT@DWD 或 SUMMARY@DWS |
| 新建 APPLICATION | 目标层自动为 ADS，可选 revision-pinned、CURRENT、无环的任意合法 DWD/DWS/ADS 四类模型 |
| 四类模型表单/API 提交 ODS_RAW/ODS_STANDARDIZED/STG | 拒绝并返回稳定入口 blocker |
| FACT DRAFT 无上游 | 可保存；实现门禁返回 `MODEL_SPEC_FACT_INPUT_REQUIRED` |
| FACT 选择 DWS/ADS 上游 | fail closed，返回 `MODEL_SPEC_UPSTREAM_LAYER_NOT_ALLOWED` |
| DIMENSION 只有有效 generationStrategy | 可进入实现；不伪造 sourceRef |
| SUMMARY/APPLICATION 上游未锁 revision 或已漂移 | fail closed，并提供版本修复入口 |
| 已有 ODS 表 | 通过元数据同步和来源盘点登记，不创建四类 ModelSpec |
| 尚未产生 ODS 表 | 从接入任务创建，执行和同步后再纳入来源盘点 |
| 完成后打开历史 ODS/STG ModelSpec | 可查看审计/血缘，编辑、实现和发布复用 `MODEL_SPEC_LEGACY_READONLY`；该专属分类/UI 当前待实现 |
| 直接构造 API 绕过前端 | 服务端按相同矩阵拒绝，数据库无新增非法组合 |

## E2E 与证据

部署后至少以真实 Chrome 95、认证 API 和 PostgreSQL 完成：

1. 四种 ModelSpec 的目标层自动投影和只读展示；
2. 每类允许/禁止上游候选和服务端绕过拒绝；
3. ODS 已有表与尚未产生表的两条技术入口及安全返回；
4. FACT 无上游草稿、实现 blocker 和补齐后的门禁变化；
5. revision 漂移、跨层反向依赖、循环依赖和无权候选的 fail-closed 行为；
6. 历史 ODS/STG ModelSpec 的只读详情、审计/血缘及迁移入口；
7. 数据库证明无新增 `modelType + targetLayer` 非法组合，blocker、审计和当前 revision 一致。

Mock API 截图只能证明布局和文案，不得替代后端矩阵、权限、revision 和数据库事实。

## 完成标准

- [x] 后端以单一矩阵校验四类 ModelSpec 的目标层、上游层和 revision，返回稳定 blocker。
- [x] 前端按模型类型自动投影只读目标层，并只展示允许的输入方式和候选。
- [ ] ODS_RAW/ODS_STANDARDIZED/STG 从四类模型入口移除，并提供数据接入/来源盘点修复路径。
- [ ] 历史 ODS/STG ModelSpec 只读兼容、迁移报告和回滚边界通过验证。
- [ ] 聚焦 Java/TypeScript 契约测试、一次最终 production build 和 GitNexus 范围审计通过。
- [ ] 真实 Chrome 95/API/PostgreSQL 完成允许/禁止组合、ODS 双入口和旧记录只读 E2E。
- [ ] F3-T06 与本 Task 的来源语义共同通过增量 Go/No-Go 后，才可关闭 F3 和 Sprint-67。

聚焦实现与自动化证据见 [f3-t07-model-layer-dependency-matrix.txt](../../it/evidence/backend-contract/f3-t07-model-layer-dependency-matrix.txt)。本 Task 仍保持 `IN_PROGRESS`，不以聚焦测试替代 production build、部署后 Chrome 95/API/PostgreSQL 或历史数据迁移证据。
