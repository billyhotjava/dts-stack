# T01: 概览仪表盘 Tab

**优先级**: P1
**状态**: READY
**依赖**: F3

## 目标
新增概览 Tab，管理者 10 秒内看清数据质量全貌。

## 技术设计

### 布局
```
┌──────────┬──────────┬──────────┬──────────┐
│ 规则总数   │ 覆盖数据集 │ 今日检查   │ 待修复行数  │
└──────────┴──────────┴──────────┴──────────┘
┌─────────────────────────────────────────────┐
│ 7日质量趋势（折线图，Y轴通过率%）              │
└─────────────────────────────────────────────┘
┌───────────────────┬─────────────────────────┐
│ Top5 问题数据集     │ 最近失败的检查            │
└───────────────────┴─────────────────────────┘
```

### 后端 API
```
GET /api/governance/quality/dashboard
→ {
  ruleCount, coveredDatasets, totalDatasets,
  todayPassed, todayFailed,
  pendingFixRows,
  trend7d: [{ date, passRate }],
  topFailingDatasets: [{ name, failingRows }],
  recentFailedRuns: [{ ruleName, dataset, time, status }]
}
```

### 前端组件
- 指标卡片：4 个 StatCard 组件
- 趋势图：ECharts 折线图（复用项目已有 ECharts）
- Top5 + 最近失败：表格组件，点击行跳转对应 Tab

## 影响范围
- 新增 `QualityDashboardResource.java` 或在 `GovernanceResource` 中增加端点
- 新增 `QualityDashboardService.java`：聚合查询
- 新增前端 `QualityDashboard.tsx` 组件

## 验证
- [ ] 4 个指标数字正确
- [ ] 趋势图展示最近 7 天数据
- [ ] Top5 排序正确
- [ ] 点击跳转正常

## 完成标准
- [ ] 概览 Tab 完整可用
