import assert from 'node:assert/strict';
import test from 'node:test';
import {
    filterMarketplaceItems,
    resolveMarketplaceEmptyState,
    resolveMarketplaceInstallMessage,
} from './ScreenMarketplacePage.helpers';

test('filterMarketplaceItems matches name, description and tags', () => {
    const items = [
        { id: '1', name: '高级 KPI 卡片', description: '统计插件包中的 KPI 卡片。', tags: ['kpi', 'metric'] },
        { id: '2', name: '零售运营模板', description: '关注门店与库存。', tags: ['retail', 'ops'] },
    ];

    assert.equal(filterMarketplaceItems(items, 'kpi').length, 1);
    assert.equal(filterMarketplaceItems(items, '门店').length, 1);
    assert.equal(filterMarketplaceItems(items, 'ops').length, 1);
    assert.equal(filterMarketplaceItems(items, '不存在').length, 0);
});

test('resolveMarketplaceEmptyState distinguishes remote empty, filter empty and load error', () => {
    assert.deepEqual(
        resolveMarketplaceEmptyState({
            hasError: true,
            hasRemoteItems: false,
            hasFilteredItems: false,
            activeTab: 'components',
            search: '',
        }),
        {
            title: '市场加载失败',
            description: '请检查分析服务状态，或稍后重试。',
        },
    );

    assert.deepEqual(
        resolveMarketplaceEmptyState({
            hasError: false,
            hasRemoteItems: false,
            hasFilteredItems: false,
            activeTab: 'templates',
            search: '',
        }),
        {
            title: '暂无可安装模板',
            description: '当前还没有符合条件的模板资产发布到市场。',
        },
    );

    assert.deepEqual(
        resolveMarketplaceEmptyState({
            hasError: false,
            hasRemoteItems: true,
            hasFilteredItems: false,
            activeTab: 'components',
            search: 'retail',
        }),
        {
            title: '没有匹配结果',
            description: '没有找到与“retail”匹配的市场内容。',
        },
    );
});

test('resolveMarketplaceInstallMessage returns tab-specific success and failure copy', () => {
    assert.equal(
        resolveMarketplaceInstallMessage('components', '高级 KPI 卡片', true),
        '组件“高级 KPI 卡片”已安装，可在组件库中继续使用。',
    );
    assert.equal(
        resolveMarketplaceInstallMessage('templates', '零售运营模板', false),
        '模板“零售运营模板”安装失败，请稍后重试。',
    );
});
