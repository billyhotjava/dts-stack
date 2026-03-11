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

## 产物清单

| 类型 | 位置 | 说明 |
|------|------|------|
| dbt 模型 (11) | `services/dts-dbt/models/{dim,dwd,dws,ads}/` | ODS source 注册 + 3 DIM + 1 DWD + 3 DWS + 4 ADS |
| 部署脚本 | `bin/project-progress/deploy.sh` | 自动创建项目空间 + 导入模型 |
| 导入清单 | `bin/project-progress/manifest/models.tsv` | 11 个模型的 TSV 清单 |
| 测试数据 | `bin/project-progress/seed-ods-project-progress.sql` | 40 条数据覆盖全部枚举 |
| 指标验证 | `bin/project-progress/validate-indicators.sql` | 7 组验证查询 |
| 大屏模板 | `bin/project-progress/screen-template-project-progress.json` | 8 KPI + 4 图表 + 3 表格 |

## 部署

```bash
export API_BASE="https://bi.example.com"
export TOKEN="<bearer-token>"
export SOURCE_DATA_SOURCE_ID="<数据湖连接 UUID>"
bash bin/project-progress/deploy.sh --plan-name "项目进度分析"
```

- 项目名称冲突时自动追加后缀（name-2, name-3）
- 不与已有项目合并，每次创建独立项目
- 执行记录见 `worklog/v2.2.1/sprint-5/it/README.md`

## 任务清单

| 编号 | 任务 | 优先级 | 状态 | 产物位置 |
|------|------|--------|------|---------|
| PM-001 | ODS 表设计与 Excel 导入 | P0 | DONE | `services/dts-dbt/models/ods_sources.yml` |
| PM-002 | DIM 维度表（3 张） | P0 | DONE | `services/dts-dbt/models/dim/` |
| PM-003 | DWD 节点明细清洗表 | P0 | DONE | `services/dts-dbt/models/dwd/` |
| PM-004 | DWS 周期汇总表（3 张） | P0 | DONE | `services/dts-dbt/models/dws/` |
| PM-005 | ADS 指标表（4 张） | P0 | DONE | `services/dts-dbt/models/ads/` |
| PM-006 | 测试数据 + 验证查询 | P0 | DONE | `bin/project-progress/` |
| PM-007 | 部署脚本 + 导入清单 | P0 | DONE | `bin/project-progress/deploy.sh` |
| PM-008 | BI 大屏模板 | P1 | DONE | `bin/project-progress/screen-template-*.json` |
| PM-009 | 客户环境部署与口径确认 | P1 | TODO | 待现场执行 |

## 里程碑

- M1：模型设计评审（含客户确认）
- M2：ODS → ADS 全链路跑通
- M3：BI 看板交付
