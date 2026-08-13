# T02: 实现 dbt 模型只读投影

> 原名「实现 dbt 模型只读投影与回切交互」。回切部分已移交 `assets/sprint-92-back-conversion-handoff.md`（ADR-91-09），文件名保留不变。

**优先级**: P1
**状态**: IN_PROGRESS（源码与聚焦自动化完成；真实浏览器 IT 待补）
**依赖**: F0/T01、F1/T02

## 目标

让 DBT_MANAGED 模型在可视化模式中可读、不可误写，并让用户理解「为什么不能在这里改」以及「该去哪里改」。

## 技术设计 (Contract-first)

- **输入契约**: BUSINESS representation 的 `visualizationCapability`（`BUSINESS_VISUAL_READ` / `BLOCKED`）、`allowedActions`、`capabilityReasons`。**本 Task 不消费任何 transition 端点。**
- **输出契约**: 纯只读 visual form；无写控件、无回切按钮、无置灰假按钮。
- **数据流**: 打开 visual → 渲染 representation projection → 结束。无写路径。
- **错误路径**: `BLOCKED` 时展示逐条 `capabilityReasons` 的中文映射，并给出「在代码模式查看实现」的指引；representation 请求失败时保留模型上下文并提供重试，不本地伪造可编辑状态。
- **复用点**: 当前 `readOnly` form、`RequestState`、`Status`；`capabilityReasons` 的中文映射与 F1/F2 共用一份字典，不各写一套。

## UI 交互规格

1. 用户打开 DBT_MANAGED 模型，visual tab 顶部显示「当前由代码维护 · 可视化只读」。
2. 字段、来源、依赖只读；不得出现可修改但保存被后端拒绝的控件。
3. 面板底部一行说明：「本模型由代码维护，请在代码模式修改实现。」——陈述当前事实，不承诺未来能力，也不是禁用按钮。
4. 投影不可信时，逐条列出原因（动态依赖 / 字段不可信 / 依赖不可信 / 制品版本不匹配），并提供切到代码模式的入口。

## 影响范围

- visual mode form 的 `readOnly` 判定（改为消费 `allowedActions` 而非 ownership 猜测）
- `capabilityReasons` 中文映射字典（与 F1/F2 共用）
- component/source-contract tests、IT-04

## 验证 (RED→GREEN)

- [ ] DBT visual 的所有写控件和快捷修改均不可用。
- [ ] 页面**不存在**任何回切按钮，含置灰态——以 source-contract 断言组件树中无该动作标识。
- [ ] `BLOCKED` 四种 reason 各有一条中文文案断言。
- [ ] representation 请求失败时不渲染可编辑控件。
- [ ] 用户可见文案使用「代码维护 / 可视化维护」，不暴露 `DBT_MANAGED` 等内部枚举名。

## Definition of Done

- [ ] DBT 模型 visual 投影不存在伪编辑入口，也不存在伪回切入口。
- [ ] 只读判定由 `allowedActions` 驱动，与后端能力一致。
- [ ] IT-04 真实浏览器证据覆盖「投影可信」与「投影 BLOCKED」两种样本。
