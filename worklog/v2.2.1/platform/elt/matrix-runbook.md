# Platform 矩阵执行手册（v2.2.1）

## 目标
- 用同一套脚本完成 `mode(normal/legacy/dev) × arch(x86_64/aarch64)` 的证据采集与文档回填。
- 降低现场手工执行误差，确保可追溯。

## 前置检查
1. `docker ps` 可访问，且存在：
   - `dts-stack-dts-pg-1`
   - `dts-stack-dts-ingestion-1`
2. 工作目录位于仓库根目录：`/opt/prod/s10/dts-stack`
3. 脚本可执行：
   - `worklog/v2.2.1/platform/scripts/run-all.sh`
   - `worklog/v2.2.1/platform/scripts/run-matrix.sh`
4. 目标 ARM/麒麟环境先执行预检：
```bash
bash worklog/v2.2.1/platform/scripts/arm-kylin-preflight.sh
```
预检失败时，先修复环境再执行矩阵回填。

## 标准命令
1. x86 环境：
```bash
bash worklog/v2.2.1/platform/scripts/run-matrix.sh \
  --hours 168 \
  --arch x86_64 \
  --modes normal,legacy,dev \
  --require-data \
  --note-prefix matrix-x86 \
  --tag-prefix matrix-x86
```
2. ARM/麒麟环境：
```bash
bash worklog/v2.2.1/platform/scripts/run-matrix.sh \
  --hours 168 \
  --arch aarch64 \
  --modes normal,legacy,dev \
  --require-data \
  --note-prefix matrix-arm \
  --tag-prefix matrix-arm
```

## 可选：带 P1 对照
```bash
bash worklog/v2.2.1/platform/scripts/run-matrix.sh \
  --hours 168 \
  --arch x86_64 \
  --modes normal,legacy,dev \
  --require-data \
  --with-p1 \
  --sem-left worklog/v2.2.1/platform/raw/hourly-metrics-20260213T094146Z.csv \
  --sem-right worklog/v2.2.1/platform/raw/hourly-metrics-20260213T094326Z.csv \
  --sem-key hour_slot \
  --iso-before worklog/v2.2.1/platform/raw/isolation-snapshot-20260213T095121Z-selfcheck-before.csv \
  --iso-after worklog/v2.2.1/platform/raw/isolation-snapshot-20260213T095121Z-selfcheck-before.csv \
  --note-prefix matrix-p1 \
  --tag-prefix matrix-p1
```

## 输出位置
- 原始证据：`worklog/v2.2.1/platform/raw/`
- 环境矩阵：`worklog/v2.2.1/platform/raw/env-matrix.csv`
- 稳定性文档：`worklog/v2.2.1/platform/stability-24h.md`
- P0 回归文档：
  - `worklog/v2.2.1/p0-regression-matrix.md`
  - `worklog/v2.2.1/p0-regression-checklist.md`
- 交付包：`worklog/v2.2.1/platform/deliverable-*.tar.gz`

## 已知事项
- `collect-evidence.sh` 已加文件名唯一后缀（PID），避免同秒执行覆盖 raw 文件。
- `--require-data` 开启后，若采样窗口任务数为 0 会直接失败，适合正式回填。
