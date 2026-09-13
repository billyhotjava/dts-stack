# T03：收敛 Airflow/dbt 执行与回执对账

**优先级**：P0
**状态**：PLANNED
**依赖**：T01～T02

## 目标

实现唯一 Airflow adapter 和 dbt 回执对账，使执行状态、artifact 与 relation evidence 不被重复模板或旧 attempt 污染。

## 技术设计（Contract-first）

- **Airflow 提交**：deterministic dagRunId 由 tenant/candidate/version/attempt/mode 计算；pairwise service auth；connect 3s/read 10s。
- **DAG**：复用唯一版本化 task factory；prepare → dbt build → sync/probe → finalize/release。
- **回执**：必须携带 executionId、candidateId/version/attempt、artifactChecksum、state、artifactRefs、relationEvidenceRefs、observedAt。
- **对账**：回执 tuple 不匹配即拒绝并审计；超时进入 UNKNOWN；UNKNOWN 对账完成前禁止 retry/new attempt。
- **重试**：仅 network/429/5xx；4xx 不重试；callback 不允许 `|| true` 或静默吞错。
- **凭据**：task-scoped profile lease；secret 不进 DAG conf/XCom/API/DB/env/log/evidence。

## 影响范围

Airflow adapter、thin DAG/task factory、callback/internal route、reconciler。

## 验证

- [ ] deterministic dagRunId/duplicate submit/timeout/reconcile tests。
- [ ] old attempt/out-of-order/tampered checksum 回执拒绝。
- [ ] dbt success + relation absent 不转成功。
- [ ] profile lease consume/release 和 secret scan。

## Definition of Done

- [ ] Airflow/dbt 是执行事实，platform 投影与其一致。
- [ ] gateway adapter 有 backlog/latency/error/UNKNOWN 指标。
- [ ] 无第二套 DAG runtime template 或建模直连执行路径。
