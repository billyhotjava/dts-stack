# Governance 脚本生产化迁移任务看板

更新时间：2026-02-23（UTC）

| 任务 | 状态 | 说明 |
|---|---|---|
| TASK-GOV-OPS-001 目录迁移与入口统一 | done | 新增 `bin/ops/governance` 生产脚本目录 |
| TASK-GOV-OPS-002 配置外置化与分层 | done | 支持 `DTS_GOV_OPS_ENV`、`/etc/dts/governance-ops.env`、示例配置文件 |
| TASK-GOV-OPS-003 密钥治理 | done | token 统一环境注入，不在脚本明文写死 |
| TASK-GOV-OPS-004 输出目录与留存策略 | done | 统一 raw/report/log 目录 + `cleanup-artifacts.sh` |
| TASK-GOV-OPS-005 严格模式与退出码规范 | done | 关键脚本支持 `--strict`，失败返回非 0 |
| TASK-GOV-OPS-006 调度封装（systemd/cron） | done | 提供 cron 与 systemd 模板 |
| TASK-GOV-OPS-007 K8s Job/CronJob 模板 | done | 提供 `docs/implementation/ops/k8s/governance-ops-cronjob.yaml` |
| TASK-GOV-OPS-008 告警集成 | done | 脚本支持 webhook 通知（可选） |
| TASK-GOV-OPS-009 可观测接入 | done | 支持 Prometheus textfile 指标输出（可选） |
| TASK-GOV-OPS-010 契约基线发布流程 | done | `contract-guard.sh --bootstrap/--strict` 落地 |
| TASK-GOV-OPS-011 运行手册与应急预案 | done | 新增 runbook |
| TASK-GOV-OPS-012 验证与验收 | done | 2026-02-23 已完成 token+DB 实测：preflight/release-gate/onsite-acceptance 全链路 PASS，并归档 latest 报告 |
| TASK-GOV-OPS-013 平台页面免 token 触发 | done | 新增 `/api/governance/ops/jobs*` 与治理页面“治理运维脚本”面板，服务端注入凭据执行 |
| TASK-GOV-OPS-014 Compose 挂载与配置联通 | done | app/dev/legacy 三套编排新增脚本目录、配置目录、产物目录挂载与平台环境变量 |
| TASK-GOV-OPS-015 页面触发链路验收脚本 | done | 新增 `platform-job-api-smoke.sh`，校验 capability/trigger/poll 全链路 |

## 已交付路径

- 脚本：`bin/ops/governance/README.md`
- 配置样例：`bin/ops/governance/config/governance-ops.env.example`
- Runbook：`docs/implementation/ops/governance-ops-runbook.md`
- 调度模板：
  - `docs/implementation/ops/governance-ops-cron.example`
  - `docs/implementation/ops/systemd/dts-governance-ops-matrix.service`
  - `docs/implementation/ops/systemd/dts-governance-ops-matrix.timer`
  - `docs/implementation/ops/k8s/governance-ops-cronjob.yaml`
- 平台接口与页面：
  - `source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/GovernanceResource.java`
  - `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/governance/GovernanceOpsJobService.java`
  - `source/dts-platform-webapp/src/pages/governance/components/GovernanceOpsPanel.tsx`
