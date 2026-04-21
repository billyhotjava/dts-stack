# T05: SQL 实时预览 + 图形选择 + 结果渲染

**优先级**: P0
**状态**: READY
**依赖**: T02, T03, T04

## 目标

打通 CardEditor 的**下半区**：SQL 实时预览、图形类型选择、结果执行 + ECharts 渲染、保存 Card。这是 CardEditor 最终"出活"的一步，前面 T02/T03/T04 的选择都在这里变成用户可见的数字和图。

## 技术设计

### 1. 下半区布局

```
┌────────────────────────────────────────────────────────┐
│ [SQL 预览] [结果] [图形] (Tab 切换)                     │
├────────────────────────────────────────────────────────┤
│                                                        │
│ (Tab 当前内容)                                         │
│                                                        │
└────────────────────────────────────────────────────────┘
```

### 2. SQL 预览 Tab

- Monaco Editor 只读模式（接受现有 SqlIde 已装的 Monaco）
- 右上角：复制按钮 + "规范化"按钮（prettify）
- SQL 来自 `POST /api/semantic/query/preview-sql`
- 每当 CardEditorState 变化 500ms debounce 后重新取 SQL
- 显示：底部 status 栏"SQL 来自: ads_sales_daily × dim_customer，SecurityInjector 已注入 2 条过滤"

### 3. 结果 Tab

- 调用 `POST /api/semantic/query`（JSON 格式本 Sprint 默认）
- 表格展示（antd `Table`，支持排序、前 100 行）
- 显示元信息：行数、耗时、是否命中缓存、触发的 security 策略
- "查询"按钮手动触发；Card 参数变化不自动执行（避免每次改动都打 DB）
- 加载态、错误态完整

错误态展示：
- 422 安全/结构错误 → 红色提示 + 修复建议
- 504 超时 → 提示"查询超时，请缩小时间范围或加更多筛选"
- 500 系统错 → 显示 traceId，引导提交工单

### 4. 图形选择 Tab

本 Sprint 只做 4 种图表（够用）：
- `table` — 默认
- `line_chart` — 时间序列
- `bar_chart` — 类别对比
- `kpi` — 单指标大数字

图形类型选择影响**渲染**但不影响查询。

每种图形需要的字段数量有要求：
- line/bar_chart: ≥1 measure + ≥1 dimension（第一个为 X 轴）
- kpi: 1 measure, 0 dimension
- table: 无限制

不匹配时前端 UI 引导 + 禁用图形选项。

### 5. ECharts 渲染

复用 `screens/` 模块已有的 ECharts 封装（`EChartsRenderer.tsx`）。把 QueryResponse 映射到 ECharts option：

```ts
function toEChartsOption(
  result: QueryResponse,
  vizType: VizType,
  options: VizOptions
): ECOption {
  // 根据 vizType 产生 series / xAxis / yAxis
}
```

数据转换 + 渲染已有成熟代码，本 Task 只做"ResponseJSON → 数据集"的 adapter。

### 6. Arrow 开关（可选）

响应头 `Content-Type: application/vnd.apache.arrow.stream` 时走 Arrow 解码：
- `apache-arrow` 包 `RecordBatchReader.from(stream)` 逐 batch 读取
- 小结果（< 10 万行）直接聚合成 Table 数据
- 本 Sprint **默认关闭**；Feature Flag `VITE_SEMANTIC_ARROW=true` 打开做验证

### 7. 保存 Card

- 用户点"保存"：
  - 新建模式 → POST 创建 AnalyticsCard，`query.engine = "semantic_v1"`，携带 CardEditorState 的语义层字段
  - 编辑模式 → PUT 更新
- 保存成功跳转 `/dashboard/bi/explore` 或 Card 详情页（沿用老 Metabase fork 的详情页）

### 8. 保存 VirtualDataset

若当前是 VDS Editor：
- 保存 VDS 通过 `POST /api/semantic/virtual-datasets`
- 保存成功后用户可选"继续在此 VDS 上创建 Card"（跳 `/bi/card/new?vds=xxx`）

### 9. 快捷键

- `Ctrl/Cmd+S` — 保存
- `Ctrl/Cmd+Enter` — 执行查询
- `Esc` — 关闭侧栏抽屉

### 10. 撤销 / 重做

用 zustand middleware `temporal` / 自己维护 action 栈，至少 10 步。

## 影响范围

| 类型 | 文件 |
|---|---|
| 新建 | `src/pages/bi/card/components/SqlPreviewPane.tsx` |
| 新建 | `src/pages/bi/card/components/ResultTablePane.tsx` |
| 新建 | `src/pages/bi/card/components/VisualizationPane.tsx` |
| 新建 | `src/pages/bi/card/components/VizTypeSelector.tsx` |
| 新建 | `src/pages/bi/card/adapters/responseToEcharts.ts` |
| 新建 | `src/pages/bi/card/hooks/useCardQuery.ts` |
| 修改 | Card 保存 API 扩展 `engine: "semantic_v1"` 字段（老 Metabase fork 侧） |
| 测试 | 单元测试 adapter + E2E 保存流程 |

## 验证

- [ ] SQL 预览实时更新，复制可用
- [ ] 结果 Tab 正确展示，性能达标（100 行 < 200ms 渲染）
- [ ] 4 种图形都能正确渲染
- [ ] 图形字段要求检查生效
- [ ] 保存 Card 成功，数据库里 `query.engine = semantic_v1`
- [ ] 保存 VDS 成功
- [ ] 快捷键生效
- [ ] 撤销/重做至少 10 步稳定
- [ ] Arrow flag 打开时解码正确（验证但默认关闭）
- [ ] 错误态完整

## 完成标准

- [ ] 用户从零到保存 Card 的完整流程可走通（3 种 demo 场景）
- [ ] 端到端录屏 3 条，存 `it/evidence/f5-demos/`
- [ ] 组件 + E2E 测试覆盖率 ≥ 70%
- [ ] 在暗色/亮色主题下都无视觉 bug
