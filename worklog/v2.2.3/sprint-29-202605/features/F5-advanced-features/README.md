# F5: 高级特性（iteration/loop subflow + 右键菜单 + 快捷键 + 撤销重做 + 便签）

**优先级**: P0（用户场景已确认需要 iteration + loop）
**状态**: READY
**依赖**: F4

## 目标

支撑用户已明确的两个核心 ETL 场景：
1. **多张表批量处理** → iteration 节点（输入 tableList，子流程内是「读 → 标准化 → 写」）
2. **增量同步直到追上** → loop 节点（带退出条件，反复执行直到 hasMore=false）

附带"工业级编辑器"必备的右键菜单、快捷键、撤销重做、便签。

> **本期不做**：嵌套子流程模板库（多 sprint 复用），需要时再单独立项。

## 嵌套限制（v1.0 硬约束，前端 + 后端双拦）

| 场景 | 是否允许 | 拦截位置 |
|------|---------|---------|
| 主画布 → Iteration | ✅ | - |
| 主画布 → Loop | ✅ | - |
| Iteration 内 → Source/Transform/Validate/Sink/End | ✅ | - |
| Loop 内 → Source/Transform/Validate/Sink/End | ✅ | - |
| Iteration 内 → Iteration | ❌ | 前端 BlockSelector 隐藏 + DSL schema 拒 + 后端 service 校验 |
| Iteration 内 → Loop | ❌ | 同上 |
| Loop 内 → Iteration | ❌ | 同上 |
| Loop 内 → Loop | ❌ | 同上 |
| 任何嵌套 → Note | ✅ | -（便签可放任何地方）|

**为何强约束 1 层**：
- 实现成本：递归嵌套子画布的 store namespace + DSL 反序列化复杂度指数上升
- 用户认知：超过 1 层嵌套的 graph 反而难以维护，违背"可视化"初衷
- YAGNI：用户当前需求只是「批量多表 + 增量同步」，1 层够用

**何时解封**：当用户明确提出"我需要在 Iteration 内做条件循环重试"等场景时，再开 sprint 升级到 v2.0 DSL。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | `IterationNode` 子流程节点（多表批量） | P0 | READY | F4-T06 |
| T02 | `LoopNode` 条件循环节点（增量同步） | P0 | READY | F4-T06 |
| T03 | 4 类右键菜单：画布 / 节点 / 连线 / 多选 | P0 | READY | F4-T01 |
| T04 | 快捷键：复制/粘贴/删除/全选/撤销/重做 | P0 | READY | T03 |
| T05 | 撤销/重做 store 集成（基于 zundo 或自实现 history slice） | P0 | READY | F1-T01 |
| T06 | `NoteNode` 便签节点：纯标注用，无连线，可换色 | P1 | READY | F3-T01 |

## 完成标准

- [ ] iteration 节点能配置 `inputArray` 与 `itemAlias`，子流程内可拖入其它节点
- [ ] loop 节点能配置 `exitCondition`（表达式或 max iterations），运行预览态能看到迭代次数
- [ ] 子流程 DSL 序列化：嵌套结构以 `children: WorkflowNode[]` 表示，反序列化幂等
- [ ] 右键菜单 4 个上下文均可触发，键盘 ESC 能关
- [ ] 撤销/重做对所有节点/边操作均生效，最多 50 步历史
- [ ] 便签节点不参与 DSL 执行（运行时跳过），只持久化在 graph_dsl
- [ ] Chrome 95：右键菜单、快捷键不崩
