# P2-01 AI 大屏 Copilot

`status`: `in-progress`  
`priority`: `P2`  
`inspiration`: `DTS 自身 NL2SQL/NL2Viz 能力 + Superset Explore 思路`

## 目标

提供“自然语言到大屏草稿”的端到端能力，显著提升建屏效率。

## 子任务

1. 意图解析
- 用户输入业务问题 -> 解析维度、指标、时间范围、过滤条件。

2. 查询生成
- NL2SQL 或语义查询生成 `QuerySpec`。

3. 图表推荐
- 根据数据类型与分析意图自动生成 `VizSpec`。

4. 布局生成
- 根据组件组合策略自动生成 `ScreenSpec` 草稿布局。

5. 人机协同
- 支持“重排布局/改图表类型/改配色”的对话式迭代。

## 验收标准

- 输入一个业务问题可在 30 秒内生成可预览草稿。
- 草稿支持一键转人工编辑并继续发布流程。

## 风险与回滚

- 风险：生成结果不稳定影响信任。  
- 回滚：保留“建议模式”，不自动覆盖人工配置。

## 实现记录（2026-02-14）

- 已完成 Copilot 首版“生成 + 二次指令优化”闭环：
  - 后端新增能力：
    - `ScreenAiGenerationService.revise(prompt, screenSpec)`：
      - 解析“改主题/重排布局/改图表类型/增加筛选器”类自然语言指令；
      - 基于已有 `screenSpec` 做增量修改并返回 `actions` 执行动作列表。
    - `ScreenResource` 新增接口：`POST /api/screens/ai/revise`。
  - 前端新增能力：
    - `analyticsApi.reviseScreenSpec(...)`；
    - `ScreensPage` AI 弹窗新增“优化指令”输入框与“按指令优化”按钮；
    - 结果面板展示已执行动作（`actions`）。
- 典型可执行指令（当前已支持）：
  - `改成浅色/深色/钛合金主题`
  - `重排布局` / `改成两列布局` / `改成三列布局`
  - `首图改成柱状图/折线图/饼图/地图`
  - `增加筛选器`
- 已验证：
  - `mvn -f source/dts-analytics/pom.xml -DskipTests clean compile` 通过。
  - `pnpm -C source/dts-analytics-webapp/modern typecheck` 与 `build` 通过。
- 待继续：
  - NL2SQL / 指标语义模型联动（当前仍为启发式屏稿调整）。
  - 多轮会话上下文（当前为单轮 prompt + screenSpec 增量）。
