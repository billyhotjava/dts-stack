# 项目管理作战室 Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 将简单展示大屏升级为 master-detail 项目管理作战室，含自定义甘特图组件、参数化 SQL Card、filter 联动。

**Architecture:** 在现有 Screen Designer 体系内实现。新增 gantt-chart 组件类型（ECharts custom series），创建 12 个参数化 SQL Card 查询 DWS/DWD 层，通过 variable 系统实现月份+项目筛选联动。

**Tech Stack:** TypeScript/React 19 + ECharts 6 + echarts-for-react 3 + PostgreSQL 17.6 + Screen Designer JSON 配置

---

## Task 1: 注册 gantt-chart 组件类型

**Files:**
- Modify: `source/dts-analytics-webapp/modern/src/pages/screens/types.ts:142` (ComponentType union)
- Modify: `source/dts-analytics-webapp/modern/src/pages/screens/componentLibrary.ts:65` (component registry)

**Step 1: 在 ComponentType union 中添加 gantt-chart**

在 `types.ts:142`（`| 'waterfall-chart'` 之后）添加：

```typescript
    | 'gantt-chart'
```

**Step 2: 在 componentLibrary.ts 中注册组件**

在 `componentLibrary.ts:65`（gauge-chart 条目之后）添加：

```typescript
            {
                type: 'gantt-chart',
                name: '甘特图',
                icon: '📊',
                defaultWidth: 600,
                defaultHeight: 300,
                defaultConfig: {
                    title: '甘特图',
                    tasks: [
                        { name: '任务A', type: '一般节点', planDate: '2026-01-01', actualDate: '2026-01-15', isCompleted: true, isOverdue: false, isIncomplete: false, delayDays: 0, riskLevel: '低', owner: '张三' },
                        { name: '任务B', type: '里程碑节点', planDate: '2026-02-01', actualDate: null, isCompleted: false, isOverdue: false, isIncomplete: true, delayDays: null, riskLevel: '高', owner: '李四' },
                    ],
                },
            },
```

**Step 3: 验证编译通过**

Run: `cd /opt/prod/s10/s10-stack/source/dts-analytics-webapp/modern && npx tsc --noEmit 2>&1 | head -20`
Expected: 无 gantt-chart 相关类型错误（可能有其他预存错误，忽略不相关的）

**Step 4: Commit**

```bash
git add source/dts-analytics-webapp/modern/src/pages/screens/types.ts source/dts-analytics-webapp/modern/src/pages/screens/componentLibrary.ts
git commit -m "feat(screen): register gantt-chart component type"
```

---

## Task 2: cardDataMapper 添加 gantt-chart 映射

**Files:**
- Modify: `source/dts-analytics-webapp/modern/src/pages/screens/hooks/cardDataMapper.ts:89` (before default case)
- Create: `source/dts-analytics-webapp/modern/src/pages/screens/hooks/cardDataMapper.test.ts`

**Step 1: 编写 gantt-chart mapper 测试**

创建 `cardDataMapper.test.ts`：

