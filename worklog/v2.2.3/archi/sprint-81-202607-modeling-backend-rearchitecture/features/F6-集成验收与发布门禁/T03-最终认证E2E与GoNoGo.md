# T03：最终认证 E2E 与 Go/No-Go

**优先级**：P0
**状态**：PLANNED
**依赖**：T01～T02 全绿；全部编码结束

## 目标

一次完成新建模 UI 到 canonical backend、真实质量证据、候选/物化、Airflow/dbt、Catalog 和 dts-admin 审计的最终旅程，并给出唯一发布结论。

## 技术设计（Contract-first）

- **输入契约**：固定 commit/镜像/environment；一次性最小权限身份；隔离 WarehousePlan/ModelSpec/quality rule+binding/target relation；审批角色分离。
- **输出契约**：`GoNoGoDecision {commit,environment,tests,evidence,rollbackResult,residualRisks,decision}`。
- **旅程**：`/data-modeling/**` → WarehousePlan → ModelSpec revision → quality run → StageGate/Lifecycle → Candidate review/publish → Materialization → gateway/Airflow/dbt → relation/Catalog → dts-admin audit。
- **负向路径**：跨租户 403、CAS 409/412、quality failed/expired 422、Airflow timeout UNKNOWN、relation absent、audit receiver outage/recovery。
- **退役断言**：旧 URL 普通 404；旧 bean/table absent；无 410/tombstone；outbox backlog=0。
- **清理**：一次性身份、隔离模型/quality run/relation/profile lease 精确清理；中央审计保留。

## UI 操作走查

1. 登录并进入 Sprint-80 建模页面。
2. 选择计划，创建/编辑模型并保存 revision。
3. 运行隔离质量规则，查看门禁从阻断到通过。
4. 提交候选，由独立角色审核/发布。
5. 触发物化并查看 QUEUED/RUNNING/SUCCEEDED 或明确错误态。
6. 核验真实 relation、Catalog 资产/lineage 与中央审计。
7. 访问旧 URL 验证普通 404；复核无遗留身份/secret/outbox backlog。

## 验证

- [ ] 四态、403、409/412、422、UNKNOWN/恢复、成功均有真实证据。
- [ ] API/DB/Airflow/dbt/Catalog/audit correlationId 可贯通。
- [ ] 凭据/token/profile 不进入证据。
- [ ] 清理后隔离业务数据=0，中央审计仍可查。

## Definition of Done

- [ ] IT-08 PASS，且 IT-01～IT-07 无未关闭 P0。
- [ ] rollback 实际演练成功，残余风险获用户接受。
- [ ] 只有此时 Sprint/Feature/Task 才可改为 DONE；否则保持 IN_PROGRESS/NO_GO。
