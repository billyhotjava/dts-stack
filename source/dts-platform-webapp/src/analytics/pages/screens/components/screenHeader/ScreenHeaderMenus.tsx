import type { ChangeEvent, Dispatch, ReactNode, Ref, SetStateAction } from 'react';
import type { ThemeComponentApplyMode } from '../../screenThemes';
import type { ScreenTheme } from '../../types';
import { HeaderMenu } from './HeaderMenu';
import { ThemeSelector } from './ThemeSelector';

export type HeaderActiveMenu =
    | 'primary'
    | 'tools-view'
    | 'tools-edit'
    | 'tools-theme'
    | 'tools-io'
    | 'tools-release'
    | null;

export type PreviewDeviceMode = 'auto' | 'pc' | 'tablet' | 'mobile';

export interface ScreenHeaderPermissions {
    canRead: boolean;
    canEdit: boolean;
    canPublish: boolean;
    canManage: boolean;
    canDelete: boolean;
    isOwner: boolean;
}

type VersionAction = 'history' | 'compare';

interface ScreenHeaderMenusProps {
    menuContainerRef: Ref<HTMLDivElement>;
    activeMenu: HeaderActiveMenu;
    setActiveMenu: Dispatch<SetStateAction<HeaderActiveMenu>>;
    id?: string;
    focusMode?: boolean;
    showLibraryPanel?: boolean;
    showInspectorPanel?: boolean;
    onToggleFocusMode?: () => void;
    onToggleLibraryPanel?: () => void;
    onToggleInspectorPanel?: () => void;
    previewDeviceMode: PreviewDeviceMode;
    setPreviewDeviceMode: Dispatch<SetStateAction<PreviewDeviceMode>>;
    versionAction: VersionAction;
    setVersionAction: Dispatch<SetStateAction<VersionAction>>;
    themeApplyMode: ThemeComponentApplyMode;
    setThemeApplyMode: Dispatch<SetStateAction<ThemeComponentApplyMode>>;
    theme: ScreenTheme;
    showGrid: boolean;
    cycleWarningCount: number;
    authoringIssueCount: number;
    permissions: ScreenHeaderPermissions;
    lockedByOther: boolean;
    lockOwnerText: string;
    isPublishing: boolean;
    isSaving: boolean;
    isLoadingVersions: boolean;
    themeInputRef: Ref<HTMLInputElement>;
    executeMenuAction: (action: () => void | Promise<void>) => void;
    executeVersionAction: () => void;
    onPreview: () => void;
    onPublish: () => void | Promise<void>;
    onSave: () => void | Promise<void>;
    onThemeChange: (event: ChangeEvent<HTMLSelectElement>) => void;
    onApplyThemeToAllComponents: (mode: ThemeComponentApplyMode) => void;
    onExportThemePack: () => void;
    onImportThemePackClick: () => void;
    onThemePackFileChange: (event: ChangeEvent<HTMLInputElement>) => void | Promise<void>;
    onZoomReset: () => void;
    onZoomFit: () => void;
    onToggleGrid: () => void;
    onShortcutHelp: () => void;
    onToggleLinkageGraph: () => void;
    onOpenVariableManager: () => void;
    onOpenIssuePanel?: () => void;
    onExportPng: () => void | Promise<void>;
    onExportPdf: () => void | Promise<void>;
}

const MENU_SECTION_CLASS = 'grid gap-1.5 py-1 pb-2 border-b border-[var(--color-border)] last:border-b-0 last:pb-1';
const MENU_TITLE_CLASS = 'text-[11px] font-semibold text-[var(--color-text-secondary)] tracking-[0.04em] uppercase px-1 py-0.5';
const MENU_BUTTON_CLASS = 'header-btn flex items-center gap-1.5 px-4 py-2 border border-[var(--color-border)] rounded-md bg-[var(--color-surface)] text-[var(--color-text-primary)] text-[13px] font-medium cursor-pointer transition-all duration-200 whitespace-nowrap shrink-0 w-full justify-start py-[7px] px-2.5 hover:border-[var(--color-primary)] hover:bg-[var(--color-primary-light)] disabled:opacity-50 disabled:cursor-not-allowed';
const MENU_SELECT_CLASS = 'px-2.5 py-[7px] border border-[var(--color-border)] rounded-md bg-[var(--color-surface)] text-[var(--color-text-primary)] text-[13px] font-medium min-w-[110px] w-full focus:outline-none focus:border-[var(--color-primary)]';

