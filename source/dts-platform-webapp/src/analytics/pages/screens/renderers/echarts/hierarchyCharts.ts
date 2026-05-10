import type { ReactNode } from 'react';
import type { EChartsRendererProps } from './types';

export function renderHierarchyChart(type: string, props: EChartsRendererProps): ReactNode | null {
    const {
        c, t,
        renderEChartWithHandles,
        themeOptions, chartMotionOption, chartTitleLayout,
        seriesLabelFontSize,
        echartsClickHandler,
    } = props;

    switch (type) {
        case 'treemap-chart':
            return renderEChartWithHandles({
                ...themeOptions,
                ...chartMotionOption,
                title: chartTitleLayout.titleOption,
                tooltip: { formatter: '{b}: {c}' },
                series: [{
                    type: 'treemap',
                    data: c.data as Array<{ name: string; value?: number; children?: unknown[] }>,
                    leafDepth: 1,
                    roam: false,
                    breadcrumb: { show: true, itemStyle: { textStyle: { color: t.textPrimary } } },
                    label: { show: true, color: '#fff', fontSize: seriesLabelFontSize || 12 },
                    upperLabel: { show: true, height: 20, color: '#fff', fontSize: 11 },
                    levels: [
                        { itemStyle: { borderColor: t.echarts.splitLineColor, borderWidth: 2, gapWidth: 2 } },
                        { itemStyle: { borderColor: t.echarts.splitLineColor, borderWidth: 1, gapWidth: 1 }, upperLabel: { show: true } },
                    ],
                }],
            }, echartsClickHandler);

        case 'sunburst-chart':
            return renderEChartWithHandles({
                ...themeOptions,
                ...chartMotionOption,
                title: chartTitleLayout.titleOption,
                tooltip: { trigger: 'item', formatter: '{b}: {c}' },
                series: [{
                    type: 'sunburst',
                    data: c.data as Array<{ name: string; value?: number; children?: unknown[] }>,
                    radius: ['15%', '90%'],
                    label: { show: true, color: t.textPrimary, fontSize: seriesLabelFontSize || 11, rotate: 'radial' },
                    itemStyle: { borderWidth: 1, borderColor: t.echarts.splitLineColor },
                    emphasis: { focus: 'ancestor' },
                }],
            }, echartsClickHandler);

        case 'sankey-chart': {
            const sankeyRows = Array.isArray(c.rows) ? (c.rows as Array<Record<string, unknown>>) : [];
            const sankeyNodeSet = new Set<string>();
            const sankeyLinks: Array<{ source: string; target: string; value: number }> = [];
            for (const row of sankeyRows) {
                const vals = Array.isArray(row) ? row : Object.values(row);
                const source = String(vals[0] ?? '');
                const target = String(vals[1] ?? '');
                const value = Number(vals[2] ?? 1);
                if (source && target) {
                    sankeyNodeSet.add(source);
                    sankeyNodeSet.add(target);
                    sankeyLinks.push({ source, target, value });
                }
            }
            const sankeyNodes = Array.from(sankeyNodeSet).map(name => ({ name }));
            return renderEChartWithHandles({
                ...themeOptions,
                ...chartMotionOption,
                title: chartTitleLayout.titleOption,
                tooltip: { ...themeOptions.tooltip, trigger: 'item' },
                series: [{
                    type: 'sankey',
                    data: sankeyNodes,
                    links: sankeyLinks,
                    nodeAlign: (c.nodeAlign as string) || 'justify',
                    orient: (c.orient as string) || 'horizontal',
                    draggable: c.draggable !== false,
                    lineStyle: { color: 'gradient', curveness: 0.5 },
                    emphasis: { focus: 'adjacency' },
                    label: { color: t.textPrimary, fontSize: seriesLabelFontSize },
                }],
            }, echartsClickHandler);
        }

        case 'tree-chart': {
            let treeData: Record<string, unknown> = { name: 'root', children: [] };
            if (typeof c.data === 'string') {
                try {
                    const parsed: unknown = JSON.parse(c.data);
                    if (parsed && typeof parsed === 'object' && !Array.isArray(parsed)) {
                        treeData = parsed as Record<string, unknown>;
                    }
                } catch { /* use default */ }
            } else if (c.data && typeof c.data === 'object' && !Array.isArray(c.data)) {
                treeData = c.data as Record<string, unknown>;
            }
            return renderEChartWithHandles({
                ...themeOptions,
                ...chartMotionOption,
                title: chartTitleLayout.titleOption,
                tooltip: { ...themeOptions.tooltip, trigger: 'item' },
                series: [{
                    type: 'tree',
                    data: [treeData],
                    layout: (c.layout as string) || 'orthogonal',
                    orient: (c.orient as string) || 'LR',
                    expandAndCollapse: c.expandAndCollapse !== false,
                    symbolSize: (c.symbolSize as number) || 14,
                    label: {
                        position: 'left',
                        verticalAlign: 'middle',
                        align: 'right',
                        color: t.textPrimary,
                        fontSize: seriesLabelFontSize,
                    },
                    leaves: {
                        label: {
                            position: 'right',
                            align: 'left',
                            color: t.textPrimary,
                            fontSize: seriesLabelFontSize,
                        },
                    },
                    lineStyle: { color: t.echarts.splitLineColor },
                    animationDurationUpdate: 750,
                }],
            }, echartsClickHandler);
        }

        default:
            return null;
    }
}
