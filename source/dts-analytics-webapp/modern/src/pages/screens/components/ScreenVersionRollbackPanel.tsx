import { useEffect, useState } from 'react';
import type { ScreenVersion } from '../../../api/analyticsApi';
import { Modal } from '../../../ui/Modal/Modal';

interface ScreenVersionRollbackPanelProps {
    open: boolean;
    versions: ScreenVersion[];
    loading?: boolean;
    onClose: () => void;
    onRollback: (versionId: string) => void | Promise<void>;
}

function asId(value: unknown): string {
    return String(value ?? '').trim();
}

export function ScreenVersionRollbackPanel({
    open,
    versions,
    loading = false,
    onClose,
    onRollback,
}: ScreenVersionRollbackPanelProps) {
    const [targetVersionId, setTargetVersionId] = useState('');

    useEffect(() => {
        if (!open) return;
        const candidate = versions.find((item) => !item.currentPublished) || versions[0];
        setTargetVersionId(asId(candidate?.id));
    }, [open, versions]);

    const valid = targetVersionId.length > 0;
    const targetVersion = versions.find((item) => asId(item.id) === targetVersionId);

    return (
        <Modal isOpen={open} onClose={onClose} title="选择回滚版本" size="md">
            <div style={{ fontSize: 12, opacity: 0.8, marginBottom: 10 }}>
                选择目标版本后，草稿与发布版本将回退到该版本配置。
            </div>

            <div style={{ marginBottom: 12 }}>
                <select
                    className="property-input"
                    value={targetVersionId}
                    onChange={(e) => setTargetVersionId(e.target.value)}
                >
                    {versions.map((version) => (
                        <option key={version.id} value={asId(version.id)}>
                            ID={version.id} | v{version.versionNo ?? '-'} | {version.publishedAt || version.createdAt || '-'}{version.currentPublished ? ' [当前发布]' : ''}
                        </option>
                    ))}
                </select>
            </div>

            {targetVersion?.currentPublished && (
                <div style={{ fontSize: 12, color: '#f59e0b', marginBottom: 8 }}>
                    当前选择的是已发布版本，回滚后配置可能无可见变化。
                </div>
            )}

            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
                <button type="button" className="header-btn" onClick={onClose}>
                    关闭
                </button>
                <button
                    type="button"
                    className="header-btn save-btn"
                    disabled={!valid || loading}
                    onClick={() => {
                        void onRollback(targetVersionId);
                    }}
                >
                    {loading ? '回滚中...' : '确认回滚'}
                </button>
            </div>
        </Modal>
    );
}

