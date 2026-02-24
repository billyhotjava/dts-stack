/**
 * ScreenSnapshotPanel — 定时快照与报告管理
 *
 * Provides UI for:
 * - Manual snapshot trigger (PNG/PDF)
 * - Snapshot schedule CRUD (cron, format, distribution)
 * - Execution history
 */
import { useState, useCallback, useEffect } from 'react';
import { analyticsApi } from '../../../api/analyticsApi';

/* ---------- types (mirror backend DTOs) ---------- */

interface SnapshotSchedule {
    id: string;
    name: string;
    cron: string;
    format: 'png' | 'pdf';
    device: 'pc' | 'tablet' | 'mobile';
    enabled: boolean;
    variables?: Record<string, string>;
    distribution?: {
        type: 'email' | 'webhook';
        recipients?: string[];
        webhookUrl?: string;
    };
    createdAt?: string;
}

interface SnapshotTask {
    taskId: string;
    status: 'pending' | 'running' | 'done' | 'error';
    format: 'png' | 'pdf';
    resultUrl?: string;
    createdAt?: string;
    error?: string;
}

interface ScreenSnapshotPanelProps {
    open: boolean;
    screenId?: string;
    onClose: () => void;
}

/* ---------- helpers ---------- */

function formatDate(value?: string | null): string {
    if (!value) return '-';
    try {
        const d = new Date(value);
        const pad = (n: number) => String(n).padStart(2, '0');
        return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
    } catch {
        return String(value);
    }
}

const CRON_PRESETS = [
    { label: '每小时', value: '0 0 * * * ?' },
    { label: '每天 8:00', value: '0 0 8 * * ?' },
    { label: '每天 18:00', value: '0 0 18 * * ?' },
    { label: '每周一 9:00', value: '0 0 9 ? * MON' },
    { label: '每月1日 8:00', value: '0 0 8 1 * ?' },
];

type TabKey = 'manual' | 'schedules' | 'history';

/* ---------- component ---------- */

