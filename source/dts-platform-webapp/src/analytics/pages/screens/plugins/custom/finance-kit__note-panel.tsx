import { FinanceNotePanel, createFinanceRendererPlugin } from './financeShared';

export default function createPlugin(pluginId = 'finance-kit', componentId = 'note-panel', version = '1.0.0') {
    return createFinanceRendererPlugin({
        pluginId,
        componentId,
        version,
        name: '财务说明块',
        baseType: 'markdown-text',
        render: FinanceNotePanel,
    });
}
