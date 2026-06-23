# Sprint-3 集成 · 画布内核 · 实现进度（原型）

**状态**: DONE（核心，2026-06-23）

| Feature | 状态 | 说明 |
|---|---|---|
| F1 画布基础 | ✅ DONE | `@xyflow/react` v12 封装 + 5 类节点类型；节点面板(原生拖拽 + 点击添加 a11y 兜底)；连线/平移/缩放/网格/小地图/控件；zustand 画布 store |
| F2 节点卡片 | ✅ DONE | 单 transform 节点类型按 kind 渲染(图标+kind标签+名称+副标题+状态点+行数徽标+handle)；连接校验(源表无入/输出无出/禁自环/禁重复) |

## 部门为主模型落地
- 画布按**项目空间**加载（dev 辅助分组）：`transformService.getGraph(projectSpaceId)`。
- 集成阶段 = 项目空间选择器 + 该空间的 ELT 画布；资产产出归口部门。

## 验证（Playwright + 构建）
| 项 | 结果 | 证据 |
|---|---|---|
| 种子图渲染(PLM.订单→去重→连接←ERP.客户→ODS.宽表) | PASS | `it/canvas-full.png` |
| 5 类节点面板 + 节点卡片(状态点/行数徽标) | PASS | 同上 |
| 平移/缩放/网格/小地图/控件 | PASS | 同上(右下小地图、左下控件) |
| 连接校验(非法连接拒绝 + 提示) | PASS（逻辑） | `rejectReason()`：源表无入/输出无出/自环/重复 |
| 拖拽 + 点击添加(键盘可达) | PASS | 面板每项 draggable + 「+」按钮(aria-label) |
| reactflow CSS Chrome95 兼容 | PASS | 产物零 oklch/:has/容器查询/subgrid |
| tsc + chrome95 构建 | PASS | — |

## 待办（后续 S4）
- 节点配置属性抽屉、运行/调度/日志 dock、画布⇄列表双视图、dbt 生成映射。
- 画布图持久化(当前改动仅在内存 store)。
