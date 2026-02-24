/**
 * PageManagerPanel — 多页管理面板
 *
 * Displayed at the bottom of the designer canvas.
 * Allows creating, deleting, duplicating, reordering, and renaming pages.
 */
import { useState, useCallback, useRef, useEffect } from 'react';
import type { ScreenPage, ScreenComponent } from '../types';

interface PageManagerPanelProps {
    pages: ScreenPage[];
    currentPageIndex: number;
    onSwitchPage: (index: number) => void;
    onAddPage: () => void;
    onDeletePage: (index: number) => void;
    onDuplicatePage: (index: number) => void;
    onRenamePage: (index: number, name: string) => void;
    onMovePage: (fromIndex: number, toIndex: number) => void;
}

const STYLES = {
    container: {
        display: 'flex',
        alignItems: 'center',
        gap: 4,
        padding: '6px 12px',
        borderTop: '1px solid rgba(148,163,184,0.15)',
        background: 'var(--color-bg-elevated, rgba(15,23,42,0.95))',
        overflowX: 'auto',
        minHeight: 40,
    } as React.CSSProperties,
    tab: {
        display: 'flex',
        alignItems: 'center',
        gap: 4,
        padding: '4px 12px',
        fontSize: 12,
        borderRadius: 6,
        cursor: 'pointer',
        border: '1px solid transparent',
        background: 'rgba(148,163,184,0.06)',
        color: 'inherit',
        whiteSpace: 'nowrap',
        position: 'relative',
    } as React.CSSProperties,
    tabActive: {
        background: 'rgba(59,130,246,0.12)',
        borderColor: 'var(--color-primary, #3b82f6)',
    } as React.CSSProperties,
    addBtn: {
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        width: 28,
        height: 28,
        borderRadius: 6,
        border: '1px dashed rgba(148,163,184,0.3)',
        background: 'none',
        color: 'inherit',
        cursor: 'pointer',
        fontSize: 16,
        opacity: 0.6,
        flexShrink: 0,
    } as React.CSSProperties,
    contextMenu: {
        position: 'absolute',
        top: '100%',
        left: 0,
        marginTop: 4,
        background: 'var(--color-bg-elevated, #1e293b)',
        border: '1px solid rgba(148,163,184,0.2)',
        borderRadius: 8,
        padding: '4px 0',
        zIndex: 10001,
        minWidth: 120,
        boxShadow: '0 4px 12px rgba(0,0,0,0.3)',
    } as React.CSSProperties,
    menuItem: {
        display: 'block',
        width: '100%',
        padding: '6px 14px',
        fontSize: 12,
        border: 'none',
        background: 'none',
        color: 'inherit',
        cursor: 'pointer',
        textAlign: 'left' as const,
    } as React.CSSProperties,
    renameInput: {
        fontSize: 12,
        padding: '2px 6px',
        border: '1px solid var(--color-primary, #3b82f6)',
        borderRadius: 4,
        background: 'rgba(0,0,0,0.3)',
        color: 'inherit',
        outline: 'none',
        width: 80,
    } as React.CSSProperties,
};

