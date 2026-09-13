# dbt ZIP 导入部分成功与失败明细契约

**状态**：ACCEPTED（D07，2026-08-01）  
**适用动作**：inspect、mapping、preview、apply、retry、forward undo  
**事实源**：现有 import run / apply attempt / item result 台账；不得新建平行任务或历史表。

## 1. 状态代数

- 每个 selected item 终态必须且只能落入 `CREATED/UPDATED/SKIPPED/FAILED/BLOCKED` 一个桶。
- `succeeded = created + updated`；SKIPPED 表示已安全处理但没有写入，不计入 succeeded。
- `handled = succeeded + skipped`；`unresolved = failed + blocked`。
- `terminal = created + updated + skipped + failed + blocked`；`pending = selected - terminal`，任何计数为负或 `terminal > selected` 都属于契约错误。
- 运行中恒等式：`selected = pending + created + updated + skipped + failed + blocked`；终态必须满足 `pending=0`，即 `selected = created + updated + skipped + failed + blocked`。计数不满足时不得由前端猜测修复。
- `selected=0` 在创建 attempt 前返回 422，不生成空 attempt。
- `pending>0` 时整体状态只能为 `RUNNING`；所有 item 终结后不得继续保持 `RUNNING`。

所有 item 终结后，整体状态只由服务端按下表计算并持久化；UI 和审计直接使用该状态，不得自行重算：

| 条件 | 整体状态 |
|---|---|
| `unresolved = 0` 且 `handled = selected` | `SUCCESS` |
| `handled > 0` 且 `unresolved > 0` | `PARTIAL` |
| `handled = 0` 且 `failed > 0` | `FAILED` |
| `handled = 0` 且 `failed = 0` 且 `blocked > 0` | `BLOCKED` |

因此全 SKIP 为 SUCCESS；`SKIP+FAILED`、`SKIP+BLOCKED`、`CREATED/UPDATED+FAILED/BLOCKED` 均为 PARTIAL；`FAILED+BLOCKED` 且没有已处理项时为 FAILED。任何 API、审计或 UI 都不得把 PARTIAL 映射成 SUCCESS。

`summary` 必须完整返回 `selected/pending/succeeded/created/updated/skipped/failed/blocked`，并满足上述定义和恒等式。API、持久化摘要、公共审计和 UI 使用同一组字段与枚举，不得增设 `replayed` 桶或把 `SUCCEEDED` 作为别名输出。

### 1.1 旧状态前向迁移

本 Sprint 只保留一套 canonical 状态代数，旧持久化值必须做一次性、可校验的前向迁移：

| 旧位置/值 | canonical 值 | 迁移约束 |
|---|---|---|
| apply attempt `SUCCEEDED` | `SUCCESS` | attempt 行、存量 JSON/DTO 投影、审计结果统一归一；迁移后 API 不再输出 `SUCCEEDED` |
| item result `REPLAYED` | `SKIPPED` | 先迁移 item，再从 item 明细重算 attempt summary；迁移后不存在 `REPLAYED` item 或 `replayed` 计数 |
| `BeginDisposition.REPLAY` | 保留 `REPLAY` | 只表示同一 idempotency key + 同一规范化请求返回原 attempt，是请求级幂等响应元数据；不属于 item 状态、attempt 状态或 summary 桶 |

迁移必须保持 runId、attemptId、item identity、原始时间和审计链不变，并以规范化后的 item 明细重算：`succeeded=created+updated`、`terminal=created+updated+skipped+failed+blocked`、`pending=selected-terminal`。若 legacy summary 只有 `total`，只可在它与选中 item 身份集合一致时映射为 `selected`；若旧 `replayed` 与 item 明细不一致或任一恒等式不成立，迁移立即停止并输出具名审计/修复清单，禁止猜测或双计数。

该迁移是 forward-only：数据库约束、持久化 JSON、API DTO、审计与 UI 在同一发布序列内收敛到 canonical 值；不得长期双写、保留两套统计或在前端兼容旧代数。回滚边界遵循 [`release-plan.md`](release-plan.md)。

