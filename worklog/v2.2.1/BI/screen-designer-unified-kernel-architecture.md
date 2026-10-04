# 大屏设计器统一内核架构方案（借鉴 Metabase / Superset / DataEase 思想）

> 原则：借鉴设计思想，不复制源代码。
>
> 日期：2026-02-13
> 适用阶段：研发期（可清理历史数据）

---

## 1. 结论
- `worklog/v2.2.1` 现有指令结构清晰（README 索引 + 总任务清单 + BI 分解文档），可直接作为推进基线。
- 下一步不建议“Metabase/Superset 二选一绑定”，建议采用：
  - `统一内核协议` + `多引擎适配器` + `插件化渲染`
- 目标是形成 DTS 独立 BI/大屏产品，而不是外部系统 UI 的替代壳。

---

## 2. 三家设计思想的借鉴映射

### 2.1 Metabase（借鉴点）
- 借鉴点 A：语义化建模（Model/Metric 优先，不让业务用户直接面对底层 SQL 细节）。
- 借鉴点 B：Question/Card 作为可复用分析资产。
- 借鉴点 C：嵌入与公开分享链路（含权限和安全策略）。
- 在 DTS 的落地：
  - `DatasetSpec + MetricSpec` 进入内核；
  - `Card` 作为数据源类型之一（adapter 映射）；
  - 分享能力落在 Screen 发布与 PublicLink 策略层。

### 2.2 Superset（借鉴点）
- 借鉴点 A：SQL Lab + Explore 双轨分析模式。
- 借鉴点 B：可插拔可视化插件体系。
- 借鉴点 C：Native Filter + Dashboard 联动机制。
- 在 DTS 的落地：
  - 查询侧支持 `语义查询` 与 `SQL 查询` 双模式；
  - 图表采用 `RendererPlugin` 插件协议；
  - 统一 `InteractionSpec` 驱动全局变量和联动。

### 2.3 DataEase（借鉴点）
- 借鉴点 A：低门槛拖拽建屏与模板资产化。
- 借鉴点 B：企业权限与分享能力可配置。
- 借鉴点 C：多数据源接入与业务导向可视化。
- 在 DTS 的落地：
  - 模板市场 + 主题资产包 + 场景行业包；
  - Screen 级 ACL + 审计日志 + 安全分享；
  - 数据源统一协议（DB/API/Card/语义层）。

---

## 3. 目标架构（重构模式）

## 3.1 逻辑分层
1. `dts-bi-kernel`（领域内核）
- 定义统一协议：`QuerySpec`、`DatasetSpec`、`MetricSpec`、`VizSpec`、`ScreenSpec`、`InteractionSpec`。
- 仅表达“是什么”，不依赖 UI 框架和外部 BI 引擎。

2. `dts-bi-runtime`（运行时）
- 负责参数解析、查询调度、联动执行、缓存刷新、错误归一化。
- 输入 `ScreenSpec`，输出可渲染状态树。

3. `dts-bi-designer`（编辑器）
- 负责画布编辑、属性面板、图层、模板、发布流程。
- 只操作内核对象，不直接处理外部引擎格式。

4. `dts-bi-adapters`（外部能力适配）
- `metabase-adapter`：Card/Query -> `QuerySpec/VizSpec`。
- `superset-adapter`：Chart metadata -> `VizSpec`。
- `native-adapter`：DTS 自有 SQL/语义查询执行。

5. `dts-bi-plugins`（插件层）
- 图表插件、数据源插件、交互插件。
- 通过注册协议扩展，而非改核心代码。

## 3.2 推荐目录（基于 modern）
- `source/dts-analytics-webapp/modern/src/features/bi-kernel`
- `source/dts-analytics-webapp/modern/src/features/bi-runtime`
- `source/dts-analytics-webapp/modern/src/features/bi-designer`
- `source/dts-analytics-webapp/modern/src/features/bi-adapters/metabase`
- `source/dts-analytics-webapp/modern/src/features/bi-adapters/superset`
- `source/dts-analytics-webapp/modern/src/features/bi-plugins`

---

## 4. 核心协议建议（v2）

### 4.1 QuerySpec
- 字段建议：
  - `sourceType`: `metric | dataset | sql | card | external`
  - `sourceRef`: 引用对象 ID
  - `sql`/`semanticQuery`: 查询表达
  - `params`: 参数定义与默认值
  - `securityContext`: dept/classification/tenant
  - `cachePolicy`: ttl / key strategy

### 4.2 VizSpec
- 字段建议：
  - `vizType`: `line|bar|pie|table|kpi|map|...`
  - `fieldMapping`: 维度/指标/系列映射
  - `style`: 主题无关样式配置
  - `behavior`: 钻取、跳转、联动动作定义

### 4.3 ScreenSpec
- 字段建议：
  - `layout`: 组件树与网格信息
  - `components`: 组件实例（绑定 VizSpec/DataBinding）
  - `globalVariables`: 全局变量
  - `interactions`: 联动图
  - `publish`: 草稿/发布版本信息

---

## 5. 功能清单（重构专项）

## 5.1 阶段 A：统一内核落地（M0）
- A1：定义 `Spec v2`（Query/Viz/Screen）与 schema 校验。
- A2：设计器改造为只读写 `ScreenSpec`。
- A3：运行时改造为仅执行 `QuerySpec`。
- A4：Metabase 通过 adapter 接入（先保留 card 数据源）。
- A5：发布/回滚模型接入 `ScreenSpec`。

## 5.2 阶段 B：双模式分析能力（M1）
- B1：`metric/dataset/sql/card` 四类数据源统一到 `QuerySpec`。
- B2：全局变量 + 组件联动 + 钻取统一到 `InteractionSpec`。
- B3：图表插件化（先沉淀 ECharts 基础插件集）。
- B4：模板与主题资产中心落地。

## 5.3 阶段 C：融合增强（M2）
- C1：Superset adapter（导入图表配置到 `VizSpec`）。
- C2：企业能力（ACL、审计、安全分享）并入内核发布模型。
- C3：性能治理（缓存、预热、故障降级）。
- C4：AI 生成草稿（NL2SQL -> VizSpec -> ScreenSpec）。

---

## 6. 不做清单（明确边界）
- 不做：直接复制 Metabase/Superset/DataEase 前后端源码。
- 不做：在设计器中继续扩散外部引擎专有 JSON 结构。
- 不做：未定义内核协议就先堆新组件。

---

## 7. 验收标准
- 架构验收：
  - 新增组件无需改 `ScreenDesigner` 核心代码即可注册渲染。
  - 数据源新增通过 adapter 完成，无需侵入运行时主流程。
- 功能验收：
  - 相同大屏在 `native-adapter` 与 `metabase-adapter` 下可一致运行。
  - 发布版与草稿版隔离，公开链接只读发布版。
- 工程验收：
  - 关键协议有 schema 测试与契约测试。
  - 主要链路有错误码与 requestId 可追踪。

---

## 8. 近期执行建议（两周）
1. 第 1 周：完成 `Spec v2` + adapter 接口定义 + 目录改造。
2. 第 2 周：完成 Metabase adapter 首版迁移（screens 全链路跑通）+ 基础契约测试。

