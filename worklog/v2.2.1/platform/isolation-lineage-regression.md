# 跨项目隔离与血缘影响回归（Platform）

## 1. 目标
验证“项目 A 的导入/发布/重跑”不会污染项目 B，并能在血缘关系中正确体现影响范围。

## 2. 测试对象
- 项目 A：
- 项目 B：
- 共享数据源：
- 共享看板（如有）：

## 3. 用例清单
| 用例 | 操作 | 预期 | 结果 | 备注 |
|---|---|---|---|---|
| ISO-01 | A 项目导入模型包 | 仅 A 变更 |  |  |
| ISO-02 | A 项目发布版本 | B 不受影响 |  |  |
| ISO-03 | A 重跑全量任务 | B 表与任务不变化 |  |  |
| ISO-04 | A 删除/归档数据集 | B 看板不异常 |  |  |
| ISO-05 | A 新增字段 | 血缘影响仅覆盖依赖链路 |  |  |

## 4. 血缘校验记录
- 校验时间：
- 入口对象（表/模型/报表）：
- 观测到的上游/下游：
- 是否存在跨项目串扰：`是/否`

## 5. 问题闭环
| 问题 | 严重度 | 根因 | 修复 PR/Commit | 回归结果 |
|---|---|---|---|---|
|  |  |  |  |  |

## 6. 结论
- 是否通过：`通过/不通过`
- 风险项：
- 后续动作：

## 自动回归 20260213T095129Z
- 输入：`worklog/v2.2.1/platform/raw/isolation-snapshot-20260213T095121Z-selfcheck-before.csv` -> `worklog/v2.2.1/platform/raw/isolation-snapshot-20260213T095128Z-selfcheck-after.csv`
- 结果：OBSERVED（检测到变更（未指定目标项目，无法判定串扰），impacted=3）
- 报告：`raw/isolation-lineage-compare-20260213T095129Z.md`

## 自动回归 20260213T095146Z
- 输入：`worklog/v2.2.1/platform/raw/isolation-snapshot-20260213T095121Z-selfcheck-before.csv` -> `worklog/v2.2.1/platform/raw/isolation-snapshot-20260213T095128Z-selfcheck-after.csv`
- 结果：OBSERVED（检测到变更（未指定目标项目，无法判定串扰），impacted=3）
- 报告：`raw/isolation-lineage-compare-20260213T095146Z.md`

## 自动回归 20260213T095300Z
- 输入：`worklog/v2.2.1/platform/raw/isolation-snapshot-20260213T095121Z-selfcheck-before.csv` -> `worklog/v2.2.1/platform/raw/isolation-snapshot-20260213T095121Z-selfcheck-before.csv`
- 结果：OBSERVED（检测到变更（未指定目标项目，无法判定串扰），impacted=3）
- 报告：`raw/isolation-lineage-compare-20260213T095300Z.md`

## 自动回归 20260213T095324Z
- 输入：`worklog/v2.2.1/platform/raw/isolation-snapshot-20260213T095121Z-selfcheck-before.csv` -> `worklog/v2.2.1/platform/raw/isolation-snapshot-20260213T095121Z-selfcheck-before.csv`
- 结果：PASS（无串扰，impacted=0）
- 报告：`raw/isolation-lineage-compare-20260213T095324Z.md`

## 说明
- `20260213T095324Z` 之前的 `OBSERVED` 样本来自脚本首版 compare 键位计算缺陷（已修复），最终自检结果以 `20260213T095324Z` 为准。
