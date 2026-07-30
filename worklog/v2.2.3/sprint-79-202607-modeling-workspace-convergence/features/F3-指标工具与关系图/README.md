# F3：指标、工具与关系图

**优先级**：P1  
**状态**：PASS_WITH_GAPS（源码、定向测试与独立审查通过；PostgreSQL IT、部署和浏览器 IT-04 待执行）

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
| T01 | 嵌入指标与通用工具 | PASS_WITH_GAPS | F1/T01 |
| T02 | 建立只读关系图投影 | PASS_WITH_GAPS | F1/T01、F2/T03 |

## Definition of Ready

- [x] Indicator owner 和图 projection 边界已确定。
- [x] graph 输出上限冻结为 500 nodes / 1000 edges，reference work 上限为 2501。
- [x] 认证 UI 基线通过；F3 最终浏览器旅程仍待 IT-04。

## 完成标准

- [x] 指标面板继续复用 canonical Indicator owner，未建立旧 semantic 写入链。
- [x] 图节点具备模型 revision、维度、标准和指标工作台深链。
- [x] 超限图明确返回截断/继续提示；不完整 reference/edge 投影不签发误导性 cursor。
- [ ] IT-04 真实认证浏览器与 PostgreSQL 数据验证。
