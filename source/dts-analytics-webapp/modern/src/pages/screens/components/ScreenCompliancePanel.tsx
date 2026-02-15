import { useEffect, useMemo, useState, type CSSProperties } from 'react';
import {
    analyticsApi,
    type ScreenCompliancePolicy,
    type ScreenComplianceReport,
} from '../../../api/analyticsApi';
import { Modal } from '../../../ui/Modal/Modal';

interface ScreenCompliancePanelProps {
    open: boolean;
    screenId?: string | number;
    onClose: () => void;
}

export function ScreenCompliancePanel({ open, screenId, onClose }: ScreenCompliancePanelProps) {
    const [loading, setLoading] = useState(false);
    const [saving, setSaving] = useState(false);
    const [reporting, setReporting] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [policy, setPolicy] = useState<ScreenCompliancePolicy | null>(null);
    const [report, setReport] = useState<ScreenComplianceReport | null>(null);
    const [days, setDays] = useState<number>(30);
    const [limit, setLimit] = useState<number>(200);
    const [scope, setScope] = useState<'current' | 'all'>('current');

    const resolvedScope = useMemo(() => {
        if (!screenId) return 'all';
        return scope;
    }, [scope, screenId]);

    const loadPolicy = async () => {
        const res = await analyticsApi.getScreenCompliancePolicy();
        setPolicy(res);
    };

    const loadReport = async () => {
        setReporting(true);
        try {
            const result = await analyticsApi.getScreenComplianceReport({
                days,
                limit,
                screenId: resolvedScope === 'current' && screenId ? screenId : undefined,
            });
            setReport(result);
        } finally {
            setReporting(false);
        }
    };

    useEffect(() => {
        if (!open) return;

        let cancelled = false;
        const bootstrap = async () => {
            setLoading(true);
            setError(null);
            try {
                const [p, r] = await Promise.all([
                    analyticsApi.getScreenCompliancePolicy(),
                    analyticsApi.getScreenComplianceReport({
                        days: 30,
                        limit: 200,
                        screenId: screenId ? String(screenId) : undefined,
                    }),
                ]);
                if (cancelled) return;
                setPolicy(p);
                setReport(r);
            } catch (e) {
                if (!cancelled) {
                    setError(e instanceof Error ? e.message : '加载合规中心失败');
                }
            } finally {
                if (!cancelled) {
                    setLoading(false);
                }
            }
        };

        bootstrap();
        return () => {
            cancelled = true;
        };
    }, [open, screenId]);

    return (
        <Modal isOpen={open} onClose={onClose} title="合规策略中心" size="xl">
            {loading && <div style={{ fontSize: 12, opacity: 0.8, marginBottom: 10 }}>加载中...</div>}
            {error && (
                <div style={{
                    border: '1px solid #ef4444',
                    background: 'rgba(239,68,68,0.08)',
                    color: '#ef4444',
                    borderRadius: 8,
                    padding: 10,
                    marginBottom: 12,
                    fontSize: 12,
                    lineHeight: 1.5,
                    whiteSpace: 'pre-wrap',
                }}>
                    {error}
                </div>
            )}

            <div style={{ border: '1px solid var(--color-border)', borderRadius: 10, padding: 12, marginBottom: 12 }}>
                <div style={{ fontWeight: 600, marginBottom: 10 }}>策略开关</div>
                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, minmax(160px, 1fr))', gap: 10, marginBottom: 12 }}>
                    <label style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: 12 }}>
                        <input
                            type="checkbox"
                            checked={policy?.maskingEnabled ?? false}
                            onChange={(e) => setPolicy((prev) => ({ ...(prev || {}), maskingEnabled: e.target.checked }))}
                        />
                        启用脱敏
                    </label>
                    <label style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: 12 }}>
                        <input
                            type="checkbox"
                            checked={policy?.watermarkEnabled ?? true}
                            onChange={(e) => setPolicy((prev) => ({ ...(prev || {}), watermarkEnabled: e.target.checked }))}
                        />
                        启用水印
                    </label>
                    <label style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: 12 }}>
                        <input
                            type="checkbox"
                            checked={policy?.exportApprovalRequired ?? false}
                            onChange={(e) => setPolicy((prev) => ({ ...(prev || {}), exportApprovalRequired: e.target.checked }))}
                        />
                        导出需审批
                    </label>
                </div>

                <div style={{ display: 'grid', gridTemplateColumns: '1fr 180px 180px', gap: 10, marginBottom: 10 }}>
                    <div>
                        <div style={{ fontSize: 12, opacity: 0.8, marginBottom: 4 }}>水印文本</div>
                        <input
                            className="property-input"
                            value={policy?.watermarkText ?? 'DTS INTERNAL'}
                            onChange={(e) => setPolicy((prev) => ({ ...(prev || {}), watermarkText: e.target.value }))}
                        />
                    </div>
                    <div>
                        <div style={{ fontSize: 12, opacity: 0.8, marginBottom: 4 }}>审计保留天数</div>
                        <input
                            type="number"
                            min={7}
                            max={1095}
                            className="property-input"
                            value={policy?.auditRetentionDays ?? 180}
                            onChange={(e) => {
                                const n = Number(e.target.value);
                                setPolicy((prev) => ({
                                    ...(prev || {}),
                                    auditRetentionDays: Number.isFinite(n) ? Math.max(7, Math.min(1095, n)) : 180,
                                }));
                            }}
                        />
                    </div>
                    <div style={{ display: 'flex', alignItems: 'end', gap: 8 }}>
                        <button
                            type="button"
                            className="header-btn"
                            disabled={saving || !policy}
                            onClick={async () => {
                                if (!policy) return;
                                setSaving(true);
                                setError(null);
                                try {
                                    const saved = await analyticsApi.updateScreenCompliancePolicy(policy);
                                    setPolicy(saved);
                                } catch (e) {
                                    setError(e instanceof Error ? e.message : '保存合规策略失败');
                                } finally {
                                    setSaving(false);
                                }
                            }}
                        >
                            {saving ? '保存中...' : '保存策略'}
                        </button>
                        <button
                            type="button"
                            className="header-btn"
                            onClick={async () => {
                                setError(null);
                                try {
                                    await loadPolicy();
                                } catch (e) {
                                    setError(e instanceof Error ? e.message : '刷新策略失败');
                                }
                            }}
                        >
                            重新加载
                        </button>
                    </div>
                </div>
            </div>

            <div style={{ border: '1px solid var(--color-border)', borderRadius: 10, padding: 12 }}>
                <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 10 }}>
                    <div style={{ fontWeight: 600 }}>审计报表</div>
                    <div style={{ display: 'flex', gap: 8 }}>
                        <button
                            type="button"
                            className="header-btn"
                            disabled={!report}
                            onClick={() => {
                                if (!report) return;
                                const blob = new Blob([JSON.stringify(report, null, 2)], { type: 'application/json;charset=utf-8' });
                                const url = URL.createObjectURL(blob);
                                const a = document.createElement('a');
                                a.href = url;
                                a.download = `screen-compliance-report-${Date.now()}.json`;
                                a.click();
                                URL.revokeObjectURL(url);
                            }}
                        >
                            导出JSON
                        </button>
                    </div>
                </div>

                <div style={{ display: 'grid', gridTemplateColumns: '180px 140px 140px 1fr', gap: 8, alignItems: 'center', marginBottom: 10 }}>
                    <select
                        className="property-input"
                        value={resolvedScope}
                        disabled={!screenId}
                        onChange={(e) => setScope(e.target.value === 'all' ? 'all' : 'current')}
                    >
                        <option value="current">当前大屏</option>
                        <option value="all">全部大屏</option>
                    </select>

                    <input
                        type="number"
                        min={1}
                        max={3650}
                        className="property-input"
                        value={days}
                        onChange={(e) => {
                            const n = Number(e.target.value);
                            setDays(Number.isFinite(n) ? Math.max(1, Math.min(3650, n)) : 30);
                        }}
                        title="统计天数"
                    />

                    <input
                        type="number"
                        min={1}
                        max={1000}
                        className="property-input"
                        value={limit}
                        onChange={(e) => {
                            const n = Number(e.target.value);
                            setLimit(Number.isFinite(n) ? Math.max(1, Math.min(1000, n)) : 200);
                        }}
                        title="最大行数"
                    />

                    <button
                        type="button"
                        className="header-btn"
                        disabled={reporting}
                        onClick={async () => {
                            setError(null);
                            try {
                                await loadReport();
                            } catch (e) {
                                setError(e instanceof Error ? e.message : '加载审计报表失败');
                            }
                        }}
                    >
                        {reporting ? '加载中...' : '刷新报表'}
                    </button>
                </div>

                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, minmax(120px, 1fr))', gap: 8, marginBottom: 10 }}>
                    <MetricCell title="总记录" value={String((report?.summary as Record<string, unknown> | undefined)?.total ?? report?.rows?.length ?? 0)} />
                    <MetricCell title="范围" value={String(report?.scope ?? '-')} />
                    <MetricCell title="天数" value={String(report?.days ?? '-')} />
                    <MetricCell title="限制" value={String(report?.limit ?? '-')} />
                </div>

                <div style={{
                    border: '1px solid var(--color-border)',
                    borderRadius: 8,
                    maxHeight: 260,
                    overflow: 'auto',
                }}>
                    <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 12 }}>
                        <thead>
                            <tr style={{ position: 'sticky', top: 0, background: 'var(--color-surface)' }}>
                                <th style={thStyle}>时间</th>
                                <th style={thStyle}>屏幕ID</th>
                                <th style={thStyle}>操作者</th>
                                <th style={thStyle}>动作</th>
                                <th style={thStyle}>RequestId</th>
                            </tr>
                        </thead>
                        <tbody>
                            {(report?.rows ?? []).map((row, idx) => (
                                <tr key={String((row.id as string | number | undefined) ?? idx)}>
                                    <td style={tdStyle}>{String(row.createdAt ?? '-')}</td>
                                    <td style={tdStyle}>{String(row.screenId ?? '-')}</td>
                                    <td style={tdStyle}>{String(row.actorId ?? '-')}</td>
                                    <td style={tdStyle}>{String(row.action ?? '-')}</td>
                                    <td style={tdStyle}>{String(row.requestId ?? '-')}</td>
                                </tr>
                            ))}
                            {(report?.rows ?? []).length === 0 && (
                                <tr>
                                    <td style={tdStyle} colSpan={5}>暂无数据</td>
                                </tr>
                            )}
                        </tbody>
                    </table>
                </div>
            </div>
        </Modal>
    );
}

const thStyle: CSSProperties = {
    textAlign: 'left',
    padding: '8px 10px',
    borderBottom: '1px solid var(--color-border)',
    fontWeight: 600,
};

const tdStyle: CSSProperties = {
    textAlign: 'left',
    padding: '8px 10px',
    borderBottom: '1px solid var(--color-border)',
    verticalAlign: 'top',
};

function MetricCell({ title, value }: { title: string; value: string }) {
    return (
        <div style={{
            border: '1px solid var(--color-border)',
            borderRadius: 8,
            padding: 10,
            background: 'rgba(255,255,255,0.02)',
        }}>
            <div style={{ fontSize: 12, opacity: 0.75, marginBottom: 6 }}>{title}</div>
            <div style={{ fontSize: 16, fontWeight: 600 }}>{value}</div>
        </div>
    );
}
