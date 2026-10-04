# 项目作战室 V2 Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将项目管理作战室从简单报表升级为混合战情室，增加KPI趋势火花线、进度漏斗、责任人分布、风险气泡图，并优化布局和数据联动。

**Architecture:** 新增2个数据映射器(funnel-chart, scatter-chart)，重构5个KPI卡片为combo-chart带火花线，重建13张SQL卡片，重构screen模板JSON为5层14组件布局。

**Tech Stack:** TypeScript, ECharts, PostgreSQL, Screen Designer JSON

---

## Task 1: 新增 funnel-chart 数据映射器

**Files:**
- Modify: `source/dts-analytics-webapp/modern/src/pages/screens/hooks/cardDataMapper.ts:107` (在 gantt-chart case 之后、default 之前插入)
- Modify: `source/dts-analytics-webapp/modern/src/pages/screens/hooks/cardDataMapper.test.ts` (追加测试)

- [ ] **Step 1: 在 cardDataMapper.test.ts 追加 funnel-chart 测试**

```typescript
test('funnel-chart: maps rows to name-value data', () => {
    const cardData = {
        cols: [
            { name: 'name', display_name: '状态', base_type: 'type/Text' },
            { name: 'value', display_name: '数量', base_type: 'type/Integer' },
        ],
        rows: [
            ['总计', 12],
            ['已完成', 8],
            ['进行中', 2],
            ['超期', 1],
            ['未启动', 1],
        ],
    };
    const result = mapCardDataToConfig('funnel-chart', cardData, { nameField: 'name', valueField: 'value' });
    assert.deepStrictEqual(result.data, [
        { name: '总计', value: 12 },
        { name: '已完成', value: 8 },
        { name: '进行中', value: 2 },
        { name: '超期', value: 1 },
        { name: '未启动', value: 1 },
    ]);
});
```

- [ ] **Step 2: 运行测试确认失败**

Run: `cd source/dts-analytics-webapp/modern && npx tsx --test src/pages/screens/hooks/cardDataMapper.test.ts`
Expected: FAIL — funnel-chart case 未实现，返回空对象

- [ ] **Step 3: 在 cardDataMapper.ts 的 gantt-chart case 之后 (line 107)、default 之前 (line 109) 插入 funnel-chart case**

```typescript
        case 'funnel-chart': {
            const nameF = config?.nameField as string | undefined;
            const valF = config?.valueField as string | undefined;
            const nameIdx = nameF ? cols.findIndex((c) => c.name === nameF) : 0;
            const valIdx = valF ? cols.findIndex((c) => c.name === valF) : 1;
            return {
                data: rows.map((row) => ({
                    name: String(row[nameIdx >= 0 ? nameIdx : 0] ?? ''),
                    value: toNumber(row[valIdx >= 0 ? valIdx : 1]),
                })),
            };
        }
```

- [ ] **Step 4: 运行测试确认通过**

Run: `cd source/dts-analytics-webapp/modern && npx tsx --test src/pages/screens/hooks/cardDataMapper.test.ts`
Expected: 全部 PASS（3个 gantt + 1个 funnel）

- [ ] **Step 5: Commit**

```bash
git add source/dts-analytics-webapp/modern/src/pages/screens/hooks/cardDataMapper.ts source/dts-analytics-webapp/modern/src/pages/screens/hooks/cardDataMapper.test.ts
git commit -m "feat(screen): add funnel-chart data mapper"
```

---

## Task 2: 新增 scatter-chart 数据映射器

**Files:**
- Modify: `source/dts-analytics-webapp/modern/src/pages/screens/hooks/cardDataMapper.ts` (在 funnel-chart case 之后插入)
- Modify: `source/dts-analytics-webapp/modern/src/pages/screens/hooks/cardDataMapper.test.ts` (追加测试)

- [ ] **Step 1: 在 cardDataMapper.test.ts 追加 scatter-chart 测试**

```typescript
test('scatter-chart: maps rows to multi-series scatter data by category', () => {
    const cardData = {
        cols: [
            { name: 'x', display_name: '超期天数', base_type: 'type/Float' },
            { name: 'y', display_name: '风险值', base_type: 'type/Integer' },
            { name: 'size', display_name: '节点数', base_type: 'type/Integer' },
            { name: 'category', display_name: '风险等级', base_type: 'type/Text' },
        ],
        rows: [
            [3, 3, 2, '高'],
            [1, 2, 3, '中'],
            [0, 1, 5, '低'],
            [8, 3, 1, '高'],
        ],
    };
    const result = mapCardDataToConfig('scatter-chart', cardData, {
        xField: 'x', yField: 'y', sizeField: 'size', categoryField: 'category',
    });
    assert.ok(Array.isArray(result.series));
    const series = result.series as Array<{ name: string; data: number[][] }>;
    assert.strictEqual(series.length, 3); // 高、中、低
    const highSeries = series.find((s) => s.name === '高');
    assert.strictEqual(highSeries!.data.length, 2); // 2 rows with 高
    assert.deepStrictEqual(highSeries!.data[0], [3, 3, 2]);
});

test('scatter-chart: falls back to single series without categoryField', () => {
    const cardData = {
        cols: [
            { name: 'x', display_name: 'X', base_type: 'type/Float' },
            { name: 'y', display_name: 'Y', base_type: 'type/Float' },
        ],
        rows: [[1, 2], [3, 4]],
    };
    const result = mapCardDataToConfig('scatter-chart', cardData, {});
    assert.ok(Array.isArray(result.data));
    assert.deepStrictEqual(result.data, [[1, 2], [3, 4]]);
});
```

