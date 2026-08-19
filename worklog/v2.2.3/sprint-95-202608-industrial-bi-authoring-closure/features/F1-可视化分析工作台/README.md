# F1：可视化分析工作台

**优先级**：P0  **状态**：DONE

## 目标

分析人员在现有治理分析编辑器中通过字段货架获得实时图表，并完成样式、派生指标、日期、筛选和排序配置。

## 契约与 UI

- 路由：`/bi/questions/new`、`/bi/questions/{id}/edit`；不新增入口。
- 输入：`AnalysisDatasetDetail` + `AnalysisQuerySpec`；输出仍是 `AnalysisQuerySpec`。
- 三栏布局：左侧治理字段；中间货架+图表；右侧图表类型/样式/计算/筛选。
- 四态：契约 loading/error、无字段、查询 loading/error/empty/success、published read-only。
- 操作走查：从数据集创建 → 拖维度到横轴 → 拖度量到纵轴 → 自动出图 → 调样式 → 新增计算 → 保存重载仍一致。

## Task

| ID | Task | 状态 | 依赖 |
|---|---|---|---|
| T01 | 字段货架与实时真实图表 | DONE | F0 |
| T02 | 样式、计算和配置 round-trip | READY | T01 |

## Definition of Ready

- [x] owner/spec/query contract 已钉死。
- [x] UI 落点、四态、Chrome95 降级已命名。
- [x] RED 测试目标明确。
