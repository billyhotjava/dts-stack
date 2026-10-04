# Sprint-60 dbt 实测证据

日期：2026-07-14

## 运行环境

- 容器：`dts-dbt`（dbt-core 1.11.3，dbt-postgres 1.10.0）
- 项目：`/opt/dbt/dbt_model`
- profiles：`/root/.dbt`
- target：`dev`

## 结果

| 命令 | 结果 |
|---|---|
| `docker exec dts-dbt sh -lc 'dbt parse --project-dir /opt/dbt/dbt_model --profiles-dir /root/.dbt'` | 通过；项目完整解析 |
| `docker exec dts-dbt sh -lc 'dbt test --project-dir /opt/dbt/dbt_model --profiles-dir /root/.dbt --target dev'` | 执行 84 项：PASS=78、ERROR=6 |

6 个错误均为当前环境缺少 PostgreSQL 源表：

- `public.stg_pm__budget_v2`
- `public.stg_pm__risk_info_v2`
- `public.stg_pm__project_subject_domain_v2`

因此本轮证明了 dbt 项目可解析、测试命令可执行，但不能把真实数据质量结果标记为全绿；补齐 Addax/ODS 入库或测试 fixture 后需要重跑 `dbt test`。
