import { FinanceFilterStrip, createFinanceRendererPlugin } from './financeShared';

export default function createPlugin(pluginId = 'finance-kit', componentId = 'filter-strip', version = '1.0.0') {
    return createFinanceRendererPlugin({
        pluginId,
        componentId,
        version,
        name: '财务筛选条',
        baseType: 'container',
        render: FinanceFilterStrip,
    });
}
