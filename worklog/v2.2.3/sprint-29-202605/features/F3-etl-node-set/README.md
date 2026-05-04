# F3: ETL 最小节点集（6 类节点）

**优先级**: P0
**状态**: READY
**依赖**: F1（画布）+ F2（节点库）

## 目标

按 ETL/数据入湖标准 pipeline 模型实现 6 类节点：Start / Source / Transform / Validate / Sink / End。每个节点继承 `BaseNode` 外壳（统一 handles、密级标识、状态色、错误徽标），只关心自己的内部表单/icon/默认参数。

## 节点能力矩阵

| 节点 | 输入 handle | 输出 handle | 主要参数 |
|------|------------|------------|---------|
| Start | - | 1 | 触发方式（手动/cron/事件） |
| Source | - | 1 | 数据源 ID（复用现有 catalog/datasets） |
| Transform | 1 | 1 | SQL/脚本类型 + 内容 |
| Validate | 1 | 2（pass/fail） | 校验规则集（complete/range/regex/custom）|
| Sink | 1 | - | 目标库 + 写入模式（append/overwrite/upsert） |
| End | 1 | - | 结束策略（success/notify/rollback） |

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | `BaseNode` 节点外壳：handles + 标题区 + 状态色 + 密级 tag | P0 | READY | F2-T04 |
| T02 | `StartNode` | P0 | READY | T01 |
| T03 | `SourceNode`：复用现有 dataset selector 组件 | P0 | READY | T01 |
| T04 | `TransformNode`：SQL/脚本节点，复用 SQL IDE 子组件 | P0 | READY | T01 |
| T05 | `ValidateNode`：双输出 handle（pass/fail）+ 规则集 chip | P0 | READY | T01 |
| T06 | `SinkNode` | P0 | READY | T01 |
| T07 | `EndNode` | P0 | READY | T01 |

## 完成标准

- [ ] 6 类节点都注册进 `nodeTypes` 并显示在 BlockSelector
- [ ] 每类节点拖出后能正常显示 icon、handles、默认 label
- [ ] BaseNode 在 dragging / selected / error 三种状态下视觉清晰可辨
- [ ] 节点级 ARIA：role + aria-label，键盘 tab 可达
- [ ] 单元测试：每类节点至少 1 个 snapshot 测试
