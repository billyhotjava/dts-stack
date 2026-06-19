# Sprint-3 集成测试（IT）· 画布内核端到端验证

**状态**: READY
**范围**: 阶段② 集成 · 可视化 ELT 画布内核（F1 画布基础 + F2 节点卡片）的端到端验证。
**前置**: Sprint 1（外壳/设计系统/mock）就绪；`VITE_USE_MOCK=1`；legacy（chrome>=95）构建可启。

## 验证方法

- **手动 + Playwright**：拖拽与画布交互优先用 Playwright（含 `browser_drag` / `browser_press_key` / `browser_snapshot`）；纯逻辑（连接校验/store）补单测。
- **样例项目**："销售准备项目"（PLM.订单 + ERP.客户 → 去重/连接 → ODS.宽表），demo 节点数 ≤ 12。
- **a11y 工具**：axe（自动检查）+ 纯键盘走查。

## 端到端验证项

### A. 画布加载与样例（F1-T01/T04）

- [ ] A1 进入阶段② 集成页，画布挂载无控制台报错；`loadGraph` 经 mock `Result<T>` 注入样例图，节点/边渲染。
- [ ] A2 mock 返回 error 分支时，画布显示错误态而非空白/崩溃（不静默吞错）。
- [ ] A3 样例图 4 个节点类型/名称/状态/行数显示正确（PLM.订单·已连通、去重、ERP.客户·已连通、ODS.宽表·输出）。

### B. 拖拽落点（鼠标，F1-T02）

- [ ] B1 从节点面板用鼠标拖拽"清洗"节点到画布空白处，节点落在松手位置（`screenToFlowPosition` 坐标正确）。
- [ ] B2 5 类节点（源表/清洗/连接/聚合/输出）逐一拖入均成功，store.nodes 相应增长。
- [ ] B3 拖拽过程节点面板项有 grab/dragging 视觉态；落点后 `aria-live` 播报"已添加 X 节点"。

### C. 键盘 a11y 兜底（无鼠标，F1-T02）★

- [ ] C1 仅用 Tab 进入节点面板，方向键移动焦点，焦点环可见。
- [ ] C2 Enter/Space 选中某节点类型 → "添加到画布"按钮出现并可聚焦 → Enter 触发 → 节点落到画布默认落点（**全程无鼠标**）。
- [ ] C3 5 类节点均可通过键盘兜底添加，结果与拖拽一致。
- [ ] C4 axe 自动检查：面板项有 `aria-label`、按钮可达、无严重违规；`aria-live` 播报落点。

### D. 画布原生交互（F1-T03）

- [ ] D1 节点把手拖出可连线，边出现。
- [ ] D2 滚轮/控制器缩放、空白拖拽平移生效；缩放后视口外节点不渲染（虚拟化）。
- [ ] D3 拖动节点吸附网格（snap-to-grid，步长=配置值）。
- [ ] D4 小地图显示全部节点并随视口高亮；Controls 按钮（缩放/适配/锁定）可用。
- [ ] D5 缩放/平移仅触发 transform，无布局抖动。

### E. 节点卡片（F2-T01）

- [ ] E1 5 类卡片各显示正确图标 + 名称 + 状态点 + 行数徽标；行数 tabular-nums 对齐。
- [ ] E2 源表只有出 Handle、输出只有入 Handle，其余进出齐全。
- [ ] E3 点击卡片选中（store.selectedNodeId 更新），选中态高亮；卡片可键盘聚焦、aria-label 正确。

### F. 连接校验与边渲染（F2-T02）★

- [ ] F1 合法连接（源表→清洗→输出方向）成功，边带方向箭头并落库 store。
- [ ] F2 非法连接逐一被拦截：指向源表 / 从输出向外连 / 自环 / 重复边。
- [ ] F3 非法连接有可见反馈（拖拽态高亮 + 松手 message/`aria-live` 提示），不静默失败。
- [ ] F4 `validateConnection` 纯函数单测覆盖全部规则分支。

### G. 状态 store（F1-T04）

- [ ] G1 `addNode` 后原 nodes 数组未被 mutate（新引用，不可变）。
- [ ] G2 `connect`/`select`/`reset` 行为正确；selectedNode 由 selector 派生，无冗余存储。

### H. Chrome 95 / 兼容性

- [ ] H1 画布布局无 `:has()`/容器查询/subgrid；固定栏布局 + ResizeObserver 生效。
- [ ] H2 颜色全 HSL/hex，无 oklch（小地图/状态点/边）。
- [ ] H3 legacy（chrome>=95）构建通过，画布在 legacy bundle 下可交互。

### I. 端到端黄金路径串联

- [ ] I1 加载样例 → 键盘兜底添加 1 个"聚合"节点 → 合法连线接入 → 选中查看 → reset 还原，全链路可走通。

## 证据位置

- 截图 / 录屏：`worklog/prototype/plan/sprint-3-集成画布内核/it/assets/`
  - 拖拽落点：`assets/B-drag-drop.png`
  - 键盘 a11y 兜底走查：`assets/C-keyboard-a11y.png` / `assets/C-keyboard-a11y.webm`
  - 画布交互（缩放/小地图/网格）：`assets/D-canvas-native.png`
  - 节点卡片：`assets/E-node-cards.png`
  - 非法连接拦截反馈：`assets/F-invalid-connection.png`
- axe 报告：`it/assets/axe-report.json`
- 单测/逻辑验证输出：`it/assets/unit-summary.txt`（connectionRules / canvasStore）
- legacy 构建产物核验：`it/assets/legacy-build.txt`
- Playwright trace：`it/assets/trace.zip`

> 验证完成后在本文件勾选每项并归档对应证据；未达成项标注阻塞原因与责任 Task。
