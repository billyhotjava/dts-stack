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
import { toast } from 'sonner';

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
			toast.error('保存失败');
		}
	}, [screenId, editingSchedule, loadSchedules]);

	const handleDeleteSchedule = useCallback(async (scheduleId: string) => {
		if (!screenId || !window.confirm('确认删除该定时任务？')) return;
		try {
			await analyticsApi.deleteSnapshotSchedule(screenId, scheduleId);
			loadSchedules();
		} catch {
			toast.error('删除失败');
		}
	}, [screenId, loadSchedules]);

	if (!open) return null;

	const tabs: Array<{ key: TabKey; label: string }> = [
		{ key: 'manual', label: '手动截图' },
		{ key: 'schedules', label: '定时任务' },
		{ key: 'history', label: '执行历史' },
	];

	return (
		<div className="fixed top-0 right-0 w-[440px] h-screen bg-surface-float border-l border-border-default z-[10000] flex flex-col shadow-[-4px_0_20px_rgba(0,0,0,0.3)]">
			{/* Header */}
			<div className="flex items-center justify-between px-4 py-3 border-b border-white/10">
				<span className="text-sm font-bold">快照与报告</span>
				<button
					type="button"
					className="min-h-8 rounded-md border border-white/10 bg-white/5 text-text-primary px-2 text-sm hover:border-brand/30 hover:bg-brand/10"
					onClick={onClose}
				>
					✕
				</button>
			</div>

			{/* Tabs */}
			<div className="flex border-b border-white/10">
				{tabs.map(tab => (
					<button
						key={tab.key}
						type="button"
						onClick={() => setActiveTab(tab.key)}
						className="flex-1 py-2 text-xs bg-transparent border-none cursor-pointer"
						style={{
							borderBottom: activeTab === tab.key ? '2px solid var(--color-primary, #3b82f6)' : '2px solid transparent',
							color: activeTab === tab.key ? 'var(--color-primary, #3b82f6)' : 'inherit',
							fontWeight: activeTab === tab.key ? 600 : 400,
						}}
					>
						{tab.label}
					</button>
				))}
			</div>

			{/* Content */}
			<div className="flex-1 overflow-y-auto p-4">
				{activeTab === 'manual' && (
					<div>
						<div className="grid gap-2.5">
							<div>
								<label className="text-xs opacity-70 block mb-1">格式</label>
								<select className="w-full flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand" value={manualFormat} onChange={e => setManualFormat(e.target.value as 'png' | 'pdf')}>
									<option value="png">PNG</option>
									<option value="pdf">PDF</option>
								</select>
							</div>
							<div>
								<label className="text-xs opacity-70 block mb-1">设备模式</label>
								<select className="w-full flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand" value={manualDevice} onChange={e => setManualDevice(e.target.value as 'pc' | 'tablet' | 'mobile')}>
									<option value="pc">PC</option>
									<option value="tablet">平板</option>
									<option value="mobile">手机</option>
								</select>
							</div>
							<div>
								<label className="text-xs opacity-70 block mb-1">像素比</label>
								<select className="w-full flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand" value={manualPixelRatio} onChange={e => setManualPixelRatio(Number(e.target.value))}>
									<option value={1}>1x</option>
									<option value={2}>2x (推荐)</option>
									<option value={3}>3x</option>
								</select>
							</div>
							<div>
								<label className="text-xs opacity-70 block mb-1">等待延迟 (ms)</label>
								<input
									className="w-full flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
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
							className="w-full mt-3.5 py-2 min-h-8 rounded-md border-transparent bg-gradient-to-br from-brand to-brand-dark text-white text-xs font-semibold shadow-[0_16px_28px_rgba(80,158,227,0.24)] hover:from-[#468fd0] hover:to-[#275e95] disabled:opacity-45 disabled:cursor-not-allowed"
							disabled={manualLoading || !screenId}
							onClick={handleManualSnapshot}
						>
							{manualLoading ? '截图中...' : '立即截图'}
						</button>
						{manualResult && (
							<div className="mt-3 p-2.5 border border-border-default rounded-lg text-xs">
								<div>状态: <b>{manualResult.status}</b></div>
								{manualResult.error && <div className="text-error mt-1">{manualResult.error}</div>}
								{manualResult.resultUrl && (
									<a
										href={manualResult.resultUrl}
										target="_blank"
										rel="noreferrer"
										className="text-brand mt-1 inline-block"
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
							<div className="grid gap-2.5">
								<div>
									<label className="text-xs opacity-70 block mb-1">任务名称</label>
									<input
										className="w-full flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
										value={editingSchedule.name || ''}
										onChange={e => setEditingSchedule(prev => ({ ...prev, name: e.target.value }))}
										placeholder="每日报告"
									/>
								</div>
								<div>
									<label className="text-xs opacity-70 block mb-1">Cron 表达式</label>
									<input
										className="w-full flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
										value={editingSchedule.cron || ''}
										onChange={e => setEditingSchedule(prev => ({ ...prev, cron: e.target.value }))}
										placeholder="0 0 8 * * ?"
									/>
									<div className="flex gap-1 mt-1 flex-wrap">
										{CRON_PRESETS.map(p => (
											<button
												key={p.value}
												type="button"
												className="min-h-6 rounded-md border border-white/10 bg-white/5 text-text-primary px-1.5 text-[10px] hover:border-brand/30 hover:bg-brand/10"
												onClick={() => setEditingSchedule(prev => ({ ...prev, cron: p.value }))}
											>
												{p.label}
											</button>
										))}
									</div>
								</div>
								<div>
									<label className="text-xs opacity-70 block mb-1">格式</label>
									<select
										className="w-full flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
										value={editingSchedule.format || 'png'}
										onChange={e => setEditingSchedule(prev => ({ ...prev, format: e.target.value as 'png' | 'pdf' }))}
									>
										<option value="png">PNG</option>
										<option value="pdf">PDF</option>
									</select>
								</div>
								<div>
									<label className="text-xs opacity-70 block mb-1">分发方式</label>
									<select
										className="w-full flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
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
										<label className="text-xs opacity-70 block mb-1">收件人 (逗号分隔)</label>
										<input
											className="w-full flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
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
										<label className="text-xs opacity-70 block mb-1">Webhook URL</label>
										<input
											className="w-full flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
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
								<div className="flex gap-2 mt-1.5">
									<button
										type="button"
										className="min-h-8 rounded-md border border-white/10 bg-white/5 text-text-primary px-3.5 text-xs hover:border-brand/30 hover:bg-brand/10"
										onClick={() => setEditingSchedule(null)}
									>
										取消
									</button>
									<button
										type="button"
										className="min-h-8 rounded-md border-transparent bg-gradient-to-br from-brand to-brand-dark text-white px-3.5 text-xs shadow-[0_16px_28px_rgba(80,158,227,0.24)] hover:from-[#468fd0] hover:to-[#275e95]"
										onClick={handleSaveSchedule}
									>
										保存
									</button>
								</div>
							</div>
						) : (
							<div>
								<button
									type="button"
									className="w-full mb-3 py-1.5 min-h-8 rounded-md border-transparent bg-gradient-to-br from-brand to-brand-dark text-white text-xs font-semibold shadow-[0_16px_28px_rgba(80,158,227,0.24)] hover:from-[#468fd0] hover:to-[#275e95]"
									onClick={() => setEditingSchedule({ name: '', cron: '0 0 8 * * ?', format: 'png', device: 'pc', enabled: true })}
								>
									+ 新建定时任务
								</button>
								{schedulesLoading && <div className="text-xs opacity-60">加载中...</div>}
								{!schedulesLoading && schedules.length === 0 && (
									<div className="text-xs opacity-50 text-center py-5">
										暂无定时任务
									</div>
								)}
								{schedules.map(sch => (
									<div
										key={sch.id}
										className="px-3 py-2.5 border border-border-default rounded-lg mb-2"
									>
										<div className="flex justify-between items-center">
											<span className="text-[13px] font-semibold">{sch.name || '未命名'}</span>
											<span
												className="text-[10px] px-1.5 py-px rounded-full"
												style={{
													background: sch.enabled ? 'rgba(34,197,94,0.15)' : 'rgba(148,163,184,0.15)',
													color: sch.enabled ? '#22c55e' : 'inherit',
												}}
											>
												{sch.enabled ? '运行中' : '已停用'}
											</span>
										</div>
										<div className="text-[11px] opacity-60 mt-1">
											{sch.cron} · {sch.format.toUpperCase()} · {sch.distribution?.type || '无分发'}
										</div>
										<div className="flex gap-1.5 mt-1.5">
											<button
												type="button"
												className="min-h-6 rounded-md border border-white/10 bg-white/5 text-text-primary px-2 text-[10px] hover:border-brand/30 hover:bg-brand/10"
												onClick={() => setEditingSchedule(sch)}
											>
												编辑
											</button>
											<button
												type="button"
												className="min-h-6 rounded-md border border-white/10 bg-white/5 text-error px-2 text-[10px] hover:border-error/30 hover:bg-error/10"
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
							className="mb-2.5 min-h-8 rounded-md border border-white/10 bg-white/5 text-text-primary px-3.5 text-[11px] hover:border-brand/30 hover:bg-brand/10 disabled:opacity-45"
							onClick={loadHistory}
							disabled={historyLoading}
						>
							{historyLoading ? '加载中...' : '刷新'}
						</button>
						{!historyLoading && history.length === 0 && (
							<div className="text-xs opacity-50 text-center py-5">
								暂无执行记录
							</div>
						)}
						{history.map(task => (
							<div
								key={task.taskId}
								className="px-3 py-2 border border-border-default rounded-lg mb-1.5 text-xs"
							>
								<div className="flex justify-between">
									<span>{formatDate(task.createdAt)}</span>
									<span
										className="text-[10px] px-1.5 py-px rounded-full"
										style={{
											background: task.status === 'done' ? 'rgba(34,197,94,0.15)'
												: task.status === 'error' ? 'rgba(239,68,68,0.15)'
												: 'rgba(245,158,11,0.15)',
											color: task.status === 'done' ? '#22c55e'
												: task.status === 'error' ? '#ef4444'
												: '#f59e0b',
										}}
									>
										{task.status}
									</span>
								</div>
								<div className="opacity-60 mt-0.5">{task.format.toUpperCase()}</div>
								{task.error && <div className="text-error mt-0.5">{task.error}</div>}
								{task.resultUrl && (
									<a href={task.resultUrl} target="_blank" rel="noreferrer" className="text-brand mt-0.5 inline-block">
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
