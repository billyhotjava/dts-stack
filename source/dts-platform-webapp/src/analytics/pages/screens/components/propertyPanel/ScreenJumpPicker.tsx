// @ts-nocheck — migrated from analytics-webapp, pending unused-import cleanup
import { useCallback, useEffect, useState } from 'react';
import type { ScreenListItem } from '../../../../api/analyticsApi';
import {
    SCREEN_PREVIEW_URL_RE,
    SCREEN_REF_PREFIX,
    buildScreenJumpUrl,
    extractLegacyScreenRefName,
    extractScreenIdFromJumpUrl,
    fetchScreenList,
} from './helpers';

export function ScreenJumpPicker({ value, onChange }: { value: string; onChange: (url: string) => void }) {
    // "Screen" mode covers both the canonical id-based URL and the legacy
    // screen-ref:{name}|... form (kept for back-compat reading only).
    const isScreenJump = !value
        || SCREEN_PREVIEW_URL_RE.test(value)
        || value.startsWith(SCREEN_REF_PREFIX);
    const [mode, setMode] = useState<'screen' | 'custom'>(isScreenJump ? 'screen' : 'custom');
    const [screens, setScreens] = useState<ScreenListItem[]>([]);
    const [loading, setLoading] = useState(false);
    const [search, setSearch] = useState('');
    const [open, setOpen] = useState(false);

    // Resolve display name strictly by id. listScreens is the source of truth.
    // Falls back to legacy `screen-ref:{name}` only if the id can't be resolved
    // (e.g. v2 instance JSON imported template not yet re-saved by user).
    const selectedScreenId = extractScreenIdFromJumpUrl(value);
    const selectedScreen = selectedScreenId
        ? screens.find((s) => String(s.id) === selectedScreenId)
        : undefined;
    const selectedName = selectedScreen?.name
        ?? (selectedScreenId ? `大屏 #${selectedScreenId}` : extractLegacyScreenRefName(value));

    const loadScreens = useCallback(async () => {
        if (screens.length > 0) return;
        setLoading(true);
        try {
            const list = await fetchScreenList();
            setScreens(list);
        } catch { /* ignore */ }
        setLoading(false);
    }, [screens.length]);

    // If the value already references a screen by id, eagerly load the list
    // so the dropdown button can show the screen's actual name (not "大屏 #42").
    useEffect(() => {
        if (selectedScreenId && screens.length === 0 && !loading) {
            void loadScreens();
        }
    }, [selectedScreenId, screens.length, loading, loadScreens]);

    const filtered = screens.filter(s => !search || (s.name || '').toLowerCase().includes(search.toLowerCase()));

    const inputCls = 'property-input flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand';

    return (
        <>
            <div className="property-row flex items-center mb-3">
                <label className="property-label w-20 text-xs text-text-secondary">跳转目标</label>
                <div className="flex gap-3 flex-1">
                    <label className="flex items-center gap-1 text-xs cursor-pointer">
                        <input type="radio" checked={mode === 'screen'} onChange={() => setMode('screen')} className="accent-brand" />
                        选择大屏
                    </label>
                    <label className="flex items-center gap-1 text-xs cursor-pointer">
                        <input type="radio" checked={mode === 'custom'} onChange={() => setMode('custom')} className="accent-brand" />
                        自定义URL
                    </label>
                </div>
            </div>

            {mode === 'screen' ? (
                <div className="property-row flex items-start mb-3">
                    <label className="property-label w-20 text-xs text-text-secondary pt-1.5">目标大屏</label>
                    <div className="flex-1 relative">
                        <button
                            type="button"
                            className={inputCls + ' w-full text-left cursor-pointer flex items-center justify-between'}
                            onClick={() => { setOpen(!open); if (!open) void loadScreens(); }}
                        >
                            <span className={selectedName ? 'text-text-primary' : 'text-text-tertiary'}>
                                {selectedName || '点击选择大屏...'}
                            </span>
                            <span className="text-text-tertiary">{open ? '▲' : '▼'}</span>
                        </button>

                        {open && (
                            <div className="absolute top-full left-0 right-0 z-[999] max-h-60 overflow-y-auto border border-border-default rounded-md bg-surface-card shadow-lg mt-1">
                                <div className="px-2 py-1.5 border-b border-border-default">
                                    <input
                                        type="text"
                                        className={inputCls + ' w-full'}
                                        placeholder="搜索大屏名称..."
                                        value={search}
                                        onChange={(e) => setSearch(e.target.value)}
                                        autoFocus
                                    />
                                </div>
                                {loading ? (
                                    <div className="px-3.5 py-3 text-xs text-text-tertiary">加载中...</div>
                                ) : filtered.length === 0 ? (
                                    <div className="px-3.5 py-3 text-xs text-text-tertiary">
                                        {search ? '无匹配结果' : '暂无大屏'}
                                    </div>
                                ) : (
                                    filtered.map(s => {
                                        const isSelected = selectedScreenId != null && String(s.id) === selectedScreenId;
                                        const isPublished = s.publishedVersionNo != null && s.publishedVersionNo > 0;
                                        return (
                                            <div
                                                key={String(s.id)}
                                                onClick={() => { onChange(buildScreenJumpUrl(s)); setOpen(false); setSearch(''); }}
                                                className={`flex items-center justify-between gap-2 px-3.5 py-2 cursor-pointer text-xs hover:bg-brand/[0.06] ${isSelected ? 'bg-brand/[0.08]' : ''}`}
                                            >
                                                <span className={`flex-1 min-w-0 truncate ${isSelected ? 'font-semibold' : ''}`}>
                                                    {isSelected ? '✓ ' : ''}{s.name || `大屏 #${s.id}`}
                                                </span>
                                                <span className={`flex-shrink-0 text-[10px] px-1.5 py-0.5 rounded ${isPublished ? 'bg-emerald-500/10 text-emerald-500' : 'bg-slate-400/10 text-text-tertiary'}`}>
                                                    {isPublished ? '已发布' : '草稿'}
                                                </span>
                                            </div>
                                        );
                                    })
                                )}
                            </div>
                        )}
                    </div>
                </div>
            ) : (
                <div className="property-row flex items-center mb-3">
                    <label className="property-label w-20 text-xs text-text-secondary">跳转链接模板</label>
                    <input
                        type="text"
                        className={inputCls + ' flex-1'}
                        value={value}
                        onChange={(e) => onChange(e.target.value)}
                        placeholder="https://host/path?project={{name}}"
                    />
                </div>
            )}
        </>
    );
}