- [ ] **Step 2: 运行测试确认失败**

Run: `cd source/dts-analytics-webapp/modern && npx tsx --test src/pages/screens/hooks/cardDataMapper.test.ts`
Expected: FAIL — scatter-chart 走到 default 返回空对象

- [ ] **Step 3: 在 funnel-chart case 之后插入 scatter-chart case**

```typescript
        case 'scatter-chart': {
            const xF = config?.xField as string | undefined;
            const yF = config?.yField as string | undefined;
            const sizeF = config?.sizeField as string | undefined;
            const catF = config?.categoryField as string | undefined;
            const xIdx = xF ? cols.findIndex((c) => c.name === xF) : 0;
            const yIdx = yF ? cols.findIndex((c) => c.name === yF) : 1;
            const sizeIdx = sizeF ? cols.findIndex((c) => c.name === sizeF) : -1;
            const catIdx = catF ? cols.findIndex((c) => c.name === catF) : -1;

            if (catIdx >= 0) {
                const groups = new Map<string, number[][]>();
                for (const row of rows) {
                    const cat = String(row[catIdx] ?? '');
                    const point = [toNumber(row[xIdx >= 0 ? xIdx : 0]), toNumber(row[yIdx >= 0 ? yIdx : 1])];
                    if (sizeIdx >= 0) point.push(toNumber(row[sizeIdx]));
                    if (!groups.has(cat)) groups.set(cat, []);
                    groups.get(cat)!.push(point);
                }
                return {
                    series: Array.from(groups.entries()).map(([name, data]) => ({ name, data })),
                };
            }
            return {
                data: rows.map((row) => {
                    const point = [toNumber(row[xIdx >= 0 ? xIdx : 0]), toNumber(row[yIdx >= 0 ? yIdx : 1])];
                    if (sizeIdx >= 0) point.push(toNumber(row[sizeIdx]));
                    return point;
                }),
            };
        }
```

- [ ] **Step 4: 运行测试确认通过**

Run: `cd source/dts-analytics-webapp/modern && npx tsx --test src/pages/screens/hooks/cardDataMapper.test.ts`
Expected: 全部 PASS

- [ ] **Step 5: Commit**

```bash
git add source/dts-analytics-webapp/modern/src/pages/screens/hooks/cardDataMapper.ts source/dts-analytics-webapp/modern/src/pages/screens/hooks/cardDataMapper.test.ts
git commit -m "feat(screen): add scatter-chart data mapper with category grouping"
```

---

## Task 3: 新增 combo-chart 数据映射器

combo-chart 用于 KPI 火花线卡片。SQL 返回多行 (month, value, delta)，映射为 xAxisData + series。

**Files:**
- Modify: `source/dts-analytics-webapp/modern/src/pages/screens/hooks/cardDataMapper.ts` (在 scatter-chart case 之后插入)
- Modify: `source/dts-analytics-webapp/modern/src/pages/screens/hooks/cardDataMapper.test.ts`

- [ ] **Step 1: 追加 combo-chart 测试**

```typescript
test('combo-chart: maps rows using same axis chart logic as bar/line', () => {
    const cardData = {
        cols: [
            { name: 'month', display_name: '月份', base_type: 'type/Text' },
            { name: 'value', display_name: '值', base_type: 'type/Float' },
            { name: 'delta', display_name: '环比', base_type: 'type/Float' },
        ],
        rows: [
            ['2026-01', 65, 0],
            ['2026-02', 72, 7],
        ],
    };
    const result = mapCardDataToConfig('combo-chart', cardData, {
        xAxisField: 'month',
        series: [{ field: 'value', name: '完成率' }],
    });
    assert.deepStrictEqual(result.xAxisData, ['2026-01', '2026-02']);
    const series = result.series as Array<{ name: string; data: number[] }>;
    assert.strictEqual(series[0].name, '完成率');
    assert.deepStrictEqual(series[0].data, [65, 72]);
});
```

- [ ] **Step 2: 运行测试确认失败**

- [ ] **Step 3: 在 scatter-chart case 之后插入 combo-chart case**

