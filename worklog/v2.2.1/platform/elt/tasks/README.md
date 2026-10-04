# Platform 任务卡（分阶段 + 序号）

## 状态总览
| Task | Status |
|---|---|
| `P0-01` | `done-x86-verified` |
| `P0-02` | `done-x86-verified` |
| `P0-03` | `done-x86-verified` |
| `P0-04` | `done-x86-verified` |
| `P1-01` | `done-x86-verified` |
| `P1-02` | `done-x86-verified` |
| `P1-03` | `done-x86-verified` |
| `P2-01` | `done-x86-verified` |
| `P2-02` | `done-first-pass` |

> 当前唯一未闭环项：`P2-02` 的 ARM/麒麟现场实测回填。

## P0（证据落地）
- `P0-01-evidence-collector.md`：自动采集脚本（小时级指标/错误统计）
- `P0-02-first-run-backfill.md`：首轮实测回填（2~4h）
- `P0-03-multi-env-matrix.md`：多环境矩阵回填（x86/ARM + legacy/normal/dev）
- `P0-04-p0-regression-doc-auto-backfill.md`：P0 回归文档自动回填（矩阵/清单）

## P1（自动化深度）
- `P1-01-semantic-compare-automation.md`：Addax/Airbyte 语义对照自动化
- `P1-02-isolation-lineage-automation.md`：隔离与血缘回归自动化
- `P1-03-report-packaging.md`：一键汇总为交付报告

## P2（迁移前置）
- `P2-01-k8s-airbyte-readiness.md`：K8s + Airbyte 迁移就绪检查
- `P2-02-arm-kylin-hardening.md`：鲲鹏/麒麟兼容加固与复测

## 执行规则
- 每个任务卡至少包含：范围、依赖、交付物、验收标准、回滚点。
- 状态更新路径：先改任务卡，再同步 `worklog/v2.2.1/platform-analytics-v2.2.1-task-list.md`。
- 推荐使用 `worklog/v2.2.1/platform/scripts/run-all.sh` 作为日常执行入口，避免步骤遗漏。
- 多环境回填推荐使用 `worklog/v2.2.1/platform/scripts/run-matrix.sh`。
