# 页面能力矩阵

本 Sprint 不新增页面。唯一页面 owner：`/data-modeling/dimensions/workbench?modelSpecId={id}&view=visual|code`。

| 页面区域 | 当前能力 | Sprint-92 目标 | API/owner | 验收 |
|---|---|---|---|---|
| 模型上下文条 | 展示 model/implementation revision、ownership、capability | 展示 model/implementation pins、草稿状态、provenance、projection coverage；不展示“所有权”作为维护方式 | authoring context | IT-02～05 |
| 模式切换 | visual/code；按 ownership 可读写 | 两个视图均指向同一 draft；切换无写入、保留 dirty | URL + authoring draft | IT-03 |
| 可视化模型表单 | DBT 模型可能整页只读 | 业务元数据始终按模型状态/权限编辑；实现节点按 projection 逐节点编辑 | ModelSpec snapshot + projection | IT-04/05 |
| 代码工作区 | DESIGNER 先预览并“接管”；DBT 可编辑 | 所有来源直接编辑同一 bundle；移除接管/回切主路径 | authoring draft files | IT-03/04/09 |
| 工具栏 | visual 保存与 code save/validate/commit 分离 | 统一“保存草稿 / 校验 / 提交实现”；共享 busy/error/receipt | authoring facade | IT-03/06 |
| PUBLISHED 状态 | 只读提示但下一步不统一 | 唯一主动作“创建新草稿版本”；成功仍在同一 ModelSpec | `FORK_PUBLISHED` | IT-02 |
| 发布与物化 | 既有发布弹窗/物化入口 | 零新增入口；消费 authoring commit pins | release/materialization owner | IT-07/08 |

## 页面四态

| 状态 | 表现 |
|---|---|
| 加载 | 保留模型标题与模式按钮骨架；显示“正在读取创作上下文”，不猜测可写能力 |
| 空 | 未选择模型：引导从模型列表选择/新建；已选择但无实现：创建 canonical initialization 草稿 |
| 错误 | 展示稳定错误码、可重试动作和 correlationId；未保存内容留在浏览器内存 |
| 成功 | 显示 DRAFT、pins、provenance、coverage；保存/校验/提交反馈与当前 draft 一致 |
| 权限不足 | 业务只读投影可看；技术正文和写控件不渲染/禁用并说明所需权限；禁止先请求正文再隐藏 |
| PUBLISHED | 所有编辑控件只读；仅“创建新草稿版本”可用 |

## 布局

```text
┌ 模型标题 / DRAFT|PUBLISHED / rN / 来源 / 投影覆盖 ──────────────────────┐
│ [可视化] [代码]                       [创建新草稿版本*] [刷新]          │
├ 模型业务定义与实现编辑区 ──────────────────────────────────────────────┤
│ visual: 基本信息 + 字段/依赖 + 结构化节点 + 原始代码节点                │
│ code:   文件树 + Monaco + 同一诊断列表                                  │
├ 统一动作 ───────────────────────────────────────────────────────────────┤
│ [保存草稿] [校验] [提交实现]   ETag / validated / dependency receipt     │
└─────────────────────────────────────────────────────────────────────────┘
```

`*` 仅 PUBLISHED 显示；DRAFT 时不出现第二个创建按钮。

