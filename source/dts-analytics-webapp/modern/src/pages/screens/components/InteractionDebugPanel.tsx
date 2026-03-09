import { Fragment, useMemo, useState, type CSSProperties } from 'react';
import { Modal } from '../../../ui/Modal/Modal';
import { useScreenRuntime } from '../ScreenRuntimeContext';

interface InteractionDebugPanelProps {
    open: boolean;
    cycleWarnings?: string[];
    onClose: () => void;
}

export function InteractionDebugPanel({ open, cycleWarnings, onClose }: InteractionDebugPanelProps) {
    const { definitions, values, getEvents } = useScreenRuntime();
    const [kindFilter, setKindFilter] = useState<'all' | 'variable' | 'filter' | 'interaction' | 'drill-down' | 'drill-up' | 'jump' | 'action' | 'panel' | 'intent'>('all');
    const events = getEvents();
    const filteredEvents = useMemo(() => (
        kindFilter === 'all' ? events : events.filter((item) => item.kind === kindFilter)
    ), [events, kindFilter]);

    return (
        <Modal isOpen={open} onClose={onClose} title="联动调试台" size="xl">
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
                <section style={{ border: '1px solid rgba(148,163,184,0.25)', borderRadius: 8, padding: 10 }}>
                    <div style={{ fontSize: 12, fontWeight: 600, marginBottom: 8 }}>变量实时值</div>
                    <div style={{ display: 'grid', gridTemplateColumns: '140px 1fr', gap: 8, fontSize: 12 }}>
                        {definitions.length === 0 && <div style={{ gridColumn: '1 / -1', opacity: 0.7 }}>暂无变量定义</div>}
                        {definitions.map((item) => (
                            <Fragment key={item.key}>
                                <div style={{ opacity: 0.9 }}>{item.label || item.key}</div>
                                <code style={{ fontSize: 12 }}>{values[item.key] ?? '(空)'}</code>
                            </Fragment>
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
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 }}>
                    <div style={{ fontSize: 12, fontWeight: 600 }}>事件链路（最近 100 条）</div>
                    <select
                        value={kindFilter}
                        onChange={(e) => setKindFilter(e.target.value as typeof kindFilter)}
                        style={{ fontSize: 12, minWidth: 130 }}
                    >
                        <option value="all">全部类型</option>
                        <option value="variable">变量写入</option>
                        <option value="filter">筛选器</option>
                        <option value="interaction">联动写入</option>
                        <option value="drill-down">钻取下钻</option>
                        <option value="drill-up">钻取回退</option>
                        <option value="jump">页面跳转</option>
                        <option value="action">动作入口</option>
                        <option value="panel">详情面板</option>
                        <option value="intent">意图事件</option>
                    </select>
                </div>
                <div style={{ maxHeight: 320, overflow: 'auto', fontSize: 12 }}>
                    {filteredEvents.length === 0 ? (
                        <div style={{ opacity: 0.7 }}>暂无事件</div>
                    ) : (
                        <table style={{ width: '100%', borderCollapse: 'collapse' }}>
                            <thead>
                                <tr>
                                    <th style={th}>时间</th>
                                    <th style={th}>类型</th>
                                    <th style={th}>变量</th>
                                    <th style={th}>值</th>
                                    <th style={th}>来源</th>
                                    <th style={th}>详情</th>
                                </tr>
                            </thead>
                            <tbody>
                                {filteredEvents.map((event) => (
                                    <tr key={event.id}>
                                        <td style={td}>{event.at.replace('T', ' ').replace('Z', '')}</td>
                                        <td style={td}>{event.kind}</td>
                                        <td style={td}>{event.key}</td>
                                        <td style={td}><code>{event.value || '(空)'}</code></td>
                                        <td style={td}>{event.source || '-'}</td>
                                        <td style={td}>{event.meta || '-'}</td>
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