```typescript
import test from 'node:test';
import assert from 'node:assert/strict';
import { mapCardDataToConfig } from './cardDataMapper';

test('gantt-chart: maps rows to tasks array', () => {
    const cols = [
        { name: 'node_task', display_name: '任务', base_type: 'type/Text' },
        { name: 'node_type', display_name: '类型', base_type: 'type/Text' },
        { name: 'plan_date', display_name: '计划日期', base_type: 'type/Date' },
        { name: 'actual_date', display_name: '实际日期', base_type: 'type/Date' },
        { name: 'is_completed', display_name: '已完成', base_type: 'type/Boolean' },
        { name: 'is_overdue_completed', display_name: '超期完成', base_type: 'type/Boolean' },
        { name: 'is_incomplete', display_name: '未完成', base_type: 'type/Boolean' },
        { name: 'delay_days', display_name: '超期天数', base_type: 'type/Integer' },
        { name: 'risk_level', display_name: '风险等级', base_type: 'type/Text' },
        { name: 'owner', display_name: '责任人', base_type: 'type/Text' },
    ];
    const rows = [
        ['关键算法验证', '重大节点', '2026-02-01', '2026-02-10', true, true, false, 9, '高', '张三'],
        ['需求评审', '里程碑节点', '2026-03-01', null, false, false, true, null, '中', '李四'],
    ];

    const result = mapCardDataToConfig('gantt-chart', { rows, cols });

    assert.ok(Array.isArray(result.tasks));
    const tasks = result.tasks as Array<Record<string, unknown>>;
    assert.equal(tasks.length, 2);

    assert.equal(tasks[0].name, '关键算法验证');
    assert.equal(tasks[0].type, '重大节点');
    assert.equal(tasks[0].planDate, '2026-02-01');
    assert.equal(tasks[0].actualDate, '2026-02-10');
    assert.equal(tasks[0].isCompleted, true);
    assert.equal(tasks[0].isOverdue, true);
    assert.equal(tasks[0].isIncomplete, false);
    assert.equal(tasks[0].delayDays, 9);
    assert.equal(tasks[0].riskLevel, '高');
    assert.equal(tasks[0].owner, '张三');

    assert.equal(tasks[1].name, '需求评审');
    assert.equal(tasks[1].actualDate, '');
    assert.equal(tasks[1].isIncomplete, true);
    assert.equal(tasks[1].delayDays, 0); // null → toNumber → 0
});

test('gantt-chart: returns empty on no rows', () => {
    const result = mapCardDataToConfig('gantt-chart', { rows: [], cols: [] });
    assert.deepEqual(result, {});
});
```

**Step 2: 运行测试确认失败**

Run: `cd /opt/prod/s10/s10-stack/source/dts-analytics-webapp/modern && npx tsx --test src/pages/screens/hooks/cardDataMapper.test.ts`
Expected: FAIL — gantt-chart case 不存在，返回 `{}`

**Step 3: 实现 gantt-chart mapper**

在 `cardDataMapper.ts:89`（`scroll-ranking` case 之后，`default` 之前）添加：

```typescript
        case 'gantt-chart': {
            const colIndex = (name: string) => cols.findIndex((c) => c.name === name);
            return {
                tasks: rows.map((row) => ({
                    name: String(row[colIndex('node_task')] ?? ''),
                    type: String(row[colIndex('node_type')] ?? ''),
                    planDate: String(row[colIndex('plan_date')] ?? ''),
                    actualDate: String(row[colIndex('actual_date')] ?? ''),
                    isCompleted: Boolean(row[colIndex('is_completed')]),
                    isOverdue: Boolean(row[colIndex('is_overdue_completed')]),
                    isIncomplete: Boolean(row[colIndex('is_incomplete')]),
                    delayDays: toNumber(row[colIndex('delay_days')]),
                    riskLevel: String(row[colIndex('risk_level')] ?? ''),
                    owner: String(row[colIndex('owner')] ?? ''),
                })),
            };
        }
```

**Step 4: 运行测试确认通过**

Run: `cd /opt/prod/s10/s10-stack/source/dts-analytics-webapp/modern && npx tsx --test src/pages/screens/hooks/cardDataMapper.test.ts`
Expected: 2 tests PASS

**Step 5: Commit**

```bash
git add source/dts-analytics-webapp/modern/src/pages/screens/hooks/cardDataMapper.ts source/dts-analytics-webapp/modern/src/pages/screens/hooks/cardDataMapper.test.ts
git commit -m "feat(screen): add gantt-chart data mapper with tests"
```

---

## Task 3: ComponentRenderer 实现甘特图渲染

**Files:**
- Modify: `source/dts-analytics-webapp/modern/src/pages/screens/components/ComponentRenderer.tsx:1830` (after gauge-chart case)

**Step 1: 在 gauge-chart case 之后添加 gantt-chart case**

在 `ComponentRenderer.tsx:1831`（gauge-chart case 的 `});` 之后）添加：

