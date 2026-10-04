import { FinanceHeaderBar, createFinanceRendererPlugin } from './financeShared';

export default function createPlugin(pluginId = 'finance-kit', componentId = 'header-bar', version = '1.0.0') {
    return createFinanceRendererPlugin({
        pluginId,
        componentId,
        version,
        name: '财务标题栏',
        baseType: 'container',
        render: FinanceHeaderBar,
    });
}
