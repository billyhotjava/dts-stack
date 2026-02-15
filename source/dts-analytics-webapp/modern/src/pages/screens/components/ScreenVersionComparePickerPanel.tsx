import { useEffect, useState } from 'react';
import type { ScreenVersion } from '../../../api/analyticsApi';
import { Modal } from '../../../ui/Modal/Modal';

interface ScreenVersionComparePickerPanelProps {
    open: boolean;
    versions: ScreenVersion[];
    loading?: boolean;
    onClose: () => void;
    onCompare: (fromVersionId: string, toVersionId: string) => void | Promise<void>;
}

function asId(value: unknown): string {
    return String(value ?? '').trim();
}

export function ScreenVersionComparePickerPanel({
    open,
    versions,
    loading = false,
    onClose,
    onCompare,
}: ScreenVersionComparePickerPanelProps) {
    const [fromVersionId, setFromVersionId] = useState('');
    const [toVersionId, setToVersionId] = useState('');

    useEffect(() => {
        if (!open) return;
        const defaultTo = versions[0];
        const defaultFrom = versions[1] || versions[0];
        setFromVersionId(asId(defaultFrom?.id));
        setToVersionId(asId(defaultTo?.id));
    }, [open, versions]);

    const valid = fromVersionId.length > 0 && toVersionId.length > 0 && fromVersionId !== toVersionId;

    return (
        <Modal isOpen={open} onClose={onClose} title="选择对比版本" size="md">
            <div style={{ fontSize: 12, opacity: 0.8, marginBottom: 10 }}>
                选择起始版本（from）和目标版本（to）进行差异比较。
            </div>

            <div style={{ display: 'grid', gridTemplateColumns: '80px 1fr', gap: 8, alignItems: 'center', marginBottom: 10 }}>
                <div style={{ fontSize: 12 }}>From</div>
                <select className="property-input" value={fromVersionId} onChange={(e) => setFromVersionId(e.target.value)}>
                    {versions.map((version) => (
                        <option key={`from-${version.id}`} value={asId(version.id)}>
                            ID={version.id} | v{version.versionNo ?? '-'} | {version.publishedAt || version.createdAt || '-'}
                        </option>
                    ))}
                </select>

                <div style={{ fontSize: 12 }}>To</div>
                <select className="property-input" value={toVersionId} onChange={(e) => setToVersionId(e.target.value)}>
                    {versions.map((version) => (
                        <option key={`to-${version.id}`} value={asId(version.id)}>
                            ID={version.id} | v{version.versionNo ?? '-'} | {version.publishedAt || version.createdAt || '-'}
                        </option>
                    ))}
                </select>
            </div>

            {!valid && (
                <div style={{ fontSize: 12, color: '#f59e0b', marginBottom: 8 }}>
                    请选择两个不同版本进行对比
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
                        void onCompare(fromVersionId, toVersionId);
                    }}
                >
                    {loading ? '对比中...' : '开始对比'}
                </button>
            </div>
        </Modal>
    );
}

