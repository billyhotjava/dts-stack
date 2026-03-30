import type { MarketplaceCatalogItem } from '../../api/analyticsApi';

export type MarketplaceEmptyState = {
    title: string;
    description: string;
};

export function filterMarketplaceItems(items: MarketplaceCatalogItem[], search: string): MarketplaceCatalogItem[] {
    const normalized = search.trim().toLowerCase();
    if (!normalized) {
        return items;
    }
    return items.filter((item) => {
        const haystack = [
            item.name || '',
            item.description || '',
            item.category || '',
            ...(item.tags || []),
        ].join(' ').toLowerCase();
        return haystack.includes(normalized);
    });
}

export function resolveMarketplaceEmptyState(params: {
    hasError: boolean;
    hasRemoteItems: boolean;
    hasFilteredItems: boolean;
    activeTab: 'components' | 'templates';
    search: string;
}): MarketplaceEmptyState {
    if (params.hasError) {
        return {
            title: '市场加载失败',
            description: '请检查分析服务状态，或稍后重试。',
        };
    }
    if (!params.hasRemoteItems) {
        return {
            title: params.activeTab === 'components' ? '暂无可安装组件' : '暂无可安装模板',
            description: params.activeTab === 'components'
                ? '后端市场已接通，但当前还没有符合条件的组件发布到市场。'
                : '当前还没有符合条件的模板资产发布到市场。',
        };
    }
    if (!params.hasFilteredItems) {
        return {
            title: '没有匹配结果',
            description: params.search.trim()
                ? `没有找到与“${params.search.trim()}”匹配的市场内容。`
                : '请调整筛选条件后重试。',
        };
    }
    return {
        title: '',
        description: '',
    };
}

export function resolveMarketplaceInstallMessage(
    kind: 'components' | 'templates',
    itemName: string,
    success: boolean,
): string {
    if (success) {
        return kind === 'components'
            ? `组件“${itemName}”已安装，可在组件库中继续使用。`
            : `模板“${itemName}”已安装，可在模板资产中心继续维护。`;
    }
    return kind === 'components'
        ? `组件“${itemName}”安装失败，请稍后重试。`
        : `模板“${itemName}”安装失败，请稍后重试。`;
}