combo-chart 的数据结构与 line-chart/bar-chart 完全一致（xAxisData + series），直接复用 mapAxisChart：

```typescript
        case 'combo-chart':
            return mapAxisChart(rows, cols, config);
```

- [ ] **Step 4: 运行测试确认通过**

- [ ] **Step 5: Commit**

```bash
git add source/dts-analytics-webapp/modern/src/pages/screens/hooks/cardDataMapper.ts source/dts-analytics-webapp/modern/src/pages/screens/hooks/cardDataMapper.test.ts
git commit -m "feat(screen): add combo-chart data mapper reusing axis chart logic"
```

---

## Task 4: 增强 scatter-chart 渲染器支持多系列气泡

当前 scatter-chart 渲染器只支持单系列 `c.data`。需要增强为：当数据映射器返回 `c.series` 时，渲染多系列气泡。

**Files:**
- Modify: `source/dts-analytics-webapp/modern/src/pages/screens/components/ComponentRenderer.tsx:2006-2029` (scatter-chart case)

- [ ] **Step 1: 重写 scatter-chart case**

将 line 2006-2029 的 scatter-chart case 替换为：

```typescript
            case 'scatter-chart': {
                const RISK_COLORS: Record<string, string> = { '高': '#ff4d4f', '中': '#faad14', '低': '#52c41a' };
                const scatterSeries = c.series as Array<{ name: string; data: number[][] }> | undefined;
                const scatterData = c.data as number[][] | undefined;
                const builtSeries = scatterSeries?.length
                    ? scatterSeries.map((s, idx) => ({
                        name: s.name,
                        type: 'scatter' as const,
                        data: s.data,
                        symbolSize: (val: number[]) => Math.max((val[2] ?? 1) * 8, 8),
                        itemStyle: { color: RISK_COLORS[s.name] || seriesColors[idx] || t.scatterColor },
                    }))
                    : [{
                        type: 'scatter' as const,
                        data: scatterData || [],
                        symbolSize: 10,
                        itemStyle: { color: seriesColors[0] || t.scatterColor },
                    }];
                return renderEChartWithHandles({
                    ...themeOptions,
                    ...chartMotionOption,
                    title: { text: c.title as string, textStyle: { color: t.textPrimary, fontSize: (c.titleFontSize as number) || 14 } },
                    legend: legendConfig,
                    tooltip: {
                        ...themeOptions.tooltip,
                        formatter: (params: any) => {
                            const d = params.data || [];
                            return `${params.seriesName}<br/>超期: ${d[0]}天<br/>节点数: ${d[2] ?? 1}`;
                        },
                    },
                    xAxis: {
                        name: c.xAxisName as string || '',
                        axisLine: { lineStyle: { color: t.echarts.axisLineColor } },
                        axisLabel: { color: t.echarts.axisLabelColor, fontSize: axisFontSize },
                        splitLine: { lineStyle: { color: t.echarts.splitLineColor } },
                    },
                    yAxis: {
                        name: c.yAxisName as string || '',
                        type: 'value',
                        axisLine: { lineStyle: { color: t.echarts.axisLineColor } },
                        axisLabel: {
                            color: t.echarts.axisLabelColor,
                            fontSize: axisFontSize,
                            formatter: (v: number) => ['', '低', '中', '高'][v] || String(v),
                        },
                        splitLine: { lineStyle: { color: t.echarts.splitLineColor } },
                        min: 0,
                        max: 4,
                        interval: 1,
                    },
                    series: builtSeries,
                    grid: axisGrid,
                }, echartsClickHandler);
            }
```

- [ ] **Step 2: 确认编译通过**

Run: `cd source/dts-analytics-webapp/modern && npx tsc --noEmit --skipLibCheck 2>&1 | head -20`

- [ ] **Step 3: Commit**

```bash
git add source/dts-analytics-webapp/modern/src/pages/screens/components/ComponentRenderer.tsx
git commit -m "feat(screen): enhance scatter-chart renderer for multi-series bubbles"
```

---

## Task 5: 增强甘特图渲染器（里程碑菱形 + 未启动虚线）

**Files:**
- Modify: `source/dts-analytics-webapp/modern/src/pages/screens/components/ComponentRenderer.tsx:1832-1950` (gantt-chart case)

- [ ] **Step 1: 修改 getBarColor 函数增加未启动状态**

在 gantt-chart case 内部的 `getBarColor` 函数（约 line 1859）末尾，`return '#1890ff'` 之前插入：

```typescript
                        if (!tk.isCompleted && !tk.isIncomplete && !tk.planDate) return '#d9d9d9';
```

- [ ] **Step 2: 修改 renderItem 支持里程碑菱形**

在 renderItem 函数内部（约 line 1882），`return { type: 'rect', ...}` 之前插入里程碑判断：

