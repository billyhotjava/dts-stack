# Sprint-69：模型构建、质量与发布交付工作台重构

**时间**：2026-07
**状态**：IN_PROGRESS
**类型**：Model Delivery / Quality Gate / Release Governance / UI Refactoring
**目标**：把数据建设第六步从跳转卡和零散台账重构为计划级交付工作台，使选定的 ModelSpec revision 能完成可审计的构建、质量、审核、发布、外部注册与回滚闭环，并以真实发布证据驱动 StageProjection。

## 1. 背景

Sprint-67 已建立 canonical `WarehousePlan`、`ModelSpec`、`ModelLifecycle` 和九站 `StageProjection`，但“构建、质量与发布”仍存在产品与证据断层：

- `/modeling/plans/:planId/implementation` 只有说明卡和 SQL/dbt 跳转，没有计划级交付上下文；
- StageProjection 以所有非归档模型的 `RELEASE_READY` 近似表示第六步完成，尚未要求真实 `RELEASE/PUBLISHED`；
- 计划没有“本次发布哪些模型”的候选范围，一个暂不发布的草稿可能阻塞整个阶段；
- `quality` 与 `tests` 共用一条 dbt TEST 证据，缺少规则、阈值、失败样本和新鲜度；
- SQL/dbt 页面在 build 成功后连续提交审核、批准和发布，没有职责分离；
- timeline、部分注册重试、rollback 和 run API 已存在，但未形成可操作 UI；
- 现有 PostgreSQL IT 主要覆盖旧 ModelingVNext 路径，尚未真实执行 canonical ModelLifecycle 全链路。

本 Sprint 不重做模型设计。第六步只消费稳定的 `modelSpecId/revision/checksum/implementationMode`；维度重构只影响最终 DIMENSION 专属 E2E，不改变交付控制面的主契约。

## 2. 方案比较与决策

| 方案 | 结论 | 原因 |
|---|---|---|
| A. 继续扩充计划详情中的静态台账 | 不采用 | 无法表达发布范围、状态迁移、失败恢复和版本证据 |
| B. 把全部能力继续堆入 `SqlModelingPage` | 不采用 | 页面已过载，且执行器不应同时承担计划控制面和审批 |
| C. 建立计划级 ReleaseCandidate，复用 lifecycle/dbt/质量 owner | **采用** | 保留已有后端资产，修正证据真值，并形成客户可验证的单一旅程 |

## 3. 产品主线

```text
WarehousePlan
  → 选择本次 ReleaseCandidate
  → 锁定 modelSpecId/revision/checksum
  → 生成并校验 SQL/schema/test/doc artifacts
  → 执行真实 dbt compile/build/test
  → 保存规则级 QualityRun/QualityCheckResult
  → 提交审核
  → 独立审批
  → 发布当前候选 revision
  → Catalog/BI/Lineage 分步注册与失败重试
  → StageProjection 第六步完成
  → 运行、回滚或返回精确模型修复
```

## 4. 全局约束

1. `WarehousePlan` 和 `ModelSpec` 仍是唯一计划与模型真值，不建立第二模型台账。
2. ReleaseCandidate 只保存稳定引用、候选范围和状态，不复制 ModelSpec 正文。
3. 所有构建、质量、审核和发布证据必须绑定 `modelSpecId/revision/checksum/candidateId/environment`。
4. dbt 外部 run 必须由服务端验证 selector、target、模型 revision 和终态；不能只信任客户端 `externalRunId`。
5. `RELEASE_READY` 只表示可提交审核，不表示第六步完成；只有真实发布事件满足完成策略才可 COMPLETE。
6. 通用质量模块继续拥有规则正文；交付模块只保存规则版本引用和运行结果。
7. 提交人与批准人必须不同；审批权限与建模维护权限分离。
8. 发布部分成功必须保留已成功步骤并允许幂等重试；回滚生成新事件，不删除历史。
9. `/modeling/plans/:planId/implementation` 是第六步唯一计划级入口；不新增一级菜单。
10. `SqlModelingPage` 保留高级编辑与执行职责，不再自动完成审核和发布。
11. Chrome 95、390px、真实 Spring Security、租户隔离、强 ETag、幂等、失败恢复属于 DONE 门禁。
12. mock 浏览器只能证明交互契约，不能替代真实 API/PostgreSQL/dbt 证据。
13. 每个 Task 必须满足其完成标准中的 UI 契约、UI 实现或 UI 真实验收项；后端成功但页面无入口、无结果或无恢复路径不能标记 DONE。
14. **统一测试窗口**：F1-F5 全部功能以及 F6 的验收代码、fixture 和检查矩阵完成前，只允许编写测试、静态检查和专项代码审查；不得按单个 Task 或 Feature 启动 Maven、pnpm、PostgreSQL、dbt、浏览器或部署测试。全部功能完成后，在 F6 统一执行一次整体测试与 Go/No-Go，避免重复启动环境造成数倍 token 和时间消耗。

## 5. Feature 列表

