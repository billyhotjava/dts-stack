# 项目管理作战室 — 设计文档

> Sprint-7 · 方案 B：Screen Designer + 自定义甘特组件
> 2026-03-13

---

## 1. 需求摘要

将项目管理大屏从简单展示升级为**专业操作窗口**：

- **多角色覆盖**：管理层月度评审 / PMO 日常监控 / 项目经理自查
- **Master-Detail 布局**：左侧项目记分卡 + 右侧详情面板
- **上卷下钻**：全部项目汇总 ↔ 单项目详情，filter + 行点击联动
- **综合作战室**：进度 + 风险 + 责任分布 + 节点类型 + 甘特时间线
- **多期对比**：支持定期 Excel 导入，KPI 环比、趋势指示

---

## 2. 方案选型

| 方案 | 描述 | 优缺点 |
|------|------|--------|
| A. 纯 Screen Designer | 全配置，无前端开发 | 甘特只能近似模拟 |
| **B. Screen Designer + gantt-chart 组件** | 新增一个通用甘特组件 | **推荐**：开发量可控，复用性高 |
| C. 独立 React 页面 | 完全自定义 | 开发量大，脱离平台 BI 体系 |

选定 **方案 B**。

---

## 3. 整体布局

```
┌─────────────────────────────────────────────────────────┐
│  [月份选择器 ▼]   [项目筛选器 ▼ 全部项目]               │  ← 顶部 filter 栏
├────────────────┬────────────────────────────────────────┤
│                │  ┌─────┐┌─────┐┌─────┐┌─────┐┌─────┐ │
│  项目记分卡     │  │完成率││准时率││超期率││未完成 ││高风险│ │  ← KPI 指标行
│  (table)       │  └─────┘└─────┘└─────┘└─────┘└─────┘ │
│                │ ┌──────────────┐┌─────────────────────┐│
│  PRJ-A001  78% │ │  节点类型     ││  风险分布           ││  ← 分析图表行
│  PRJ-B002  65% │ │  完成柱状图   ││  饼图              ││
│  PRJ-C003  90% │ └──────────────┘└─────────────────────┘│
│  ...           │ ┌──────────────────────────────────────┐│
│                │ │  甘特图 / 里程碑时间线                 ││  ← 新增 gantt-chart
│                │ └──────────────────────────────────────┘│
│                │ ┌──────────────────────────────────────┐│
│                │ │  节点明细表（排序/分页/条件格式）       ││
│                │ └──────────────────────────────────────┘│
└────────────────┴────────────────────────────────────────┘
```

### Grid 布局（24列制）

| 行 | 列 | 组件 | 尺寸 |
|---|---|------|------|
| 0 | 0-5 | filter-select 月份 | 6×1 |
| 0 | 6-11 | filter-select 项目 | 6×1 |
| 0 | 12-23 | title "项目管理作战室" | 12×1 |
| 1-8 | 0-5 | 项目记分卡 table | 6×8 |
| 1-2 | 6-9 | 完成率 number-card | 4×2 |
| 1-2 | 10-13 | 准时率 number-card | 4×2 |
| 1-2 | 14-17 | 超期率 number-card | 4×2 |
| 1-2 | 18-20 | 未完成 number-card | 3×2 |
| 1-2 | 21-23 | 高风险 number-card | 3×2 |
| 3-5 | 6-14 | 节点类型完成柱状图 | 9×3 |
| 3-5 | 15-23 | 风险分布饼图 | 9×3 |
| 6-8 | 6-23 | 甘特图 | 18×3 |
| 9-12 | 0-23 | 节点明细表 | 24×4 |

---

## 4. 交互设计

### Variable 系统

| Variable | 类型 | 来源 | 作用 |
|----------|------|------|------|
| `v_plan_month` | string | filter-select 月份 | 控制所有面板的时间范围 |
| `v_project_no` | string | filter-select 项目 / 记分卡行点击 | NULL=全部汇总，有值=单项目 |

### 交互流程

1. **默认**：月份=最新期，项目=全部 → 右侧显示跨项目汇总
2. **点击记分卡行** → set `v_project_no` → 右侧所有面板联动刷新为该项目
3. **项目筛选器选"全部"** → 清空 `v_project_no` → 恢复汇总
4. **月份切换** → 所有面板重新查询

### SQL 参数化统一模式

```sql
WHERE plan_month = {{v_plan_month}}
  AND ({{v_project_no}} IS NULL OR project_no = {{v_project_no}})
```

---

## 5. 数据层 — SQL Card 清单

不需要改 dbt 模型，DWS 已保留 `project_no` 维度。