```typescript
                        // 里程碑节点画菱形
                        if (params.data?._task?.type === '里程碑节点') {
                            const size = Math.min(categoryHeight * 0.7, 16);
                            return {
                                type: 'diamond',
                                shape: { cx: startPx[0], cy: startPx[1], width: size, height: size },
                                style,
                            };
                        }
```

注意：`params` 参数需要从 renderItem 的第一个参数获取，当前代码中是 `_params`，需要改为 `params`。

- [ ] **Step 3: 确认编译通过**

Run: `cd source/dts-analytics-webapp/modern && npx tsc --noEmit --skipLibCheck 2>&1 | head -20`

- [ ] **Step 4: Commit**

```bash
git add source/dts-analytics-webapp/modern/src/pages/screens/components/ComponentRenderer.tsx
git commit -m "feat(screen): enhance gantt-chart with milestone diamonds"
```

---

## Task 6: 重建 SQL 卡片（13张）

**操作：** 通过 psql 命令直接更新 dts_analytics 数据库中的 analytics_card 表。

**重要提示：**
- 所有卡片 `database_id = 2`（pg-lake / biadmin 库）
- 参数使用 `{{v_plan_month}}` 和 `[[AND project_no = {{v_project_no}}]]` 可选块语法
- `card_type = 'model'`, `creator_id = 2`
- 需要生成 `entity_id = substr(md5(random()::text), 1, 21)`

- [ ] **Step 1: 删除旧卡片 22-33**

```bash
docker exec -i s10-stack-dts-pg-1 psql -U dts_analytics -d dts_analytics -c "DELETE FROM analytics_card WHERE id >= 22 AND id <= 33"
```

- [ ] **Step 2: 重置序列**

```bash
docker exec -i s10-stack-dts-pg-1 psql -U dts_analytics -d dts_analytics -c "SELECT setval('analytics_card_id_seq', 21)"
```

- [ ] **Step 3: 插入 13 张新卡片**

使用以下 SQL 按顺序插入（id 22-34）：

**Card 22: 月份列表**（复用原有）
```sql
INSERT INTO analytics_card (entity_id, name, description, card_type, dataset_query_json, database_id, creator_id, created_at, updated_at)
VALUES (substr(md5(random()::text),1,21), '月份列表', '筛选器-月份下拉', 'model',
'{"database":2,"type":"native","native":{"query":"SELECT DISTINCT plan_month FROM biz_dws_period_node_summary ORDER BY plan_month DESC","template-tags":{}}}',
2, 2, now(), now());
```

**Card 23: 项目列表**（复用原有）
```sql
INSERT INTO analytics_card (entity_id, name, description, card_type, dataset_query_json, database_id, creator_id, created_at, updated_at)
VALUES (substr(md5(random()::text),1,21), '项目列表', '筛选器-项目下拉', 'model',
'{"database":2,"type":"native","native":{"query":"SELECT DISTINCT project_no FROM biz_dws_period_node_summary WHERE plan_month = {{v_plan_month}} ORDER BY project_no","template-tags":{"v_plan_month":{"id":"v_plan_month","name":"v_plan_month","display-name":"v_plan_month","type":"text"}}}}',
2, 2, now(), now());
```

**Card 24: KPI趋势-完成率**
```sql
INSERT INTO analytics_card (entity_id, name, description, card_type, dataset_query_json, database_id, creator_id, created_at, updated_at)
VALUES (substr(md5(random()::text),1,21), 'KPI趋势-完成率', 'combo-chart: 6期完成率趋势+环比', 'model',
'{"database":2,"type":"native","native":{"query":"WITH monthly AS (SELECT plan_month, ROUND(SUM(completed_cnt)::numeric / NULLIF(SUM(due_cnt),0) * 100, 1) AS value FROM biz_dws_period_node_summary WHERE plan_month <= {{v_plan_month}} [[AND project_no = {{v_project_no}}]] GROUP BY plan_month ORDER BY plan_month DESC LIMIT 6) SELECT plan_month AS month, value, value - LAG(value) OVER (ORDER BY plan_month) AS delta FROM monthly ORDER BY plan_month","template-tags":{"v_plan_month":{"id":"v_plan_month","name":"v_plan_month","display-name":"v_plan_month","type":"text"},"v_project_no":{"id":"v_project_no","name":"v_project_no","display-name":"v_project_no","type":"text"}}}}',
2, 2, now(), now());
```

