# F1: CSV 训练快照契约与 dbt 包

**优先级**: P0  
**状态**: DONE

## 目标

把地铁 dbt 包从演示模型调整为正式 CSV 训练快照契约，明确 `manifest/schema/quality/lineage/data.csv` 的字段、来源和约束。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | 固化 CSV snapshot contract | P0 | DONE | - |
| T02 | 更新 thales dbt zip 与 ODS 初始化 SQL | P0 | DONE | T01 |

## 完成标准

- [x] `manifest.json` 的正式格式改为 `data_format=csv`、`data_uri=data.csv`。
- [x] `schema.json` 能表达窗口字段、特征字段和专家经验字段。
- [x] dbt 包内模型名、ODS DDL、`models.tsv` 与 zip 内容一致。
- [x] `dbt parse` 通过，zip 可导入 DTS UI。
