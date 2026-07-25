# Sprint-70：dbt 模型包转换与普通模型导入闭环

**时间**: 2026-07  
**状态**: IN_PROGRESS  
**类型**: Model Import / Canonical ModelSpec / UI Journey / Safe Migration  
**目标**: 将已有 dbt 项目转换为可预检、可确认、可幂等导入的 DTS 模型包，在当前建设计划下自动创建四类 canonical ModelSpec，并将无法安全降级的 SQL 保留为受治理的 dbt 实现。

## 背景

当前平台已有两类导入能力，但都不能完成普通模型的完整导入：

- 高级 SQL/dbt 页面可以导入单个 SQL、ZIP 或 dbt 文件，但主要写入旧 SQL 模型工作区，缺少计划级预检和 canonical ModelSpec 编排。
- `/api/modeling/vnext/dbt/import` 已能读取单个 manifest 节点并绑定 `DBT_MANAGED` artifact，但要求 ModelSpec 和 active implementation 预先存在。

本 Sprint 不重写已有 dbt parser，也不把任意 SQL 伪装成普通可视化实现。新增能力聚焦：

1. 从 `manifest.json + catalog.json + schema.yml/meta.dts` 生成版本化 `dts-model-package.json`。
2. 将候选分类为 `DESIGNER_GENERATED / DBT_BACKED / BLOCKED`。
3. 在零写入预检后，按拓扑顺序创建 ModelSpec、锁定来源与上游 revision，并建立 implementation/artifact 绑定。
4. 在建模工作台和模型中心提供同一个导入向导，形成“计划上下文 → 预检 → 确认 → 结果”的完整路径。

## 用户路径与 UI 决策

- 建模工作台当前计划卡片提供“导入已有模型”次主按钮；未选择计划时禁用并说明原因。
- 模型中心标题区提供同一入口；携带 `planId` 时锁定计划，无上下文时先选择建设计划。
- 两个入口复用同一个四步向导：上传模型包、确认上下文、查看预检矩阵、确认并查看结果。
- 高级 SQL/dbt 页面保留专家兼容入口，但不作为首次用户主路径；不新增一级菜单。
- 导入成功后优先返回模型中心，并提供查看单个 ModelSpec、返回当前计划和仅重试失败项。

详细设计见：

- [模型包架构与转换策略](assets/model-package-architecture.md)
- [预检与应用 API 契约](assets/import-api-contract.md)
- [导入向导 UI 设计](assets/import-ui-flow.md)
- [集成验收](it/README.md)

## Feature 列表

| ID | Feature | Task 数 | 状态 |
|----|---------|---------|------|
| F1 | 模型包契约与转换器 | 4 | DONE |
| F2 | 导入预检与差异分析 | 4 | DONE |
| F3 | canonical 模型应用引擎 | 4 | IN_PROGRESS |
| F4 | 建模工作台导入体验 | 5 | READY |
| F5 | 集成验收与交付 | 4 | READY |

**统计**: READY=9, IN_PROGRESS=4, DONE=8, BLOCKED=0

## 既有能力复用与去重

- 复用 Sprint-60 的 manifest/SQL 解析、漂移和 artifact 导入契约，不再新建第二套 dbt parser。
- 复用 Sprint-65/67 的 ModelSpec、ModelImplementation、PhysicalAssetRevision 四层边界。
- 复用现有 ModelSpec 创建、来源版本校验、依赖规则、implementation claim、CAS 和幂等机制。
- 旧 `/modeling/sql-models/import`、`/batch-import` 仅作为兼容入口保留，不升级为 canonical 主链。
- Sprint-69 继续负责构建、质量、发布与回滚；本 Sprint 只交付可进入该工作台的模型与实现绑定。

## 范围边界

### 本 Sprint 完成

- 包契约、JSON Schema、生成器、预检、差异、确认应用、幂等和审计。
- 普通 ModelSpec 自动创建，以及 `DESIGNER_GENERATED / DBT_BACKED` 双实现分流。
- STG/ephemeral 技术节点保留在实现图中，但不创建四类 ModelSpec。
- 工作台/模型中心双入口和共享导入向导。
- PJM 预算链真实数据库、权限、来源版本和 Chrome 95 验收。

### 本 Sprint 不完成

- 任意 SQL 到可视化 AST 的通用反编译。
- 新增 ODS/STG canonical 模型类别。
- 新增一级菜单或第三套模型台账。
- 自动猜测业务分类、粒度、字段角色、SCD 策略、消费场景或安全等级。
- 替代 Sprint-69 的真实 dbt build/test/release。

## 执行顺序

`F1 → F2 → F3 → F4 → F5`

- F4 的页面壳层可在 F2 契约冻结后并行，但确认应用按钮必须等待 F3。
- F1-F4 全部实现后，再统一执行一次后端组合测试、一次前端 production build 和一次 Chrome 95 真实验收。
- Task 阶段只运行与修改范围直接相关的 focused test，不进行反复全量编译。

## 完成标准

- [ ] dbt 项目可稳定生成符合 JSON Schema 的 `dts-model-package.json`。
- [ ] package checksum 覆盖字段、配置、依赖、SQL 和业务语义覆盖项。
- [ ] 预检严格零写入，并明确 `CREATE / UPDATE / SKIP / CONFLICT / BLOCKED`。
- [ ] `source()` 能解析到当前计划已确认、当前版本可用的 SourceBinding。
- [ ] `ref()` 能按拓扑顺序转换为 revision-pinned ModelSpec/implementation 引用。
- [ ] STG/ephemeral 节点不会错误创建为四类 ModelSpec，也不会从实现依赖图丢失。
- [ ] 简单模型进入 `DESIGNER_GENERATED`；复杂 SQL 进入普通 ModelSpec + `DBT_BACKED`，不伪造字段映射。
- [ ] apply 可幂等重放；同键异载荷、preview 漂移和并发占用必须 fail closed。
- [ ] 建模工作台和模型中心打开同一导入流程，且计划上下文不会静默切换。
- [ ] 导入结果逐项可解释，并可跳转到生成的 ModelSpec。
- [ ] PJM 预算链完成真实 PostgreSQL、Spring Security、API、dbt artifact 和 Chrome 95 验收。
- [ ] 后端组合测试、前端 production build、文档和 Go/No-Go 证据齐全。