```typescript
            case 'gantt-chart': {
                const tasks = (c.tasks as Array<{
                    name: string; type: string; planDate: string; actualDate: string;
                    isCompleted: boolean; isOverdue: boolean; isIncomplete: boolean;
                    delayDays: number; riskLevel: string; owner: string;
                }>) || [];
                if (!tasks.length) {
                    return renderEChartWithHandles({ ...themeOptions, title: { text: '暂无数据', left: 'center', top: 'center', textStyle: { color: t.textSecondary, fontSize: 14 } } });
                }

                // Sort tasks by planDate ascending, reverse for y-axis (bottom-up)
                const sorted = [...tasks].sort((a, b) => a.planDate.localeCompare(b.planDate));
                const categories = sorted.map((tk) => tk.name);

                // Date range for x-axis
                const allDates = sorted.flatMap((tk) => [tk.planDate, tk.actualDate].filter(Boolean));
                const minDate = allDates.reduce((a, b) => (a < b ? a : b), allDates[0]);
                const maxDate = allDates.reduce((a, b) => (a > b ? a : b), allDates[0]);
                const today = new Date().toISOString().slice(0, 10);

                // Color by status
                const getBarColor = (tk: typeof sorted[0]) => {
                    if (tk.isCompleted && !tk.isOverdue) return '#52c41a'; // green - on time
                    if (tk.isCompleted && tk.isOverdue) return '#faad14';  // orange - overdue completed
                    if (tk.isIncomplete) return '#ff4d4f';                 // red - incomplete
                    return '#1890ff';                                       // blue - pending
                };

                // Build custom series data
                const barData = sorted.map((tk, idx) => {
                    const start = new Date(tk.planDate).getTime();
                    const end = tk.actualDate ? new Date(tk.actualDate).getTime() : Date.now();
                    return {
                        value: [idx, start, end, tk.delayDays],
                        itemStyle: { color: getBarColor(tk) },
                        task: tk,
                    };
                });

                // Milestone markers (diamond shape)
                const milestones = sorted
                    .map((tk, idx) => (tk.type === '里程碑节点' ? {
                        value: [idx, new Date(tk.planDate).getTime()],
                        symbol: 'diamond',
                        symbolSize: 14,
                        itemStyle: { color: '#722ed1' },
                        task: tk,
                    } : null))
                    .filter(Boolean);

                const ganttOption: Record<string, unknown> = {
                    ...themeOptions,
                    tooltip: {
                        trigger: 'item',
                        formatter: (params: { data?: { task?: typeof sorted[0] } }) => {
                            const tk = params.data?.task;
                            if (!tk) return '';
                            return [
                                `<b>${tk.name}</b>`,
                                `类型: ${tk.type}`,
                                `责任人: ${tk.owner}`,
                                `计划: ${tk.planDate}`,
                                tk.actualDate ? `实际: ${tk.actualDate}` : '实际: 未完成',
                                tk.delayDays ? `超期: ${tk.delayDays}天` : '',
                                `风险: ${tk.riskLevel}`,
                            ].filter(Boolean).join('<br/>');
                        },
                    },
                    grid: { left: 120, right: 40, top: 30, bottom: 50 },
                    xAxis: {
                        type: 'time',
                        min: minDate,
                        max: maxDate > today ? maxDate : today,
                        axisLabel: { color: t.textSecondary, fontSize: 11 },
                        splitLine: { lineStyle: { color: t.gridLineColor, type: 'dashed' } },
                    },
                    yAxis: {
                        type: 'category',
                        data: categories,
                        inverse: true,
                        axisLabel: {
                            color: t.textPrimary,
                            fontSize: 11,
                            width: 100,
                            overflow: 'truncate',
                        },
                        splitLine: { show: false },
                    },
                    dataZoom: [{ type: 'inside', xAxisIndex: 0 }],
                    series: [
                        {
                            type: 'custom',
                            renderItem: (params: { coordSys: { x: number; y: number; width: number; height: number } }, api: {
                                value: (idx: number) => number;
                                coord: (val: [number, number]) => [number, number];
                                size: (val: [number, number]) => [number, number];
                                style: (extra?: Record<string, unknown>) => Record<string, unknown>;
                            }) => {
                                const catIdx = api.value(0);
                                const startTime = api.value(1);
                                const endTime = api.value(2);
                                const start = api.coord([startTime, catIdx]);
                                const end = api.coord([endTime, catIdx]);
                                const barHeight = api.size([0, 1])[1] * 0.6;
                                return {
                                    type: 'rect',
                                    shape: {
                                        x: start[0],
                                        y: start[1] - barHeight / 2,
                                        width: Math.max(end[0] - start[0], 3),
                                        height: barHeight,
                                        r: 2,
                                    },
                                    style: api.style(),
                                };
                            },
                            encode: { x: [1, 2], y: 0 },
                            data: barData,
                        },
                        // Milestone markers
                        ...(milestones.length ? [{
                            type: 'scatter',
                            data: milestones,
                            encode: { x: 1, y: 0 },
                            symbolSize: 14,
                            z: 10,
                        }] : []),
                    ],
                };

                // Today line
                if (today >= minDate && today <= (maxDate > today ? maxDate : today)) {
                    (ganttOption as Record<string, unknown>).series = [
                        ...((ganttOption as Record<string, unknown>).series as unknown[]),
                    ];
                    // Use xAxis markLine via first series
                    const firstSeries = ((ganttOption as Record<string, unknown>).series as Array<Record<string, unknown>>)[0];
                    firstSeries.markLine = {
                        silent: true,
                        symbol: 'none',
                        lineStyle: { color: '#ff4d4f', type: 'dashed', width: 2 },
                        data: [{ xAxis: new Date(today).getTime() }],
                        label: { formatter: '今日', position: 'start', color: '#ff4d4f', fontSize: 11 },
                    };
                }

                return renderEChartWithHandles(ganttOption, echartsClickHandler);
            }
```