| ID | Feature | Task 数 | 状态 | 交付重点 |
|---|---|---:|---|---|
| [F1](features/F1-发布候选与证据真值/README.md) | 发布候选与证据真值 | 4 | IN_PROGRESS | 候选范围、CAS、状态与计划读模型 |
| [F2](features/F2-构建运行与版本绑定/README.md) | 构建运行与版本绑定 | 4 | READY | artifact、dbt run、版本漂移与修复 |
| [F3](features/F3-结构化质量门禁/README.md) | 结构化质量门禁 | 4 | READY | 默认规则包、规则级结果、阈值与新鲜度 |
| [F4](features/F4-审核发布回滚治理/README.md) | 审核发布回滚治理 | 4 | READY | 职责分离、发布、注册重试、回滚与 StageProjection |
| [F5](features/F5-交付工作台UI重构/README.md) | 交付工作台 UI 重构 | 5 | READY | 计划工作台、证据详情、显式审批发布和 SQL 页拆分 |
| [F6](features/F6-真实集成验收与发布/README.md) | 真实集成验收与发布 | 4 | READY | API、安全、PostgreSQL、dbt、Chrome 95 与 Go/No-Go |

**统计**：READY=21，IN_PROGRESS=3，DONE=1，BLOCKED=0

## 6. 依赖与执行顺序

```text
F1 发布候选与证据真值
 ├─> F2 构建运行与版本绑定 ───────┐
 ├─> F3 结构化质量门禁 ──────────┼─> F4 审核发布回滚治理
 └─> F5-T01/T02 UI 契约与壳层 ──┘        │
                                           └─> F5-T03/T04/T05 完整 UI
                                                        │
                                                        └─> F6 真实集成验收
```

- F2 与 F3 可在 F1 的候选主键和 CAS 契约冻结后并行。
- F5 可先完成 UX、视图模型和只读壳层；写动作等待对应 F2-F4 API。
- F6-T03 的 FACT/SUMMARY/APPLICATION 可先验收；DIMENSION/SCD2 场景等待 Sprint-67 四层模型接口稳定。
- Sprint-68 标准内容库可并行；本 Sprint 只消费稳定标准引用，不修改标准包内容。

## 7. 完成标准

- [ ] 计划负责人可创建明确范围的发布候选，不再由所有非归档草稿隐式决定第六步。
- [ ] 每个候选条目锁定 `modelSpecId/revision/checksum`，漂移后自动 STALE 并阻止后续动作。
- [ ] SQL/SCHEMA/TEST/DOC artifact 与真实 dbt compile/build/test 分开记录且可回链。
- [ ] 质量门禁保存规则版本、阈值、结果、失败样本摘要和数据快照时间，不再复用一个 TEST 布尔值。
- [ ] 审核提交、批准和发布为显式状态迁移，提交人与批准人不同。
- [ ] 发布部分失败可只重试失败的 Catalog/BI/Lineage 步骤，成功步骤不重复。
- [ ] 回滚生成可审计事件并恢复产品状态，不删除旧发布、运行和证据。
- [ ] StageProjection 第六步只在候选真实发布成功后 COMPLETE，PARTIAL、STALE 和 READY 均不算完成。
- [ ] `/modeling/plans/:planId/implementation` 可完成候选、构建、质量、审核、发布、重试和回滚主旅程。
- [ ] 模型详情显示当前 revision 的交付时间线；SQL/dbt 页面锁定 canonical 上下文且不再自动审批发布。
- [ ] canonical ModelLifecycle 在真实 PostgreSQL 中完成创建候选、构建、质量、审核、发布、部分重试和回滚。
- [ ] 最小 dbt 项目在真实 PostgreSQL target 执行 compile/build/test，并证明失败不能产生发布完成证据。
- [ ] Chrome 95 桌面和 390px、真实权限、跨租户、并发、失败恢复和返回链均通过。
- [ ] 25 个 Task 均按 Task/UI 验收矩阵提交对应证据，页面状态不依赖前端自行推导。
- [ ] 最终报告分别给出代码、测试、数据迁移、部署、浏览器和回滚六层结论。

## 8. 明确不做

- 不在本 Sprint 重构 DimensionDefinition 或四层模型内部字段。
- 不新建一级“发布中心”菜单，不把 `/ops/release-governance` 改成模型发布主线。
- 不建立第二套 dbt 项目或质量规则正文。
- 不用页面访问、按钮点击、toast 或客户端状态作为完成证据。
- 不在发布候选中复制 ModelSpec、标准、质量规则或资产正文。
- 不以 source-contract、mock 截图或预置 PUBLISHED 数据替代真实 canonical lifecycle E2E。

## 9. 权威资产

- [交付工作台设计](assets/build-quality-release-workbench-design.md)
- [页面能力与目标 UX 矩阵](assets/page-capability-and-target-ux-matrix.md)
- [Task/UI 验收矩阵](assets/task-ui-acceptance-matrix.md)
- [Feature/Task 依赖图](assets/feature-dependency-map.md)
- [实施计划](assets/implementation-plan.md)
- [IT 与交付证据计划](it/README.md)
- [2026-07-20～2026-07-24 周报](../weekly-report/weekly-2026-07-24.md)
