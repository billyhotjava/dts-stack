/**
 * VersionHistoryPanel — 版本历史面板
 *
 * Displays a timeline of published versions with change summaries,
 * per-version diff details, and integrated rollback.
 */
import { useState, useMemo, useCallback } from 'react';
import type { ScreenVersion, ScreenVersionDiff } from '../../../api/analyticsApi';
import {
    diffScreenConfigs,
    formatDiffSummary,
    formatDiffValue,
    type ConfigDiffResult,
    type ComponentChange,
    type PropertyChange,
    type VariableChange,
} from '../screenConfigDiff';
import type { ScreenConfig } from '../types';

/* ---------- types ---------- */

interface VersionHistoryPanelProps {
    open: boolean;
    versions: ScreenVersion[];
    currentConfig?: ScreenConfig | null;
    loading?: boolean;
    onClose: () => void;
    onRollback: (versionId: string) => void | Promise<void>;
    onCompare: (fromVersionId: string, toVersionId: string) => void | Promise<void>;
    /** Server-side diff result (from backend compareScreenVersions) */
    serverDiff?: ScreenVersionDiff | null;
}

type DiffTab = 'components' | 'canvas' | 'variables';

/* ---------- helpers ---------- */

function asId(value: unknown): string {
    return String(value ?? '').trim();
}

function formatDate(value?: string | null): string {
    if (!value) return '-';
    try {
        const d = new Date(value);
        const pad = (n: number) => String(n).padStart(2, '0');
        return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
    } catch {
        return value;
    }
}

function changeTypeBadge(ct: 'added' | 'removed' | 'modified'): { label: string; color: string; bg: string } {
    switch (ct) {
        case 'added': return { label: '新增', color: '#22c55e', bg: 'rgba(34,197,94,0.12)' };
        case 'removed': return { label: '删除', color: '#ef4444', bg: 'rgba(239,68,68,0.12)' };
        case 'modified': return { label: '修改', color: '#f59e0b', bg: 'rgba(245,158,11,0.12)' };
    }
}

/* ---------- sub-components ---------- */

function PropertyChangeRow({ change }: { change: PropertyChange }) {
    return (
        <div style={{
            display: 'grid',
            gridTemplateColumns: '140px 1fr 20px 1fr',
            gap: 6,
            fontSize: 11,
            padding: '4px 8px',
            borderBottom: '1px solid rgba(148,163,184,0.1)',
            alignItems: 'start',
        }}>
            <div style={{ color: 'rgba(148,163,184,0.8)', fontFamily: 'monospace', wordBreak: 'break-all' }}>
                {change.path || '(root)'}
            </div>
            <div style={{
                padding: '2px 6px',
                borderRadius: 4,
                background: 'rgba(239,68,68,0.08)',
                fontFamily: 'monospace',
                wordBreak: 'break-all',
            }}>
                {formatDiffValue(change.oldValue)}
            </div>
            <div style={{ textAlign: 'center', color: 'rgba(148,163,184,0.6)' }}>→</div>
            <div style={{
                padding: '2px 6px',
                borderRadius: 4,
                background: 'rgba(34,197,94,0.08)',
                fontFamily: 'monospace',
                wordBreak: 'break-all',
            }}>
                {formatDiffValue(change.newValue)}
            </div>
        </div>
    );
}

function ComponentChangeCard({ change }: { change: ComponentChange }) {
    const [expanded, setExpanded] = useState(false);
    const badge = changeTypeBadge(change.changeType);
    const hasDetails = change.propertyChanges && change.propertyChanges.length > 0;

    return (
        <div style={{
            border: '1px solid var(--color-border, rgba(148,163,184,0.2))',
            borderRadius: 8,
            marginBottom: 6,
            overflow: 'hidden',
        }}>
            <div
                style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: 8,
                    padding: '8px 12px',
                    cursor: hasDetails ? 'pointer' : 'default',
                    background: 'rgba(148,163,184,0.04)',
                }}
                onClick={() => hasDetails && setExpanded(!expanded)}
            >
                {hasDetails && (
                    <span style={{ fontSize: 10, opacity: 0.6, transform: expanded ? 'rotate(90deg)' : 'none', transition: 'transform 0.15s' }}>
                        ▶
                    </span>
                )}
                <span style={{
                    fontSize: 11,
                    padding: '1px 8px',
                    borderRadius: 999,
                    background: badge.bg,
                    color: badge.color,
                    fontWeight: 600,
                }}>
                    {badge.label}
                </span>
                <span style={{ fontSize: 12, fontWeight: 600 }}>{change.componentName}</span>
                <span style={{ fontSize: 11, opacity: 0.6 }}>({change.componentType})</span>
                {hasDetails && (
                    <span style={{ fontSize: 11, opacity: 0.5, marginLeft: 'auto' }}>
                        {change.propertyChanges!.length} 处变更
                    </span>
                )}
            </div>
            {expanded && hasDetails && (
                <div style={{ borderTop: '1px solid rgba(148,163,184,0.1)' }}>
                    {change.propertyChanges!.map((pc, idx) => (
                        <PropertyChangeRow key={`${pc.path}-${idx}`} change={pc} />
                    ))}
                </div>
            )}
        </div>
    );
}