| # | Card 名称 | 数据来源 | 参数 | 用途 |
|---|----------|---------|------|------|
| 1 | 项目记分卡 | DWS period_node_summary + period_risk_summary | v_plan_month | 每项目一行：完成率/准时率/超期数/高风险数 |
| 2 | KPI-完成率 | DWS period_node_summary | v_plan_month, v_project_no | number-card（含环比 delta） |
| 3 | KPI-准时率 | 同上 | 同上 | number-card |
| 4 | KPI-超期率 | 同上 | 同上 | number-card |
| 5 | KPI-未完成数 | 同上 | 同上 | number-card |
| 6 | KPI-高风险数 | DWS period_risk_summary | 同上 | number-card |
| 7 | 节点类型完成柱状图 | DWS period_node_type_summary | 同上 | 堆叠柱状图 |
| 8 | 风险分布图 | DWS period_risk_summary | 同上 | 饼图 |
| 9 | 甘特图数据 | DWD biz_dwd_project_node | 同上 | gantt-chart |
| 10 | 节点明细表 | DWD biz_dwd_project_node | 同上 | table |
| 11 | 月份列表 | DWS period_node_summary | 无 | filter option |
| 12 | 项目列表 | DWS period_node_summary | v_plan_month | filter option |

### KPI 环比 SQL 示例

```sql
WITH cur AS (
    SELECT SUM(completed_cnt)::float / NULLIF(SUM(due_cnt), 0) AS rate
    FROM biz_dws_period_node_summary
    WHERE plan_month = {{v_plan_month}}
      AND ({{v_project_no}} IS NULL OR project_no = {{v_project_no}})
),
prev AS (
    SELECT SUM(completed_cnt)::float / NULLIF(SUM(due_cnt), 0) AS rate
    FROM biz_dws_period_node_summary
    WHERE plan_month = ({{v_plan_month}}::date - interval '1 month')::text
      AND ({{v_project_no}} IS NULL OR project_no = {{v_project_no}})
)
SELECT cur.rate AS value,
       cur.rate - prev.rate AS delta
FROM cur CROSS JOIN prev
```

---

## 6. gantt-chart 组件设计

### 技术方案

基于 ECharts **custom series**，不引入第三方甘特库。

### 数据格式

Card SQL 返回列：`node_task, node_type, plan_date, actual_date, is_completed, is_overdue_completed, is_incomplete, delay_days, risk_level, owner`

### cardDataMapper 映射

```typescript
case 'gantt-chart':
    return {
        tasks: rows.map(row => ({
            name: getString(row, cols, 'node_task'),
            type: getString(row, cols, 'node_type'),
            planDate: getString(row, cols, 'plan_date'),
            actualDate: getString(row, cols, 'actual_date'),
            isCompleted: getBool(row, cols, 'is_completed'),
            isOverdue: getBool(row, cols, 'is_overdue_completed'),
            isIncomplete: getBool(row, cols, 'is_incomplete'),
            delayDays: getNumber(row, cols, 'delay_days'),
            riskLevel: getString(row, cols, 'risk_level'),
            owner: getString(row, cols, 'owner'),
        })),
    };
```

### 渲染规则

**颜色**：
- 按时完成 → `#52c41a`（绿）
- 超期已完成 → `#faad14`（橙）
- 超期未完成 → `#ff4d4f`（红）
- 正常待完成 → `#1890ff`（蓝）

**特殊标记**：
- 里程碑节点 → 菱形 ◆ 标记
- 今日线 → markLine 红色虚线
- 支持 dataZoom 横向缩放

**Tooltip**：任务名 / 责任人 / 计划日期 / 实际日期 / 超期天数 / 风险等级

### 不做的事情

- 不做拖拽调整日期（展示组件）
- 不做任务依赖箭头（数据无前置关系）
- 不做资源负载图

---

## 7. 多期对比

### KPI number-card 环比

SQL 同时查当期和上期，返回 `value` + `delta`。显示为：`78% ↑3.2%`

### 记分卡增加 delta 列

完成率旁加环比变化列，条件格式：正值绿 ↑、负值红 ↓

### 柱状图双期对比

SQL 返回当期+上期，grouped bar：当期实色 vs 上期半透明

---

## 8. 条件格式规则

| 列 | 条件 | 样式 |
|---|------|------|
| 完成率 | < 0.6 | 红色字体 |
| 完成率 | 0.6~0.8 | 橙色字体 |
| 完成率 | ≥ 0.8 | 绿色字体 |
| 超期数 | > 0 | 红色字体 + 加粗 |
| 高风险 | > 0 | 红色背景 |
| delta | > 0 | 绿色 ↑ |
| delta | < 0 | 红色 ↓ |

---

## 9. 开发工作量估算

| 模块 | 工作内容 | 量级 |
|------|---------|------|
| gantt-chart 组件 | cardDataMapper + ComponentRenderer + componentLibrary 注册 | ~200 行 TS |
| SQL Card | 12 个参数化 SQL | ~12 个 SQL 语句 |
| Screen 模板 JSON | 模板配置 + 组件布局 + variable 绑定 | 1 个 JSON |
| DB 数据 | INSERT analytics_card + analytics_screen + analytics_screen_template | SQL 脚本 |

---

## 10. 边界声明

**不做**：
- 独立趋势分析页（当前数据量小，后续加）
- 跨年度对比（YAGNI）
- 自动邮件/报告导出
- 甘特拖拽编辑
- 任务依赖箭头
- 资源负载图
