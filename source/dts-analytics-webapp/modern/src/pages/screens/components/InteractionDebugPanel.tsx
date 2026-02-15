import type { CSSProperties } from 'react';
import { Modal } from '../../../ui/Modal/Modal';
import { useScreenRuntime } from '../ScreenRuntimeContext';

interface InteractionDebugPanelProps {
    open: boolean;
    cycleWarnings?: string[];
    onClose: () => void;
}

export function InteractionDebugPanel({ open, cycleWarnings, onClose }: InteractionDebugPanelProps) {
    const { definitions, values, events } = useScreenRuntime();

    return (
        <Modal isOpen={open} onClose={onClose} title="联动调试台" size="xl">
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
                <section style={{ border: '1px solid rgba(148,163,184,0.25)', borderRadius: 8, padding: 10 }}>
                    <div style={{ fontSize: 12, fontWeight: 600, marginBottom: 8 }}>变量实时值</div>
                    <div style={{ display: 'grid', gridTemplateColumns: '140px 1fr', gap: 8, fontSize: 12 }}>
                        {definitions.length === 0 && <div style={{ gridColumn: '1 / -1', opacity: 0.7 }}>暂无变量定义</div>}
                        {definitions.map((item) => (
                            <>
                                <div key={`${item.key}-key`} style={{ opacity: 0.9 }}>{item.label || item.key}</div>
                                <code key={`${item.key}-value`} style={{ fontSize: 12 }}>{values[item.key] || '(空)'}</code>
                            </>
                        ))}
                    </div>
                </section>

                <section style={{ border: '1px solid rgba(148,163,184,0.25)', borderRadius: 8, padding: 10 }}>
                    <div style={{ fontSize: 12, fontWeight: 600, marginBottom: 8 }}>循环/冲突检测</div>
                    {cycleWarnings && cycleWarnings.length > 0 ? (
                        <div style={{ display: 'flex', flexDirection: 'column', gap: 6, fontSize: 12 }}>
                            {cycleWarnings.map((item, idx) => (
                                <div key={`${item}-${idx}`} style={{ color: '#f59e0b' }}>{item}</div>
                            ))}
                        </div>
                    ) : (
                        <div style={{ fontSize: 12, opacity: 0.75 }}>未发现循环依赖</div>
                    )}
                </section>
            </div>

            <section style={{ marginTop: 12, border: '1px solid rgba(148,163,184,0.25)', borderRadius: 8, padding: 10 }}>
                <div style={{ fontSize: 12, fontWeight: 600, marginBottom: 8 }}>事件链路（最近 100 条）</div>
                <div style={{ maxHeight: 320, overflow: 'auto', fontSize: 12 }}>
                    {events.length === 0 ? (
                        <div style={{ opacity: 0.7 }}>暂无事件</div>
                    ) : (
                        <table style={{ width: '100%', borderCollapse: 'collapse' }}>
                            <thead>
                                <tr>
                                    <th style={th}>时间</th>
                                    <th style={th}>变量</th>
                                    <th style={th}>值</th>
                                    <th style={th}>来源</th>
                                </tr>
                            </thead>
                            <tbody>
                                {events.map((event) => (
                                    <tr key={event.id}>
                                        <td style={td}>{event.at.replace('T', ' ').replace('Z', '')}</td>
                                        <td style={td}>{event.key}</td>
                                        <td style={td}><code>{event.value || '(空)'}</code></td>
                                        <td style={td}>{event.source || '-'}</td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    )}
                </div>
            </section>
        </Modal>
    );
}

const th: CSSProperties = {
    textAlign: 'left',
    borderBottom: '1px solid rgba(148,163,184,0.3)',
    padding: '6px 8px',
    position: 'sticky',
    top: 0,
    background: 'rgba(2,6,23,0.95)',
};

const td: CSSProperties = {
    borderBottom: '1px solid rgba(148,163,184,0.15)',
    padding: '6px 8px',
    verticalAlign: 'top',
};
