# Sprint-4 集成 · 配置/运行 · 实现进度（原型）

**状态**: DONE（2026-06-23）

| Feature | 状态 | 说明 |
|---|---|---|
| F1 属性抽屉 | ✅ DONE | 节点配置抽屉（各 kind 表单：源表/清洗/聚合/连接/输出，编辑写回画布 store）+ 数据预览(20 行 mock) |
| F2 运行 dock | ✅ DONE | ▶运行(模拟执行: running→ok + 节点状态回写) + 运行历史(成功/行数/耗时) + 日志 |
| F3 双视图 | ✅ DONE | 画布 ⇄ 列表 Segmented 切换；列表视图(收编 Transform 表格：节点/类型/状态/行数/上游) |
| F4 dbt 生成映射 | ✅ DONE | `generateDbtModels(graph)`：源表→source、清洗/连接/聚合→view、输出→table；ref()/source() 沿袭；"查看生成的 dbt"只读抽屉 |

## dbt 隐藏（核心验证）
- 用户在画布搭转换 → dbt 模型由 `generateDbtModels` 自动生成；普通流程不手写 dbt。
- 只读抽屉展示：`stg_1.sql`(view) / `int_2.sql`(view, join) / `mart_ods.sql`(table)，含 `{{ ref() }}`/`{{ source() }}` 沿袭。

## 验证（Playwright + 构建）
| 项 | 结果 | 证据 |
|---|---|---|
| 工作台布局(工具条+画布+运行 dock) | PASS | `it/workbench.png` |
| 运行 → 运行历史(成功·32,000 行·1000ms) + 节点状态回写 | PASS | `it/run-history.png` |
| dbt 自动生成只读抽屉(ref/source 沿袭) | PASS | `it/dbt-drawer.png` |
| 画布⇄列表切换 / 节点配置抽屉 | PASS（构建+类型） | 组件已接入 |
| tsc + chrome95 构建 | PASS | 产物零 oklch/:has/容器查询/subgrid |

## 待办（后续）
- 画布图持久化（当前改动在内存 store）；运行真实编排；节点配置落库。
- 包体代码分割（reactflow + AntD，gzip 偏大）。