## 2. 逐项失败对象

每个 FAILED/BLOCKED 项必须有一个主失败对象；可附加 issues，但不得只有自由文本：

```json
{
  "code": "DBT_IMPORT_DEPENDENCY_MISSING",
  "stage": "PREVIEW",
  "category": "DEPENDENCY",
  "message": "模型依赖的上游节点未包含在本次导入范围内。",
  "fieldPath": null,
  "dependencyUniqueId": "model.demo.stg_order",
  "retryable": false,
  "recoveryAction": "INCLUDE_DEPENDENCY",
  "correlationId": "..."
}
```

必填字段：

| 字段 | 约束 |
|---|---|
| `code` | 稳定、可测试的产品错误码；不得直接透传异常类名或数据库错误正文 |
| `stage` | `INSPECT/MAPPING/PREVIEW/APPLY/RETRY/UNDO` |
| `category` | `VALIDATION/SECURITY/DEPENDENCY/CONFLICT/PERMISSION/STALE/PERSISTENCE/INTERNAL` |
| `message` | 面向用户的脱敏原因，明确失败对象和原因；不能只写“失败”“请查看日志” |
| `retryable` | 当前条件下能否安全重试；不是异常是否瞬时的猜测 |
| `recoveryAction` | `REUPLOAD/COMPLETE_MAPPING/INCLUDE_DEPENDENCY/RESOLVE_CONFLICT/REAUTHORIZE/REFRESH_PREVIEW/RETRY/OPEN_MODEL/CONTACT_ADMIN/NONE` |
| `correlationId` | 连接 UI、应用日志和公共审计；不得包含租户密钥或敏感正文 |

`fieldPath`、`dependencyUniqueId` 在对应失败由字段或依赖触发时必填。内部异常可以使用安全的通用 message，但必须提供稳定 code、stage 和 correlationId。

## 3. API 与重试规则

1. 请求在创建 attempt 前失败，使用 HTTP 4xx/5xx 和同构安全错误对象；不得伪造逐项结果。
2. attempt 已创建后，逐模型事务结果全部写入现有 item result；一个模型失败不得抹去其他已成功模型。
3. retry 只接收 `retryable=true` 且前置条件已经满足的 FAILED/BLOCKED 项；成功项和安全 SKIP 项不得重放。
4. 同一 idempotencyKey + 同一规范化请求返回原 attempt；同 key 不同请求返回 409。
5. retry 成功只推进该项的 accepted base；原失败记录保留，形成可审计尝试链。
6. forward undo 使用同一状态代数和失败对象；不能撤销的项返回 BLOCKED，不能通过物理删除历史伪造成功。

## 4. UI 呈现

- 结果页顶部展示状态和计数；PARTIAL 使用独立警示态，不使用绿色成功态。
- 主表至少展示模型、预检动作、最终状态、失败阶段、失败原因和建议动作。
- 行内动作必须由 `recoveryAction` 驱动；冲突进入逐项解决，映射问题返回映射步骤，权限问题提示重新授权，瞬时故障才允许直接 retry。
- 详情抽屉展示 code、关联字段/依赖和 correlationId；禁止展示 SQL/Jinja、ZIP 文件正文、凭据、变量值、堆栈和数据库错误正文。
- 刷新页面后从服务端 run/attempt/result 恢复相同结果，不依赖 toast 或浏览器内存。

## 5. 审计与验收

- 公共审计记录 action、tenant、runId、attemptId、item identity、stage、category、code、结果计数、correlationId 和恢复动作；不记录 SQL、ZIP、secret 正文。
- IT-05 必须覆盖至少一种 VALIDATION、DEPENDENCY、CONFLICT、PERMISSION、STALE、PERSISTENCE/INTERNAL 失败，以及 PARTIAL → retry → SUCCESS/PARTIAL 的结果链。
- 每个 FAILED/BLOCKED 项的必填字段完整率必须为 100%；`summary` 与 item 明细必须一致；敏感正文泄漏数必须为 0。
