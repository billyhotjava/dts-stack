# 黄金链路 Smoke 计划

**Sprint**: Sprint-31
**Feature**: F1/T02
**状态**: DONE

## 目标

定义一份可重复执行的 smoke 脚本，从数据源登记验证到 ODS、dbt、Catalog、语义候选和消费层权限检查，避免继续按模块孤岛验收。

## 脚本

```text
worklog/v2.2.3/sprint-31-202605/it/scripts/golden-path-smoke.sh
```

## 输入变量

| 变量 | 默认值 | 说明 |
|---|---|---|
| `PLATFORM_BASE_URL` | `http://127.0.0.1:18082` | platform API |
| `DBT_PROJECT_DIR` | `services/dts-dbt` | dbt project |
| `DBT_PROFILES_DIR` | `services/dts-dbt/profiles` | dbt profiles |
| `DATA_SOURCE_ID` | 空 | 样例 JDBC 数据源 ID |
| `CATALOG_ASSET_ID` | 空 | 样例资产 ID |
| `SERVICE_TOKEN` | 空 | internal service token |

## 输出

- capabilities 响应摘要
- ODS 预检响应
- dbt compile/test/build 结果
- catalog asset contract/schema contract 响应
- governance gaps / lineage failures 摘要
- asset permission check 响应

## 当前约束

本阶段只生成脚本和计划，不执行 smoke；最终统一测试阶段归档实际输出。
