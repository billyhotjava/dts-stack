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
- `run-all.sh`：一键串行执行 P0 回填 -> 矩阵更新 -> P0 文档回填 -> 可选 P1 对照 -> 打包。
- `run-matrix.sh`：按 `mode` 批量执行 `run-all.sh`，一次回填 `normal/legacy/dev`。
- `arm-kylin-preflight.sh`：ARM/麒麟环境预检（架构/发行版/docker/关键容器）。

## 使用方式
1. 执行 P0：证据采集、首轮回填、环境矩阵更新。
2. 执行 P1：语义对照、隔离回归、交付包生成。
3. 执行 P2：迁移就绪与 ARM/麒麟复测清单落地。

## 推荐入口
- 最小闭环：
  - `bash worklog/v2.2.1/platform/scripts/run-all.sh --hours 24 --mode normal --arch x86_64 --result OBSERVED --note "daily-refresh" --tag daily-refresh`
- 严格模式（要求采样窗口内存在执行记录）：
  - `bash worklog/v2.2.1/platform/scripts/run-all.sh --hours 24 --mode normal --arch x86_64 --result OBSERVED --note "daily-strict" --tag daily-strict --require-data`
- 多模式矩阵（x86）：
  - `bash worklog/v2.2.1/platform/scripts/run-matrix.sh --hours 168 --arch x86_64 --modes normal,legacy,dev --require-data --note-prefix matrix --tag-prefix matrix`
- 多模式矩阵（ARM/麒麟）：
  - `bash worklog/v2.2.1/platform/scripts/run-matrix.sh --hours 168 --arch aarch64 --modes normal,legacy,dev --require-data --note-prefix matrix-arm --tag-prefix matrix-arm`

## 门禁说明
- 架构门禁：`collect-evidence.sh` 默认校验 `--arch` 必须等于宿主机 `uname -m`，避免把 x86 结果误标为 aarch64。
- 如需显式覆盖标签（不推荐用于正式证据）：可加 `--allow-arch-override`。
- 兼容性：若 `worklog/v2.2.1/p0-regression-matrix.md` / `p0-regression-checklist.md` 不存在，`run-all.sh` 会跳过 `update-p0-regression.sh` 并给出告警，不中断证据采集主流程。

## 最新执行（2026-02-14）
- 严格模式样本：`summary-20260214T120042Z.txt`（168h，total=3，success=2，failed=1）。
- 最新交付包：`deliverable-20260214T120043Z-run-all-168h-strict.tar.gz`。
- 多模式矩阵：`normal/legacy/dev + x86_64` 已完成一轮自动回填。
- 矩阵交付包样例：`deliverable-20260214T120628Z-matrix-legacy-x86_64.tar.gz`、`deliverable-20260214T120629Z-matrix-dev-x86_64.tar.gz`。
- 预检脚本样例：
  - PASS（本机验证）：`raw/arm-kylin-preflight-20260214T133341Z-1725681.txt`
  - FAIL（默认 aarch64+麒麟门禁）：`raw/arm-kylin-preflight-20260214T133341Z-1725708.txt`
