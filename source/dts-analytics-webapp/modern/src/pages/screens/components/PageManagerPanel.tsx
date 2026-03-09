/**
 * PageManagerPanel — 多页管理面板
 *
 * Displayed at the bottom of the designer canvas.
 * Allows creating, deleting, duplicating, reordering, and renaming pages.
 */
import { useState, useCallback, useRef, useEffect } from 'react';
import type { ScreenPage } from '../types';

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
        <div className="screen-page-manager">
            {pages.map((page, idx) => {
                const isActive = idx === currentPageIndex;
                const isRenaming = idx === renamingIndex;

                return (
                    <div
                        key={page.id}
                        className={`screen-page-manager__tab ${isActive ? 'is-active' : ''}`}
                        onClick={() => onSwitchPage(idx)}
                        onContextMenu={(e) => handleContextMenu(e, idx)}
                        onDoubleClick={() => handleStartRename(idx)}
                    >
                        <span className="screen-page-manager__index">{idx + 1}</span>
                        {isRenaming ? (
                            <input
                                ref={renameInputRef}
                                className="screen-page-manager__rename"
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
                            <span className="screen-page-manager__name">{page.name}</span>
                        )}

                        {contextMenuIndex === idx && (
                            <div className="screen-page-manager__menu" onClick={(e) => e.stopPropagation()}>
                                <button
                                    type="button"
                                    className="screen-page-manager__menu-item"
                                    onClick={() => { handleStartRename(idx); }}
                                >
                                    重命名
                                </button>
                                <button
                                    type="button"
                                    className="screen-page-manager__menu-item"
                                    onClick={() => { onDuplicatePage(idx); setContextMenuIndex(null); }}
                                >
                                    复制页面
                                </button>
                                {idx > 0 && (
                                    <button
                                        type="button"
                                        className="screen-page-manager__menu-item"
                                        onClick={() => { onMovePage(idx, idx - 1); setContextMenuIndex(null); }}
                                    >
                                        前移
                                    </button>
                                )}
                                {idx < pages.length - 1 && (
                                    <button
                                        type="button"
                                        className="screen-page-manager__menu-item"
                                        onClick={() => { onMovePage(idx, idx + 1); setContextMenuIndex(null); }}
                                    >
                                        后移
                                    </button>
                                )}
                                {pages.length > 1 && (
                                    <button
                                        type="button"
                                        className="screen-page-manager__menu-item is-danger"
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

            <button type="button" className="screen-page-manager__add" onClick={onAddPage} title="添加页面">
                +
            </button>

            <span className="screen-page-manager__count">
                {pages.length} 页
            </span>
        </div>
    );
}
