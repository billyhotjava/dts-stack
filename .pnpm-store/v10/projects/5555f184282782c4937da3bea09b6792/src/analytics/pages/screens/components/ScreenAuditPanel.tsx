import { useEffect, useMemo, useState, type CSSProperties } from 'react';
import { analyticsApi, type ScreenAuditEntry } from '../../../api/analyticsApi';
import { Modal } from 'antd';

interface ScreenAuditPanelProps {
	open: boolean;
	screenId?: string | number;
	onClose: () => void;
}

export function ScreenAuditPanel({ open, screenId, onClose }: ScreenAuditPanelProps) {
	const [loading, setLoading] = useState(false);
	const [error, setError] = useState<string | null>(null);
	const [limit, setLimit] = useState(200);
	const [rows, setRows] = useState<ScreenAuditEntry[]>([]);
	const [actionFilter, setActionFilter] = useState<string>('all');
	const [keyword, setKeyword] = useState('');
	const [onlyExportActions, setOnlyExportActions] = useState(false);
	const [autoRefresh, setAutoRefresh] = useState(true);
	const [refreshSeconds, setRefreshSeconds] = useState(20);

	const loadRows = async (targetLimit: number) => {
		if (!screenId) return;
		setLoading(true);
		setError(null);
		try {
			const data = await analyticsApi.getScreenAuditLogs(screenId, targetLimit);
			setRows(Array.isArray(data) ? data : []);
		} catch (e) {
			setRows([]);
			setError(e instanceof Error ? e.message : '加载审计日志失败');
		} finally {
			setLoading(false);
		}
	};

	useEffect(() => {
		if (!open || !screenId) return;
		loadRows(limit);
		// eslint-disable-next-line react-hooks/exhaustive-deps
	}, [open, screenId]);

	useEffect(() => {
		if (!open || !screenId || !autoRefresh || loading) {
			return;
		}
		const seconds = Number.isFinite(refreshSeconds) ? Math.max(5, Math.min(120, Math.floor(refreshSeconds))) : 20;
		const timer = window.setInterval(() => {
			void loadRows(limit);
		}, seconds * 1000);
		return () => window.clearInterval(timer);
	}, [autoRefresh, limit, loading, open, refreshSeconds, screenId]);

	const actionOptions = useMemo(() => {
		const set = new Set<string>();
		for (const row of rows) {
			const action = String(row.action || '').trim();
			if (action) set.add(action);
		}
		return Array.from(set).sort((a, b) => a.localeCompare(b, 'zh-CN'));
	}, [rows]);

	const filteredRows = useMemo(() => {
		const kw = keyword.trim().toLowerCase();
		return rows.filter((row) => {
			const action = String(row.action || '').trim();
			if (onlyExportActions && !action.startsWith('screen.export')) {
				return false;
			}
			if (actionFilter !== 'all' && action !== actionFilter) {
				return false;
			}
			if (!kw) return true;
			const actor = String(row.actorId ?? '');
			const req = String(row.requestId ?? '');
			const created = String(row.createdAt ?? '');
			const haystack = `${action} ${actor} ${req} ${created}`.toLowerCase();
			return haystack.includes(kw);
		});
	}, [actionFilter, keyword, onlyExportActions, rows]);

	const exportActionCount = useMemo(
		() => rows.filter((row) => String(row.action || '').startsWith('screen.export')).length,
		[rows],
	);

	return (
		<Modal open={open} onCancel={onClose} title="操作审计链路" width={960}>
			<div className="grid gap-2 mb-2.5" style={{ gridTemplateColumns: 'repeat(3, minmax(120px, 1fr))' }}>
				<MetricCell title="审计总数" value={String(rows.length)} />
				<MetricCell title="筛选后" value={String(filteredRows.length)} />
				<MetricCell title="导出事件" value={String(exportActionCount)} />
			</div>
			<div className="flex gap-2 mb-2.5 items-center">
				<span className="text-xs opacity-80">最大行数</span>
				<input
					type="number"
					min={1}
					max={1000}
					className="px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
					value={limit}
					onChange={(e) => {
						const n = Number(e.target.value);
						setLimit(Number.isFinite(n) ? Math.max(1, Math.min(1000, n)) : 200);
					}}
					style={{ width: 140 }}
				/>
				<button
					type="button"
					className="min-h-8 rounded-md border border-white/10 bg-white/5 text-text-primary px-3.5 text-xs hover:border-brand/30 hover:bg-brand/10 disabled:opacity-45 disabled:cursor-not-allowed"
					disabled={!screenId || loading}
					onClick={() => loadRows(limit)}
				>
					{loading ? '刷新中...' : '刷新'}
				</button>
				<label className="inline-flex items-center gap-1.5 text-xs opacity-90">
					<input
						type="checkbox"
						checked={autoRefresh}
						onChange={(e) => setAutoRefresh(e.target.checked)}
					/>
					自动刷新
				</label>
				<input
					type="number"
					min={5}
					max={120}
					className="px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand disabled:opacity-45"
					value={refreshSeconds}
					onChange={(e) => {
						const n = Number(e.target.value);
						setRefreshSeconds(Number.isFinite(n) ? Math.max(5, Math.min(120, n)) : 20);
					}}
					style={{ width: 100 }}
					title="自动刷新间隔(秒)"
					disabled={!autoRefresh}
				/>
			</div>

			<div className="grid gap-2 items-center mb-2.5" style={{ gridTemplateColumns: '220px 1fr auto' }}>
				<select
					className="flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
					value={actionFilter}
					onChange={(e) => setActionFilter(e.target.value)}
				>
					<option value="all">全部动作</option>
					{actionOptions.map((item) => (
						<option key={item} value={item}>{item}</option>
					))}
				</select>
				<input
					type="text"
					className="flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
					value={keyword}
					onChange={(e) => setKeyword(e.target.value)}
					placeholder="搜索 action / actor / requestId / 时间"
				/>
				<label className="inline-flex items-center gap-1.5 text-xs">
					<input
						type="checkbox"
						checked={onlyExportActions}
						onChange={(e) => setOnlyExportActions(e.target.checked)}
					/>
					仅导出事件
				</label>
			</div>

			{error && (
				<div className="border border-error bg-error/10 text-error rounded-lg p-2.5 mb-2.5 text-xs whitespace-pre-wrap">
					{error}
				</div>
			)}

			<div className="border border-border-default rounded-lg max-h-[360px] overflow-auto">
				<table className="w-full border-collapse text-xs">
					<thead>
						<tr className="sticky top-0 bg-surface-card">
							<th style={thStyle}>时间</th>
							<th style={thStyle}>操作者</th>
							<th style={thStyle}>动作</th>
							<th style={thStyle}>RequestId</th>
						</tr>
					</thead>
					<tbody>
						{filteredRows.map((row) => (
							<tr key={String(row.id)}>
								<td style={tdStyle}>{String(row.createdAt ?? '-')}</td>
								<td style={tdStyle}>{String(row.actorId ?? '-')}</td>
								<td style={tdStyle}>{String(row.action ?? '-')}</td>
								<td style={tdStyle}>{String(row.requestId ?? '-')}</td>
							</tr>
						))}
						{filteredRows.length === 0 && !loading && (
							<tr>
								<td style={tdStyle} colSpan={4}>暂无数据</td>
							</tr>
						)}
					</tbody>
				</table>
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
		<div className="border border-border-default rounded-lg p-2.5 bg-white/[0.02]">
			<div className="text-xs opacity-75 mb-1.5">{title}</div>
			<div className="text-base font-semibold">{value}</div>
		</div>
	);
}
