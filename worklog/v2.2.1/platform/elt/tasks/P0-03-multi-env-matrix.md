# P0-03 多环境矩阵回填

## 状态
- `done-x86-verified`

## 范围
- 形成 `x86/ARM × legacy/normal/dev` 的统一验证矩阵。

## 依赖
- `P0-02` 完成。

## 交付物
- `platform/scripts/update-env-matrix.sh`
- `platform/scripts/run-matrix.sh`
- `platform/raw/env-matrix.csv`
- 更新 `platform/stability-24h.md` 的环境对比节。
- `platform/matrix-runbook.md`

## 本轮进展
- 已新增矩阵汇总脚本：`platform/scripts/update-env-matrix.sh`。
- 已实跑并写入样本：`normal + x86_64`。
- 已在 `stability-24h.md` 自动生成“环境矩阵（自动汇总）”表格。
- 最新有效复跑：新增 `20260214T120627Z + normal + x86_64`、`20260214T120653Z-1490075 + legacy + x86_64`、`20260214T120653Z-1490428 + dev + x86_64`。
- 已新增批量入口：`run-matrix.sh`，并完成 `normal/legacy/dev + x86_64` 一轮严格模式回填。
- 已修复同秒多次执行导致 raw 文件名冲突覆盖的问题（`collect-evidence.sh` 增加 PID 后缀）。

## 验收标准
- 每个组合都有结论（通过/不通过）与问题摘要。

## 回滚点
- 删除 `platform/raw/env-matrix.csv` 并移除文档矩阵段。
