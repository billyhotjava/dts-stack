import type { ReactNode } from 'react';
import { renderAxisChart } from './axisCharts';
import { renderPieChart } from './pieCharts';
import { renderHierarchyChart } from './hierarchyCharts';
import { renderSpecialChart } from './specialCharts';
import { renderExtendedChart } from './extendedCharts';
import type { EChartsRendererProps } from './types';

export type { EChartsRendererProps } from './types';

export function renderECharts(props: EChartsRendererProps): ReactNode | null {
    const { type } = props;
    return (
        renderAxisChart(type, props)
        ?? renderPieChart(type, props)
        ?? renderHierarchyChart(type, props)
        ?? renderSpecialChart(type, props)
        ?? renderExtendedChart(type, props)
    );
}
