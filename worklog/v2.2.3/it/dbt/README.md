# IT Demo dbt 高级实现

本项目为 `worklog/v2.2.3/it` 中 6 个 ModelSpec 提供高级 SQL/dbt 实现。它不创建第二套逻辑模型台账，也不创建物理 STG 表。

## 目标关系

```text
model.it_demo_governance.it_demo_dwd_dim_date
model.it_demo_governance.it_demo_dwd_dim_org
model.it_demo_governance.it_demo_dwd_dim_project
model.it_demo_governance.it_demo_dwd_fct_task_snapshot
model.it_demo_governance.it_demo_dws_project_health
model.it_demo_governance.it_demo_ads_project_overview
```

每个节点都必须在 DTS 中绑定对应 ModelSpec ID 和 revision。

## 源表

默认从 `public` Schema 读取：

- `ods_it_demo_org`
- `ods_it_demo_project`
- `ods_it_demo_task_snapshot`

如 Demo ODS 位于其他 Schema，运行时设置：

```bash
dbt build --vars '{"it_demo_ods_schema": "your_schema"}'
```

## 本地语法/编译验证

使用 DTS 运行时 profile，不在本目录保存凭据：

```bash
dbt deps
dbt parse
dbt compile
dbt test --select source:it_demo_ods
dbt build --select tag:it-demo
```

`dbt build` 必须在已经创建 3 张 Demo ODS 的目标环境运行。任何 target、logs、profile、密码或编译产物都不得提交到本目录。

## 设计说明

- 6 个业务模型全部物化为 table，确保质量演示删除脏数据后可以通过 FULL 重建恢复。
- 周期历史保留在任务快照事实中。
- 状态和风险原始值在事实 SQL 中收敛为稳定 ASCII 标准码。
- 未识别值映射为 UNKNOWN，但 schema test 的发布白名单故意不接受 UNKNOWN，因此脏数据会阻断。
- 项目健康状态输出 `PH-GREEN`、`PH-AMBER`、`PH-RED`。
- 质量测试严重度均为 error。