function toPreviewDeviceMode(value: string): PreviewDeviceMode {
    return value === 'pc' || value === 'tablet' || value === 'mobile' ? value : 'auto';
}

function MenuSection({
    title,
    children,
}: {
    title: string;
    children: ReactNode;
}) {
    return (
        <div className={MENU_SECTION_CLASS}>
            <div className={MENU_TITLE_CLASS}>{title}</div>
            {children}
        </div>
    );
}

function MenuButton({
    active,
    children,
    className = '',
    disabled,
    onClick,
    title,
}: {
    active?: boolean;
    children: ReactNode;
    className?: string;
    disabled?: boolean;
    onClick?: () => void;
    title?: string;
}) {
    return (
        <button
            type="button"
            className={`${MENU_BUTTON_CLASS} ${active ? 'border-[var(--color-primary)] bg-[var(--color-primary-light)]' : ''} ${className}`}
            onClick={onClick}
            disabled={disabled}
            title={title}
        >
            {children}
        </button>
    );
}

function DeviceModeSelect({
    id,
    value,
    onChange,
}: {
    id: string;
    value: PreviewDeviceMode;
    onChange: (mode: PreviewDeviceMode) => void;
}) {
    return (
        <select
            id={id}
            className={MENU_SELECT_CLASS}
            value={value}
            onChange={(event) => onChange(toPreviewDeviceMode(event.target.value))}
            title="预览设备模式"
        >
            <option value="auto">自动</option>
            <option value="pc">PC</option>
            <option value="tablet">平板</option>
            <option value="mobile">手机</option>
        </select>
    );
}