**Step 2: 验证编译通过**

Run: `cd /opt/prod/s10/s10-stack/source/dts-analytics-webapp/modern && npx tsc --noEmit 2>&1 | grep -i gantt`
Expected: 无 gantt 相关错误

**Step 3: Commit**

```bash
git add source/dts-analytics-webapp/modern/src/pages/screens/components/ComponentRenderer.tsx
git commit -m "feat(screen): implement gantt-chart renderer with ECharts custom series"
```

---

## Task 4: 构建并部署前端镜像

**Files:**
- Read: `docker-compose-app.yml`（确认镜像名和构建路径）

**Step 1: 构建 modern webapp**

Run: `cd /opt/prod/s10/s10-stack/source/dts-analytics-webapp/modern && npm run build 2>&1 | tail -5`
Expected: build 成功

**Step 2: 复制 dist 到 Docker 构建上下文并构建镜像**

Run: `cd /opt/prod/s10/s10-stack && ./bin/build-analytics-webapp.sh` 或等效的 Docker build 命令（参考之前的构建流程）

**Step 3: 重启服务**

Run: `cd /opt/prod/s10/s10-stack && docker compose -f docker-compose.yml -f docker-compose-app.yml up -d dts-analytics-webapp`

**Step 4: Commit**

```bash
git commit -m "chore: build analytics-webapp with gantt-chart support"
```

---

## Task 5: 创建参数化 SQL Card（数据库操作）

**Files:**
- 执行 SQL: 在 `analytics_card` 表中 INSERT 12 条记录

**Step 1: 创建月份列表 Card（card 11）**

```sql
INSERT INTO analytics_card (name, description, database_id, card_type, dataset_query_json, display, creator_id, created_at, updated_at)
VALUES (
    '月份列表',
    '作战室月份选择器数据源',
    2, 'model',
    '{"type":"native","native":{"query":"SELECT DISTINCT plan_month FROM biz_dws_period_node_summary ORDER BY plan_month DESC"}}',
    'table', 2, now(), now()
);
```

**Step 2: 创建项目列表 Card（card 12）**

```sql
INSERT INTO analytics_card (name, description, database_id, card_type, dataset_query_json, display, creator_id, created_at, updated_at)
VALUES (
    '项目列表',
    '作战室项目选择器数据源',
    2, 'model',
    '{"type":"native","native":{"query":"SELECT DISTINCT project_no FROM biz_dws_period_node_summary WHERE plan_month = {{v_plan_month}} ORDER BY project_no","template-tags":{"v_plan_month":{"type":"text","name":"v_plan_month","display-name":"月份"}}}}',
    'table', 2, now(), now()
);
```

