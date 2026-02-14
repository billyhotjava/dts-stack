# P0-04 P0 回归文档自动回填

## 状态
- `done-first-pass`

## 范围
- 将 `platform/raw/env-matrix.csv` 自动同步到 `v2.2.1` 根目录的 P0 回归文档，减少手工维护偏差。

## 依赖
- `P0-03` 完成。

## 交付物
- `platform/scripts/update-p0-regression.sh`
- 更新：`worklog/v2.2.1/p0-regression-matrix.md`
- 更新：`worklog/v2.2.1/p0-regression-checklist.md`

## 验收标准
- 一条命令可回填自动汇总段。
- 回填不覆盖人工模板区，仅更新自动区块。

## 回滚点
- 删除自动区块或回退文档改动。