export function ScreenSnapshotPanel({ open, screenId, onClose }: ScreenSnapshotPanelProps) {
    const [activeTab, setActiveTab] = useState<TabKey>('manual');

    // Manual snapshot state
    const [manualFormat, setManualFormat] = useState<'png' | 'pdf'>('png');
    const [manualDevice, setManualDevice] = useState<'pc' | 'tablet' | 'mobile'>('pc');
    const [manualPixelRatio, setManualPixelRatio] = useState(2);
    const [manualDelay, setManualDelay] = useState(2000);
    const [manualLoading, setManualLoading] = useState(false);
    const [manualResult, setManualResult] = useState<SnapshotTask | null>(null);

    // Schedule state
    const [schedules, setSchedules] = useState<SnapshotSchedule[]>([]);
    const [schedulesLoading, setSchedulesLoading] = useState(false);
    const [editingSchedule, setEditingSchedule] = useState<Partial<SnapshotSchedule> | null>(null);

    // History state
    const [history, setHistory] = useState<SnapshotTask[]>([]);
    const [historyLoading, setHistoryLoading] = useState(false);

    // Load schedules and history when panel opens
    useEffect(() => {
        if (!open || !screenId) return;
        loadSchedules();
        loadHistory();
    }, [open, screenId]);

    const loadSchedules = useCallback(async () => {
        if (!screenId) return;
        setSchedulesLoading(true);
        try {
            const result = await analyticsApi.listSnapshotSchedules(screenId);
            setSchedules(Array.isArray(result) ? result as SnapshotSchedule[] : []);
        } catch {
            // API may not exist yet (backend not implemented)
            setSchedules([]);
        } finally {
            setSchedulesLoading(false);
        }
    }, [screenId]);

    const loadHistory = useCallback(async () => {
        if (!screenId) return;
        setHistoryLoading(true);
        try {
            const result = await analyticsApi.listSnapshotTasks(screenId);
            setHistory(Array.isArray(result) ? result as SnapshotTask[] : []);
        } catch {
            setHistory([]);
        } finally {
            setHistoryLoading(false);
        }
    }, [screenId]);

    const handleManualSnapshot = useCallback(async () => {
        if (!screenId) return;
        setManualLoading(true);
        setManualResult(null);
        try {
            const result = await analyticsApi.createSnapshot(screenId, {
                format: manualFormat,
                device: manualDevice,
                pixelRatio: manualPixelRatio,
                delay: manualDelay,
                mode: 'published',
            }) as SnapshotTask;
            setManualResult(result);
            // Poll for completion
            if (result?.taskId) {
                pollSnapshotTask(result.taskId);
            }
        } catch (err) {
            setManualResult({ taskId: '', status: 'error', format: manualFormat, error: '截图请求失败' });
        } finally {
            setManualLoading(false);
        }
    }, [screenId, manualFormat, manualDevice, manualPixelRatio, manualDelay]);

    const pollSnapshotTask = useCallback(async (taskId: string) => {
        for (let i = 0; i < 30; i++) {
            await new Promise(r => setTimeout(r, 2000));
            try {
                const task = await analyticsApi.getSnapshotTask(taskId) as SnapshotTask;
                setManualResult(task);
                if (task?.status === 'done' || task?.status === 'error') return;
            } catch {
                return;
            }
        }
    }, []);

    const handleSaveSchedule = useCallback(async () => {
        if (!screenId || !editingSchedule) return;
        try {
            if (editingSchedule.id) {
                await analyticsApi.updateSnapshotSchedule(screenId, editingSchedule.id, editingSchedule);
            } else {
                await analyticsApi.createSnapshotSchedule(screenId, editingSchedule);
            }
            setEditingSchedule(null);
            loadSchedules();
        } catch {
            alert('保存失败');
        }
    }, [screenId, editingSchedule, loadSchedules]);

    const handleDeleteSchedule = useCallback(async (scheduleId: string) => {
        if (!screenId || !window.confirm('确认删除该定时任务？')) return;
        try {
            await analyticsApi.deleteSnapshotSchedule(screenId, scheduleId);
            loadSchedules();
        } catch {
            alert('删除失败');
        }
    }, [screenId, loadSchedules]);

    if (!open) return null;

    const tabs: Array<{ key: TabKey; label: string }> = [
        { key: 'manual', label: '手动截图' },
        { key: 'schedules', label: '定时任务' },
        { key: 'history', label: '执行历史' },
    ];

    return (
        <div style={{
            position: 'fixed',
            top: 0,
            right: 0,
            width: 440,
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
                <span style={{ fontSize: 14, fontWeight: 700 }}>快照与报告</span>
                <button type="button" className="header-btn" onClick={onClose} style={{ fontSize: 14, padding: '4px 8px' }}>
                    ✕
                </button>
            </div>

            {/* Tabs */}
            <div style={{ display: 'flex', borderBottom: '1px solid rgba(148,163,184,0.15)' }}>
                {tabs.map(tab => (
                    <button
                        key={tab.key}
                        type="button"
                        onClick={() => setActiveTab(tab.key)}
                        style={{
                            flex: 1,
                            padding: '8px 0',
                            fontSize: 12,
                            background: 'none',
                            border: 'none',
                            borderBottom: activeTab === tab.key ? '2px solid var(--color-primary, #3b82f6)' : '2px solid transparent',
                            color: activeTab === tab.key ? 'var(--color-primary, #3b82f6)' : 'inherit',
                            cursor: 'pointer',
                            fontWeight: activeTab === tab.key ? 600 : 400,
                        }}
                    >
                        {tab.label}
                    </button>
                ))}
            </div>

            {/* Content */}
            <div style={{ flex: 1, overflowY: 'auto', padding: 16 }}>
                {activeTab === 'manual' && (
                    <div>
                        <div style={{ display: 'grid', gap: 10 }}>
                            <div>
                                <label style={{ fontSize: 12, opacity: 0.7, display: 'block', marginBottom: 4 }}>格式</label>
                                <select className="property-input" value={manualFormat} onChange={e => setManualFormat(e.target.value as 'png' | 'pdf')}>
                                    <option value="png">PNG</option>
                                    <option value="pdf">PDF</option>
                                </select>
                            </div>
                            <div>
                                <label style={{ fontSize: 12, opacity: 0.7, display: 'block', marginBottom: 4 }}>设备模式</label>
                                <select className="property-input" value={manualDevice} onChange={e => setManualDevice(e.target.value as 'pc' | 'tablet' | 'mobile')}>
                                    <option value="pc">PC</option>
                                    <option value="tablet">平板</option>
                                    <option value="mobile">手机</option>
                                </select>
                            </div>
                            <div>
                                <label style={{ fontSize: 12, opacity: 0.7, display: 'block', marginBottom: 4 }}>像素比</label>
                                <select className="property-input" value={manualPixelRatio} onChange={e => setManualPixelRatio(Number(e.target.value))}>
                                    <option value={1}>1x</option>
                                    <option value={2}>2x (推荐)</option>
                                    <option value={3}>3x</option>
                                </select>
                            </div>
                            <div>
                                <label style={{ fontSize: 12, opacity: 0.7, display: 'block', marginBottom: 4 }}>等待延迟 (ms)</label>
                                <input
                                    className="property-input"
                                    type="number"
                                    min={500}
                                    max={10000}
                                    step={500}
                                    value={manualDelay}
                                    onChange={e => setManualDelay(Number(e.target.value) || 2000)}
                                />
                            </div>
                        </div>
                        <button
                            type="button"
                            className="header-btn save-btn"
                            style={{ width: '100%', marginTop: 14, padding: '8px 0' }}
                            disabled={manualLoading || !screenId}
                            onClick={handleManualSnapshot}
                        >
                            {manualLoading ? '截图中...' : '立即截图'}
                        </button>
                        {manualResult && (
                            <div style={{
                                marginTop: 12,
                                padding: 10,
                                border: '1px solid var(--color-border)',
                                borderRadius: 8,
                                fontSize: 12,
                            }}>
                                <div>状态: <b>{manualResult.status}</b></div>
                                {manualResult.error && <div style={{ color: '#ef4444', marginTop: 4 }}>{manualResult.error}</div>}
                                {manualResult.resultUrl && (
                                    <a
                                        href={manualResult.resultUrl}
                                        target="_blank"
                                        rel="noreferrer"
                                        style={{ color: '#3b82f6', marginTop: 4, display: 'inline-block' }}
                                    >
                                        下载截图
                                    </a>
                                )}
                            </div>
                        )}
                    </div>
                )}

                {activeTab === 'schedules' && (
                    <div>
                        {editingSchedule ? (
                            <div style={{ display: 'grid', gap: 10 }}>
                                <div>
                                    <label style={{ fontSize: 12, opacity: 0.7, display: 'block', marginBottom: 4 }}>任务名称</label>
                                    <input
                                        className="property-input"
                                        value={editingSchedule.name || ''}
                                        onChange={e => setEditingSchedule(prev => ({ ...prev, name: e.target.value }))}
                                        placeholder="每日报告"
                                    />
                                </div>
                                <div>
                                    <label style={{ fontSize: 12, opacity: 0.7, display: 'block', marginBottom: 4 }}>Cron 表达式</label>
                                    <input
                                        className="property-input"
                                        value={editingSchedule.cron || ''}
                                        onChange={e => setEditingSchedule(prev => ({ ...prev, cron: e.target.value }))}
                                        placeholder="0 0 8 * * ?"
                                    />
                                    <div style={{ display: 'flex', gap: 4, marginTop: 4, flexWrap: 'wrap' }}>
                                        {CRON_PRESETS.map(p => (
                                            <button
                                                key={p.value}
                                                type="button"
                                                className="header-btn"
                                                style={{ fontSize: 10, padding: '2px 6px' }}
                                                onClick={() => setEditingSchedule(prev => ({ ...prev, cron: p.value }))}
                                            >
                                                {p.label}
                                            </button>
                                        ))}
                                    </div>
                                </div>
                                <div>
                                    <label style={{ fontSize: 12, opacity: 0.7, display: 'block', marginBottom: 4 }}>格式</label>
                                    <select
                                        className="property-input"
                                        value={editingSchedule.format || 'png'}
                                        onChange={e => setEditingSchedule(prev => ({ ...prev, format: e.target.value as 'png' | 'pdf' }))}
                                    >
                                        <option value="png">PNG</option>
                                        <option value="pdf">PDF</option>
                                    </select>
                                </div>
                                <div>
                                    <label style={{ fontSize: 12, opacity: 0.7, display: 'block', marginBottom: 4 }}>分发方式</label>
                                    <select
                                        className="property-input"
                                        value={editingSchedule.distribution?.type || 'email'}
                                        onChange={e => setEditingSchedule(prev => ({
                                            ...prev,
                                            distribution: { ...prev?.distribution, type: e.target.value as 'email' | 'webhook' },
                                        }))}
                                    >
                                        <option value="email">邮件</option>
                                        <option value="webhook">Webhook</option>
                                    </select>
                                </div>
                                {editingSchedule.distribution?.type === 'email' && (
                                    <div>
                                        <label style={{ fontSize: 12, opacity: 0.7, display: 'block', marginBottom: 4 }}>收件人 (逗号分隔)</label>
                                        <input
                                            className="property-input"
                                            value={(editingSchedule.distribution?.recipients || []).join(', ')}
                                            onChange={e => setEditingSchedule(prev => ({
                                                ...prev,
                                                distribution: {
                                                    ...prev?.distribution,
                                                    type: prev?.distribution?.type || 'email',
                                                    recipients: e.target.value.split(',').map(s => s.trim()).filter(Boolean),
                                                },
                                            }))}
                                            placeholder="user@example.com"
                                        />
                                    </div>
                                )}
                                {editingSchedule.distribution?.type === 'webhook' && (
                                    <div>
                                        <label style={{ fontSize: 12, opacity: 0.7, display: 'block', marginBottom: 4 }}>Webhook URL</label>
                                        <input
                                            className="property-input"
                                            value={editingSchedule.distribution?.webhookUrl || ''}
                                            onChange={e => setEditingSchedule(prev => ({
                                                ...prev,
                                                distribution: {
                                                    ...prev?.distribution,
                                                    type: 'webhook',
                                                    webhookUrl: e.target.value,
                                                },
                                            }))}
                                            placeholder="https://oapi.dingtalk.com/robot/send?access_token=..."
                                        />
                                    </div>
                                )}
                                <div style={{ display: 'flex', gap: 8, marginTop: 6 }}>
                                    <button type="button" className="header-btn" onClick={() => setEditingSchedule(null)}>
                                        取消
                                    </button>
                                    <button type="button" className="header-btn save-btn" onClick={handleSaveSchedule}>
                                        保存
                                    </button>
                                </div>
                            </div>
                        ) : (
                            <div>
                                <button
                                    type="button"
                                    className="header-btn save-btn"
                                    style={{ width: '100%', marginBottom: 12, padding: '6px 0' }}
                                    onClick={() => setEditingSchedule({ name: '', cron: '0 0 8 * * ?', format: 'png', device: 'pc', enabled: true })}
                                >
                                    + 新建定时任务
                                </button>
                                {schedulesLoading && <div style={{ fontSize: 12, opacity: 0.6 }}>加载中...</div>}
                                {!schedulesLoading && schedules.length === 0 && (
                                    <div style={{ fontSize: 12, opacity: 0.5, textAlign: 'center', padding: 20 }}>
                                        暂无定时任务
                                    </div>
                                )}
                                {schedules.map(sch => (
                                    <div
                                        key={sch.id}
                                        style={{
                                            padding: '10px 12px',
                                            border: '1px solid var(--color-border)',
                                            borderRadius: 8,
                                            marginBottom: 8,
                                        }}
                                    >
                                        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                                            <span style={{ fontSize: 13, fontWeight: 600 }}>{sch.name || '未命名'}</span>
                                            <span style={{
                                                fontSize: 10,
                                                padding: '1px 6px',
                                                borderRadius: 999,
                                                background: sch.enabled ? 'rgba(34,197,94,0.15)' : 'rgba(148,163,184,0.15)',
                                                color: sch.enabled ? '#22c55e' : 'inherit',
                                            }}>
                                                {sch.enabled ? '运行中' : '已停用'}
                                            </span>
                                        </div>
                                        <div style={{ fontSize: 11, opacity: 0.6, marginTop: 4 }}>
                                            {sch.cron} · {sch.format.toUpperCase()} · {sch.distribution?.type || '无分发'}
                                        </div>
                                        <div style={{ display: 'flex', gap: 6, marginTop: 6 }}>
                                            <button
                                                type="button"
                                                className="header-btn"
                                                style={{ fontSize: 10, padding: '2px 8px' }}
                                                onClick={() => setEditingSchedule(sch)}
                                            >
                                                编辑
                                            </button>
                                            <button
                                                type="button"
                                                className="header-btn"
                                                style={{ fontSize: 10, padding: '2px 8px', color: '#ef4444' }}
                                                onClick={() => handleDeleteSchedule(sch.id)}
                                            >
                                                删除
                                            </button>
                                        </div>
                                    </div>
                                ))}
                            </div>
                        )}
                    </div>
                )}

                {activeTab === 'history' && (
                    <div>
                        <button
                            type="button"
                            className="header-btn"
                            style={{ marginBottom: 10, fontSize: 11 }}
                            onClick={loadHistory}
                            disabled={historyLoading}
                        >
                            {historyLoading ? '加载中...' : '刷新'}
                        </button>
                        {!historyLoading && history.length === 0 && (
                            <div style={{ fontSize: 12, opacity: 0.5, textAlign: 'center', padding: 20 }}>
                                暂无执行记录
                            </div>
                        )}
                        {history.map(task => (
                            <div
                                key={task.taskId}
                                style={{
                                    padding: '8px 12px',
                                    border: '1px solid var(--color-border)',
                                    borderRadius: 8,
                                    marginBottom: 6,
                                    fontSize: 12,
                                }}
                            >
                                <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                                    <span>{formatDate(task.createdAt)}</span>
                                    <span style={{
                                        fontSize: 10,
                                        padding: '1px 6px',
                                        borderRadius: 999,
                                        background: task.status === 'done' ? 'rgba(34,197,94,0.15)'
                                            : task.status === 'error' ? 'rgba(239,68,68,0.15)'
                                            : 'rgba(245,158,11,0.15)',
                                        color: task.status === 'done' ? '#22c55e'
                                            : task.status === 'error' ? '#ef4444'
                                            : '#f59e0b',
                                    }}>
                                        {task.status}
                                    </span>
                                </div>
                                <div style={{ opacity: 0.6, marginTop: 2 }}>{task.format.toUpperCase()}</div>
                                {task.error && <div style={{ color: '#ef4444', marginTop: 2 }}>{task.error}</div>}
                                {task.resultUrl && (
                                    <a href={task.resultUrl} target="_blank" rel="noreferrer" style={{ color: '#3b82f6', marginTop: 2, display: 'inline-block' }}>
                                        下载
                                    </a>
                                )}
                            </div>
                        ))}
                    </div>
                )}
            </div>
        </div>
    );
}