function VariableChangeCard({ change }: { change: VariableChange }) {
    const [expanded, setExpanded] = useState(false);
    const badge = changeTypeBadge(change.changeType);
    const hasDetails = change.propertyChanges && change.propertyChanges.length > 0;

    return (
        <div style={{
            border: '1px solid var(--color-border, rgba(148,163,184,0.2))',
            borderRadius: 8,
            marginBottom: 6,
            overflow: 'hidden',
        }}>
            <div
                style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: 8,
                    padding: '8px 12px',
                    cursor: hasDetails ? 'pointer' : 'default',
                    background: 'rgba(148,163,184,0.04)',
                }}
                onClick={() => hasDetails && setExpanded(!expanded)}
            >
                {hasDetails && (
                    <span style={{ fontSize: 10, opacity: 0.6, transform: expanded ? 'rotate(90deg)' : 'none', transition: 'transform 0.15s' }}>
                        ▶
                    </span>
                )}
                <span style={{
                    fontSize: 11,
                    padding: '1px 8px',
                    borderRadius: 999,
                    background: badge.bg,
                    color: badge.color,
                    fontWeight: 600,
                }}>
                    {badge.label}
                </span>
                <span style={{ fontSize: 12, fontWeight: 600 }}>{change.key}</span>
                {change.label && <span style={{ fontSize: 11, opacity: 0.6 }}>({change.label})</span>}
            </div>
            {expanded && hasDetails && (
                <div style={{ borderTop: '1px solid rgba(148,163,184,0.1)' }}>
                    {change.propertyChanges!.map((pc, idx) => (
                        <PropertyChangeRow key={`${pc.path}-${idx}`} change={pc} />
                    ))}
                </div>
            )}
        </div>
    );
}

function DiffDetailView({ diff }: { diff: ConfigDiffResult }) {
    const [activeTab, setActiveTab] = useState<DiffTab>('components');

    const tabs: Array<{ key: DiffTab; label: string; count: number }> = [
        { key: 'components', label: '组件变更', count: diff.componentChanges.length },
        { key: 'canvas', label: '画布', count: diff.canvasChanges.length },
        { key: 'variables', label: '变量', count: diff.variableChanges.length },
    ];

    return (
        <div>
            {/* Summary bar */}
            <div style={{
                display: 'grid',
                gridTemplateColumns: 'repeat(4, 1fr)',
                gap: 8,
                marginBottom: 12,
            }}>
                <SummaryCard label="新增" value={diff.summary.componentsAdded} color="#22c55e" />
                <SummaryCard label="删除" value={diff.summary.componentsRemoved} color="#ef4444" />
                <SummaryCard label="修改" value={diff.summary.componentsModified} color="#f59e0b" />
                <SummaryCard label="属性变更" value={diff.summary.totalPropertyChanges} color="#3b82f6" />
            </div>

            {/* Tabs */}
            <div style={{ display: 'flex', gap: 4, marginBottom: 10, borderBottom: '1px solid rgba(148,163,184,0.15)' }}>
                {tabs.map(tab => (
                    <button
                        key={tab.key}
                        type="button"
                        onClick={() => setActiveTab(tab.key)}
                        style={{
                            background: 'none',
                            border: 'none',
                            borderBottom: activeTab === tab.key ? '2px solid var(--color-primary, #3b82f6)' : '2px solid transparent',
                            color: activeTab === tab.key ? 'var(--color-primary, #3b82f6)' : 'inherit',
                            padding: '6px 12px',
                            fontSize: 12,
                            cursor: 'pointer',
                            fontWeight: activeTab === tab.key ? 600 : 400,
                            opacity: activeTab === tab.key ? 1 : 0.7,
                        }}
                    >
                        {tab.label} ({tab.count})
                    </button>
                ))}
            </div>

            {/* Tab content */}
            {activeTab === 'components' && (
                <div style={{ maxHeight: 300, overflowY: 'auto' }}>
                    {diff.componentChanges.length === 0 ? (
                        <div style={{ fontSize: 12, opacity: 0.6, padding: 10, textAlign: 'center' }}>无组件变更</div>
                    ) : (
                        diff.componentChanges.map(cc => (
                            <ComponentChangeCard key={cc.componentId} change={cc} />
                        ))
                    )}
                </div>
            )}
            {activeTab === 'canvas' && (
                <div style={{ maxHeight: 300, overflowY: 'auto' }}>
                    {diff.canvasChanges.length === 0 ? (
                        <div style={{ fontSize: 12, opacity: 0.6, padding: 10, textAlign: 'center' }}>画布属性无变化</div>
                    ) : (
                        diff.canvasChanges.map((pc, idx) => (
                            <PropertyChangeRow key={`${pc.path}-${idx}`} change={pc} />
                        ))
                    )}
                </div>
            )}
            {activeTab === 'variables' && (
                <div style={{ maxHeight: 300, overflowY: 'auto' }}>
                    {diff.variableChanges.length === 0 ? (
                        <div style={{ fontSize: 12, opacity: 0.6, padding: 10, textAlign: 'center' }}>全局变量无变化</div>
                    ) : (
                        diff.variableChanges.map(vc => (
                            <VariableChangeCard key={vc.key} change={vc} />
                        ))
                    )}
                </div>
            )}
        </div>
    );
}

