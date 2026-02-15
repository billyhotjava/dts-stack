import { Modal } from '../../../ui/Modal/Modal';
import type { ScreenVersionDiff } from '../../../api/analyticsApi';
import type { ReactNode } from 'react';
import { writeTextToClipboard } from '../../../hooks/clipboard';

interface ScreenVersionComparePanelProps {
    open: boolean;
    diff: ScreenVersionDiff | null;
    onClose: () => void;
}

function renderTagList(items: string[] | undefined, emptyText: string) {
    const values = Array.isArray(items) ? items.filter((item) => String(item || '').trim().length > 0) : [];
    if (values.length === 0) {
        return <div style={{ fontSize: 12, opacity: 0.7 }}>{emptyText}</div>;
    }
    return (
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6 }}>
            {values.map((item) => (
                <span
                    key={item}
                    style={{
                        fontSize: 12,
                        padding: '2px 8px',
                        borderRadius: 999,
                        border: '1px solid var(--color-border)',
                        background: 'rgba(148,163,184,0.08)',
                    }}
                >
                    {item}
                </span>
            ))}
        </div>
    );
}

export function ScreenVersionComparePanel({ open, diff, onClose }: ScreenVersionComparePanelProps) {
    const s = diff?.summary || {};
    const details = diff?.details || {};
    const changedTypeComponents = Array.isArray(details.changedTypeComponents) ? details.changedTypeComponents : [];

    return (
        <Modal isOpen={open} onClose={onClose} title="版本差异详情" size="xl">
            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8, marginBottom: 10 }}>
                <button
                    type="button"
                    className="header-btn"
                    disabled={!diff}
                    onClick={async () => {
                        if (!diff) return;
                        const summaryText = [
                            `版本差异摘要`,
                            `组件数: ${s.componentCountFrom ?? '-'} -> ${s.componentCountTo ?? '-'}`,
                            `新增/移除组件: ${s.addedComponents ?? 0} / ${s.removedComponents ?? 0}`,
                            `新增/移除变量: ${s.addedVariables ?? 0} / ${s.removedVariables ?? 0}`,
                            `类型变化组件: ${s.changedTypeComponents ?? 0}`,
                        ].join('\n');
                        const copied = await writeTextToClipboard(summaryText);
                        if (!copied) {
                            alert(summaryText);
                        }
                    }}
                >
                    复制摘要
                </button>
                <button
                    type="button"
                    className="header-btn"
                    disabled={!diff}
                    onClick={() => {
                        if (!diff) return;
                        const blob = new Blob([JSON.stringify(diff, null, 2)], { type: 'application/json;charset=utf-8' });
                        const url = URL.createObjectURL(blob);
                        const link = document.createElement('a');
                        link.href = url;
                        link.download = `screen-version-diff-${Date.now()}.json`;
                        document.body.appendChild(link);
                        link.click();
                        document.body.removeChild(link);
                        URL.revokeObjectURL(url);
                    }}
                >
                    导出JSON
                </button>
            </div>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, minmax(120px, 1fr))', gap: 8, marginBottom: 12 }}>
                <Metric title="组件数" value={`${s.componentCountFrom ?? '-'} -> ${s.componentCountTo ?? '-'}`} />
                <Metric title="新增/移除组件" value={`${s.addedComponents ?? 0} / ${s.removedComponents ?? 0}`} />
                <Metric title="新增/移除变量" value={`${s.addedVariables ?? 0} / ${s.removedVariables ?? 0}`} />
                <Metric title="类型变化组件" value={String(s.changedTypeComponents ?? 0)} />
            </div>

            <Section title="新增组件ID">
                {renderTagList(details.addedComponentIds, '无')}
            </Section>
            <Section title="移除组件ID">
                {renderTagList(details.removedComponentIds, '无')}
            </Section>
            <Section title="新增组件类型">
                {renderTagList(details.addedComponentTypes, '无')}
            </Section>
            <Section title="移除组件类型">
                {renderTagList(details.removedComponentTypes, '无')}
            </Section>
            <Section title="新增变量Key">
                {renderTagList(details.addedVariableKeys, '无')}
            </Section>
            <Section title="移除变量Key">
                {renderTagList(details.removedVariableKeys, '无')}
            </Section>

            <Section title="组件类型变化">
                {changedTypeComponents.length === 0 ? (
                    <div style={{ fontSize: 12, opacity: 0.7 }}>无</div>
                ) : (
                    <div style={{ display: 'grid', gap: 6 }}>
                        {changedTypeComponents.map((item, idx) => (
                            <div
                                key={`${item.id || 'id'}-${idx}`}
                                style={{
                                    fontSize: 12,
                                    padding: '8px 10px',
                                    border: '1px solid var(--color-border)',
                                    borderRadius: 8,
                                    background: 'rgba(148,163,184,0.08)',
                                }}
                            >
                                <b>{item.id || '-'}</b>: {item.fromType || '-'} {'->'} {item.toType || '-'}
                            </div>
                        ))}
                    </div>
                )}
            </Section>
        </Modal>
    );
}

function Metric({ title, value }: { title: string; value: string }) {
    return (
        <div style={{ border: '1px solid var(--color-border)', borderRadius: 8, padding: 10 }}>
            <div style={{ fontSize: 12, opacity: 0.75, marginBottom: 6 }}>{title}</div>
            <div style={{ fontSize: 15, fontWeight: 600 }}>{value}</div>
        </div>
    );
}

function Section({ title, children }: { title: string; children: ReactNode }) {
    return (
        <div style={{ marginBottom: 12 }}>
            <div style={{ fontWeight: 600, marginBottom: 6 }}>{title}</div>
            {children}
        </div>
    );
}