**Card 25: KPI趋势-准时率**
```sql
INSERT INTO analytics_card (entity_id, name, description, card_type, dataset_query_json, database_id, creator_id, created_at, updated_at)
VALUES (substr(md5(random()::text),1,21), 'KPI趋势-准时率', 'combo-chart: 6期准时率趋势+环比', 'model',
'{"database":2,"type":"native","native":{"query":"WITH monthly AS (SELECT plan_month, ROUND(SUM(on_time_cnt)::numeric / NULLIF(SUM(due_cnt),0) * 100, 1) AS value FROM biz_dws_period_node_summary WHERE plan_month <= {{v_plan_month}} [[AND project_no = {{v_project_no}}]] GROUP BY plan_month ORDER BY plan_month DESC LIMIT 6) SELECT plan_month AS month, value, value - LAG(value) OVER (ORDER BY plan_month) AS delta FROM monthly ORDER BY plan_month","template-tags":{"v_plan_month":{"id":"v_plan_month","name":"v_plan_month","display-name":"v_plan_month","type":"text"},"v_project_no":{"id":"v_project_no","name":"v_project_no","display-name":"v_project_no","type":"text"}}}}',
2, 2, now(), now());
```

**Card 26: KPI趋势-超期率**
```sql
INSERT INTO analytics_card (entity_id, name, description, card_type, dataset_query_json, database_id, creator_id, created_at, updated_at)
VALUES (substr(md5(random()::text),1,21), 'KPI趋势-超期率', 'combo-chart: 6期超期率趋势+环比', 'model',
'{"database":2,"type":"native","native":{"query":"WITH monthly AS (SELECT plan_month, ROUND((SUM(overdue_completed_cnt) + SUM(incomplete_cnt))::numeric / NULLIF(SUM(due_cnt),0) * 100, 1) AS value FROM biz_dws_period_node_summary WHERE plan_month <= {{v_plan_month}} [[AND project_no = {{v_project_no}}]] GROUP BY plan_month ORDER BY plan_month DESC LIMIT 6) SELECT plan_month AS month, value, value - LAG(value) OVER (ORDER BY plan_month) AS delta FROM monthly ORDER BY plan_month","template-tags":{"v_plan_month":{"id":"v_plan_month","name":"v_plan_month","display-name":"v_plan_month","type":"text"},"v_project_no":{"id":"v_project_no","name":"v_project_no","display-name":"v_project_no","type":"text"}}}}',
2, 2, now(), now());
```

**Card 27: KPI趋势-未完成数**
```sql
INSERT INTO analytics_card (entity_id, name, description, card_type, dataset_query_json, database_id, creator_id, created_at, updated_at)
VALUES (substr(md5(random()::text),1,21), 'KPI趋势-未完成数', 'combo-chart: 6期未完成数趋势+环比', 'model',
'{"database":2,"type":"native","native":{"query":"WITH monthly AS (SELECT plan_month, SUM(incomplete_cnt)::int AS value FROM biz_dws_period_node_summary WHERE plan_month <= {{v_plan_month}} [[AND project_no = {{v_project_no}}]] GROUP BY plan_month ORDER BY plan_month DESC LIMIT 6) SELECT plan_month AS month, value, value - LAG(value) OVER (ORDER BY plan_month) AS delta FROM monthly ORDER BY plan_month","template-tags":{"v_plan_month":{"id":"v_plan_month","name":"v_plan_month","display-name":"v_plan_month","type":"text"},"v_project_no":{"id":"v_project_no","name":"v_project_no","display-name":"v_project_no","type":"text"}}}}',
2, 2, now(), now());
```

**Card 28: KPI趋势-高风险数**
```sql
INSERT INTO analytics_card (entity_id, name, description, card_type, dataset_query_json, database_id, creator_id, created_at, updated_at)
VALUES (substr(md5(random()::text),1,21), 'KPI趋势-高风险数', 'combo-chart: 6期高风险数趋势+环比', 'model',
'{"database":2,"type":"native","native":{"query":"WITH monthly AS (SELECT s.plan_month, SUM(CASE WHEN s.risk_level = ''高'' THEN s.incomplete_cnt ELSE 0 END)::int AS value FROM biz_dws_period_risk_summary s WHERE s.plan_month <= {{v_plan_month}} [[AND s.project_no = {{v_project_no}}]] GROUP BY s.plan_month ORDER BY s.plan_month DESC LIMIT 6) SELECT plan_month AS month, value, value - LAG(value) OVER (ORDER BY plan_month) AS delta FROM monthly ORDER BY plan_month","template-tags":{"v_plan_month":{"id":"v_plan_month","name":"v_plan_month","display-name":"v_plan_month","type":"text"},"v_project_no":{"id":"v_project_no","name":"v_project_no","display-name":"v_project_no","type":"text"}}}}',
2, 2, now(), now());
```

