# F2: React Flow 语义图画布

**优先级**: P0
**状态**: IN_PROGRESS

## 目标

使用 `@xyflow/react` 构建指标与语义中心主画布，承载资产、业务对象、Join、维度、指标、模型和发布节点。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | React Flow 基础架构 | P0 | DONE | F1 |
| T02 | 节点池和字段树 | P0 | IN_PROGRESS | T01 |
| T03 | Join 边与 fanout 风险 | P0 | READY | T02 |
| T04 | Graph draft 保存与加载 | P0 | READY | T03 |
| T05 | 图结构校验与节点诊断 | P0 | READY | T04 |

## 完成标准

- [ ] 画布基于 `@xyflow/react`，不再使用手写 SVG 模拟图编辑。
- [ ] 节点和边支持新增、连接、选择、配置、删除、保存、加载。
- [ ] 图结构校验结果能定位到具体节点和边。