function SummaryCard({ label, value, color }: { label: string; value: number; color: string }) {
    return (
        <div style={{
            border: '1px solid var(--color-border, rgba(148,163,184,0.2))',
            borderRadius: 8,
            padding: '8px 10px',
            textAlign: 'center',
        }}>
            <div style={{ fontSize: 18, fontWeight: 700, color }}>{value}</div>
            <div style={{ fontSize: 11, opacity: 0.7 }}>{label}</div>
        </div>
    );
}

/* ---------- main panel ---------- */

export function VersionHistoryPanel({
    open,
    versions,
    loading = false,
    onClose,
    onRollback,
    onCompare,
}: VersionHistoryPanelProps) {
    const [selectedVersionId, setSelectedVersionId] = useState<string>('');
    const [compareFromId, setCompareFromId] = useState<string>('');
    const [compareToId, setCompareToId] = useState<string>('');
    const [showDiffDetail, setShowDiffDetail] = useState(false);
    const [diffMode, setDiffMode] = useState(false);
    const [confirmingRollback, setConfirmingRollback] = useState(false);

    // Client-side diff between adjacent versions is not available (we don't have full configs).
    // We provide a server-side compare trigger and display the version timeline.

    const sortedVersions = useMemo(() => {
        return [...versions].sort((a, b) => {
            const va = a.versionNo ?? 0;
            const vb = b.versionNo ?? 0;
            return vb - va; // newest first
        });
    }, [versions]);

    const selectedVersion = useMemo(
        () => sortedVersions.find(v => asId(v.id) === selectedVersionId),
        [sortedVersions, selectedVersionId],
    );

    const handleSelect = useCallback((verId: string) => {
        setSelectedVersionId(verId);
        setConfirmingRollback(false);
        if (diffMode) {
            if (!compareFromId) {
                setCompareFromId(verId);
            } else if (!compareToId && verId !== compareFromId) {
                setCompareToId(verId);
            } else {
                // Reset
                setCompareFromId(verId);
                setCompareToId('');
            }
        }
    }, [diffMode, compareFromId, compareToId]);

    const handleStartCompare = useCallback(() => {
        if (compareFromId && compareToId) {
            void onCompare(compareFromId, compareToId);
        }
    }, [compareFromId, compareToId, onCompare]);

    const handleRollbackClick = useCallback(() => {
        setConfirmingRollback(true);
    }, []);

    const handleConfirmRollback = useCallback(() => {
        if (selectedVersionId) {
            void onRollback(selectedVersionId);
        }
        setConfirmingRollback(false);
    }, [selectedVersionId, onRollback]);

    if (!open) return null;

    return (
        <div style={{
            position: 'fixed',
            top: 0,
            right: 0,
            width: 480,
            height: '100vh',
            background: 'var(--color-bg-elevated, #1e293b)',
            borderLeft: '1px solid var(--color-border, rgba(148,163,184,0.2))',
            zIndex: 10000,
            display: 'flex',
            flexDirection: 'column',
            boxShadow: '-4px 0 20px rgba(0,0,0,0.3)',
        }}>
            {/* Header */}
            <div style={{
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
                padding: '12px 16px',
                borderBottom: '1px solid rgba(148,163,184,0.15)',
            }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                    <span style={{ fontSize: 14, fontWeight: 700 }}>版本历史</span>
                    <span style={{ fontSize: 11, opacity: 0.6 }}>{versions.length} 个版本</span>
                </div>
                <div style={{ display: 'flex', gap: 6 }}>
                    <button
                        type="button"
                        className="header-btn"
                        style={{
                            fontSize: 11,
                            padding: '4px 10px',
                            background: diffMode ? 'var(--color-primary, #3b82f6)' : undefined,
                            color: diffMode ? '#fff' : undefined,
                        }}
                        onClick={() => {
                            setDiffMode(!diffMode);
                            setCompareFromId('');
                            setCompareToId('');
                        }}
                    >
                        {diffMode ? '退出对比' : '对比模式'}
                    </button>
                    <button type="button" className="header-btn" onClick={onClose} style={{ fontSize: 14, padding: '4px 8px' }}>
                        ✕
                    </button>
                </div>
            </div>

            {/* Diff mode hint */}
            {diffMode && (
                <div style={{
                    padding: '8px 16px',
                    background: 'rgba(59,130,246,0.08)',
                    borderBottom: '1px solid rgba(148,163,184,0.1)',
                    fontSize: 11,
                    display: 'flex',
                    alignItems: 'center',
                    gap: 8,
                }}>
                    <span>点击选择两个版本进行对比</span>
                    {compareFromId && (
                        <span style={{ padding: '1px 6px', borderRadius: 4, background: 'rgba(239,68,68,0.15)', fontSize: 10 }}>
                            FROM: v{sortedVersions.find(v => asId(v.id) === compareFromId)?.versionNo ?? '?'}
                        </span>
                    )}
                    {compareToId && (
                        <span style={{ padding: '1px 6px', borderRadius: 4, background: 'rgba(34,197,94,0.15)', fontSize: 10 }}>
                            TO: v{sortedVersions.find(v => asId(v.id) === compareToId)?.versionNo ?? '?'}
                        </span>
                    )}
                    {compareFromId && compareToId && (
                        <button
                            type="button"
                            className="header-btn save-btn"
                            style={{ fontSize: 11, padding: '3px 10px', marginLeft: 'auto' }}
                            disabled={loading}
                            onClick={handleStartCompare}
                        >
                            {loading ? '对比中...' : '开始对比'}
                        </button>
                    )}
                </div>
            )}

            {/* Version timeline */}
            <div style={{ flex: 1, overflowY: 'auto', padding: '8px 0' }}>
                {loading && versions.length === 0 && (
                    <div style={{ textAlign: 'center', padding: 40, opacity: 0.6, fontSize: 12 }}>加载中...</div>
                )}
                {!loading && versions.length === 0 && (
                    <div style={{ textAlign: 'center', padding: 40, opacity: 0.6, fontSize: 12 }}>暂无版本记录</div>
                )}
                {sortedVersions.map((version, idx) => {
                    const vid = asId(version.id);
                    const isSelected = vid === selectedVersionId;
                    const isCompareFrom = diffMode && vid === compareFromId;
                    const isCompareTo = diffMode && vid === compareToId;
                    const isPublished = version.currentPublished;

                    return (
                        <div
                            key={vid}
                            onClick={() => handleSelect(vid)}
                            style={{
                                display: 'flex',
                                gap: 12,
                                padding: '10px 16px',
                                cursor: 'pointer',
                                background: isSelected ? 'rgba(59,130,246,0.08)'
                                    : isCompareFrom ? 'rgba(239,68,68,0.06)'
                                    : isCompareTo ? 'rgba(34,197,94,0.06)'
                                    : 'transparent',
                                borderLeft: isSelected ? '3px solid var(--color-primary, #3b82f6)'
                                    : isCompareFrom ? '3px solid #ef4444'
                                    : isCompareTo ? '3px solid #22c55e'
                                    : '3px solid transparent',
                                transition: 'background 0.15s',
                            }}
                        >
                            {/* Timeline dot */}
                            <div style={{
                                display: 'flex',
                                flexDirection: 'column',
                                alignItems: 'center',
                                paddingTop: 4,
                            }}>
                                <div style={{
                                    width: 10,
                                    height: 10,
                                    borderRadius: '50%',
                                    background: isPublished
                                        ? 'var(--color-primary, #3b82f6)'
                                        : 'rgba(148,163,184,0.4)',
                                    border: isPublished ? '2px solid rgba(59,130,246,0.3)' : 'none',
                                    flexShrink: 0,
                                }} />
                                {idx < sortedVersions.length - 1 && (
                                    <div style={{
                                        width: 1,
                                        flex: 1,
                                        minHeight: 20,
                                        background: 'rgba(148,163,184,0.15)',
                                        marginTop: 4,
                                    }} />
                                )}
                            </div>

                            {/* Version info */}
                            <div style={{ flex: 1, minWidth: 0 }}>
                                <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 4 }}>
                                    <span style={{ fontSize: 13, fontWeight: 600 }}>
                                        v{version.versionNo ?? '?'}
                                    </span>
                                    {isPublished && (
                                        <span style={{
                                            fontSize: 10,
                                            padding: '1px 6px',
                                            borderRadius: 999,
                                            background: 'rgba(34,197,94,0.15)',
                                            color: '#22c55e',
                                            fontWeight: 600,
                                        }}>
                                            当前发布
                                        </span>
                                    )}
                                    {version.status && version.status !== 'published' && (
                                        <span style={{ fontSize: 10, opacity: 0.5 }}>{version.status}</span>
                                    )}
                                </div>
                                <div style={{ fontSize: 11, opacity: 0.6 }}>
                                    {formatDate(version.publishedAt || version.createdAt)}
                                    {version.name ? ` · ${version.name}` : ''}
                                </div>
                                {version.description && (
                                    <div style={{ fontSize: 11, opacity: 0.5, marginTop: 2 }}>
                                        {version.description}
                                    </div>
                                )}
                            </div>
                        </div>
                    );
                })}
            </div>

            {/* Action bar (when a version is selected, non-diff mode) */}
            {selectedVersion && !diffMode && (
                <div style={{
                    padding: '12px 16px',
                    borderTop: '1px solid rgba(148,163,184,0.15)',
                    display: 'flex',
                    flexDirection: 'column',
                    gap: 8,
                }}>
                    <div style={{ fontSize: 12, display: 'flex', alignItems: 'center', gap: 6 }}>
                        <span style={{ fontWeight: 600 }}>选中:</span>
                        <span>v{selectedVersion.versionNo ?? '?'}</span>
                        <span style={{ opacity: 0.6 }}>
                            {formatDate(selectedVersion.publishedAt || selectedVersion.createdAt)}
                        </span>
                    </div>

                    {confirmingRollback ? (
                        <div style={{
                            padding: '10px 12px',
                            border: '1px solid rgba(239,68,68,0.3)',
                            borderRadius: 8,
                            background: 'rgba(239,68,68,0.06)',
                        }}>
                            <div style={{ fontSize: 12, marginBottom: 8 }}>
                                确认回滚到 v{selectedVersion.versionNo}？草稿和发布版本将恢复到此版本配置。
                            </div>
                            <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end' }}>
                                <button
                                    type="button"
                                    className="header-btn"
                                    style={{ fontSize: 11, padding: '4px 12px' }}
                                    onClick={() => setConfirmingRollback(false)}
                                >
                                    取消
                                </button>
                                <button
                                    type="button"
                                    className="header-btn"
                                    style={{
                                        fontSize: 11,
                                        padding: '4px 12px',
                                        background: '#ef4444',
                                        color: '#fff',
                                        border: 'none',
                                    }}
                                    disabled={loading}
                                    onClick={handleConfirmRollback}
                                >
                                    {loading ? '回滚中...' : '确认回滚'}
                                </button>
                            </div>
                        </div>
                    ) : (
                        <div style={{ display: 'flex', gap: 8 }}>
                            <button
                                type="button"
                                className="header-btn"
                                style={{ flex: 1, fontSize: 12, padding: '6px 0' }}
                                disabled={selectedVersion.currentPublished || loading}
                                onClick={handleRollbackClick}
                                title={selectedVersion.currentPublished ? '当前已是发布版本' : '回滚到此版本'}
                            >
                                回滚到此版本
                            </button>
                            <button
                                type="button"
                                className="header-btn"
                                style={{ flex: 1, fontSize: 12, padding: '6px 0' }}
                                disabled={loading}
                                onClick={() => {
                                    const pub = sortedVersions.find(v => v.currentPublished);
                                    const pubId = pub ? asId(pub.id) : '';
                                    setDiffMode(true);
                                    setCompareFromId(pubId || selectedVersionId);
                                    setCompareToId(pubId ? selectedVersionId : '');
                                }}
                            >
                                与发布版对比
                            </button>
                        </div>
                    )}
                </div>
            )}
        </div>
    );
}
