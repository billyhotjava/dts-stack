interface SaveFailureNotice {
    message: string;
    failedAt: number;
}

interface ScreenHeaderNoticesProps {
    lockedByOther: boolean;
    lockOwnerText: string;
    lockErrorText: string | null;
    saveFailure: SaveFailureNotice | null;
    hasUnsavedChanges: boolean;
    lastRecoverySavedAt: number | null;
    hasClassification: boolean;
    canEdit: boolean;
    isSaving: boolean;
    onRetrySave: () => void | Promise<void>;
}

export function ScreenHeaderNotices({
    lockedByOther,
    lockOwnerText,
    lockErrorText,
    saveFailure,
    hasUnsavedChanges,
    lastRecoverySavedAt,
    hasClassification,
    canEdit,
    isSaving,
    onRetrySave,
}: ScreenHeaderNoticesProps) {
    return (
        <>
            {lockedByOther ? (
                <div className="px-4 py-3 text-xs text-[#f59e0b] border-b border-white/[0.08] shrink-0 bg-[rgba(245,158,11,0.12)]">
                    编辑锁提示：当前由 {lockOwnerText} 编辑中，保存/发布已被保护性禁用。
                    {lockErrorText ? ` (${lockErrorText})` : ''}
                </div>
            ) : null}

            {saveFailure ? (
                <div
                    data-testid="analytics-screen-save-retry-notice"
                    className="px-4 py-2.5 text-xs border-b border-white/[0.08] shrink-0 flex items-center gap-3"
                    style={{ color: '#fecaca', background: 'rgba(239,68,68,0.12)' }}
                >
                    <span className="min-w-0 flex-1 overflow-hidden text-ellipsis whitespace-nowrap">
                        保存失败：{saveFailure.message}。本地恢复点仍会保留，可修正后重试。
                    </span>
                    <span className="text-[11px] text-white/55 shrink-0">
                        {new Date(saveFailure.failedAt).toLocaleTimeString()}
                    </span>
                    <button
                        type="button"
                        className="header-btn flex items-center gap-1.5 px-3 py-1.5 border border-white/[0.18] rounded-md bg-white/[0.08] text-white text-[12px] font-medium cursor-pointer transition-all duration-200 whitespace-nowrap shrink-0 hover:bg-white/[0.14] disabled:opacity-50 disabled:cursor-not-allowed"
                        onClick={() => void onRetrySave()}
                        disabled={isSaving || !canEdit || lockedByOther}
                    >
                        重试保存
                    </button>
                </div>
            ) : null}

            {!saveFailure && hasUnsavedChanges && lastRecoverySavedAt && !lockedByOther ? (
                <div
                    data-testid="analytics-screen-local-recovery-status"
                    className="px-4 py-2 text-xs text-[#a7f3d0] border-b border-white/[0.08] shrink-0 bg-[rgba(16,185,129,0.08)]"
                >
                    本地恢复点已更新：{new Date(lastRecoverySavedAt).toLocaleTimeString()}。异常关闭后可恢复当前编辑内容。
                </div>
            ) : null}

            {!hasClassification && canEdit ? (
                <div
                    data-testid="analytics-screen-header-classification-missing"
                    className="px-4 py-2.5 text-xs text-[#f59e0b] border-b border-white/[0.08] shrink-0 bg-[rgba(245,158,11,0.12)] flex items-center gap-2"
                >
                    <span style={{ fontSize: 14, lineHeight: 1 }}>⚠</span>
                    <span>
                        本大屏尚未设置密级。未设密级时大屏对所有登录用户可见，建议在右侧
                        <strong style={{ margin: '0 4px' }}>属性面板 → 密级</strong>
                        中补登（公开 / 内部 / 秘密 / 机密 之一），保存后将按密级管控可见范围。
                    </span>
                </div>
            ) : null}
        </>
    );
}