**Step 3: 创建项目记分卡 Card**

```sql
INSERT INTO analytics_card (name, description, database_id, card_type, dataset_query_json, display, creator_id, created_at, updated_at)
VALUES (
    '项目记分卡',
    '左侧项目列表：每项目的完成率/准时率/超期数/高风险数',
    2, 'model',
    '{"type":"native","native":{"query":"WITH node AS (SELECT project_no, SUM(completed_cnt) AS completed, SUM(due_cnt) AS due, SUM(on_time_cnt) AS on_time, SUM(incomplete_cnt) AS incomplete FROM biz_dws_period_node_summary WHERE plan_month = {{v_plan_month}} GROUP BY project_no), risk AS (SELECT project_no, SUM(CASE WHEN risk_level = ''高'' THEN incomplete_cnt ELSE 0 END) AS high_risk FROM biz_dws_period_risk_summary WHERE plan_month = {{v_plan_month}} GROUP BY project_no) SELECT n.project_no, ROUND(n.completed::numeric / NULLIF(n.due, 0), 4) AS completion_rate, ROUND(n.on_time::numeric / NULLIF(n.due, 0), 4) AS on_time_rate, n.incomplete AS incomplete_cnt, COALESCE(r.high_risk, 0) AS high_risk_cnt FROM node n LEFT JOIN risk r ON n.project_no = r.project_no ORDER BY completion_rate ASC","template-tags":{"v_plan_month":{"type":"text","name":"v_plan_month","display-name":"月份"}}}}',
    'table', 2, now(), now()
);
```

**Step 4: 创建 5 个 KPI number-card Cards（完成率/准时率/超期率/未完成数/高风险数）**

每个 KPI Card 使用 CTE 查当期+上期，返回 value + delta。以完成率为例：

```sql
INSERT INTO analytics_card (name, description, database_id, card_type, dataset_query_json, display, creator_id, created_at, updated_at)
VALUES (
    'KPI-完成率',
    '完成率指标卡（含环比）',
    2, 'model',
    '{"type":"native","native":{"query":"WITH cur AS (SELECT SUM(completed_cnt)::float / NULLIF(SUM(due_cnt), 0) AS rate FROM biz_dws_period_node_summary WHERE plan_month = {{v_plan_month}} AND ({{v_project_no}} IS NULL OR project_no = {{v_project_no}})), prev AS (SELECT SUM(completed_cnt)::float / NULLIF(SUM(due_cnt), 0) AS rate FROM biz_dws_period_node_summary WHERE plan_month = ({{v_plan_month}}::date || ''-01'')::date - interval ''1 month'' AND ({{v_project_no}} IS NULL OR project_no = {{v_project_no}})) SELECT ROUND(cur.rate::numeric * 100, 2) AS value, ROUND((cur.rate - COALESCE(prev.rate, 0))::numeric * 100, 2) AS delta FROM cur CROSS JOIN prev","template-tags":{"v_plan_month":{"type":"text","name":"v_plan_month","display-name":"月份"},"v_project_no":{"type":"text","name":"v_project_no","display-name":"项目","required":false}}}}',
    'number-card', 2, now(), now()
);
```

类似地为准时率、超期率、未完成数、高风险数各创建一条。SQL 结构相同，仅指标计算公式不同。

**Step 5: 创建节点类型柱状图 Card**

```sql
INSERT INTO analytics_card (name, description, database_id, card_type, dataset_query_json, display, creator_id, created_at, updated_at)
VALUES (
    '节点类型完成分析',
    '按节点类型统计完成/未完成数',
    2, 'model',
    '{"type":"native","native":{"query":"SELECT node_type, SUM(completed_cnt) AS completed_cnt, SUM(incomplete_cnt) AS incomplete_cnt, SUM(total_cnt) AS total_cnt FROM biz_dws_period_node_type_summary WHERE plan_month = {{v_plan_month}} AND ({{v_project_no}} IS NULL OR project_no = {{v_project_no}}) GROUP BY node_type ORDER BY node_type","template-tags":{"v_plan_month":{"type":"text","name":"v_plan_month","display-name":"月份"},"v_project_no":{"type":"text","name":"v_project_no","display-name":"项目","required":false}}}}',
    'bar-chart', 2, now(), now()
);
```

