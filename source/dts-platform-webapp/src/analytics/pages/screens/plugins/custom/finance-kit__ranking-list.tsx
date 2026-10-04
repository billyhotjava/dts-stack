import { FinanceRankingList, createFinanceRendererPlugin } from './financeShared';

export default function createPlugin(pluginId = 'finance-kit', componentId = 'ranking-list', version = '1.0.0') {
    return createFinanceRendererPlugin({
        pluginId,
        componentId,
        version,
        name: '财务排行块',
        baseType: 'table',
        render: FinanceRankingList,
    });
}