**Card 29: 项目记分卡排行**
```sql
INSERT INTO analytics_card (entity_id, name, description, card_type, dataset_query_json, database_id, creator_id, created_at, updated_at)
VALUES (substr(md5(random()::text),1,21), '项目记分卡排行', 'table: 项目健康度排行', 'model',
'{"database":2,"type":"native","native":{"query":"WITH cur AS (SELECT project_no, SUM(completed_cnt) AS completed, SUM(due_cnt) AS due, SUM(on_time_cnt) AS on_time, SUM(incomplete_cnt) AS incomplete, SUM(overdue_completed_cnt) AS overdue FROM biz_dws_period_node_summary WHERE plan_month = {{v_plan_month}} GROUP BY project_no), risk AS (SELECT project_no, SUM(CASE WHEN risk_level = ''高'' THEN incomplete_cnt ELSE 0 END) AS high_risk FROM biz_dws_period_risk_summary WHERE plan_month = {{v_plan_month}} GROUP BY project_no), prev AS (SELECT project_no, ROUND(SUM(completed_cnt)::numeric/NULLIF(SUM(due_cnt),0)*100,1) AS prev_health FROM biz_dws_period_node_summary WHERE plan_month = to_char(({{v_plan_month}}||''-01'')::date - interval ''1 month'',''YYYY-MM'') GROUP BY project_no) SELECT c.project_no AS \"项目编号\", ROUND(c.completed::numeric/NULLIF(c.due,0)*100,1) AS \"完成率\", ROUND(c.on_time::numeric/NULLIF(c.due,0)*100,1) AS \"准时率\", c.incomplete AS \"超期数\", COALESCE(r.high_risk,0) AS \"高风险\", ROUND((c.completed::numeric/NULLIF(c.due,0)*40 + c.on_time::numeric/NULLIF(c.due,0)*30 + (1-c.overdue::numeric/NULLIF(c.due,0))*20 + (1-COALESCE(r.high_risk,0)::numeric/NULLIF(c.due,0))*10)*100/100,1) AS \"健康度\", CASE WHEN p.prev_health IS NULL THEN ''→'' WHEN ROUND((c.completed::numeric/NULLIF(c.due,0)*40+c.on_time::numeric/NULLIF(c.due,0)*30+(1-c.overdue::numeric/NULLIF(c.due,0))*20+(1-COALESCE(r.high_risk,0)::numeric/NULLIF(c.due,0))*10)*100/100,1) > p.prev_health THEN ''↑'' WHEN ROUND((c.completed::numeric/NULLIF(c.due,0)*40+c.on_time::numeric/NULLIF(c.due,0)*30+(1-c.overdue::numeric/NULLIF(c.due,0))*20+(1-COALESCE(r.high_risk,0)::numeric/NULLIF(c.due,0))*10)*100/100,1) < p.prev_health THEN ''↓'' ELSE ''→'' END AS \"趋势\" FROM cur c LEFT JOIN risk r ON c.project_no = r.project_no LEFT JOIN prev p ON c.project_no = p.project_no ORDER BY \"健康度\" ASC","template-tags":{"v_plan_month":{"id":"v_plan_month","name":"v_plan_month","display-name":"v_plan_month","type":"text"}}}}',
2, 2, now(), now());
```

**Card 30: 节点进度漏斗**
```sql
INSERT INTO analytics_card (entity_id, name, description, card_type, dataset_query_json, database_id, creator_id, created_at, updated_at)
VALUES (substr(md5(random()::text),1,21), '节点进度漏斗', 'funnel-chart: 节点状态分布', 'model',
'{"database":2,"type":"native","native":{"query":"SELECT ''总计'' AS name, SUM(due_cnt)::int AS value FROM biz_dws_period_node_summary WHERE plan_month = {{v_plan_month}} [[AND project_no = {{v_project_no}}]] UNION ALL SELECT ''已完成'', SUM(completed_cnt)::int FROM biz_dws_period_node_summary WHERE plan_month = {{v_plan_month}} [[AND project_no = {{v_project_no}}]] UNION ALL SELECT ''进行中'', SUM(pending_normal_cnt)::int FROM biz_dws_period_node_summary WHERE plan_month = {{v_plan_month}} [[AND project_no = {{v_project_no}}]] UNION ALL SELECT ''超期未完'', SUM(incomplete_cnt)::int FROM biz_dws_period_node_summary WHERE plan_month = {{v_plan_month}} [[AND project_no = {{v_project_no}}]] UNION ALL SELECT ''未启动'', SUM(abnormal_pending_cnt)::int FROM biz_dws_period_node_summary WHERE plan_month = {{v_plan_month}} [[AND project_no = {{v_project_no}}]]","template-tags":{"v_plan_month":{"id":"v_plan_month","name":"v_plan_month","display-name":"v_plan_month","type":"text"},"v_project_no":{"id":"v_project_no","name":"v_project_no","display-name":"v_project_no","type":"text"}}}}',
2, 2, now(), now());
```

