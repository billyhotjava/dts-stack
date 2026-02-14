# P0-03 多环境矩阵回填

## 状态
- `done-first-pass`

## 范围
- 形成 `x86/ARM × legacy/normal/dev` 的统一验证矩阵。

## 依赖
- `P0-02` 完成。

## 交付物
- `platform/scripts/update-env-matrix.sh`
- `platform/raw/env-matrix.csv`
- 更新 `platform/stability-24h.md` 的环境对比节。

## 本轮进展
- 已新增矩阵汇总脚本：`platform/scripts/update-env-matrix.sh`。
- 已实跑并写入样本：`normal + x86_64`。
- 已在 `stability-24h.md` 自动生成“环境矩阵（自动汇总）”表格。

## 验收标准
- 每个组合都有结论（通过/不通过）与问题摘要。

## 回滚点
- 删除 `platform/raw/env-matrix.csv` 并移除文档矩阵段。