**Step 6: 创建风险分布图 Card**

```sql
INSERT INTO analytics_card (name, description, database_id, card_type, dataset_query_json, display, creator_id, created_at, updated_at)
VALUES (
    '风险分布',
    '按风险等级统计未完成节点数',
    2, 'model',
    '{"type":"native","native":{"query":"SELECT risk_level AS name, SUM(incomplete_cnt) AS value FROM biz_dws_period_risk_summary WHERE plan_month = {{v_plan_month}} AND ({{v_project_no}} IS NULL OR project_no = {{v_project_no}}) GROUP BY risk_level ORDER BY CASE risk_level WHEN ''高'' THEN 1 WHEN ''中'' THEN 2 WHEN ''低'' THEN 3 END","template-tags":{"v_plan_month":{"type":"text","name":"v_plan_month","display-name":"月份"},"v_project_no":{"type":"text","name":"v_project_no","display-name":"项目","required":false}}}}',
    'pie-chart', 2, now(), now()
);
```

**Step 7: 创建甘特图 Card**

```sql
INSERT INTO analytics_card (name, description, database_id, card_type, dataset_query_json, display, creator_id, created_at, updated_at)
VALUES (
    '甘特图数据',
    '甘特图：节点任务时间线',
    2, 'model',
    '{"type":"native","native":{"query":"SELECT node_task, node_type, plan_date::text, actual_date::text, is_completed, is_overdue_completed, is_incomplete, delay_days, risk_level, owner FROM biz_dwd_project_node WHERE plan_month = {{v_plan_month}} AND ({{v_project_no}} IS NULL OR project_no = {{v_project_no}}) ORDER BY plan_date ASC, node_type DESC","template-tags":{"v_plan_month":{"type":"text","name":"v_plan_month","display-name":"月份"},"v_project_no":{"type":"text","name":"v_project_no","display-name":"项目","required":false}}}}',
    'gantt-chart', 2, now(), now()
);
```

**Step 8: 创建节点明细表 Card**

```sql
INSERT INTO analytics_card (name, description, database_id, card_type, dataset_query_json, display, creator_id, created_at, updated_at)
VALUES (
    '节点明细表',
    '完整节点列表，支持排序分页',
    2, 'model',
    '{"type":"native","native":{"query":"SELECT node_task AS 节点任务, node_type AS 节点类型, owner AS 责任人, plan_date::text AS 计划日期, actual_date::text AS 实际日期, completion_status AS 完成状态, delay_days AS 超期天数, risk_level AS 风险等级, risk_content AS 风险内容 FROM biz_dwd_project_node WHERE plan_month = {{v_plan_month}} AND ({{v_project_no}} IS NULL OR project_no = {{v_project_no}}) ORDER BY plan_date ASC","template-tags":{"v_plan_month":{"type":"text","name":"v_plan_month","display-name":"月份"},"v_project_no":{"type":"text","name":"v_project_no","display-name":"项目","required":false}}}}',
    'table', 2, now(), now()
);
```

**Step 9: 验证所有 Card 已创建**

Run: `docker exec dts-pg psql -U biadmin -d dts_platform -c "SELECT id, name, display FROM analytics_card ORDER BY id DESC LIMIT 15;"`
Expected: 12 条新 Card

---

## Task 6: 创建作战室 Screen 模板和实例

**Files:**
- 执行 SQL: INSERT into `analytics_screen_template` and `analytics_screen`

**Step 1: 记录 Card ID 映射**

先查询上一步创建的 Card ID：
```sql
SELECT id, name FROM analytics_card WHERE name IN ('月份列表','项目列表','项目记分卡','KPI-完成率','KPI-准时率','KPI-超期率','KPI-未完成数','KPI-高风险数','节点类型完成分析','风险分布','甘特图数据','节点明细表') ORDER BY id;
```

**Step 2: 创建 Screen 模板**

