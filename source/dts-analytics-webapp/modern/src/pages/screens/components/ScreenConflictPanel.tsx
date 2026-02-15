import { Modal } from '../../../ui/Modal/Modal';

export interface ScreenUpdateConflict {
    code: string;
    message: string;
    componentIds: string[];
    fields: string[];
}

interface ScreenConflictPanelProps {
    open: boolean;
    conflict: ScreenUpdateConflict | null;
    loading?: boolean;
    onClose: () => void;
    onReloadLatest: () => void | Promise<void>;
    onSelectConflictComponents: (ids: string[]) => void;
}

export function ScreenConflictPanel({
    open,
    conflict,
    loading = false,
    onClose,
    onReloadLatest,
    onSelectConflictComponents,
}: ScreenConflictPanelProps) {
    const componentIds = conflict?.componentIds ?? [];
    const fields = conflict?.fields ?? [];

    return (
        <Modal isOpen={open} onClose={onClose} title="检测到并发编辑冲突" size="md">
            <div style={{ fontSize: 12, opacity: 0.85, marginBottom: 10, lineHeight: 1.6 }}>
                {conflict?.message || '当前草稿已被其他用户更新，且与你本地改动在相同字段/组件上发生重叠。'}
            </div>

            {fields.length > 0 && (
                <div style={{ marginBottom: 10 }}>
                    <div style={{ fontWeight: 600, marginBottom: 6 }}>冲突字段</div>
                    <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6 }}>
                        {fields.map((field) => (
                            <span
                                key={field}
                                style={{
                                    fontSize: 12,
                                    padding: '2px 8px',
                                    borderRadius: 999,
                                    border: '1px solid rgba(245,158,11,0.45)',
                                    background: 'rgba(245,158,11,0.12)',
                                    color: '#f59e0b',
                                }}
                            >
                                {field}
                            </span>
                        ))}
                    </div>
                </div>
            )}

            {componentIds.length > 0 && (
                <div style={{ marginBottom: 12 }}>
                    <div style={{ fontWeight: 600, marginBottom: 6 }}>冲突组件</div>
                    <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6 }}>
                        {componentIds.map((componentId) => (
                            <button
                                key={componentId}
                                type="button"
                                className="header-btn"
                                style={{ fontSize: 12, padding: '4px 8px' }}
                                onClick={() => onSelectConflictComponents([componentId])}
                            >
                                {componentId}
                            </button>
                        ))}
                    </div>
                </div>
            )}

            <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end' }}>
                <button type="button" className="header-btn" onClick={onClose}>
                    关闭
                </button>
                <button
                    type="button"
                    className="header-btn"
                    disabled={componentIds.length === 0}
                    onClick={() => onSelectConflictComponents(componentIds)}
                >
                    选中冲突组件
                </button>
                <button
                    type="button"
                    className="header-btn save-btn"
                    disabled={loading}
                    onClick={() => {
                        void onReloadLatest();
                    }}
                >
                    {loading ? '重载中...' : '重载最新草稿'}
                </button>
            </div>
        </Modal>
    );
}