**Card 31: 责任人任务分布**
```sql
INSERT INTO analytics_card (entity_id, name, description, card_type, dataset_query_json, database_id, creator_id, created_at, updated_at)
VALUES (substr(md5(random()::text),1,21), '责任人任务分布', 'bar-chart: 按责任人堆叠', 'model',
'{"database":2,"type":"native","native":{"query":"SELECT owner AS category, SUM(CASE WHEN is_completed AND NOT is_overdue_completed THEN 1 ELSE 0 END) AS \"已完成\", SUM(CASE WHEN NOT is_completed AND NOT is_incomplete AND delay_days <= 0 THEN 1 ELSE 0 END) AS \"进行中\", SUM(CASE WHEN is_overdue_completed OR is_incomplete THEN 1 ELSE 0 END) AS \"超期\" FROM biz_dwd_project_node WHERE plan_month = {{v_plan_month}} [[AND project_no = {{v_project_no}}]] GROUP BY owner ORDER BY COUNT(*) DESC","template-tags":{"v_plan_month":{"id":"v_plan_month","name":"v_plan_month","display-name":"v_plan_month","type":"text"},"v_project_no":{"id":"v_project_no","name":"v_project_no","display-name":"v_project_no","type":"text"}}}}',
2, 2, now(), now());
```

**Card 32: 风险气泡分布**
```sql
INSERT INTO analytics_card (entity_id, name, description, card_type, dataset_query_json, database_id, creator_id, created_at, updated_at)
VALUES (substr(md5(random()::text),1,21), '风险气泡分布', 'scatter-chart: 风险等级×超期天数气泡', 'model',
'{"database":2,"type":"native","native":{"query":"SELECT COALESCE(AVG(GREATEST(delay_days,0)),0)::int AS x, CASE risk_level WHEN ''高'' THEN 3 WHEN ''中'' THEN 2 ELSE 1 END AS y, COUNT(*)::int AS size, risk_level AS category FROM biz_dwd_project_node WHERE plan_month = {{v_plan_month}} AND is_incomplete [[AND project_no = {{v_project_no}}]] GROUP BY risk_level","template-tags":{"v_plan_month":{"id":"v_plan_month","name":"v_plan_month","display-name":"v_plan_month","type":"text"},"v_project_no":{"id":"v_project_no","name":"v_project_no","display-name":"v_project_no","type":"text"}}}}',
2, 2, now(), now());
```

**Card 33: 甘特图数据**（复用原有，增加project_no列）
```sql
INSERT INTO analytics_card (entity_id, name, description, card_type, dataset_query_json, database_id, creator_id, created_at, updated_at)
VALUES (substr(md5(random()::text),1,21), '甘特图数据', 'gantt-chart数据源', 'model',
'{"database":2,"type":"native","native":{"query":"SELECT node_task, node_type, plan_date::text, actual_date::text, is_completed, is_overdue_completed, is_incomplete, delay_days, risk_level, owner FROM biz_dwd_project_node WHERE plan_month = {{v_plan_month}} [[AND project_no = {{v_project_no}}]] ORDER BY CASE node_type WHEN ''重大节点'' THEN 1 WHEN ''重要节点'' THEN 2 WHEN ''一般节点'' THEN 3 WHEN ''里程碑节点'' THEN 4 END, plan_date ASC","template-tags":{"v_plan_month":{"id":"v_plan_month","name":"v_plan_month","display-name":"v_plan_month","type":"text"},"v_project_no":{"id":"v_project_no","name":"v_project_no","display-name":"v_project_no","type":"text"}}}}',
2, 2, now(), now());
```

**Card 34: 节点明细表**（增强版：含状态标签和排序）
```sql
INSERT INTO analytics_card (entity_id, name, description, card_type, dataset_query_json, database_id, creator_id, created_at, updated_at)
VALUES (substr(md5(random()::text),1,21), '节点明细表', 'table: 增强版节点明细', 'model',
'{"database":2,"type":"native","native":{"query":"SELECT node_task AS \"节点任务\", node_type AS \"节点类型\", owner AS \"责任人\", plan_date::text AS \"计划日期\", actual_date::text AS \"实际日期\", CASE WHEN is_completed AND NOT is_overdue_completed THEN ''按时完成'' WHEN is_completed AND is_overdue_completed THEN ''超期完成'' WHEN is_incomplete THEN ''未完成'' ELSE ''进行中'' END AS \"完成状态\", delay_days AS \"超期天数\", risk_level AS \"风险等级\", risk_content AS \"风险内容\" FROM biz_dwd_project_node WHERE plan_month = {{v_plan_month}} [[AND project_no = {{v_project_no}}]] ORDER BY CASE risk_level WHEN ''高'' THEN 1 WHEN ''中'' THEN 2 ELSE 3 END, delay_days DESC NULLS LAST","template-tags":{"v_plan_month":{"id":"v_plan_month","name":"v_plan_month","display-name":"v_plan_month","type":"text"},"v_project_no":{"id":"v_project_no","name":"v_project_no","display-name":"v_project_no","type":"text"}}}}',
2, 2, now(), now());
```