使用查到的 Card ID 替换下面 JSON 中的 `cardId` 值。模板 JSON 包含：
- 2 个 filter-select（月份 + 项目）
- 1 个 title
- 1 个 table（项目记分卡，带行点击 action 设置 v_project_no）
- 5 个 number-card（KPI 行）
- 1 个 bar-chart（节点类型）
- 1 个 pie-chart（风险分布）
- 1 个 gantt-chart
- 1 个 table（节点明细）

共 13 个组件。

模板 JSON 结构较大，在执行时根据实际 Card ID 生成 INSERT 语句。

**关键配置要点：**

filter-select 月份组件配置：
```json
{
    "type": "filter-select",
    "variableKey": "v_plan_month",
    "optionSourceMode": "data",
    "dataSource": { "type": "card", "cardId": <月份列表CardId> }
}
```

项目记分卡 table 组件的 action 配置：
```json
{
    "actions": [{
        "type": "set-variable",
        "mappings": [{ "sourcePath": "row[0]", "variableKey": "v_project_no" }]
    }]
}
```

各 KPI/图表组件的 parameterBindings：
```json
{
    "dataSource": {
        "type": "card",
        "cardId": <对应CardId>,
        "databaseConfig": {
            "parameterBindings": [
                { "name": "v_plan_month", "variableKey": "v_plan_month" },
                { "name": "v_project_no", "variableKey": "v_project_no" }
            ]
        }
    }
}
```

**Step 3: 创建 Screen 实例**

```sql
INSERT INTO analytics_screen (name, description, template_id, variables, creator_id, created_at, updated_at)
VALUES (
    '项目管理作战室',
    '综合项目管理操作窗口：master-detail 布局，支持上卷下钻',
    <template_id>,
    '[{"key":"v_plan_month","type":"string","defaultValue":"2026-02"},{"key":"v_project_no","type":"string","defaultValue":null}]',
    2, now(), now()
);
```

**Step 4: 验证 Screen 预览**

在浏览器打开 Screen 预览页面，确认：
- 月份下拉有数据
- 项目记分卡显示项目列表
- 右侧 KPI 显示跨项目汇总
- 点击记分卡行 → 右侧刷新为单项目
- 甘特图渲染正常

**Step 5: Commit**

```bash
git commit -m "feat(screen): create project cockpit screen template with 13 components"
```

---

## Task 7: 条件格式与视觉优化

**Files:**
- 修改 Screen 模板 JSON 中各组件的条件格式配置

**Step 1: 记分卡 table 添加条件格式**

更新模板 JSON 中项目记分卡组件的 `conditionalFormatting`：

```json
{
    "conditionalFormatting": [
        { "column": "completion_rate", "rules": [
            { "operator": "<", "value": 0.6, "style": { "color": "#ff4d4f" } },
            { "operator": ">=", "value": 0.6, "compareTo": 0.8, "style": { "color": "#faad14" } },
            { "operator": ">=", "value": 0.8, "style": { "color": "#52c41a" } }
        ]},
        { "column": "incomplete_cnt", "rules": [
            { "operator": ">", "value": 0, "style": { "color": "#ff4d4f", "fontWeight": "bold" } }
        ]},
        { "column": "high_risk_cnt", "rules": [
            { "operator": ">", "value": 0, "style": { "backgroundColor": "#fff2f0", "color": "#ff4d4f" } }
        ]}
    ]
}
```

**Step 2: number-card 配置 suffix 显示环比**

number-card 配置中利用 delta 值显示趋势（需确认 number-card 是否支持 delta 字段，若不支持，在 title/suffix 中用 SQL 拼接文本）。

**Step 3: 验证视觉效果**

预览页面确认条件格式颜色正确渲染。

**Step 4: Commit**

```bash
git commit -m "feat(screen): add conditional formatting to project cockpit"
```

---

## 执行依赖关系

```
Task 1 (注册类型) ──→ Task 2 (mapper) ──→ Task 3 (renderer) ──→ Task 4 (构建部署)
                                                                        ↓
Task 5 (SQL Cards) ─────────────────────────────────────────────→ Task 6 (Screen模板)
                                                                        ↓
                                                                  Task 7 (条件格式)
```

Task 1→2→3→4 是前端链路，Task 5 可与 1-3 并行。Task 6 依赖 4+5 都完成。Task 7 依赖 6。
