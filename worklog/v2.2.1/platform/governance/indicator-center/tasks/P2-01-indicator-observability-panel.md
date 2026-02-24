# P2-01 指标可观测面板

`status`: `completed`  
`priority`: `P2`

## 目标

提供指标中心运维可观测视图，覆盖失败率、耗时、状态分布。

## 范围

- 指标校验执行趋势
- 发布失败分类 TopN
- 指标生命周期状态分布

## 子任务

1. 后端聚合接口设计与实现。
2. 前端指标运维面板展示。
3. 与治理运维脚本报告联动。

## 验收标准

- 可在页面观察到核心运行指标。
- 关键异常可被快速发现。

## 当前完成

- 后端聚合接口已落地：
  - `GET /api/governance/indicators/ops/overview`
  - `GET /api/governance/indicators/ops/trend`
- 前端指标页已展示：
  - 生命周期状态分布（发布/草稿/废止）
  - 校验成功/失败/未校验、成功率/失败率
  - 失败分类 TopN
  - 最近 7 天按天趋势表
- 脚本与报告已落地：
  - `worklog/v2.2.1/platform/governance/indicator-center/scripts/collect-indicator-observability.sh`
  - `worklog/v2.2.1/platform/governance/indicator-center/scripts/render-indicator-observability-report.sh`
  - `worklog/v2.2.1/platform/governance/indicator-center/scripts/run-indicator-observability.sh`
  - `worklog/v2.2.1/platform/governance/indicator-center/report/p2-01-indicator-observability-latest.md`
- 实测结果（2026-02-23）：
  - 已通过 `--docker-container dts-stack-dts-pg-1` + `PGUSER/PGDATABASE` 成功采集。
  - 最新报告：`report/p2-01-indicator-observability-latest.md`（`collect_failed=0`）。

## 风险与回滚

- 风险：统计 SQL 对线上性能产生压力。  
- 回滚：降低刷新频率，改离线汇总。
