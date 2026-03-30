import { FinanceKpiCard, createFinanceRendererPlugin } from './financeShared';

export default function createPlugin(pluginId = 'finance-kit', componentId = 'kpi-card', version = '1.0.0') {
    return createFinanceRendererPlugin({
        pluginId,
        componentId,
        version,
        name: '财务KPI卡',
        baseType: 'number-card',
        render: FinanceKpiCard,
    });
}
