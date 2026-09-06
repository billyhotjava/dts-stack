# IT-04 复合粒度键 dbt 验证记录

- 提交：`33a31f595094cea900f81cbfcdafb5f7c42c7f1b`
- 正式镜像：`dts-dbt:1.10.0`，ID `sha256:0ba5a2a6d1999ddfffd9f7b9265811ec184cbb9b53ea3c669b4c0e15a2c16a17`
- 运行时：dbt Core `1.10.22`，Postgres adapter `1.10.0`
- 原始机器可读证据：[T04-composite-dbt.json](T04-composite-dbt.json)；宏来源摘要为 `5d43e82a01bbec9d97878479f3122a35a85904db4876b1112cb4dab6eb5d2e60`。

以上是已有专项运行的实际 SHA；当前正式构建候选为 `f14830709`，尚未以该候选重新执行本专项。

| 样例 | 预期退出码 | 实际 | 结果 |
| --- | ---: | ---: | --- |
| `valid_order` | 0 | 0 | `(project_id, month_id)` 唯一且两个键均非空，3 项测试通过。 |
| `valid_reversed` | 0 | 0 | 换为 `(month_id, project_id)` 后同样 3 项测试通过。 |
| `duplicate_combination` | 1 | 1 | 组合唯一性测试检出 1 条重复组合。 |
| `duplicate_reversed` | 1 | 1 | 换键顺序仍检出同一重复组合。 |
| `null_key` | 1 | 1 | `month_id` 的 `not_null` 检出 1 条空值。 |

执行使用一次性、`--network host` 的正式 dbt 镜像容器；部署源码为只读挂载，证据目录为唯一可写挂载，认证仅通过环境变量传入，未写入本归档。

此验证以临时 `ephemeral` 的 `SELECT VALUES` 样例运行 generic tests，证明宏与 dbt 运行时行为；它不是模型编译产物、发布、物化或页面端到端验收。
