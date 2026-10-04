# Sprint-69 构建、质量与发布交付工作台设计

## 1. 设计目标

把第六步从“查看一个阻塞并跳往 SQL/dbt”的导航节点，改为能回答以下问题的交付控制面：

1. 本次准备发布哪些模型和版本？
2. 每个版本生成了什么产物，实际执行了哪次 dbt 运行？
3. 哪些质量规则通过或失败，证据是否仍属于当前 revision？
4. 谁提交、谁批准、谁发布，是否满足职责分离？
5. 外部注册是否完整，失败后如何重试？
6. 发生问题时如何回到精确模型、运行和发布记录，或执行回滚？

## 2. 领域边界

| 对象 | 唯一所有者 | 本 Sprint 使用方式 |
|---|---|---|
| WarehousePlan | modeling warehouse plan | 计划上下文和 StageProjection |
| ModelSpec revision | canonical modeling | 候选条目锁定 ID/revision/checksum |
| ReleaseCandidate | model delivery | 本次交付范围和状态 |
| Artifact | model lifecycle | SQL/schema/test/doc 产物和 checksum |
| External dbt run | ETL/ops | 服务端验证后的执行证据 |
| QualityRule | governance quality | 保存稳定 ruleId/version 引用 |
| QualityRun/CheckResult | quality execution | 保存本次候选的规则级结果 |
| Review/Release/Rollback | model lifecycle | 显式事件与审计 |
| Catalog/BI/Lineage | 专业 owner | 发布后的分步注册引用 |

ReleaseCandidate 不复制上述 owner 的正文。

## 3. 核心契约

```text
ReleaseCandidate
  candidateId
  tenantId
  planId
  environment
  status
  version
  createdBy/createdAt
  submittedBy/submittedAt
  approvedBy/approvedAt
  releaseEventId

ReleaseCandidateEntry
  candidateId
  modelSpecId
  revision
  modelChecksum
  implementationMode
  selector
  target
  status
```

所有 mutation 使用强 ETag：

```text
"release-candidate:{candidateId}:{version}"
```

候选条目创建时由服务端读取当前 ModelSpec，客户端不得提交可信 checksum。条目进入 BUILDING 后候选范围冻结；模型 revision 漂移时状态变为 STALE，必须创建或刷新新候选。

## 4. 状态机

```text
DRAFT
  → BUILDING
  → BUILD_FAILED | BUILT
  → QUALITY_RUNNING
  → QUALITY_FAILED | QUALITY_PASSED
  → REVIEW_PENDING
  → REJECTED | APPROVED
  → PUBLISHING
  → PARTIAL | PUBLISHED
  → ROLLED_BACK
```

约束：

- `REJECTED`、`STALE`、`ROLLED_BACK` 是当前候选的终态；再次交付必须执行
  `CREATE_REPLACEMENT_CANDIDATE`，使用新 candidate ID 从 `DRAFT/version 1` 开始。
- replacement candidate 不复用旧候选的提交、审批、发布、回滚审计或构建证据；旧候选及证据保持只读可追溯。
- `PARTIAL` 表示 ModelSpec 已发布但专业注册未完整；只允许重试失败步骤或执行回滚。
- `PUBLISHED` 是第六步完成的最低发布状态；StageProjection 仍需核验候选和证据 CURRENT。
- 任一状态发现 revision/checksum 不匹配，投影为 STALE 并拒绝向后迁移。

## 5. 构建与运行证据

静态产物与外部执行分开：

- compile lifecycle：生成 SQL/SCHEMA/TEST/DOC，并保存 artifact checksum；
- external build：保存 selector、target、invocationId、开始/结束时间、结果和日志引用；
- test evidence：从持久化 dbt run 和规则结果中验证，不接受客户端自报 PASS；
- 候选多模型时，每个 entry 都必须有自己的 selector 和结果，不能把一个聚合 run 无条件回写到当前 activeModel。

## 6. 质量门禁

默认规则包由 ModelSpec 契约投影生成，但规则正文仍归质量模块：

| 类别 | 默认检查 |
|---|---|
| 结构 | 字段存在、类型兼容、必填字段非空 |
| 粒度 | grain key 唯一、KEY 字段一一对应 |
| 维度 | natural/surrogate key、SCD2 区间不重叠、唯一 current |
| 引用 | dimensionRefs、dependsOn 和公共码值参照完整 |
| 业务 | accepted values、表达式和客户显式规则 |
| 运行 | 新鲜度、行数下限、波动阈值、失败率和耗时 |
| 对账 | FACT→SUMMARY、SUMMARY→APPLICATION 的聚合或计数对账 |

