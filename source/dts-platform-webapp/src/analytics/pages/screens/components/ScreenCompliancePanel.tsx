import { useEffect, useMemo, useState, type CSSProperties } from 'react';
import {
	analyticsApi,
	type ScreenCompliancePolicy,
	type ScreenComplianceReport,
} from '../../../api/analyticsApi';
import { SortableHeader } from '../../../components/SortableHeader';
import { dateComparator, stringComparator, useTableSort } from '../../../hooks/useTableSort';
import { Modal } from 'antd';

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

	const complianceRows = report?.rows ?? [];
	const complianceSortColumns = useMemo(
		() => ({
			createdAt: dateComparator<Record<string, unknown>>((r) => r.createdAt as string | null | undefined),
			screenId: stringComparator<Record<string, unknown>>((r) => String(r.screenId ?? '')),
			actorId: stringComparator<Record<string, unknown>>((r) => String(r.actorId ?? '')),
			action: stringComparator<Record<string, unknown>>((r) => String(r.action ?? '')),
			requestId: stringComparator<Record<string, unknown>>((r) => String(r.requestId ?? '')),
		}),
		[],
	);
	const {
		sortedItems: sortedComplianceRows,
		sortState: complianceSortState,
		requestSort: requestComplianceSort,
	} = useTableSort(complianceRows, {
		columns: complianceSortColumns,
		defaultSort: { key: 'createdAt', direction: 'desc' },
	});

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
		<Modal open={open} onCancel={onClose} title="合规策略中心" width={960}>
			{loading && <div className="text-xs opacity-80 mb-2.5">加载中...</div>}
			{error && (
				<div className="border border-error bg-error/10 text-error rounded-lg p-2.5 mb-3 text-xs leading-relaxed whitespace-pre-wrap">
					{error}
				</div>
			)}

			<div className="border border-border-default rounded-[10px] p-3 mb-3">
				<div className="font-semibold mb-2.5">策略开关</div>
				<div className="grid gap-2.5 mb-3" style={{ gridTemplateColumns: 'repeat(3, minmax(160px, 1fr))' }}>
					<label className="flex items-center gap-1.5 text-xs">
						<input
							type="checkbox"
							checked={policy?.maskingEnabled ?? false}
							onChange={(e) => setPolicy((prev) => ({ ...(prev || {}), maskingEnabled: e.target.checked }))}
						/>
						启用脱敏
					</label>
					<label className="flex items-center gap-1.5 text-xs">
						<input
							type="checkbox"
							checked={policy?.watermarkEnabled ?? true}
							onChange={(e) => setPolicy((prev) => ({ ...(prev || {}), watermarkEnabled: e.target.checked }))}
						/>
						启用水印
					</label>
					<label className="flex items-center gap-1.5 text-xs">
						<input
							type="checkbox"
							checked={policy?.exportApprovalRequired ?? false}
							onChange={(e) => setPolicy((prev) => ({ ...(prev || {}), exportApprovalRequired: e.target.checked }))}
						/>
						导出需审批
					</label>
				</div>

				<div className="grid gap-2.5 mb-2.5" style={{ gridTemplateColumns: '1fr 180px 180px' }}>
					<div>
						<div className="text-xs opacity-80 mb-1">水印文本</div>
						<input
							className="w-full flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
							value={policy?.watermarkText ?? 'DTS INTERNAL'}
							onChange={(e) => setPolicy((prev) => ({ ...(prev || {}), watermarkText: e.target.value }))}
						/>
					</div>
					<div>
						<div className="text-xs opacity-80 mb-1">审计保留天数</div>
						<input
							type="number"
							min={7}
							max={1095}
							className="w-full flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
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
					<div className="flex items-end gap-2">
						<button
							type="button"
							className="min-h-8 rounded-md border border-white/10 bg-white/5 text-text-primary px-3.5 text-xs hover:border-brand/30 hover:bg-brand/10 disabled:opacity-45 disabled:cursor-not-allowed"
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
							className="min-h-8 rounded-md border border-white/10 bg-white/5 text-text-primary px-3.5 text-xs hover:border-brand/30 hover:bg-brand/10"
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

			<div className="border border-border-default rounded-[10px] p-3">
				<div className="flex items-center justify-between mb-2.5">
					<div className="font-semibold">审计报表</div>
					<div className="flex gap-2">
						<button
							type="button"
							className="min-h-8 rounded-md border border-white/10 bg-white/5 text-text-primary px-3.5 text-xs hover:border-brand/30 hover:bg-brand/10 disabled:opacity-45"
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

				<div className="grid gap-2 items-center mb-2.5" style={{ gridTemplateColumns: '180px 140px 140px 1fr' }}>
					<select
						className="flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand disabled:opacity-45"
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
						className="flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
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
						className="flex-1 px-2.5 py-1.5 border border-border-default rounded bg-surface-card text-text-primary text-xs focus:outline-none focus:border-brand"
						value={limit}
						onChange={(e) => {
							const n = Number(e.target.value);
							setLimit(Number.isFinite(n) ? Math.max(1, Math.min(1000, n)) : 200);
						}}
						title="最大行数"
					/>

					<button
						type="button"
						className="min-h-8 rounded-md border border-white/10 bg-white/5 text-text-primary px-3.5 text-xs hover:border-brand/30 hover:bg-brand/10 disabled:opacity-45"
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

				<div className="grid gap-2 mb-2.5" style={{ gridTemplateColumns: 'repeat(4, minmax(120px, 1fr))' }}>
					<MetricCell title="总记录" value={String((report?.summary as Record<string, unknown> | undefined)?.total ?? report?.rows?.length ?? 0)} />
					<MetricCell title="范围" value={String(report?.scope ?? '-')} />
					<MetricCell title="天数" value={String(report?.days ?? '-')} />
					<MetricCell title="限制" value={String(report?.limit ?? '-')} />
				</div>

				<div className="border border-border-default rounded-lg max-h-[260px] overflow-auto">
					<table className="w-full border-collapse text-xs">
						<thead>
							<tr className="sticky top-0 bg-surface-card">
								<SortableHeader sortKey="createdAt" sortState={complianceSortState} onSort={requestComplianceSort} style={thStyle}>时间</SortableHeader>
								<SortableHeader sortKey="screenId" sortState={complianceSortState} onSort={requestComplianceSort} style={thStyle}>屏幕ID</SortableHeader>
								<SortableHeader sortKey="actorId" sortState={complianceSortState} onSort={requestComplianceSort} style={thStyle}>操作者</SortableHeader>
								<SortableHeader sortKey="action" sortState={complianceSortState} onSort={requestComplianceSort} style={thStyle}>动作</SortableHeader>
								<SortableHeader sortKey="requestId" sortState={complianceSortState} onSort={requestComplianceSort} style={thStyle}>RequestId</SortableHeader>
							</tr>
						</thead>
						<tbody>
							{sortedComplianceRows.map((row, idx) => (
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
		<div className="border border-border-default rounded-lg p-2.5 bg-white/[0.02]">
			<div className="text-xs opacity-75 mb-1.5">{title}</div>
			<div className="text-base font-semibold">{value}</div>
		</div>
	);
}
