# T21: ResultChart（ECharts 快速可视化）

**优先级**: P1
**状态**: READY
**依赖**: F4

## 目标

底部面板的 `Chart` Tab 对接 ECharts，实现结果集一键出图，支持 5 种基础图表，可一键保存为看板组件（跳转 ScreenDesigner）。

## 技术设计

### 依赖

复用 `dts-analytics-webapp` 已有的 ECharts 封装组件（不重新集成 ECharts）。

### 支持图表类型

- 柱状图 (Bar)
- 折线图 (Line)
- 饼图 (Pie)
- 散点图 (Scatter)
- 面积图 (Area)

### 自动推荐

进入 Chart Tab 时，扫描结果列 schema，推荐最合适的图表：

- 有 1 个时间列 + 1 个数值列 → 折线图
- 有 1 个分类列（低基数 <20）+ 1 个数值列 → 柱状图或饼图
- 2 个数值列 → 散点图
- 其他 → 默认柱状图

### 配置面板（Chart Tab 右侧）

```
┌──────────────┐
│ Type         │
│  ▸ Bar ▾     │
│ X Axis       │
│  [name ▾]    │
│ Y Axis       │
│  [count ▾]   │
│ Group By     │
│  [none ▾]    │
│ Aggregation  │
│  [SUM ▾]     │
└──────────────┘
```

### 一键保存为看板组件

- 底部 "Save to Dashboard" 按钮
- 收集当前图表 config + SQL + 数据源信息
- 跳转到现有 `ScreenDesigner`，自动插入该组件
- 对接方式：调现有看板组件创建 API（查现有实现）

### 数据规模限制

- Chart 直接用当前分页结果（前 5000 行），不强拉全量
- 若需全量，提示用户改写 SQL 聚合

## 影响范围

- 新增 `result/ResultChart.tsx`
- 复用 `dts-analytics-webapp` 的 ECharts 封装

## 验证

- [ ] 5 种图表类型切换无 bug
- [ ] 自动推荐合理
- [ ] 保存到看板成功跳转并预填
- [ ] 简洁模式/高级模式都可用

## 完成标准

- [ ] 功能完整
- [ ] 保存看板闭环
