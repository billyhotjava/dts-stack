# F3：指标、工具与关系图

**优先级**：P1  
**状态**：DRAFT

## 目标

让指标、常用建模工具和跨模块关系在工作台可用，同时继续复用各自 canonical owner。

## 契约

| 类型 | 契约 | 要点 |
|---|---|---|
| 指标 | `/governance/indicators/**` | 版本、发布和 ModelSpec 字段引用不变 |
| 工具 | import/reverse/naming/SQL/dbt deep-link | 工具只触发既有流程 |
| 图 | `GET .../{planId}/relationship-graph` | 聚合只读 projection，无新表 |

## UI/UX

指标延续三栏：目录、列表、定义。工具页只提供高频入口和最近运行，不复制专业页面。关系图支持按节点类型筛选，点击节点回到对应 module/asset 深链。

## Tasks

| ID | Task | 状态 | 依赖 |
|---|---|---|---|
| T01 | 嵌入指标与通用工具 | DRAFT | F1/T01 |
| T02 | 建立只读关系图投影 | DRAFT | F1/T01、F2/T03 |

## Definition of Ready

- [x] Indicator owner 和图 projection 边界已确定。
- [ ] 客户数据规模画像完成后确认 graph 上限。
- [ ] 认证 UI 基线通过。

## 完成标准

- [ ] 指标创建/绑定/发布未回潮旧 semantic owner。
- [ ] 图节点可回到具体模型/维度/指标。
- [ ] 超限图不崩溃且明确提示截断。
