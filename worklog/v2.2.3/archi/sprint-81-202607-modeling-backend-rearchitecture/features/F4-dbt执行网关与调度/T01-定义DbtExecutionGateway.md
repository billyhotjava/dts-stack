# T01：定义并实现 DbtExecutionGateway

**优先级**：P0
**状态**：PLANNED
**依赖**：F1 模块边界；F2/T03 candidate/materialization contract

## 目标

建立与 HTTP/Docker/Airflow SDK 解耦的 dbt 执行端口，固定提交、状态和取消语义。

## 技术设计（Contract-first）

- **输入契约**：`DbtExecutionRequest {candidateId,candidateVersion,attempt,mode,environment,executionTargetKey,projectRef,selector,artifactChecksum,profileLeaseId,idempotencyKey,correlationId}`。
- **输出契约**：`ExecutionHandle {executionId,dagRunId,acceptedAt}`；`ExecutionStatus {state,artifactRefs,relationEvidenceRefs,observedAt}`。
- **方法**：`submit(request)`、`query(handle)`、`cancel(handle,expectedState)`。
- **幂等键**：tenant + candidateId + candidateVersion + attempt + mode；相同 payload 返回同 handle，不同 payload 409。
- **错误路径**：参数/lease/target 非法 typed rejection；upstream timeout 返回 UNKNOWN/待对账；已终态 cancel 幂等。
- **安全**：request 只含 profileLeaseId，不含 secret/profile/env credential。

## 影响范围

execution port/types、materialization adapter、测试 fake；不得在 modeling 包 import Airflow client。

## 验证

- [ ] contract test 覆盖 submit/query/cancel 全状态。
- [ ] 100 次重放同 handle；payload mismatch 409。
- [ ] secret scanner 对 request/log/outbox/DB 为 0。

## Definition of Done

- [ ] port 位于稳定 application boundary，adapter 可替换。
- [ ] modeling 编译不依赖 HTTP resource/Airflow SDK/Docker。
- [ ] F4/T02/T03 可仅消费该端口。
