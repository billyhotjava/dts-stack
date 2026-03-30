import { FinanceStatusGrid, createFinanceRendererPlugin } from './financeShared';

export default function createPlugin(pluginId = 'finance-kit', componentId = 'status-grid', version = '1.0.0') {
    return createFinanceRendererPlugin({
        pluginId,
        componentId,
        version,
        name: '财务状态矩阵',
        baseType: 'container',
        render: FinanceStatusGrid,
    });
}