export function PageManagerPanel({
    pages,
    currentPageIndex,
    onSwitchPage,
    onAddPage,
    onDeletePage,
    onDuplicatePage,
    onRenamePage,
    onMovePage,
}: PageManagerPanelProps) {
    const [contextMenuIndex, setContextMenuIndex] = useState<number | null>(null);
    const [renamingIndex, setRenamingIndex] = useState<number | null>(null);
    const [renameValue, setRenameValue] = useState('');
    const renameInputRef = useRef<HTMLInputElement>(null);

    // Close context menu on outside click
    useEffect(() => {
        if (contextMenuIndex === null) return;
        const handler = () => setContextMenuIndex(null);
        window.addEventListener('click', handler);
        return () => window.removeEventListener('click', handler);
    }, [contextMenuIndex]);

    // Focus rename input
    useEffect(() => {
        if (renamingIndex !== null && renameInputRef.current) {
            renameInputRef.current.focus();
            renameInputRef.current.select();
        }
    }, [renamingIndex]);

    const handleContextMenu = useCallback((e: React.MouseEvent, index: number) => {
        e.preventDefault();
        e.stopPropagation();
        setContextMenuIndex(index);
    }, []);

    const handleStartRename = useCallback((index: number) => {
        setRenamingIndex(index);
        setRenameValue(pages[index]?.name ?? '');
        setContextMenuIndex(null);
    }, [pages]);

    const handleFinishRename = useCallback(() => {
        if (renamingIndex !== null && renameValue.trim()) {
            onRenamePage(renamingIndex, renameValue.trim());
        }
        setRenamingIndex(null);
    }, [renamingIndex, renameValue, onRenamePage]);

    if (pages.length <= 1 && pages.length > 0) {
        // Single page — show minimal bar with add button
    }

    return (
        <div style={STYLES.container}>
            {pages.map((page, idx) => {
                const isActive = idx === currentPageIndex;
                const isRenaming = idx === renamingIndex;

                return (
                    <div
                        key={page.id}
                        style={{
                            ...STYLES.tab,
                            ...(isActive ? STYLES.tabActive : {}),
                        }}
                        onClick={() => onSwitchPage(idx)}
                        onContextMenu={(e) => handleContextMenu(e, idx)}
                        onDoubleClick={() => handleStartRename(idx)}
                    >
                        <span style={{ fontSize: 10, opacity: 0.5 }}>{idx + 1}</span>
                        {isRenaming ? (
                            <input
                                ref={renameInputRef}
                                style={STYLES.renameInput}
                                value={renameValue}
                                onChange={(e) => setRenameValue(e.target.value)}
                                onBlur={handleFinishRename}
                                onKeyDown={(e) => {
                                    if (e.key === 'Enter') handleFinishRename();
                                    if (e.key === 'Escape') setRenamingIndex(null);
                                }}
                                onClick={(e) => e.stopPropagation()}
                            />
                        ) : (
                            <span>{page.name}</span>
                        )}

                        {/* Context menu */}
                        {contextMenuIndex === idx && (
                            <div style={STYLES.contextMenu} onClick={(e) => e.stopPropagation()}>
                                <button
                                    type="button"
                                    style={STYLES.menuItem}
                                    onClick={() => { handleStartRename(idx); }}
                                >
                                    重命名
                                </button>
                                <button
                                    type="button"
                                    style={STYLES.menuItem}
                                    onClick={() => { onDuplicatePage(idx); setContextMenuIndex(null); }}
                                >
                                    复制页面
                                </button>
                                {idx > 0 && (
                                    <button
                                        type="button"
                                        style={STYLES.menuItem}
                                        onClick={() => { onMovePage(idx, idx - 1); setContextMenuIndex(null); }}
                                    >
                                        前移
                                    </button>
                                )}
                                {idx < pages.length - 1 && (
                                    <button
                                        type="button"
                                        style={STYLES.menuItem}
                                        onClick={() => { onMovePage(idx, idx + 1); setContextMenuIndex(null); }}
                                    >
                                        后移
                                    </button>
                                )}
                                {pages.length > 1 && (
                                    <button
                                        type="button"
                                        style={{ ...STYLES.menuItem, color: '#ef4444' }}
                                        onClick={() => {
                                            if (window.confirm(`确认删除页面"${page.name}"？`)) {
                                                onDeletePage(idx);
                                            }
                                            setContextMenuIndex(null);
                                        }}
                                    >
                                        删除页面
                                    </button>
                                )}
                            </div>
                        )}
                    </div>
                );
            })}

            {/* Add page button */}
            <button type="button" style={STYLES.addBtn} onClick={onAddPage} title="添加页面">
                +
            </button>

            {/* Page count */}
            <span style={{ fontSize: 10, opacity: 0.4, marginLeft: 'auto', flexShrink: 0 }}>
                {pages.length} 页
            </span>
        </div>
    );
}
