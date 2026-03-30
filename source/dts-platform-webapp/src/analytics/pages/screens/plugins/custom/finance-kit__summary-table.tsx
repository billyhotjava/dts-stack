import { FinanceSummaryTable, createFinanceRendererPlugin } from './financeShared';

export default function createPlugin(pluginId = 'finance-kit', componentId = 'summary-table', version = '1.0.0') {
    return createFinanceRendererPlugin({
        pluginId,
        componentId,
        version,
        name: '财务汇总表',
        baseType: 'table',
        render: FinanceSummaryTable,
    });
}
