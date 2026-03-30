import type { ScreenConfig, ScreenPage } from './types';

function clampPageIndex(pages: ScreenPage[] | undefined, pageIndex: number): number {
    if (!pages || pages.length === 0) {
        return 0;
    }
    return Math.max(0, Math.min(pageIndex, pages.length - 1));
}

export function resolveScreenPages(config: Pick<ScreenConfig, 'pages' | 'components'>): ScreenPage[] {
    if (config.pages && config.pages.length > 0) {
        return config.pages;
    }
    return [{
        id: '__default__',
        name: '页面 1',
        components: config.components || [],
    }];
}

export function materializeScreenPage(config: ScreenConfig, pageIndex: number): ScreenConfig {
    if (!config.pages || config.pages.length === 0) {
        return config;
    }
    const safeIndex = clampPageIndex(config.pages, pageIndex);
    const page = config.pages[safeIndex];
    if (!page) {
        return config;
    }
    const nextBackgroundColor = page.backgroundColor ?? config.backgroundColor;
    const nextBackgroundImage = page.backgroundImage ?? config.backgroundImage;
    if (
        config.components === page.components
        && config.backgroundColor === nextBackgroundColor
        && config.backgroundImage === nextBackgroundImage
    ) {
        return config;
    }
    return {
        ...config,
        components: page.components || [],
        backgroundColor: nextBackgroundColor,
        backgroundImage: nextBackgroundImage,
    };
}

export function commitScreenPageDraft(config: ScreenConfig, pageIndex: number): ScreenConfig {
    if (!config.pages || config.pages.length === 0) {
        return config;
    }
    const safeIndex = clampPageIndex(config.pages, pageIndex);
    let changed = false;
    const nextPages = config.pages.map((page, index) => {
        if (index !== safeIndex) {
            return page;
        }
        if (
            page.components === config.components
            && page.backgroundColor === config.backgroundColor
            && page.backgroundImage === config.backgroundImage
        ) {
            return page;
        }
        changed = true;
        return {
            ...page,
            components: config.components,
            backgroundColor: config.backgroundColor,
            backgroundImage: config.backgroundImage,
        };
    });
    if (!changed) {
        return config;
    }
    return {
        ...config,
        pages: nextPages,
    };
}

export function switchScreenPage(config: ScreenConfig, fromIndex: number, toIndex: number): ScreenConfig {
    const committed = commitScreenPageDraft(config, fromIndex);
    return materializeScreenPage(committed, toIndex);
}
