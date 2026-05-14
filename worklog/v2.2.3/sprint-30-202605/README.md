# Sprint-30: 地铁小模型 CSV 训练快照正式版 (202605)

**时间**: 2026-05  
**状态**: IN_PROGRESS  
**类型**: Implementation（dts-ingestion / dts-platform / dbt package / metro-stack）  
**目标**: 把演示版地铁 LSTM 小模型链路升级为可交付的 CSV 训练快照闭环：运营商 CSV 经 DTS 入湖、dbt 治理建模和专家经验融合后，导出标准训练快照包，metro-stack 按契约读取快照并训练。

## 背景

演示阶段已经证明了 DTS 与 metro-stack 的产品叙事：DTS 负责数据清洗、治理、质量、血缘和专家经验管理，metro-stack 负责小模型训练、评估、产物和上线门禁。但演示阶段仍有两个问题：

- DTS Parquet 能力尚未产品化，短期做 Parquet 会拉长交付周期。
- metro-stack 仍有 demo contract / stub API，正式版必须读取真实 DTS 导出的训练快照。

本 Sprint 明确不做 Parquet。第一阶段正式版采用 CSV 训练快照包，接口和契约保留 `data_format` 字段，未来可扩展为 Parquet。

## 目标架构

```text
运营商原始 CSV
  -> DTS 文件接入 / ODS 原始层
  -> dbt STG/DWD 模型
  -> 专家治理表融合
  -> 训练快照导出任务
  -> snapshot package:
       manifest.json
       schema.json
       quality_report.json
       lineage.json
       data.csv
  -> metro-stack 读取契约和 data.csv
  -> 窗口构造 / LSTM 训练 / 评估 / 产物
```

## 快照包契约

| 文件 | 必须 | 说明 |
|---|---|---|
| `manifest.json` | 是 | 快照 ID、版本、`data_format=csv`、`data_uri=data.csv`、窗口参数、特征数 |
| `schema.json` | 是 | 字段名、字段角色、必填标记、特征顺序、专家经验特征标记 |
| `quality_report.json` | 是 | 缺失率、重复率、时间连续性、专家覆盖率、阻断/告警项 |
| `lineage.json` | 是 | ODS、dbt 模型、导出任务、metro 训练任务血缘 |
| `data.csv` | 是 | 治理后的 LSTM 训练快照数据，不是原始 CSV 原样转存 |

## Feature 列表

| ID | Feature | 优先级 | Task 数 | 状态 | 依赖 |
|----|---------|--------|---------|------|------|
| F1 | CSV 训练快照契约与 dbt 包 | P0 | 2 | DONE | - |
| F2 | DTS 训练快照导出服务/API | P0 | 3 | READY | F1 |
| F3 | metro-stack 真实快照消费 | P0 | 3 | IN_PROGRESS | F1, F2 |
| F4 | 端到端集成验收 | P0 | 2 | READY | F1, F2, F3 |

**统计**: READY=7, IN_PROGRESS=0, DONE=3, BLOCKED=0

## 非目标

- 不做 Parquet 写入、读取或湖仓原生改造。
- 不引入 Spark/Trino/DuckDB 作为 DTS 主链路依赖。
- 不把运营商原始 CSV 原样导出给 metro-stack；必须经过 DTS 契约和治理模型。
- 不把 demo contract 当正式数据源。

## 完成标准

- [x] `thales/v1` dbt 包中的地铁训练模型明确输出 CSV snapshot contract 所需字段。
- [ ] DTS 后端提供训练快照导出 API，能产出五件套快照包。
- [ ] metro-stack 后端支持真实 snapshot package 目录校验和 `data.csv` 读取。
- [ ] metro-stack 前端 `数据与特征` 页面展示真实快照契约，而非固定 demo contract。
- [ ] 用一份运营商 CSV 样例跑通端到端，并在 `it/README.md` 记录命令、接口、输出文件和已知限制。

## 相关路径

- dbt 包: `worklog/v2.2.3/thales/v1/`
- DTS 后端: `source/dts-platform/`、`source/dts-ingestion/`
- DTS 前端: `source/dts-platform-webapp/`
- metro-stack: `/opt/prod/metro-app/sources/metro-stack/`
- 实施计划: `worklog/v2.2.3/sprint-30-202605/assets/implementation-plan.md`
- 集成测试: `worklog/v2.2.3/sprint-30-202605/it/README.md`