export function ScreenHeaderMenus({
    menuContainerRef,
    activeMenu,
    setActiveMenu,
    id,
    focusMode,
    showLibraryPanel,
    showInspectorPanel,
    onToggleFocusMode,
    onToggleLibraryPanel,
    onToggleInspectorPanel,
    previewDeviceMode,
    setPreviewDeviceMode,
    versionAction,
    setVersionAction,
    themeApplyMode,
    setThemeApplyMode,
    theme,
    showGrid,
    cycleWarningCount,
    authoringIssueCount,
    permissions,
    lockedByOther,
    lockOwnerText,
    isPublishing,
    isSaving,
    isLoadingVersions,
    themeInputRef,
    executeMenuAction,
    executeVersionAction,
    onPreview,
    onPublish,
    onSave,
    onThemeChange,
    onApplyThemeToAllComponents,
    onExportThemePack,
    onImportThemePackClick,
    onThemePackFileChange,
    onZoomReset,
    onZoomFit,
    onToggleGrid,
    onShortcutHelp,
    onToggleLinkageGraph,
    onOpenVariableManager,
    onOpenIssuePanel,
    onExportPng,
    onExportPdf,
}: ScreenHeaderMenusProps) {
    return (
        <div className="flex items-center gap-2 shrink-0" ref={menuContainerRef}>
            <div className="header-mobile-primary-menu hidden">
                <HeaderMenu
                    label="操作"
                    open={activeMenu === 'primary'}
                    onToggle={() => setActiveMenu((prev) => (prev === 'primary' ? null : 'primary'))}
                >
                    <MenuSection title="快捷操作">
                        <MenuButton
                            onClick={() => executeMenuAction(onPreview)}
                            title={`预览大屏（${previewDeviceMode === 'auto' ? '自动' : previewDeviceMode}）`}
                        >
                            预览
                        </MenuButton>
                        {id ? (
                            <MenuButton
                                onClick={() => executeMenuAction(onPublish)}
                                disabled={isPublishing || !permissions.canPublish || lockedByOther}
                                title={lockedByOther ? `当前由 ${lockOwnerText} 持有编辑锁` : '发布当前草稿'}
                            >
                                {isPublishing ? '发布中...' : '发布'}
                            </MenuButton>
                        ) : null}
                        <MenuButton
                            onClick={() => executeMenuAction(onSave)}
                            disabled={isSaving || !permissions.canEdit || lockedByOther}
                            title={lockedByOther ? `当前由 ${lockOwnerText} 持有编辑锁` : '保存草稿'}
                        >
                            {isSaving ? '保存中...' : '保存'}
                        </MenuButton>
                    </MenuSection>
                </HeaderMenu>
            </div>

            <HeaderMenu
                label="视图"
                open={activeMenu === 'tools-view'}
                onToggle={() => setActiveMenu((prev) => (prev === 'tools-view' ? null : 'tools-view'))}
            >
                {onToggleFocusMode ? (
                    <MenuSection title="面板">
                        <MenuButton
                            active={focusMode}
                            onClick={() => {
                                onToggleFocusMode();
                                setActiveMenu(null);
                            }}
                            title="Ctrl/Cmd + \\"
                        >
                            {focusMode ? '退出聚焦' : '聚焦模式'}
                        </MenuButton>
                        {!focusMode && onToggleLibraryPanel ? (
                            <MenuButton
                                active={showLibraryPanel}
                                onClick={onToggleLibraryPanel}
                                title="Ctrl/Cmd+Alt+1"
                            >
                                {showLibraryPanel ? '隐藏左栏' : '显示左栏'}
                            </MenuButton>
                        ) : null}
                        {!focusMode && onToggleInspectorPanel ? (
                            <MenuButton
                                active={showInspectorPanel}
                                onClick={onToggleInspectorPanel}
                                title="Ctrl/Cmd+Alt+2"
                            >
                                {showInspectorPanel ? '隐藏右栏' : '显示右栏'}
                            </MenuButton>
                        ) : null}
                    </MenuSection>
                ) : null}
                <MenuSection title="视图">
                    <MenuButton onClick={onZoomReset} title="缩放重置为 100%">缩放100%</MenuButton>
                    <MenuButton onClick={onZoomFit} title="按当前窗口自动适配缩放">缩放适配</MenuButton>
                    <MenuButton active={showGrid} onClick={onToggleGrid} title="显示/隐藏网格">
                        {showGrid ? '隐藏网格' : '显示网格'}
                    </MenuButton>
                </MenuSection>
                <MenuSection title="帮助">
                    <MenuButton onClick={onShortcutHelp} title="查看快捷键">快捷键</MenuButton>
                </MenuSection>
            </HeaderMenu>

            <HeaderMenu
                label={`编辑${authoringIssueCount > 0 ? `(${authoringIssueCount})` : (cycleWarningCount > 0 ? `(${cycleWarningCount})` : '')}`}
                open={activeMenu === 'tools-edit'}
                onToggle={() => setActiveMenu((prev) => (prev === 'tools-edit' ? null : 'tools-edit'))}
            >
                {onOpenIssuePanel ? (
                    <MenuSection title="编排检查">
                        <MenuButton
                            onClick={() => executeMenuAction(onOpenIssuePanel)}
                            title="集中查看并定位发布前问题"
                        >
                            问题中心{authoringIssueCount > 0 ? `（${authoringIssueCount}）` : ''}
                        </MenuButton>
                    </MenuSection>
                ) : null}
                <MenuSection title="联动配置">
                    <MenuButton
                        onClick={() => {
                            setActiveMenu(null);
                            onToggleLinkageGraph();
                        }}
                        title="查看组件联动关系图"
                    >
                        联动关系图
                    </MenuButton>
                    <MenuButton
                        onClick={() => executeMenuAction(onOpenVariableManager)}
                        title="管理全局变量"
                    >
                        变量管理
                    </MenuButton>
                </MenuSection>
            </HeaderMenu>

            <HeaderMenu
                label="主题"
                open={activeMenu === 'tools-theme'}
                onToggle={() => setActiveMenu((prev) => (prev === 'tools-theme' ? null : 'tools-theme'))}
            >
                <MenuSection title="主题选择">
                    <ThemeSelector value={theme || 'legacy-dark'} onChange={onThemeChange} />
                </MenuSection>
                <MenuSection title="应用与主题包">
                    <select
                        className={MENU_SELECT_CLASS}
                        value={themeApplyMode}
                        onChange={(event) => setThemeApplyMode(event.target.value === 'safe' ? 'safe' : 'force')}
                        title="组件样式应用策略"
                    >
                        <option value="force">强制覆盖</option>
                        <option value="safe">仅补缺省</option>
                    </select>
                    <MenuButton
                        onClick={() => onApplyThemeToAllComponents(themeApplyMode)}
                        title="按当前主题批量刷新组件样式"
                    >
                        应用样式
                    </MenuButton>
                    <MenuButton onClick={onExportThemePack} title="导出主题包">导出主题</MenuButton>
                    <MenuButton onClick={onImportThemePackClick} title="导入主题包">导入主题</MenuButton>
                </MenuSection>
            </HeaderMenu>

            <HeaderMenu
                label="导出"
                open={activeMenu === 'tools-io'}
                onToggle={() => setActiveMenu((prev) => (prev === 'tools-io' ? null : 'tools-io'))}
            >
                <MenuSection title="导出快照">
                    <label className="text-xs text-[var(--color-text-secondary)] px-1" htmlFor="screen-io-device-mode">预览设备</label>
                    <DeviceModeSelect
                        id="screen-io-device-mode"
                        value={previewDeviceMode}
                        onChange={setPreviewDeviceMode}
                    />
                    <MenuButton
                        onClick={() => executeMenuAction(onExportPng)}
                        disabled={!id}
                        title="导出 PNG 图片"
                    >
                        导出 PNG
                    </MenuButton>
                    <MenuButton
                        onClick={() => executeMenuAction(onExportPdf)}
                        disabled={!id}
                        title="导出 PDF 文档"
                    >
                        导出 PDF
                    </MenuButton>
                    <div className="text-[11px] text-[var(--color-text-secondary)] px-1 py-0.5">
                        JSON 导入/导出请前往「大屏列表」。
                    </div>
                </MenuSection>
            </HeaderMenu>

            <HeaderMenu
                label="版本"
                open={activeMenu === 'tools-release'}
                onToggle={() => setActiveMenu((prev) => (prev === 'tools-release' ? null : 'tools-release'))}
            >
                <MenuSection title="版本管理">
                    <label className="text-xs text-[var(--color-text-secondary)] px-1" htmlFor="screen-preview-device-mode">预览设备</label>
                    <DeviceModeSelect
                        id="screen-preview-device-mode"
                        value={previewDeviceMode}
                        onChange={setPreviewDeviceMode}
                    />
                    {id ? (
                        <>
                            <label className="text-xs text-[var(--color-text-secondary)] px-1" htmlFor="screen-version-action">版本动作</label>
                            <select
                                id="screen-version-action"
                                className={MENU_SELECT_CLASS}
                                value={versionAction}
                                onChange={(event) => setVersionAction(event.target.value === 'compare' ? 'compare' : 'history')}
                                title="选择版本动作"
                            >
                                <option value="history">版本历史/回滚</option>
                                <option value="compare">版本对比</option>
                            </select>
                            <MenuButton
                                onClick={executeVersionAction}
                                disabled={isLoadingVersions || (versionAction === 'history' ? !permissions.canPublish : !permissions.canRead)}
                                title={versionAction === 'history' ? '查看版本历史并回滚' : '查看版本差异摘要'}
                            >
                                {isLoadingVersions ? '加载中...' : '执行版本动作'}
                            </MenuButton>
                        </>
                    ) : null}
                </MenuSection>
            </HeaderMenu>

            <input
                ref={themeInputRef}
                type="file"
                accept="application/json,.json"
                style={{ display: 'none' }}
                onChange={onThemePackFileChange}
            />
        </div>
    );
}
