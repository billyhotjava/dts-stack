# F1: 基础画布与 zustand store（抄 Dify workflow 三层 slice）

**优先级**: P0
**状态**: DONE
**依赖**: F0（structuredClone polyfill 必须先到位）

## 目标

搭建 Dify 风格 workflow 编辑器的"骨架"：zustand 三层 slice（nodes / edges / ui）+ ReactFlow Provider + 自定义边/连线/对齐辅助线 + 缩放工具栏，让后续 BlockSelector/Node/Panel 都有可挂载的"地基"。

## 背景

Dify `web/app/components/workflow/` 把状态拆为三类 slice：
- **nodes-slice**：节点增删改查、位置同步、isDragging
- **edges-slice**：连线增删、handles 校验
- **ui-slice**：当前选中、画布缩放、help-line 显隐、panel 打开状态

不抄 Dify 的 collaboration / datasets-detail-store / debug-store 等领域无关切片。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | 创建 `src/components/workflow/store/` 三层 slice（nodes/edges/ui）+ workflow-store 入口 | P0 | DONE | F0-T01 |
| T02 | 创建 `WorkflowCanvas` 基础壳：ReactFlowProvider + 配色 + viewport 持久化 | P0 | DONE | T01 |
| T03 | 自定义 `CustomEdge`：渐变色 + label 槽 + 删除 hover 按钮 | P0 | DONE | T02 |
| T04 | 自定义 `CustomConnectionLine`：拖拽中虚线 + 端点高亮 | P0 | DONE | T02 |
| T05 | `HelpLine` 对齐辅助线：节点拖拽时显示水平/垂直对齐参考线 | P1 | DONE | T02 |
| T06 | `Operator` 工具栏：缩放 / fit view / 截图 / 撤销重做占位（实际逻辑在 F5-T05） | P0 | DONE | T02 |
| T07 | 在 OrchestrationPage 新增「编排画布」Tab（暂只挂壳，无节点） | P0 | DONE | T02..T06 |

## 完成标准

- [x] 路径 `src/components/workflow/` 初始化完成，目录结构与设计稿一致（store / hooks / operator / styles 子目录全开）
- [x] zustand store 全 immutable 更新（spread copy 配 22 个 slice 单测，禁止 mutate）
- [x] OrchestrationPage 进入「编排画布」Tab 能看到空白画布 + Operator 工具栏，缩放/平移/fit view 正常
- [x] CustomEdge / CustomConnectionLine 在 Chrome 95 上拖拽连线不崩（依赖 F0 polyfill；isValidConnection 拒绝自环 / 重复 handle）
- [x] 单元测试：workflow 模块 40 用例 + OrchestrationPage 2 用例 全绿；store 三层 slice 覆盖 add/update/remove/setPosition/setDragging 等
- [x] **YAGNI 跟踪**：Operator 截图按钮 disabled，待引入 `html-to-image` 后启用（见 T06 文档尾部）
