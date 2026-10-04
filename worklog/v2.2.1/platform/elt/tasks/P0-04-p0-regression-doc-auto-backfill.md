# P0-04 P0 回归文档自动回填

## 状态
- `done-x86-verified`

## 范围
- 将 `platform/raw/env-matrix.csv` 自动同步到 `v2.2.1` 根目录的 P0 回归文档，减少手工维护偏差。

## 依赖
- `P0-03` 完成。

## 交付物
- `platform/scripts/update-p0-regression.sh`
- `platform/scripts/run-all.sh`
- 更新：`worklog/v2.2.1/p0-regression-matrix.md`
- 更新：`worklog/v2.2.1/p0-regression-checklist.md`

## 本轮进展
- 已验证 `update-p0-regression.sh` 在最新样本上的自动回填行为。
- 已补充一键执行入口 `run-all.sh`，减少串行手工执行偏差。
- 已在 `run-all.sh` 加入兼容分支：当 `worklog/v2.2.1/p0-regression-*.md` 不存在时，自动跳过回填并保留主流程成功。

## 验收标准
- 一条命令可回填自动汇总段。
- 回填不覆盖人工模板区，仅更新自动区块。

## 回滚点
- 删除自动区块或回退文档改动。
