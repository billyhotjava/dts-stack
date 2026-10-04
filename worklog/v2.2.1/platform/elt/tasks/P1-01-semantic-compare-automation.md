# P1-01 Addax/Airbyte 语义对照自动化

## 状态
- `done-x86-verified`

## 范围
- 自动计算全量/增量样本任务的行数、关键字段哈希与语义差异。

## 依赖
- P0 全部完成。

## 交付物
- `platform/scripts/semantic-compare.sh`
- 更新 `platform/addax-airbyte-semantic-compare.md`
- 产物样例：`platform/raw/semantic-compare-20260213T094615Z.md`
- 最新产物：`platform/raw/semantic-compare-20260214T115413Z.md`

## 验收标准
- 能输出差异清单（漏数/重数/字段偏差）。

## 回滚点
- 删除脚本与差异报告增量段。
