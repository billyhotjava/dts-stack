# V221-Platform 验证与落地索引

## 目标
将 v2.2.1 平台侧（ETL/调度/可视化数据集）的验证从“人工口头结论”升级为“可审计、可复跑、可比对”的交付物。

## 证据文档
- `worklog/v2.2.1/platform/stability-24h.md`
  - 24 小时稳定性压测与巡检记录。
- `worklog/v2.2.1/platform/addax-airbyte-semantic-compare.md`
  - Addax（当前）与 Airbyte（K8s 目标）在全量/增量语义的一致性对照。
- `worklog/v2.2.1/platform/isolation-lineage-regression.md`
  - 跨项目隔离与血缘影响回归结果。
- `worklog/v2.2.1/platform/k8s-airbyte-readiness.md`
  - K8s + Airbyte 迁移就绪检查。
- `worklog/v2.2.1/platform/arm-kylin-hardening.md`
  - ARM/麒麟兼容加固与复测手册。

## 任务分解
- `worklog/v2.2.1/platform/tasks/README.md`
  - 按 P0/P1/P2 拆分的执行任务卡。

## 目录约定
- `worklog/v2.2.1/platform/scripts/`
  - 自动采集与回填脚本。
- `worklog/v2.2.1/platform/raw/`
  - 原始采集数据（csv/txt/md 报告）。

## 脚本清单
- `collect-evidence.sh`：采集小时级执行指标与 404 异常计数。
- `backfill-first-run.sh`：首轮采集结果回填到稳定性文档。
- `update-env-matrix.sh`：自动更新多环境矩阵。
- `update-p0-regression.sh`：将环境矩阵自动回填到 `v2.2.1` 根目录 P0 回归文档。
- `semantic-compare.sh`：对比 Addax/Airbyte 样本语义差异。
- `isolation-lineage-check.sh`：快照 + 对比，输出跨项目串扰结论。
- `package-report.sh`：打包交付物（文档 + raw + scripts + tasks）。

## 使用方式
1. 执行 P0：证据采集、首轮回填、环境矩阵更新。
2. 执行 P1：语义对照、隔离回归、交付包生成。
3. 执行 P2：迁移就绪与 ARM/麒麟复测清单落地。
