# Sprint-5：项目主体域数据建模与指标分析

## 背景

客户提供了"项目进度汇总表"Excel（29 字段、12 枚举维度、35 个统计指标），需要在 DTS 平台中完成数据导入、清洗建模和 BI 可视化分析。

## 需求来源

- 需求文档：`worklog/v2.2.1/req/项目主体域数据说明-20260310.xlsx`

## 目标

1. 完成 Excel → ODS → DWD → DWS → ADS 全链路数仓建模
2. 实现 35 个项目进度指标的自动计算
3. 在 BI 看板中展示关键 KPI

## 方案

采用**混合方案（方案 C）**：
- DWD 层打好时间标签（周、月、季、年）
- DWS 按固定周期预聚合，覆盖 80% 常规查询场景
- 保留参数化查询模板，支持自定义时间区间

## 设计文档

- 模型设计：`worklog/v2.2.1/sprint-5/model-design.md`

## 任务清单

| 编号 | 任务 | 优先级 | 状态 | 产物 |
|------|------|--------|------|------|
| PM-001 | ODS 表设计与 Excel 导入 | P0 | DONE | `ods_sources.yml` 已注册 |
| PM-002 | DIM 维度表（3 张枚举表） | P0 | DONE | `models/dim/dim_*.sql` |
| PM-003 | DWD 节点明细清洗表 | P0 | DONE | `models/dwd/biz_dwd_project_node.sql` |
| PM-004 | DWS 周期汇总表（3 张） | P0 | DONE | `models/dws/biz_dws_*.sql` |
| PM-005 | ADS 指标表（4 张） | P0 | DONE | `models/ads/biz_ads_*.sql` |
| PM-006 | 集成测试数据 + 验证查询 | P0 | DONE | `it/seed-*.sql`, `it/validate-*.sql` |
| PM-007 | BI 大屏模板 | P1 | DONE | `it/screen-template-project-progress.json` |
| PM-008 | 客户环境部署与口径确认 | P1 | TODO | 待现场执行 |

## 里程碑

- M1：模型设计评审（含客户确认）
- M2：ODS → ADS 全链路跑通
- M3：BI 看板交付
