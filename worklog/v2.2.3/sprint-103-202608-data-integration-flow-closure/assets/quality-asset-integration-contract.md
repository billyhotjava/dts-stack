# ADR-103-02：接入后质量验证与资产可信证据契约

状态：`ACCEPTED_DESIGN`
日期：2026-08-27

## 决策摘要

1. 数据资产身份先于质量验证存在。目标端必须解析到既有 `CatalogAssetType.DATASET + CatalogAssetKey`，并关联正式 `CatalogDataset.id`；质量模块不创建第二个资产身份。
2. 质量采用两段式：接入过程中执行字段、类型、映射、权限、密级和可写性等接入校验；单次 execution 完整提交后，异步启动正式资产质量工作流。
3. 兼容字段 `qualityPolicyRef` 的正式格式已经确认是 `dataset:<uuid>`。UI 不让用户任意挑选另一资产的“策略”，而是在目标资产下开启“接入后质量验证”并展示该资产已有的正式规则绑定。
4. 接入成功与质量通过是两个独立事实。质量失败、工作流启动失败或证据过期不得改写接入 execution 的成功状态，但必须影响资产质量展示与消费资格。
5. 不新增 `TRUSTED`、`VERIFIED` 等资产生命周期枚举。“可信可用”只在当前接入版本具有有效质量证据且消费资格为 `ELIGIBLE` 时派生展示。

资产身份建立与运行证据更新是两次不同动作：design/admission 阶段必须解析既有数据集，或通过既有 catalog seam 预声明同一个 canonical datasetId；execution 成功后只向该身份追加接入观察/血缘证据，不在此时创建另一资产。

## 统一时序

```text
任务设计/准入
  → 解析或登记目标数据资产
  → 接入过程校验
  → execution 完整提交成功
  → 资产可见，质量证据待更新
  → after-commit 触发正式质量工作流
  → PASSED：更新当前质量证据并重新计算消费资格
  → FAILED/BLOCKED：保留资产和接入成功事实，限制消费并进入治理处置
  → RETRY_WAIT/EXHAUSTED：显示质量验证启动异常，不沿用旧证据冒充当前可信
```

“完整提交”指一个 execution 的批次、分区或快照边界，不是等待数据源永久停止。流式或持续增量任务按微批次/水位窗口产生证据。

## 领域边界

| 事实 | 唯一 owner | 本 Sprint 的责任 | 禁止做法 |
|---|---|---|---|
| 目标资产身份 | 目录 `CatalogAssetType + CatalogAssetKey` / `CatalogDataset` | 解析、引用、深链与一致性校验 | 以库表字符串或 `qualityPolicyRef` 再造资产身份 |
| 接入过程校验 | ingestion design/admission/staging pre-check | 展示错误并阻止非法配置/坏批次提交 | 将预检查冒充正式资产质量证据 |
| 正式质量执行 | `QualityWorkflowOrchestrator` + `QualityRunService` | 传递 execution 身份并消费结果 | 在编排画布复制规则 JSON 或执行器 |
| 接入执行账本 | `IngestionExecution` | 保存 revision、plan、质量 workflow/run 关联 | 用 Airflow dagRun 代替业务 execution |
| 资产质量投影 | catalog asset status/eligibility owner | 展示当前证据、原因与深链 | 质量结果覆盖生命周期或发布事实 |

## 设计契约

F0/T02 必须将以下结构逐字段映射到现有 DTO；字段可兼容扩展，但语义不可在实施期改写。

```text
DestinationAssetRef {
  assetType: "DATASET"
  assetKey: string
  datasetId: uuid
  resolution: RESOLVED | UNRESOLVED
}

PostIngestionQualityConfig {
  enabled: boolean
  policyRef: string | null          // enabled=true 时固定为 dataset:<datasetId>
  timing: "POST_COMMIT"             // 服务端常量，不提供任意时序选择
  boundRuleCount: int
}

QualityEvidenceRef {
  ingestionExecutionId: long
  qualityWorkflowId: uuid | null
  qualityRunId: uuid | null
  triggerStatus: NOT_CONFIGURED | TRIGGERING | TRIGGERED | RETRY_WAIT | EXHAUSTED
  evidenceState: MISSING | PENDING | CURRENT | STALE | TRIGGER_FAILED
  qualityStatus: UNKNOWN | RUNNING | PASSED | FAILED
  observedAt: instant | null
}
```

`triggerStatus` 是接入侧“是否成功登记质量工作流”的状态；`qualityStatus` 是质量 owner 的正式运行结果；两者不能合并成一个枚举。

## 一致性规则