- [ ] **Step 4: 验证卡片数量**

```bash
docker exec s10-stack-dts-pg-1 psql -U dts_analytics -d dts_analytics -c "SELECT id, name FROM analytics_card WHERE id >= 22 ORDER BY id"
```

Expected: 13行，id 22-34

- [ ] **Step 5: Commit SQL 脚本**

将上述 SQL 保存为 `worklog/v2.2.1/sprint-7/cockpit-v2-cards.sql` 并 commit。

---

## Task 7: 重建 Screen 模板（14组件布局）

**操作：** 更新 analytics_screen id=10 的 components_json 和 height。

- [ ] **Step 1: 构建 components_json**

14个组件的完整 JSON 数组。每个组件包含：
- `type`: 组件类型
- `x, y, w, h`: 位置和尺寸（基于1200宽画布）
- `config`: 静态配置
- `dataSource`: 数据绑定

布局坐标（基于设计文档）：

| # | 组件 | type | x | y | w | h | cardId |
|---|------|------|---|---|---|---|--------|
| 0 | 标题 | title | 400 | 0 | 400 | 50 | — |
| 1 | 月份筛选 | filter-select | 20 | 5 | 160 | 40 | 22 |
| 2 | 项目筛选 | filter-select | 200 | 5 | 160 | 40 | 23 |
| 3 | KPI-完成率 | combo-chart | 20 | 55 | 228 | 110 | 24 |
| 4 | KPI-准时率 | combo-chart | 258 | 55 | 228 | 110 | 25 |
| 5 | KPI-超期率 | combo-chart | 496 | 55 | 228 | 110 | 26 |
| 6 | KPI-未完成 | combo-chart | 734 | 55 | 228 | 110 | 27 |
| 7 | KPI-高风险 | combo-chart | 972 | 55 | 208 | 110 | 28 |
| 8 | 记分卡排行 | table | 20 | 175 | 1160 | 200 | 29 |
| 9 | 进度漏斗 | funnel-chart | 20 | 385 | 380 | 280 | 30 |
| 10 | 责任人分布 | bar-chart | 410 | 385 | 380 | 280 | 31 |
| 11 | 风险气泡 | scatter-chart | 800 | 385 | 380 | 280 | 32 |
| 12 | 甘特图 | gantt-chart | 20 | 675 | 1160 | 320 | 33 |
| 13 | 明细表 | table | 20 | 1005 | 1160 | 350 | 34 |

- [ ] **Step 2: 用 Python 脚本构建并更新 screen JSON**

使用 Python 构建完整 components_json 并通过 psql 更新 screen id=10。

关键配置字段：
- combo-chart: `xAxisField: "month"`, `series: [{field: "value", name: "完成率", type: "line"}]`, `yAxis: [{min: 0, max: 100}]`
- funnel-chart: `nameField: "name"`, `valueField: "value"`, `title: "节点进度"`
- bar-chart: `xAxisField: "category"`, 不设 series（自动使用所有非x列），`title: "责任人任务分布"`, 需配置横向
- scatter-chart: `xField: "x"`, `yField: "y"`, `sizeField: "size"`, `categoryField: "category"`, `title: "风险气泡"`
- table: 记分卡需配置 `clickAction: {type: "setVariable", variableKey: "v_project_no", valueField: "项目编号"}`

- [ ] **Step 3: 更新 screen height**

```sql
UPDATE analytics_screen SET height = 1600, components_json = '<json>', updated_at = now() WHERE id = 10;
```

- [ ] **Step 4: 验证 screen 数据**

```bash
docker exec s10-stack-dts-pg-1 psql -U dts_analytics -d dts_analytics -c "SELECT id, name, height, jsonb_array_length(components_json::jsonb) as comp_count FROM analytics_screen WHERE id = 10"
```

Expected: height=1600, comp_count=14

- [ ] **Step 5: Commit**

---

## Task 8: 构建部署并验证

- [ ] **Step 1: 前端构建**

```bash
cd source/dts-analytics-webapp/modern && npm run build
```

- [ ] **Step 2: Docker 镜像重建**

```bash
cd /opt/prod/s10/s10-stack && docker build -t dts-analytics-webapp-modern:1.0.0 -f builds/dts-analytics-webapp/modern/Dockerfile .
```

- [ ] **Step 3: 重启容器**

```bash
docker restart s10-stack-dts-analytics-webapp-modern-1
```

- [ ] **Step 4: 验证页面**

浏览器访问大屏页面，检查：
1. 5个KPI卡片带火花线趋势
2. 记分卡排行表格
3. 进度漏斗图
4. 责任人分布横向柱状图
5. 风险气泡散点图
6. 甘特图（含里程碑菱形）
7. 节点明细表
8. 月份筛选联动
9. 点击记分卡行联动下方面板
