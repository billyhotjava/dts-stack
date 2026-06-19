# F2: 语义建模

**优先级**: P0
**状态**: READY

## 目标

在阶段④落地语义建模能力：语义建模中心（模型/对象/度量/主题统一入口）+ 语义发布/运行。把现网 modeling 筒仓的 `SemanticModelingCenter/Models/Objects/Metrics/Subjects/Runs/Publish` 收口进阶段④。语义层是指标的事实定义层——指标（F1）引用语义度量，语义模型映射 S5 发布的数据集；语义发布触发底层 dbt 自动生成（隐藏引擎），普通用户只在语义层操作。命名对齐现网 `SemanticModelingCenterPage` / `SemanticModelsPage` / `SemanticObjectsPage` / `SemanticMetricsPage` / `SubjectsPage` / `SemanticRunsPage` / `SemanticPublishPage`，service 用 `semanticModelingApi`。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| [T01](./T01-语义建模中心.md) | 语义建模中心（模型/对象/度量/主题） | P0 | READY | S4 |
| [T02](./T02-语义发布运行.md) | 语义发布/运行 | P0 | READY | T01 |

## 完成标准

- [ ] `SemanticModelingCenterPage` 作为语义入口，聚合模型(`SemanticModelsPage`)/对象(`SemanticObjectsPage`)/度量(`SemanticMetricsPage`)/主题(`SubjectsPage`)四视图。
- [ ] 语义模型映射到 S5 发布的数据集（事实来源），度量可被 F1 指标引用。
- [ ] `SemanticPublishPage` 支持把语义模型发布，发布即触发底层 dbt 自动生成（隐藏，仅 F4 抽屉可查产物）；`SemanticRunsPage` 用 CompactTable 呈现运行/发布历史。
- [ ] 全部经 `semanticModelingApi` 取数；样例项目可呈现「销售达成率」对应的语义度量与发布记录。
- [ ] 语义层各页**不出现 dbt 文件树或编辑入口**；发布产物 dbt 对普通用户透明。
