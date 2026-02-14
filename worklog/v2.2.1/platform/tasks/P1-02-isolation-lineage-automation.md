# P1-02 隔离与血缘回归自动化

## 状态
- `done-first-pass`

## 范围
- 自动执行 A/B 项目隔离用例并记录血缘影响范围。

## 依赖
- P0 全部完成。

## 交付物
- `platform/scripts/isolation-lineage-check.sh`
- 更新 `platform/isolation-lineage-regression.md`
- 产物样例：`platform/raw/isolation-lineage-compare-20260213T095324Z.md`

## 验收标准
- 可一键输出“串扰/无串扰”结论及证据。

## 回滚点
- 删除检查脚本与 raw 输出。
