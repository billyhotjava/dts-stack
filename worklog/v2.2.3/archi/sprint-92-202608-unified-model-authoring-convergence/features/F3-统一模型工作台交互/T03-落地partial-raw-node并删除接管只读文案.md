# T03：落地 PARTIAL/raw node 并删除接管只读文案

**优先级**：P0  
**状态**：IMPLEMENTED  
**依赖**：T01、F2/T02、F2/T03

## 目标

在 visual 中呈现结构化节点和原始代码节点，删除接管/回切/整页只读交互，同时保留 provenance 的只读说明。

## 技术设计（Contract-first）

- **输入契约**：`AuthoringProjection` nodes/reasons/managed paths；provider files；button matrix。
- **输出契约**：structured node editor + raw node card `{sourcePath,line,column,checksum}`；来源 badge；无 ownership selector/transition dialog。
- **数据流**：projection → node list → structured form 或 lazy Monaco raw editor → 同一 provider save。
- **错误路径**：无法定位 raw node 显示文件级节点；checksum 变化触发冲突；projection NONE 仍保留 ModelSpec 基本信息/字段/依赖编辑。
- **复用点**：`DbtCodeEditor` lazy loader、ModelFieldEditorTable、现有诊断定位。
- **Chrome95**：不使用新 CSS selector/browser API；窄视口节点纵向排列。

## UI 交互规格

FULL 仅显示结构化编辑；PARTIAL 混合结构化卡片与 raw node；NONE 保留业务表单并显示一个实现 raw node。节点错误就地展示原因和“在代码视图定位”，不显示未来路线图。

## 影响范围

`AdvancedDbtWorkspace.tsx`、`ModelImplementationBindingFields.tsx`、visual node 组件、共享 CSS、source-contract/Vitest；不复制 Monaco loader 或 SQL IDE。

## 验证（RED→GREEN）

- [ ] source-contract 断言删除四类旧文案/按钮/selector。
- [ ] Vitest：FULL/PARTIAL/NONE、raw 定位、lazy Monaco、unmanaged conflict。
- [ ] UI smoke：ZIP 与平台生成样本的页面主动作相同。

## Definition of Done

- [ ] 页面不再出现“当前由代码维护 · 可视化只读”“接管代码实现”“转为可视化维护”。
- [ ] PARTIAL/NONE 可理解、可编辑、可恢复，且不伪装成完全结构化。
- [ ] 业务元数据不会因复杂 SQL 全部禁用。