`QualityCheckResult` 至少保存：

```text
candidateId, modelSpecId, revision, ruleId, ruleVersion,
severity, threshold, actual, status, checkedAt,
datasetSnapshotRef, sampleErrorRef
```

BLOCKER 失败阻止审核；WARN 需要在审核界面显式确认。超过新鲜度窗口的 PASSED 结果变为 STALE。

## 7. 审核、发布与回滚

- 建模维护者可构建、运行质量并提交审核。
- 发布审核者可批准或驳回，但 `approvedBy != submittedBy`。
- 发布操作者必须拥有发布权限；可与审核者为同一受权角色，但仍不得是提交人。
- publish 只消费当前 APPROVED candidate 和当前 ModelSpec revision。
- Catalog、BI、Lineage 分别保存 ATTEMPTING/SUCCEEDED/FAILED；重试跳过已成功步骤。
- rollback 新增事件和反向专业动作，保留旧 release、registration 和 run。

## 8. UI 信息架构

### 8.1 计划级交付工作台

路径保持 `/modeling/plans/:planId/implementation`：

```text
计划上下文 + 当前候选 + 唯一主动作
├─ 候选范围：模型、类型、层、revision、漂移状态
├─ 构建：artifact、dbt run、日志、失败修复
├─ 质量：通过率、BLOCKER/WARN、规则级明细
├─ 审核发布：提交、审批、发布、注册步骤
└─ 历史：旧候选、发布、回滚和操作者
```

主动作由后端候选状态决定，不由前端推导：

- DRAFT：开始构建
- BUILD_FAILED：修复并重新构建
- BUILT：运行质量检查
- QUALITY_FAILED：查看质量问题
- QUALITY_PASSED：提交审核
- REVIEW_PENDING：等待审核/进入审核
- APPROVED：发布
- PARTIAL：重试失败注册
- PUBLISHED：查看发布成果

### 8.2 模型详情

模型详情继续负责模型 revision：

- 展示实现门禁和当前候选状态；
- 展示 lifecycle timeline；
- 可进入对应候选或 SQL/dbt 实现；
- revision 漂移时显示旧证据已失效及“加入新候选”动作。

### 8.3 SQL/dbt 页面

保留高级执行职责：

- 锁定一个 canonical candidate entry，或显式使用批次模式；
- 编辑 SQL、compile/build/test、查看运行日志；
- 回写 entry 证据；
- 不再自动 submit review、approve 和 publish；
- 将 lifecycle 上下文、运行提交、结果轮询和页面渲染拆成独立 hook/component，降低超大页面耦合。

### 8.4 通用页面边界

- `/governance/rules` 和 `/governance/quality` 继续维护通用规则与报告，支持按 model/candidate 上下文过滤。
- `/ops/release-governance` 继续做平台级健康聚合，不承接模型发布主动作。
- 不新增一级菜单。

## 9. 错误与恢复

| 场景 | 行为 |
|---|---|
| ModelSpec revision 漂移 | 候选 STALE，保留旧证据，只能刷新或新建候选 |
| dbt run 不存在/未终态/selector 不匹配 | fail closed，不写 PASSED |
| 质量 owner 暂不可用 | UNKNOWN，不显示为通过 |
| ETag 冲突 | 保留表单和候选选择，加载最新版后显式重试 |
| 审核人与提交人相同 | 403/422，前端不展示可执行批准 |
| 外部注册部分失败 | PARTIAL，显示失败步骤和重试 |
| rollback 专业动作部分失败 | ROLLBACK_PARTIAL，保留步骤审计并可重试 |
| 页面或列表读取失败 | 保留已加载数据，提供局部重试，不伪装为空态 |

## 10. 测试策略

- Unit：状态机、候选范围、漂移、质量阈值、职责分离、StageProjection。
- API：强 ETag、幂等、租户/部门/角色、跨候选和跨模型伪造。
- PostgreSQL：canonical 全链路与迁移/回滚。
- dbt：最小真实项目 compile/build/test 和失败数据集。
- UI source-contract：单一主动作、参数白名单、无自动批准、Chrome 95 语法。
- Browser：真实登录/API/PostgreSQL/dbt 的发布与失败恢复旅程。

## 11. 设计完成标准

只有当“候选范围明确、证据绑定精确、质量结果结构化、审核职责分离、UI 可操作、真实环境可回滚”同时成立，第六步才算完成。台账条数、页面显示绿色、预置 PUBLISHED 数据或 mock 截图均不是单独完成证据。
