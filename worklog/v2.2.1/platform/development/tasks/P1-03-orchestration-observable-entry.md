# P1-03 编排入口可观测化

- 优先级：P1
- 状态：done

## 范围

- 在保留外链模式下，补齐“平台内可观测入口”。

## 子任务

- `OrchestrationPage.tsx` 增加 DAG 列表、最近运行、失败任务摘要。
- 复用 `EtlResource` 现有 Airflow 列表与触发接口。
- 支持按项目/标签过滤，并保留“进入外部编排平台”按钮。

## 验收标准

- 用户在平台内可看到关键编排运行状态。
- 失败任务可直接触发重试或跳转外部平台定位。

## 风险与回滚

- 风险：Airflow API 抖动导致页面超时。
- 回滚：展示降级视图，仅保留外链入口与最近一次缓存状态。

## 已完成进展（2026-02-16）

- `OrchestrationPage.tsx` 已重构为“平台内可观测入口”：
  - DAG 列表（状态、最近运行、标签、项目）
  - 最近运行表（按选中 DAG 展示运行记录）
  - 失败摘要卡片（失败 DAG 快速重跑）
- 已复用 `EtlResource` 现有接口：
  - `GET /api/etl/airflow/jobs`
  - `GET /api/etl/airflow/jobs/{dagId}/runs`
  - `POST /api/etl/airflow/jobs/{dagId}/trigger`
- 已支持筛选能力：
  - 项目筛选（基于 DAG 命名/标签推断）
  - 标签筛选
  - 关键字搜索
- 外部编排入口保留：
  - 继续使用 `EXPLORE_ETL_ORCHESTRATION` 链接
  - 提供“进入编排平台”跳转按钮

## 回归结果（2026-02-16）

- `pnpm -C source/dts-platform-webapp build`：通过
