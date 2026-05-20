// @ts-nocheck — migrated from analytics-webapp, pending unused-import cleanup
import type { ReactNode } from 'react';
import { ProjectGanttBoard, type ProjectGanttTask } from '../../../project-cockpit/components/ProjectGanttBoard';
import { BoardHierarchicalGanttWithModal } from '../../../project-cockpit/components/ProjectDetailGanttModal';
import { isLightColor, SCREEN_UI_FONT_FAMILY } from './optionBuilders';
import type { EChartsRendererProps } from './types';

export function renderSpecialChart(type: string, props: EChartsRendererProps): ReactNode | null {
    const {
        c, t, height, mode, componentId, runtime,
        EChart, renderEChartWithHandles,
        themeOptions, chartMotionOption, chartTitleLayout, legendConfig, axisGrid, seriesColors,
        axisFontSize, axisLabelColor: axisLabelColorOverride, seriesLabelFontSize,
        xAxisLabelRotate,
        axisSeriesLabelShow,
        echartsClickHandler, componentActions, executeComponentActions,
        plotCenterX, plotCenterY, radarRadius,
        mapDrillRegion, setMapDrillRegion, mapReadyVersion, hasMapFn,
        cardData, fontFamily,
    } = props;

    const axisLabelColor = axisLabelColorOverride || t.echarts.axisLabelColor;
    const component = { id: componentId };

    switch (type) {
        case 'gantt-chart': {
            /* eslint-disable @typescript-eslint/no-explicit-any */
            const tasks = Array.isArray(c.tasks) ? (c.tasks as Array<Record<string, any>>) : [];
            const ganttRenderMode = String(c.renderMode ?? '').trim().toLowerCase();
            const defaultZoomRaw = typeof c.defaultZoom === 'string' ? c.defaultZoom.trim().toLowerCase() : '';
            const defaultZoom = (['day', 'week', 'month', 'quarter'] as const).includes(defaultZoomRaw as any)
                ? (defaultZoomRaw as 'day' | 'week' | 'month' | 'quarter')
                : undefined;
            if (ganttRenderMode === 'board-hierarchical') {
                return (
                    <BoardHierarchicalGanttWithModal
                        cardData={cardData ?? null}
                        maxHeight={height}
                        sideTextColor={typeof c.sideTextColor === 'string' ? c.sideTextColor : undefined}
                        dark={isLightColor(t.textPrimary)}
                        defaultZoom={defaultZoom}
                    />
                );
            }
            if (ganttRenderMode === 'board') {
                const onTaskClick = mode === 'preview' && componentActions.length > 0
                    ? (task: ProjectGanttTask) => {
                        executeComponentActions({
                            data: task,
                            name: task.name,
                            ...task,
                        });
                    }
                    : undefined;
                return (
                    <ProjectGanttBoard
                        tasks={tasks as ProjectGanttTask[]}
                        maxHeight={height}
                        onTaskClick={onTaskClick}
                        sideTextColor={typeof c.sideTextColor === 'string' ? c.sideTextColor : undefined}
                        dark={isLightColor(t.textPrimary)}
                        defaultZoom={defaultZoom}
                    />
                );
            }
            if (!tasks.length) {
                return renderEChartWithHandles({ ...themeOptions, title: { text: '暂无数据', left: 'center', top: 'center', textStyle: { color: t.textSecondary, fontSize: 14 } } });
            }

            const sorted = [...tasks].sort((a, b) => String(a.planDate ?? '').localeCompare(String(b.planDate ?? '')));
            const categories = sorted.map((tk) => String(tk.name ?? ''));

            const allDates = sorted.flatMap((tk) => [tk.planDate, tk.actualDate].filter(Boolean).map(String));
            if (!allDates.length) {
                return renderEChartWithHandles({ ...themeOptions, title: { text: '无有效日期数据', left: 'center', top: 'center', textStyle: { color: t.textSecondary, fontSize: 14 } } });
            }
            const minDate = allDates.reduce((a, b) => (a < b ? a : b));
            const maxDate = allDates.reduce((a, b) => (a > b ? a : b));
            const today = new Date().toISOString().slice(0, 10);

            const getBarColor = (tk: Record<string, any>) => {
                if (tk.isCompleted && !tk.isOverdue) return '#52c41a';
                if (tk.isCompleted && tk.isOverdue) return '#faad14';
                if (tk.isIncomplete) return '#ff4d4f';
                return '#1890ff';
            };

            // Build bar data: each bar is [startTime, endTime, categoryIndex]
            // Using xAxis=time, yAxis=category, bar series type for compatibility
            const barSeries: any[] = [];
            sorted.forEach((tk, idx) => {
                const startRaw = tk.planDate ? new Date(String(tk.planDate)).getTime() : NaN;
                if (isNaN(startRaw)) return; // 跳过无效日期的任务，避免黑色条
                const end = tk.actualDate ? new Date(String(tk.actualDate)).getTime() : Date.now();
                const endSafe = isNaN(end) ? Date.now() : end;
                barSeries.push({
                    value: [startRaw, idx, endSafe - startRaw, tk.delayDays],
                    itemStyle: { color: getBarColor(tk) },
                    _task: tk,
                });
            });

            const xMax = maxDate > today ? maxDate : today;
            const ganttOption: Record<string, unknown> = {
                ...themeOptions,
                tooltip: {
                    trigger: 'item',
                    formatter: (params: any) => {
                        const tk = params.data?._task;
                        if (!tk) return '';
                        return [
                            `<b>${tk.name}</b>`,
                            tk.majorProjectName ? `重大项目: ${tk.majorProjectName}` : '',
                            tk.subprojectName ? `子项目: ${tk.subprojectName}` : '',
                            `类型: ${tk.type}`,
                            `责任人: ${tk.owner}`,
                            tk.status ? `状态: ${tk.status}` : '',
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
                    max: xMax,
                    axisLabel: { color: t.textSecondary, fontSize: 11 },
                    splitLine: { lineStyle: { color: t.echarts.splitLineColor, type: 'dashed' } },
                },
                yAxis: {
                    type: 'category',
                    data: categories,
                    inverse: true,
                    axisLabel: {
                        color: t.textPrimary,
                        fontSize: 11,
                        width: 100,
                        overflow: 'truncate' as const,
                    },
                    splitLine: { show: false },
                },
                dataZoom: [{ type: 'inside', xAxisIndex: 0 }],
                series: [
                    {
                        type: 'custom',
                        renderItem: (params: any, api: any) => {
                            const startVal = api.value(0);
                            const catIdx = api.value(1);
                            const duration = api.value(2);
                            const endVal = startVal + duration;
                            const startPx = api.coord([startVal, catIdx]);
                            const endPx = api.coord([endVal, catIdx]);
                            const categoryHeight = typeof api.size === 'function' ? api.size([0, 1])[1] : 30;
                            const barHeight = categoryHeight * 0.6;
                            const baseStyle = typeof api.style === 'function' ? api.style() : {};
                            const fillColor = (typeof api.visual === 'function' ? api.visual('color') : null)
                                || baseStyle.fill
                                || barSeries[params.dataIndex]?.itemStyle?.color
                                || '#1890ff';
                            const style = { ...baseStyle, fill: fillColor };
                            // 里程碑节点画菱形
                            if (params.data?._task?.type === '里程碑节点') {
                                const sz = Math.min(categoryHeight * 0.7, 16);
                                return {
                                    type: 'diamond',
                                    shape: { cx: startPx[0], cy: startPx[1], width: sz, height: sz },
                                    style,
                                };
                            }
                            return {
                                type: 'rect',
                                shape: {
                                    x: startPx[0],
                                    y: startPx[1] - barHeight / 2,
                                    width: Math.max(endPx[0] - startPx[0], 4),
                                    height: barHeight,
                                    r: [2, 2, 2, 2],
                                },
                                style,
                            };
                        },
                        encode: { x: [0], y: 1 },
                        data: barSeries,
                        markLine: {
                            silent: true,
                            symbol: 'none',
                            lineStyle: { color: '#ff4d4f', type: 'dashed', width: 2 },
                            data: [{ xAxis: new Date(today).getTime() }],
                            label: { formatter: '今日', position: 'start', color: '#ff4d4f', fontSize: 11 },
                        },
                    },
                ],
            };
            /* eslint-enable @typescript-eslint/no-explicit-any */

            return renderEChartWithHandles(ganttOption, echartsClickHandler);
        }

        case 'radar-chart':
            {
                const mappedSeries = Array.isArray(c.series)
                    ? (c.series as Array<{ name?: string; data?: number[] }>)
                    : [];
                const radarSeries = mappedSeries.length > 0
                    ? mappedSeries.map((item, index) => ({
                        name: item.name,
                        value: Array.isArray(item.data) ? item.data : [],
                        // 默认填充透明度统一到 0.25 起步，逐系列递减，视觉更饱满
                        areaStyle: { opacity: Math.max(0.18, 0.35 - (index * 0.08)) },
                        lineStyle: { width: 2 },
                        symbol: 'circle',
                        symbolSize: 5,
                    }))
                    : [{
                        value: c.data as number[],
                        areaStyle: { opacity: 0.32 },
                        lineStyle: { width: 2 },
                        symbol: 'circle',
                        symbolSize: 5,
                    }];
            return renderEChartWithHandles({
                ...themeOptions,
                ...chartMotionOption,
                ...(seriesColors.length > 0 ? { color: seriesColors } : {}),
                title: chartTitleLayout.titleOption,
                legend: legendConfig,
                radar: {
                    indicator: c.indicator as Array<{ name: string; max: number }>,
                    center: [plotCenterX, plotCenterY],
                    radius: radarRadius,
                    axisName: { color: t.radar.axisNameColor, fontSize: 12 },
                    axisLine: { lineStyle: { color: t.radar.splitLineColor } },
                    splitLine: { lineStyle: { color: t.radar.splitLineColor } },
                    // 交错色带替代单色背景，视觉更专业
                    splitArea: {
                        show: true,
                        areaStyle: {
                            color: ['rgba(148,163,184,0.04)', 'rgba(148,163,184,0.01)'],
                        },
                    },
                },
                series: [{
                    type: 'radar',
                    emphasis: { focus: 'self', lineStyle: { width: 3 } },
                    data: radarSeries,
                }],
            }, echartsClickHandler);
            }

        case 'wordcloud-chart':
            return renderEChartWithHandles({
                ...themeOptions,
                title: chartTitleLayout.titleOption,
                tooltip: { show: true, formatter: '{b}: {c}' },
                series: [{
                    type: 'wordCloud',
                    shape: (c.shape as string) || 'circle',
                    sizeRange: (c.fontSizeRange as [number, number]) || [14, 60],
                    rotationRange: (c.rotationRange as [number, number]) || [-45, 45],
                    rotationStep: 15,
                    gridSize: 8,
                    drawOutOfBound: false,
                    textStyle: {
                        fontFamily: fontFamily || SCREEN_UI_FONT_FAMILY,
                        color: () => t.echarts.colorPalette[Math.floor(Math.random() * t.echarts.colorPalette.length)],
                    },
                    data: (c.data as Array<{ name: string; value: number }>)?.map(d => ({
                        name: d.name,
                        value: d.value,
                    })) || [],
                }],
            });

        case 'map-chart': {
            const title = String(c.title ?? '区域地图');
            const mapScope = String(c.mapScope ?? 'china');
            const defaultRegions = mapScope === 'world'
                ? [
                    { name: 'China', code: 'CN', value: 260 },
                    { name: 'United States of America', code: 'US', value: 180 },
                    { name: 'Russia', code: 'RU', value: 150 },
                    { name: 'India', code: 'IN', value: 140 },
                    { name: 'Brazil', code: 'BR', value: 110 },
                    { name: 'Australia', code: 'AU', value: 90 },
                ]
                : [
                    { name: '北京市', code: '110000', value: 120 },
                    { name: '上海市', code: '310000', value: 180 },
                    { name: '广东省', code: '440000', value: 140 },
                    { name: '浙江省', code: '330000', value: 95 },
                    { name: '四川省', code: '510000', value: 72 },
                    { name: '湖北省', code: '420000', value: 88 },
                ];
            const regions = Array.isArray(c.regions) && c.regions.length > 0 ? c.regions as Array<Record<string, unknown>> : defaultRegions;
            const getChildren = (item: unknown): Array<Record<string, unknown>> => {
                if (!item || typeof item !== 'object') return [];
                const raw = (item as Record<string, unknown>).children;
                if (!Array.isArray(raw)) return [];
                return raw.filter((node): node is Record<string, unknown> => !!node && typeof node === 'object');
            };
            const activeRegion = mapDrillRegion
                ? regions.find((item) => String(item.name ?? '') === mapDrillRegion)
                : undefined;
            const canRegionDrill = c.enableRegionDrill !== false;
            const drillRows = getChildren(activeRegion);
            const listRows = drillRows.length > 0 ? drillRows : regions;

            const maxValue = Math.max(1, ...listRows.map((item) => Number(item.value ?? 0)));
            const minValue = Math.min(...listRows.map((item) => Number(item.value ?? 0)));
            const mapName = String(c.mapName || mapScope || `dts-${mapScope}`).trim();
            const usingGeoMap = !mapDrillRegion && Boolean(EChart) && Boolean(hasMapFn?.(mapName)) && mapReadyVersion >= 0;
            const regionCodeVariableKey = String(c.regionCodeVariableKey ?? '').trim();
            const resolveRegionCode = (item: Record<string, unknown> | undefined): string => {
                if (!item) return '';
                const candidate = item.code ?? item.adcode ?? item.regionCode ?? item.id;
                return String(candidate ?? '').trim();
            };

            if (usingGeoMap) {
                const mapMode = String(c.mapMode ?? 'region');
                const baseTitle = chartTitleLayout.titleOption;
                const mapClickHandler = (params: Record<string, unknown>) => {
                    const regionName = String(params.name ?? '');
                    const row = params.data && typeof params.data === 'object'
                        ? (params.data as Record<string, unknown>)
                        : undefined;
                    const clickedCode = String(row?.code ?? row?.adcode ?? '').trim();
                    const target = clickedCode
                        ? regions.find((item) => resolveRegionCode(item) === clickedCode)
                            || regions.find((item) => String(item.name ?? '') === regionName)
                        : regions.find((item) => String(item.name ?? '') === regionName);
                    if (canRegionDrill && target && getChildren(target).length > 0) {
                        setMapDrillRegion(regionName);
                    }
                    const variableKey = String(c.regionVariableKey ?? '').trim();
                    if (variableKey && regionName) {
                        runtime.setVariable(variableKey, regionName, `map-chart:${component.id}`);
                    }
                    const code = resolveRegionCode(target);
                    if (regionCodeVariableKey && code) {
                        runtime.setVariable(regionCodeVariableKey, code, `map-chart:${component.id}`);
                    }
                };

                // Build mapMode-specific ECharts options
                let mapOption: Record<string, unknown>;
                if (mapMode === 'bubble' || mapMode === 'scatter') {
                    const scatterData = (c.scatterData as Array<{ name: string; value: [number, number, number] }>) || [];
                    const sizeRange = (c.bubbleSizeRange as [number, number]) || (mapMode === 'scatter' ? [6, 6] : [8, 40]);
                    const maxMag = Math.max(1, ...scatterData.map(d => Math.abs(d.value?.[2] ?? 0)));
                    mapOption = {
                        ...themeOptions, ...chartMotionOption,
                        title: baseTitle,
                        tooltip: { trigger: 'item', formatter: (p: Record<string, unknown>) => {
                            const d = p.data as Record<string, unknown> | undefined;
                            return d ? `${d.name}: ${(d.value as number[])?.[2] ?? ''}` : '';
                        }},
                        geo: { map: mapName, roam: true, label: { show: false }, itemStyle: { areaColor: '#1e293b', borderColor: t.echarts.splitLineColor }, emphasis: { itemStyle: { areaColor: '#334155' } } },
                        series: [{
                            type: 'scatter', coordinateSystem: 'geo',
                            data: scatterData.map(d => ({ name: d.name, value: d.value })),
                            symbolSize: (val: number[]) => { const mag = val?.[2] ?? 0; return sizeRange[0] + (sizeRange[1] - sizeRange[0]) * (Math.abs(mag) / maxMag); },
                            itemStyle: { color: (c.bubbleColor as string) || t.echarts.colorPalette[0] },
                            label: { show: mapMode === 'scatter', formatter: '{b}', color: t.textPrimary, fontSize: 10 },
                        }],
                    };
                } else if (mapMode === 'heatmap') {
                    const heatmapData = (c.heatmapData as Array<[number, number, number]>) || [];
                    mapOption = {
                        ...themeOptions, ...chartMotionOption,
                        title: baseTitle,
                        tooltip: { show: true },
                        geo: { map: mapName, roam: true, label: { show: false }, itemStyle: { areaColor: '#1e293b', borderColor: t.echarts.splitLineColor }, emphasis: { itemStyle: { areaColor: '#334155' } } },
                        visualMap: { show: true, min: 0, max: Math.max(1, ...heatmapData.map(d => d[2] || 0)), left: 6, bottom: 8, itemWidth: 10, itemHeight: 60, textStyle: { color: t.textSecondary, fontSize: 10 }, inRange: { color: ['#3b82f6', '#f59e0b', '#ef4444'] } },
                        series: [{
                            type: 'heatmap', coordinateSystem: 'geo',
                            data: heatmapData,
                            pointSize: (c.heatmapRadius as number) || 20,
                            blurSize: ((c.heatmapRadius as number) || 20) * 1.5,
                        }],
                    };
                } else if (mapMode === 'flow') {
                    const flowData = (c.flowData as Array<{ from: { name: string; coord: [number, number] }; to: { name: string; coord: [number, number] }; value?: number }>) || [];
                    const curveness = (c.flowLineStyle as Record<string, unknown>)?.curveness as number ?? 0.2;
                    const flowColor = (c.flowLineStyle as Record<string, unknown>)?.color as string ?? t.echarts.colorPalette[0];
                    const showEffect = c.showFlowEffect !== false;
                    const endpoints = new Map<string, [number, number]>();
                    for (const f of flowData) {
                        if (f.from?.name && f.from?.coord) endpoints.set(f.from.name, f.from.coord);
                        if (f.to?.name && f.to?.coord) endpoints.set(f.to.name, f.to.coord);
                    }
                    mapOption = {
                        ...themeOptions, ...chartMotionOption,
                        title: baseTitle,
                        tooltip: { trigger: 'item' },
                        geo: { map: mapName, roam: true, label: { show: false }, itemStyle: { areaColor: '#1e293b', borderColor: t.echarts.splitLineColor }, emphasis: { itemStyle: { areaColor: '#334155' } } },
                        series: [
                            {
                                type: 'lines', coordinateSystem: 'geo',
                                data: flowData.map(f => ({ coords: [f.from.coord, f.to.coord], value: f.value })),
                                lineStyle: { color: flowColor, width: 1.5, curveness, opacity: 0.6 },
                                effect: showEffect ? { show: true, period: 4, trailLength: 0.2, symbol: 'arrow', symbolSize: 6, color: flowColor } : undefined,
                            },
                            {
                                type: 'effectScatter', coordinateSystem: 'geo',
                                data: Array.from(endpoints.entries()).map(([name, coord]) => ({ name, value: coord })),
                                symbolSize: 6,
                                rippleEffect: { brushType: 'stroke', scale: 3 },
                                itemStyle: { color: flowColor },
                                label: { show: true, formatter: '{b}', position: 'right', color: t.textPrimary, fontSize: 10 },
                            },
                        ],
                    };
                } else {
                    // Default: region fill map
                    mapOption = {
                        ...themeOptions, ...chartMotionOption,
                        title: baseTitle,
                        visualMap: {
                            min: Number.isFinite(minValue) ? minValue : 0,
                            max: Number.isFinite(maxValue) ? maxValue : 100,
                            text: ['高', '低'], left: 6, bottom: 8, itemWidth: 10, itemHeight: 60,
                            textStyle: { color: t.textSecondary, fontSize: 10 },
                            inRange: { color: ['#93c5fd', '#3b82f6', '#1d4ed8'] },
                        },
                        tooltip: { trigger: 'item', formatter: '{b}: {c}' },
                        series: [{
                            type: 'map', map: mapName, roam: true,
                            label: { show: true, color: t.textPrimary, fontSize: 10 },
                            emphasis: { label: { color: t.textPrimary } },
                            data: regions.map((item) => ({
                                name: String(item.name ?? ''),
                                value: Number(item.value ?? 0),
                                code: resolveRegionCode(item),
                            })),
                        }],
                    };
                }

                return (
                    <div style={{ width: '100%', height: '100%', position: 'relative' }}>
                        <EChart
                            style={{ width: '100%', height: '100%' }}
                            option={mapOption}
                            onEvents={{ click: mapClickHandler }}
                        />
                    </div>
                );
            }

            return (
                <div style={{ width: '100%', height: '100%', display: 'flex', flexDirection: 'column', gap: 10 }}>
                    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                        <div style={{ color: t.textPrimary, fontSize: 14, fontWeight: 600 }}>{title}</div>
                        {mapDrillRegion ? (
                            <button
                                type="button"
                                onClick={() => setMapDrillRegion(null)}
                                style={{
                                    border: '1px solid rgba(148,163,184,0.4)',
                                    background: 'rgba(15,23,42,0.45)',
                                    color: t.textPrimary,
                                    borderRadius: 4,
                                    fontSize: 11,
                                    cursor: 'pointer',
                                    padding: '2px 8px',
                                }}
                            >
                                返回上级
                            </button>
                        ) : null}
                    </div>
                    <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(120px, 1fr))', gap: 8 }}>
                        {listRows.map((item, index) => {
                            const value = Number(item.value ?? 0);
                            const ratio = maxValue <= 0 ? 0 : Math.max(0, Math.min(1, value / maxValue));
                            const colorAlpha = 0.18 + ratio * 0.46;
                            const name = String(item.name ?? `区域${index + 1}`);
                            const hasChild = getChildren(item).length > 0;
                            return (
                                <button
                                    key={`${name}_${index}`}
                                    type="button"
                                    onClick={() => {
                                        if (canRegionDrill && hasChild) {
                                            setMapDrillRegion(name);
                                        }
                                        const variableKey = String(c.regionVariableKey ?? '').trim();
                                        if (variableKey) {
                                            runtime.setVariable(variableKey, name, `map-grid:${component.id}`);
                                        }
                                        const code = resolveRegionCode(item);
                                        if (regionCodeVariableKey && code) {
                                            runtime.setVariable(regionCodeVariableKey, code, `map-grid:${component.id}`);
                                        }
                                    }}
                                    style={{
                                        border: '1px solid rgba(148,163,184,0.25)',
                                        borderRadius: 8,
                                        background: `rgba(59,130,246,${colorAlpha.toFixed(3)})`,
                                        color: t.textPrimary,
                                        textAlign: 'left',
                                        padding: '8px 10px',
                                        minHeight: 56,
                                        cursor: 'pointer',
                                    }}
                                >
                                    <div style={{ fontSize: 12, fontWeight: 600 }}>{name}</div>
                                    <div style={{ marginTop: 4, fontSize: 12, color: t.textSecondary }}>
                                        {Number.isFinite(value) ? value.toLocaleString('zh-CN') : '-'}
                                    </div>
                                </button>
                            );
                        })}
                    </div>
                </div>
            );
        }

        case 'heatmap-chart': {
            /* eslint-disable @typescript-eslint/no-explicit-any */
            const heatmapRows = Array.isArray(c.rows) ? (c.rows as Array<Record<string, any>>) : [];
            const xCatSet = new Set<string>();
            const yCatSet = new Set<string>();
            const heatmapParsed: Array<[string, string, number]> = [];
            for (const row of heatmapRows) {
                const vals = Array.isArray(row) ? row : Object.values(row);
                const x = String(vals[0] ?? '');
                const y = String(vals[1] ?? '');
                const v = Number(vals[2] ?? 0);
                xCatSet.add(x);
                yCatSet.add(y);
                heatmapParsed.push([x, y, v]);
            }
            const xCategories = Array.from(xCatSet);
            const yCategories = Array.from(yCatSet);
            const heatmapData = heatmapParsed.map(([x, y, v]) => [xCategories.indexOf(x), yCategories.indexOf(y), v]);
            return renderEChartWithHandles({
                ...themeOptions,
                ...chartMotionOption,
                title: chartTitleLayout.titleOption,
                tooltip: { ...themeOptions.tooltip, position: 'top' },
                xAxis: {
                    type: 'category',
                    data: xCategories,
                    axisLine: { lineStyle: { color: t.echarts.axisLineColor } },
                    axisLabel: { color: axisLabelColor, fontSize: axisFontSize, rotate: xAxisLabelRotate, hideOverlap: true },
                },
                yAxis: {
                    type: 'category',
                    data: yCategories,
                    axisLine: { lineStyle: { color: t.echarts.axisLineColor } },
                    axisLabel: { color: axisLabelColor, fontSize: axisFontSize },
                },
                visualMap: {
                    min: (c.visualMapMin as number) ?? 0,
                    max: (c.visualMapMax as number) ?? 100,
                    inRange: { color: (c.visualMapColors as string[]) || ['#313695', '#4575b4', '#74add1', '#abd9e9', '#fee090', '#fdae61', '#f46d43', '#d73027', '#a50026'] },
                    calculable: true,
                    textStyle: { color: t.textSecondary },
                },
                series: [{
                    type: 'heatmap',
                    data: heatmapData,
                    itemStyle: {
                        // 单元格圆角 + 2px 间隙让热力图更像"卡片阵列"而不是一整块色块
                        borderRadius: 4,
                        borderColor: t.echarts.tooltipBg || 'rgba(255,255,255,0.85)',
                        borderWidth: 1,
                    },
                    emphasis: { itemStyle: { shadowBlur: 12, shadowColor: 'rgba(0,0,0,0.2)' } },
                    label: { show: axisSeriesLabelShow, color: t.textPrimary, fontSize: seriesLabelFontSize },
                }],
                grid: axisGrid,
            }, echartsClickHandler);
            /* eslint-enable @typescript-eslint/no-explicit-any */
        }

        case 'graph-chart': {
            /* eslint-disable @typescript-eslint/no-explicit-any */
            let graphNodes: Array<{ name: string; symbolSize?: number; category?: number }> = [];
            let graphLinks: Array<{ source: string; target: string; value?: number }> = [];
            // Try JSON structure first
            if (typeof c.data === 'string') {
                try {
                    const parsed = JSON.parse(c.data as string);
                    if (parsed.nodes) graphNodes = parsed.nodes;
                    if (parsed.links) graphLinks = parsed.links;
                } catch { /* fall through to rows */ }
            } else if (c.data && typeof c.data === 'object' && !Array.isArray(c.data)) {
                const d = c.data as any;
                if (d.nodes) graphNodes = d.nodes;
                if (d.links) graphLinks = d.links;
            }
            if (graphNodes.length === 0) {
                const graphRows = Array.isArray(c.rows) ? (c.rows as Array<Record<string, any>>) : [];
                const nodeSet = new Set<string>();
                for (const row of graphRows) {
                    const vals = Array.isArray(row) ? row : Object.values(row);
                    const source = String(vals[0] ?? '');
                    const target = String(vals[1] ?? '');
                    const value = vals[2] != null ? Number(vals[2]) : undefined;
                    if (source && target) {
                        nodeSet.add(source);
                        nodeSet.add(target);
                        graphLinks.push({ source, target, value });
                    }
                }
                graphNodes = Array.from(nodeSet).map(name => ({ name }));
            }
            return renderEChartWithHandles({
                ...themeOptions,
                ...chartMotionOption,
                title: chartTitleLayout.titleOption,
                tooltip: { ...themeOptions.tooltip },
                series: [{
                    type: 'graph',
                    layout: (c.layout as string) || 'force',
                    roam: c.roam !== false,
                    draggable: c.draggable !== false,
                    data: graphNodes.map(n => ({
                        ...n,
                        symbolSize: n.symbolSize || (c.symbolSize as number) || 20,
                        label: { show: true, color: t.textPrimary, fontSize: seriesLabelFontSize },
                    })),
                    links: graphLinks,
                    force: { repulsion: (c.repulsion as number) || 200, edgeLength: [50, 200] },
                    emphasis: { focus: 'adjacency' },
                    lineStyle: { curveness: 0.3, color: t.echarts.splitLineColor },
                    label: { show: true, position: 'right', color: t.textPrimary, fontSize: seriesLabelFontSize },
                }],
            }, echartsClickHandler);
            /* eslint-enable @typescript-eslint/no-explicit-any */
        }

        case 'parallel-chart': {
            /* eslint-disable @typescript-eslint/no-explicit-any */
            const parallelRows = Array.isArray(c.rows) ? (c.rows as Array<Record<string, any>>) : [];
            let parallelDims: string[] = [];
            let parallelData: number[][] = [];
            if (parallelRows.length > 0) {
                const firstRow = parallelRows[0];
                const firstVals = Array.isArray(firstRow) ? firstRow : Object.values(firstRow);
                // Check if first row looks like dimension names (all strings)
                if (firstVals.every((v: any) => typeof v === 'string' && isNaN(Number(v)))) {
                    parallelDims = firstVals.map(String);
                    parallelData = parallelRows.slice(1).map(row => {
                        const vals = Array.isArray(row) ? row : Object.values(row);
                        return vals.map(Number);
                    });
                } else {
                    // Use column keys or indices as dimension names
                    if (Array.isArray(c.cols) && (c.cols as any[]).length > 0) {
                        parallelDims = (c.cols as any[]).map((col: any) => String(col.name ?? col.label ?? col));
                    } else {
                        parallelDims = firstVals.map((_: any, i: number) => `Dim ${i + 1}`);
                    }
                    parallelData = parallelRows.map(row => {
                        const vals = Array.isArray(row) ? row : Object.values(row);
                        return vals.map(Number);
                    });
                }
            }
            return renderEChartWithHandles({
                ...themeOptions,
                ...chartMotionOption,
                title: chartTitleLayout.titleOption,
                parallelAxis: parallelDims.map((name, i) => ({
                    dim: i,
                    name,
                    nameTextStyle: { color: t.textPrimary, fontSize: axisFontSize },
                    axisLabel: { color: axisLabelColor, fontSize: axisFontSize },
                    axisLine: { lineStyle: { color: t.echarts.axisLineColor } },
                })),
                series: [{
                    type: 'parallel',
                    data: parallelData,
                    lineStyle: { opacity: (c.lineOpacity as number) ?? 0.5 },
                    smooth: !!c.smooth,
                }],
            }, echartsClickHandler);
            /* eslint-enable @typescript-eslint/no-explicit-any */
        }

        case 'calendar-chart': {
            /* eslint-disable @typescript-eslint/no-explicit-any */
            const calendarRows = Array.isArray(c.rows) ? (c.rows as Array<Record<string, any>>) : [];
            const calendarData: Array<[string, number]> = [];
            let calendarMax = 0;
            for (const row of calendarRows) {
                const vals = Array.isArray(row) ? row : Object.values(row);
                const date = String(vals[0] ?? '');
                const value = Number(vals[1] ?? 0);
                calendarData.push([date, value]);
                if (value > calendarMax) calendarMax = value;
            }
            return renderEChartWithHandles({
                ...themeOptions,
                ...chartMotionOption,
                title: chartTitleLayout.titleOption,
                tooltip: { ...themeOptions.tooltip, formatter: (params: any) => `${params.value?.[0]}: ${params.value?.[1]}` },
                visualMap: {
                    min: 0,
                    max: calendarMax || 100,
                    inRange: { color: (c.visualMapColors as string[]) || ['#ebedf0', '#9be9a8', '#40c463', '#30a14e', '#216e39'] },
                    show: false,
                },
                calendar: {
                    range: (c.yearRange as string) || new Date().getFullYear().toString(),
                    cellSize: [(c.cellSize as number) || 16, (c.cellSize as number) || 16],
                    orient: (c.orient as string) || 'horizontal',
                    itemStyle: { borderColor: t.echarts.splitLineColor },
                    dayLabel: { color: axisLabelColor, fontSize: axisFontSize },
                    monthLabel: { color: axisLabelColor, fontSize: axisFontSize },
                    yearLabel: { color: t.textPrimary },
                },
                series: [{
                    type: 'heatmap',
                    coordinateSystem: 'calendar',
                    data: calendarData,
                }],
            }, echartsClickHandler);
            /* eslint-enable @typescript-eslint/no-explicit-any */
        }

        case 'themeRiver-chart': {
            /* eslint-disable @typescript-eslint/no-explicit-any */
            const riverRows = Array.isArray(c.rows) ? (c.rows as Array<Record<string, any>>) : [];
            const riverData: Array<[string, number, string]> = [];
            for (const row of riverRows) {
                const vals = Array.isArray(row) ? row : Object.values(row);
                riverData.push([String(vals[0] ?? ''), Number(vals[1] ?? 0), String(vals[2] ?? '')]);
            }
            return renderEChartWithHandles({
                ...themeOptions,
                ...chartMotionOption,
                title: chartTitleLayout.titleOption,
                tooltip: { ...themeOptions.tooltip, trigger: 'axis' },
                singleAxis: {
                    type: 'time',
                    axisTick: {},
                    axisLabel: { color: axisLabelColor, fontSize: axisFontSize },
                    axisLine: { lineStyle: { color: t.echarts.axisLineColor } },
                },
                series: [{
                    type: 'themeRiver',
                    data: riverData,
                    emphasis: { focus: 'series' },
                    label: { color: t.textPrimary, fontSize: seriesLabelFontSize },
                }],
            }, echartsClickHandler);
            /* eslint-enable @typescript-eslint/no-explicit-any */
        }

        default:
            return null;
    }
}
