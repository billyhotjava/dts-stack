# Sprint-74 运维 Runbook

## 1. 日常健康检查

- 容器：`dts-platform` healthy，`dts-platform-webapp` running，PostgreSQL healthy。
- 后端：`GET /management/health` 为 `UP`。
- 模型：随机读取一个 ModelSpec 和 `/stage-gates`，确认 revision/checksum 一致。
- 实现：核对 current implementation 的 model revision/checksum pin。
- 发布结果：只有 artifact 含 `physicalAssetRef` 时才显示真实资产；COMPILED 不等于 PUBLISHED。

## 2. 用户问题定位

### “为什么逻辑设计不能进入数据实现”

查看 `DESIGNED` gate，只处理该 gate blocker。来源、物理名、dbt、质量和发布证据不得进入 DESIGNED。

### “为什么数据实现已保存但不能发布”

分别检查：

1. implementation 是否验证通过并锁定 current model revision；
2. build/test artifact 是否属于 current implementation revision；
3. 计划的 standardCoverage/qualityGate；
4. 密级传播、审核与发布候选。

不要在逻辑页补物理设置来绕过 gate。

### “发布结果为什么没有物理表”

先看 lifecycle timeline。只有 COMPILE/PASSED 时应显示编译证据和空资产状态；只有发布/登记产生真实 `physicalAssetRef` 后才显示 table/view/DDL。

### “调整模型类型不可用”

改型只允许 DRAFT、无 current implementation、无 lifecycle/release evidence 的模型。其他模型必须复制为新模型。preview 必须先通过，apply 必须携带 current ETag、全部 acceptedClearFields 与新 idempotency key。

## 3. 兼容迁移操作

1. 永远先 dry-run，并保存 total/eligible/conflict/skipped/checksum。
2. 人工审阅 conflict；不得用空值覆盖 current implementation。
3. apply 使用完全相同的模型集合与 checksum。
4. 重放应返回同一批次，不新增 implementation revision。
5. rollback 前确认目标 revision 未被 lifecycle/release 使用；不满足时保持 compatibility read。

## 4. 告警与审计

应告警：

- 同一模型连续出现 CAS/409；
- migration conflict 数增加；
- stage-gates P95 > 1s；
- classification provider 不可用导致 RELEASE_READY 大面积 BLOCKED；
- artifact 无 current model/implementation 精确 pin；
- 物理资产页出现无 `physicalAssetRef` 的资产。

改型审计至少包含 actor、from/to type、result revision/checksum、idempotency key 和时间。ledger 是 append-only，不得 UPDATE/DELETE。

## 5. 隔离验收数据

验收计划 ID：`15a6bfc4-2b50-4f4b-8bf8-597d4d863a9b`。当前保留该数据用于回归和运行证据，不自动清理。

若未来需要清理，必须先：

1. 确认 plan/name 前缀属于 Sprint74；
2. 确认没有业务用户引用；
3. 先删除 lifecycle/release/implementation 依赖，再删除 ModelSpec；
4. 对 command ledger 和 changeset 05 的 rollback 边界单独评估；
5. 经用户明确授权后执行，记录前后计数。

本 Sprint 未执行破坏性清理。
