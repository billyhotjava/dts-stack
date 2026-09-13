# T03：完成发布、回滚、运维证据与最终 Go/No-Go

**优先级**：P0
**状态**：DRAFT
**依赖**：T02

## 目标

证明 Sprint-76 可安全升级、监控、修复和回滚，并以完整证据作最终发布决策。

## 技术设计（Contract-first）

- **release plan**：完善既有 `assets/release-plan.md`；补 migration expand/rollback 命令、Candidate/legacy route 兼容窗口、kill switch、交叉版本矩阵、回滚触发与真实演练。
- **runbook**：Build Intent→candidate→pipeline/outbox→Airflow pairwise service auth→tmpfs profile lease→single task factory→dbt→observation→Publish Intent→reviewer/operator + M05 action policy→mandatory publication→binding/thin DAG deployment→manual/scheduled operational run；覆盖 QUEUED/RUNNING/UNKNOWN/FAILED/PARTIAL/STALE/DEGRADED、DAG parse/schedule drift、service-auth/profile lease/secret/target unavailable。
- **指标/日志**：构建数/失败率/耗时、publication request、职责/M05 拒绝、PARTIAL、external sync degraded、DAG parse/deployment/templateVersion/checksum/schedule lag、scheduled-open、orphan/duplicate run、operational run、profile lease active/expired cleanup、service-auth/probe/secret 失败；日志带 correlation ids，无 secret。
- **回滚**：
  - DB schema rollback rehearsal；
  - 应用版本回滚仍能读取新 nullable 字段/历史 evidence；
  - kill switch 停止 Publish Intent/publication/outbox/binding worker；
  - kill switch 不停止 Airflow scheduler；必须 pause 受影响 plan DAG，并对账未完成 DagRun；
  - 已发生外部副作用时走前向修复，不盲删 Catalog/关系；
  - 产品 rollback 恢复发布事实/资产状态；
  - 回滚/换版使旧 execution binding STALE/DISABLED，不再触发；
  - 不自动 DROP relation。
- **变更审计**：每个 owning symbol 编码前 impact；提交前 `gitnexus_detect_changes()`；范围外 dirty files 不吸收。
- **Go/No-Go**：PG-01/02/03、IT、NFR、Chrome95、migration、security、runbook 全 PASS 才 GO；Sprint-36/F3 已 DONE 只代表外部依赖就绪，Candidate 双门禁消费或 IT-14 未通过仍必须 NO-GO。

## 影响范围

- `assets/release-plan.md`
- `assets/runbook.md`
- `it/evidence/it-15-release.md`
- final acceptance summary

## 验证

- [ ] 故障注入和恢复命令真实执行。
- [ ] migration upgrade/rollback/re-upgrade。
- [ ] 旧客户端/旧 artifact 兼容回归。
- [ ] mandatory PARTIAL、external DEGRADED、binding FAILED 的前向修复/回滚边界演练。
- [ ] Airflow pause/redeploy/reparse、single task factory 对账、legacy per-tag pause、scheduled DagRun 对账和 target secret rotation 演练。
- [ ] tmpfs/profile lease kill-restart/TTL/path traversal 与 pairwise service-token rotation/forgery 演练。
- [ ] 三类职责账号、auditor read-only、Sprint-36/F3 policy DENY/缺失/过期和同人隔离演练。
- [ ] secret scan、diff check、GitNexus detect changes。

## Definition of Done

- [ ] Go/No-Go 有日期、环境、证据和剩余风险。
- [ ] 无 placeholder、无“应当可用”式结论。
- [ ] 删除/清理风险明确留给后续显式能力。
