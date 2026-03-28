# Ant Design 与 Tailwind 边界规范

## 1. 背景

当前两个前端项目的样式基础并不相同：

- `source/dts-platform-webapp`
  已同时使用 `AntD`、`Tailwind` 和自定义主题适配层。
- `source/dts-analytics-webapp/modern`
  当前仍以 `AntD + CSS/主题配置` 为主，没有必要为了“统一技术栈”强行引入 Tailwind。

本规范的目标不是统一到同一种技术，而是统一“谁负责什么”。

## 2. 总体结论

### 2.1 dts-platform-webapp

推荐保留 `Tailwind + AntD` 混用，但必须明确边界：

- `AntD` 负责组件系统和主题中心
- `Tailwind` 负责布局、容器、间距、壳层和轻量级自定义区域
- 优先禁用 Tailwind `preflight`
- 不推荐直接采用全局 `important: true`

### 2.2 dts-analytics-webapp/modern

当前不建议为了“风格统一”主动引入 Tailwind：

- 保持 `AntD + 主题 + CSS` 的现有结构
- 只有在明确需要 utility-first 布局体系时，才评估引入 Tailwind
- 在没有清晰收益前，不新增第二套样式范式

## 3. 职责边界

## 3.1 AntD 负责的内容

以下内容默认由 `AntD` 承担，不建议再用 Tailwind 或自定义 CSS 重做一套：

- 表单控件
- 表格
- 弹窗、抽屉
- 标签页
- 菜单、下拉
- 日期选择、树控件、分页
- 步骤条、上传、空态、结果态、加载态

原因：

- 这些组件交互复杂
- 已有主题 token、禁用态、悬浮态、错误态和可访问性能力
- 用 Tailwind 重拼或深度覆写，长期维护成本高

## 3.2 Tailwind 负责的内容

以下场景适合由 `Tailwind` 负责：

- 页面整体布局
- `flex/grid` 栅格组织
- 间距、留白、对齐
- 页面 header / content / side panel 壳层
- 自定义卡片外层容器
- 页面级响应式编排
- 非组件内部的轻量视觉修饰

换句话说：

- `AntD` 管组件
- `Tailwind` 管布局

## 4. 禁止混用的场景

以下情况应避免：

- 用大量 Tailwind class 改 `AntD` 组件内部 DOM 的视觉细节
- 用普通 CSS 与 AntD token 同时定义同一组颜色、圆角、字号
- 同一块 UI 同时叠加：
  - AntD token
  - Tailwind utility
  - scoped CSS
  - inline style
  且没有明确主次
- 为了覆盖 AntD 默认样式而开启全局 `important: true`

## 5. 对 dts-platform-webapp 的具体建议

## 5.1 保留 Tailwind，但收敛边界

`dts-platform-webapp` 已存在 Tailwind 和 AntD 共存的现实，不建议大幅回退。但后续重构应遵守：

- AntD 继续作为组件系统主轴
- 颜色、字号、圆角等主题能力以 AntD adapter 为准
- Tailwind 只处理布局和轻量容器

## 5.2 建议处理 preflight

推荐优先方案：

- 关闭 Tailwind `preflight`

理由：

- 避免基础 reset 干扰 AntD 组件默认表现
- 降低“全局基础样式被改写”的隐性风险
- 更符合当前项目以 AntD 为组件中心的结构

不推荐直接使用全局 `important: true`，因为：

- 会抬高调试成本
- 会让 AntD 覆盖链路变复杂
- 容易造成局部修样式时的优先级军备竞赛

## 6. 对 dts-analytics-webapp/modern 的具体建议

## 6.1 暂不主动引入 Tailwind

当前不建议为了“前端统一”把 Tailwind 加到 `analytics-webapp/modern`：

- 该项目当前不是 Tailwind 项目
- 主要复杂度在模板、图表、主题和编辑器，不在布局工具本身
- 新增 Tailwind 会增加样式体系复杂度

## 6.2 保持现有方向

建议继续沿用：

- `AntD`
- 主题 token
- 自定义 CSS
- 局部组件样式配置

即：先把现有样式体系收敛好，而不是再引入第二套范式。

## 7. 落地规则

后续重构时，建议直接按以下规则执行：

1. 业务组件优先使用 AntD
2. 页面布局优先使用 Tailwind（仅限 `platform-webapp`）
3. 全局视觉 token 只认 AntD 主题中心
4. Tailwind 不负责改 AntD 内部交互结构
5. `platform-webapp` 可混用 Tailwind，但要关闭 `preflight`
6. `analytics-webapp/modern` 暂不引入 Tailwind

## 8. 适用范围

本规范当前适用于：

- `source/dts-platform-webapp`
- `source/dts-analytics-webapp/modern`

后续若 `dts-admin-webapp` 也引入 Tailwind，应先补充本规范再实施。
