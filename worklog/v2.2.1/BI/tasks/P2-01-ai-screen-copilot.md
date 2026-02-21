# P2-01 AI 大屏 Copilot

`status`: `done`  
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
- 增强（2026-02-16）：
  - `ScreenAiGenerationService.generate/revise` 新增 `intent/queryRecommendations/vizRecommendations` 输出：
    - `intent`: 领域、时间范围、粒度、指标、维度、筛选器识别结果；
    - `queryRecommendations`: 面向 `q-kpi/q-trend/q-compare/q-share/q-detail` 的建议查询规范；
    - `vizRecommendations`: 查询到组件类型映射建议（NL2Viz 首版）。
  - `revise` 新增自然语言执行能力：
    - `改成4K/1080P`
    - `放大字体/缩小字体`
    - `增加指标卡`
    - `删除明细表/表格`
    - `刷新30秒/5分钟`（自动解析刷新间隔）
  - 前端 `ScreensPage` AI 弹窗新增意图、查询建议、图表建议展示，便于人工确认和继续编辑。
  - 前端 AI 弹窗新增“复制建议”按钮：
    - 一键复制 `intent/queryRecommendations/vizRecommendations/quality/actions` JSON；
    - 便于交给数据建模/接口同学快速落地绑定。
- 增强（2026-02-17）：
  - `revise` 新增“Tab 场景切换”指令能力（如：`加一个tab切换场景/分场景展示`）：
    - 自动补齐 `tabKey` 全局变量；
    - 自动新增或更新 `tab-switcher` 组件；
    - 自动为图表/表格类组件生成 `visibilityRule*` 显隐规则，实现一键场景切换。
  - AI 反馈文案补强：
    - 当指令未命中时，提示中增加 `加tab切换场景` 示例；
    - 优化建议列表同步加入 `Tab 场景切换` 指令，引导前端快速试用。
  - 新增“移除 Tab 场景”反向指令（如：`移除tab切换/取消场景切换`）：
    - 自动删除 `tab-switcher` 组件；
    - 自动清理 `tabKey` 全局变量；
    - 自动清理组件中的 `visibilityRule*` 关联配置。
  - 单元测试补齐：
    - 新增 `ScreenAiGenerationServiceTest`，覆盖“generate首版Tab场景 + 新增Tab场景 + 更新已有Tab + 移除Tab场景”四条核心链路；
    - 验证命令：`mvn -f source/dts-analytics/pom.xml -Dtest=ScreenAiGenerationServiceTest test`。
  - 移除 Tab 场景清理鲁棒性增强（2026-02-19）：
    - `removeTabScenarioSwitcher` 默认纳入历史 `tabKey` 清理集合，避免“自定义变量Key + 历史tabKey残留”导致部分组件规则未被回收；
    - 新增回归用例：`revise_removeTabScenarioInstruction_cleansCustomTabVariableAndLegacyTabKey`；
    - `ScreenAiGenerationServiceTest` 用例数已扩展为 5 条并通过。
- 启发式语义联动补强（2026-02-20）：
  - 后端 `ScreenAiGenerationService` 新增语义建模输出：
    - `semanticModelHints`（`factTable/timeField/metricMappings`）；
    - `sqlBlueprints`（按 `q-kpi/q-trend/...` 生成可落地 SQL 模板草图）；
    - `queryRecommendations` 补齐 `semanticLayer/factTable/timeField/sqlHint`。
  - 前端 `ScreensPage` AI 结果卡新增“语义映射 / SQL蓝图”可视化摘要；
  - “复制建议”载荷新增 `semanticModelHints/sqlBlueprints`，便于和建模同学联调。
  - 单测补齐：`generate_salesPrompt_outputsSemanticHintsAndSqlBlueprints`，覆盖语义输出核心字段。
- 待继续：
  - 与真实语义层/NL2SQL 执行引擎联动（当前仍为启发式 SQL blueprint，不直接执行真实查询）。
  - 多轮会话上下文（2026-02-15 已完成首版）：
    - 后端 `POST /api/screens/ai/revise` 支持可选 `context[]`；
    - `ScreenAiGenerationService.revise` 支持结合历史上下文 + 当前指令做启发式解析；
    - 响应新增 `contextCount`，前端预览卡显示本次使用上下文条数；
    - 前端 AI 弹窗新增上下文历史区（最近 12 条），支持清空；
    - 每次优化调用自动携带最近 8 条上下文，降低“每轮从零提示”导致的结果漂移。
- 优化模式补强（2026-02-20）：
  - `POST /api/screens/ai/revise` 新增 `mode=apply|suggest`；
  - 返回新增 `applyMode/applied` 标记，建议模式会额外提示“仅预览建议，不自动覆盖发布内容”；
  - 前端 AI 弹窗新增“优化模式”切换（应用模式/建议模式），并在结果卡展示当前模式。
- 多轮上下文稳态增强（2026-02-20）：
  - 后端 `ScreenResource.parseAiContext` 增加上下文条数与单条长度上限，避免超长上下文拖慢提示构建；
  - `ScreenAiGenerationService` 对上下文做去重、截断并新增 `usedContextCount` 回传，区分“传入条数”与“实际使用条数”；
  - 前端 AI 结果卡展示 `usedContextCount`，便于判断上下文是否被裁剪；
  - 单测补齐：覆盖上下文去重/裁剪后的计数行为，确保建议模式与应用模式下输出一致。
- 外部语义/NL2SQL 桥接（2026-02-21）：
  - `ScreenAiGenerationService` 新增可选桥接能力（默认关闭）：
    - 配置 `dts.ai.semantic.bridge.url` 或环境变量 `DTS_AI_SEMANTIC_BRIDGE_URL` 后，生成/优化会将 `intent/queryRecommendations/sqlBlueprints/vizRecommendations` 发往外部服务；
    - 桥接返回的推荐结果会覆盖启发式推荐，并在 `engine` 标记追加外部引擎名（如 `heuristic-v1+bridge-engine`）。
  - 增加桥接超时配置：`dts.ai.semantic.bridge.timeout-ms`（默认 2500ms，范围 500-15000ms）。
  - 桥接异常/超时自动回退启发式结果，并向 `quality.warnings` 注入可读提示，确保生成链路稳定。