1. `PostIngestionQualityConfig.enabled=true` 时，`policyRef.datasetId == destination.assetRef.datasetId`；不一致返回 422 `QUALITY_ASSET_MISMATCH`。
2. 目标资产未解析、不可读、密级不满足或不属于允许的数据湖范围时，不允许启用接入后质量验证；分别返回稳定 422/403。
3. `qualityPolicyRef`、目标资产引用和接入配置一起进入 revision/runtime snapshot 与 `planChecksum`；execution 冻结同一引用。
4. 正式触发的幂等身份继续使用目标 `datasetId + ingestionExecutionId`；同一 execution 不得创建第二个质量工作流。
5. 最新质量证据必须能证明属于当前目标资产和当前 execution。新 execution 完整提交后，旧 `PASSED` 立即降为 `STALE/PENDING`，直到当前工作流产生结果。
6. `EXHAUSTED`、无可执行规则或工作流 `BLOCKED` 不得回落为旧 `PASSED`；资产消费资格至少为 `CONDITIONAL`，严格策略下为 `BLOCKED`，并返回原因码。
7. 未配置质量验证时显示“未启用接入后质量验证”，不能显示“已通过”或“可信”。

## 安全边界

- trusted ingestion 触发只能由服务端已认证的 `dts-ingestion` 机器身份进入；浏览器、普通用户 token 或伪造 `machineActor/X-Quality-*` Header 不能取得 trusted 模式。
- 机器调用只能消费已经过设计/准入权限与密级校验、并冻结在 revision/execution 中的 datasetId；不得接受调用者临时替换任意资产 ID。
- task、execution、asset、workflow/run 深链分别执行既有服务端权限过滤；前端隐藏链接不是授权控制。
- API 错误只返回稳定业务码与 correlationId；凭据、规则正文、内部响应和 stack trace 不进入响应、审计或日志。
- 重复/突发触发受幂等唯一性、有限重试和受控调用频率约束，不能通过重放制造重复 workflow/run。

## 状态与客户文案

| 事实组合 | 质量展示 | 消费资格展示 | 可选徽标 |
|---|---|---|---|
| 无当前证据 | 暂无质量证据 | 有条件可消费/不可消费，显示原因 | 无 |
| 当前 execution 待触发或运行中 | 待质量验证/验证中 | 有条件可消费 | 无 |
| 旧证据对应旧 execution | 质量证据已失效 | 有条件可消费 | 无 |
| 当前证据 `PASSED`，但治理/权限/发布未齐 | 已通过质量验证 | 有条件可消费 | 无 |
| 当前证据 `PASSED` 且 eligibility=`ELIGIBLE` | 已通过质量验证 | 可消费 | 可信可用 |
| 当前证据 `FAILED/BLOCKED` | 未通过质量验证 | 不可消费 | 无 |

禁止使用裸“已验证”：现有 `DiscoveryState.VERIFIED` 表示资产发现证据已核实，不代表数据质量通过。

## UI 落点

- 数据集成流程：第四步名称为“接入后质量验证（可选）”；目标资产未解析时禁用并提示“请先确认目标数据资产”。
- 自动拓扑：`数据源 → 写入目标 → 更新资产证据 → 接入后质量验证? → 完成`；节点只读。目标资产身份已在设计/准入阶段确定，“更新资产证据”不新建第二身份。
- 运行实例：展示接入状态和质量状态两条独立时间线，支持从 `executionId` 进入 `qualityWorkflowId`。
- 数据资产详情：复用“质量与SLA”和消费资格卡，展示本次 execution、workflow/run、验证时间和证据是否为当前版本。
- 质量页面：保留规则、运行、问题单 owner；可按 `datasetId/workflowId` 从接入流程和资产详情深链进入。

## 错误与降级

| 场景 | 接入事实 | 质量/资产表现 |
|---|---|---|
| 接入过程校验失败 | 不提交本批次 | 不生成正式质量证据 |
| 接入成功、质量未配置 | SUCCESS | 资产存在；质量未启用，不派生可信 |
| 接入成功、触发暂时失败 | SUCCESS | `RETRY_WAIT`；当前证据待更新 |
| 触发三次失败 | SUCCESS | `EXHAUSTED/TRIGGER_FAILED`；禁止沿用旧可信徽标 |
| 正式质量失败 | SUCCESS | `FAILED`；消费资格重新计算并进入问题处置 |
| 质量通过 | SUCCESS | `PASSED/CURRENT`；满足其他门禁时派生“可信可用” |

## 验收硬条件

- 同一金丝雀 execution 的 task/revision/plan/dataset/workflow/run 可双向追踪。
- 新接入完成到资产显示“待质量验证”的投影延迟满足 NFR，旧通过证据不再显示当前可信。
- 质量失败不把 ingestion execution 改成失败，也不删除或重建资产。
- 目标资产不匹配、跨部门、跨密级和无正式规则四条错误路径均有契约测试。
- Chrome 95 完成“选择目标资产 → 开启接入后质量验证 → 运行 → 查看资产证据”的单一旅程。
