// @ts-nocheck — migrated from analytics-webapp, pending unused-import cleanup
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
		<div className="grid gap-1.5 text-[11px] px-2 py-1 border-b border-white/5 items-start" style={{ gridTemplateColumns: '140px 1fr 20px 1fr' }}>
			<div className="text-text-muted font-mono break-all">
				{change.path || '(root)'}
			</div>
			<div className="px-1.5 py-0.5 rounded bg-error/10 font-mono break-all">
				{formatDiffValue(change.oldValue)}
			</div>
			<div className="text-center text-text-muted/60">→</div>
			<div className="px-1.5 py-0.5 rounded bg-success/10 font-mono break-all">
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
		<div className="border border-border-default rounded-lg mb-1.5 overflow-hidden">
			<div
				className="flex items-center gap-2 px-3 py-2 bg-surface-muted/20"
				style={{ cursor: hasDetails ? 'pointer' : 'default' }}
				onClick={() => hasDetails && setExpanded(!expanded)}
			>
				{hasDetails && (
					<span className="text-[10px] opacity-60 transition-transform duration-150" style={{ transform: expanded ? 'rotate(90deg)' : 'none' }}>
						▶
					</span>
				)}
				<span className="text-[11px] px-2 py-px rounded-full font-semibold" style={{ background: badge.bg, color: badge.color }}>
					{badge.label}
				</span>
				<span className="text-xs font-semibold">{change.componentName}</span>
				<span className="text-[11px] opacity-60">({change.componentType})</span>
				{hasDetails && (
					<span className="text-[11px] opacity-50 ml-auto">
						{change.propertyChanges!.length} 处变更
					</span>
				)}
			</div>
			{expanded && hasDetails && (
				<div className="border-t border-white/5">
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
		<div className="border border-border-default rounded-lg mb-1.5 overflow-hidden">
			<div
				className="flex items-center gap-2 px-3 py-2 bg-surface-muted/20"
				style={{ cursor: hasDetails ? 'pointer' : 'default' }}
				onClick={() => hasDetails && setExpanded(!expanded)}
			>
				{hasDetails && (
					<span className="text-[10px] opacity-60 transition-transform duration-150" style={{ transform: expanded ? 'rotate(90deg)' : 'none' }}>
						▶
					</span>
				)}
				<span className="text-[11px] px-2 py-px rounded-full font-semibold" style={{ background: badge.bg, color: badge.color }}>
					{badge.label}
				</span>
				<span className="text-xs font-semibold">{change.key}</span>
				{change.label && <span className="text-[11px] opacity-60">({change.label})</span>}
			</div>
			{expanded && hasDetails && (
				<div className="border-t border-white/5">
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
			<div className="grid grid-cols-4 gap-2 mb-3">
				<SummaryCard label="新增" value={diff.summary.componentsAdded} color="#22c55e" />
				<SummaryCard label="删除" value={diff.summary.componentsRemoved} color="#ef4444" />
				<SummaryCard label="修改" value={diff.summary.componentsModified} color="#f59e0b" />
				<SummaryCard label="属性变更" value={diff.summary.totalPropertyChanges} color="#3b82f6" />
			</div>

			{/* Tabs */}
			<div className="flex gap-1 mb-2.5 border-b border-white/10">
				{tabs.map(tab => (
					<button
						key={tab.key}
						type="button"
						onClick={() => setActiveTab(tab.key)}
						className="bg-transparent border-none px-3 py-1.5 text-xs cursor-pointer"
						style={{
							borderBottom: activeTab === tab.key ? '2px solid var(--color-primary, #3b82f6)' : '2px solid transparent',
							color: activeTab === tab.key ? 'var(--color-primary, #3b82f6)' : 'inherit',
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
				<div className="max-h-[300px] overflow-y-auto">
					{diff.componentChanges.length === 0 ? (
						<div className="text-xs opacity-60 p-2.5 text-center">无组件变更</div>
					) : (
						diff.componentChanges.map(cc => (
							<ComponentChangeCard key={cc.componentId} change={cc} />
						))
					)}
				</div>
			)}
			{activeTab === 'canvas' && (
				<div className="max-h-[300px] overflow-y-auto">
					{diff.canvasChanges.length === 0 ? (
						<div className="text-xs opacity-60 p-2.5 text-center">画布属性无变化</div>
					) : (
						diff.canvasChanges.map((pc, idx) => (
							<PropertyChangeRow key={`${pc.path}-${idx}`} change={pc} />
						))
					)}
				</div>
			)}
			{activeTab === 'variables' && (
				<div className="max-h-[300px] overflow-y-auto">
					{diff.variableChanges.length === 0 ? (
						<div className="text-xs opacity-60 p-2.5 text-center">全局变量无变化</div>
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
		<div className="border border-border-default rounded-lg px-2.5 py-2 text-center">
			<div className="text-lg font-bold" style={{ color }}>{value}</div>
			<div className="text-[11px] opacity-70">{label}</div>
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
		<div className="fixed top-0 right-0 w-[480px] h-screen bg-surface-float border-l border-border-default z-[10000] flex flex-col shadow-[-4px_0_20px_rgba(0,0,0,0.3)]">
			{/* Header */}
			<div className="flex items-center justify-between px-4 py-3 border-b border-white/10">
				<div className="flex items-center gap-2.5">
					<span className="text-sm font-bold">版本历史</span>
					<span className="text-[11px] opacity-60">{versions.length} 个版本</span>
				</div>
				<div className="flex gap-1.5">
					<button
						type="button"
						className="min-h-8 rounded-md border border-white/10 bg-white/5 text-text-primary px-2.5 text-[11px] hover:border-brand/30 hover:bg-brand/10"
						style={{
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
					<button
						type="button"
						className="min-h-8 rounded-md border border-white/10 bg-white/5 text-text-primary px-2 text-sm hover:border-brand/30 hover:bg-brand/10"
						onClick={onClose}
					>
						✕
					</button>
				</div>
			</div>

			{/* Diff mode hint */}
			{diffMode && (
				<div className="px-4 py-2 bg-brand/10 border-b border-white/5 text-[11px] flex items-center gap-2">
					<span>点击选择两个版本进行对比</span>
					{compareFromId && (
						<span className="px-1.5 py-px rounded bg-error/15 text-[10px]">
							FROM: v{sortedVersions.find(v => asId(v.id) === compareFromId)?.versionNo ?? '?'}
						</span>
					)}
					{compareToId && (
						<span className="px-1.5 py-px rounded bg-success/15 text-[10px]">
							TO: v{sortedVersions.find(v => asId(v.id) === compareToId)?.versionNo ?? '?'}
						</span>
					)}
					{compareFromId && compareToId && (
						<button
							type="button"
							className="min-h-8 rounded-md border-transparent bg-gradient-to-br from-brand to-brand-dark text-white px-2.5 text-[11px] ml-auto disabled:opacity-45"
							disabled={loading}
							onClick={handleStartCompare}
						>
							{loading ? '对比中...' : '开始对比'}
						</button>
					)}
				</div>
			)}

			{/* Version timeline */}
			<div className="flex-1 overflow-y-auto py-2">
				{loading && versions.length === 0 && (
					<div className="text-center py-10 opacity-60 text-xs">加载中...</div>
				)}
				{!loading && versions.length === 0 && (
					<div className="text-center py-10 opacity-60 text-xs">暂无版本记录</div>
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
							className="flex gap-3 px-4 py-2.5 cursor-pointer transition-colors duration-150"
							style={{
								background: isSelected ? 'rgba(59,130,246,0.08)'
									: isCompareFrom ? 'rgba(239,68,68,0.06)'
									: isCompareTo ? 'rgba(34,197,94,0.06)'
									: 'transparent',
								borderLeft: isSelected ? '3px solid var(--color-primary, #3b82f6)'
									: isCompareFrom ? '3px solid #ef4444'
									: isCompareTo ? '3px solid #22c55e'
									: '3px solid transparent',
							}}
						>
							{/* Timeline dot */}
							<div className="flex flex-col items-center pt-1">
								<div
									className="w-2.5 h-2.5 rounded-full shrink-0"
									style={{
										background: isPublished
											? 'var(--color-primary, #3b82f6)'
											: 'rgba(148,163,184,0.4)',
										border: isPublished ? '2px solid rgba(59,130,246,0.3)' : 'none',
									}}
								/>
								{idx < sortedVersions.length - 1 && (
									<div className="w-px flex-1 min-h-[20px] bg-white/10 mt-1" />
								)}
							</div>

							{/* Version info */}
							<div className="flex-1 min-w-0">
								<div className="flex items-center gap-2 mb-1">
									<span className="text-[13px] font-semibold">
										v{version.versionNo ?? '?'}
									</span>
									{isPublished && (
										<span className="text-[10px] px-1.5 py-px rounded-full bg-success/15 text-success font-semibold">
											当前发布
										</span>
									)}
									{version.status && version.status !== 'published' && (
										<span className="text-[10px] opacity-50">{version.status}</span>
									)}
								</div>
								<div className="text-[11px] opacity-60">
									{formatDate(version.publishedAt || version.createdAt)}
									{version.name ? ` · ${version.name}` : ''}
								</div>
								{version.description && (
									<div className="text-[11px] opacity-50 mt-0.5">
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
				<div className="px-4 py-3 border-t border-white/10 flex flex-col gap-2">
					<div className="text-xs flex items-center gap-1.5">
						<span className="font-semibold">选中:</span>
						<span>v{selectedVersion.versionNo ?? '?'}</span>
						<span className="opacity-60">
							{formatDate(selectedVersion.publishedAt || selectedVersion.createdAt)}
						</span>
					</div>

					{confirmingRollback ? (
						<div className="px-3 py-2.5 border border-error/30 rounded-lg bg-error/5">
							<div className="text-xs mb-2">
								确认回滚到 v{selectedVersion.versionNo}？草稿和发布版本将恢复到此版本配置。
							</div>
							<div className="flex gap-2 justify-end">
								<button
									type="button"
									className="min-h-8 rounded-md border border-white/10 bg-white/5 text-text-primary px-3 text-[11px] hover:border-brand/30 hover:bg-brand/10"
									onClick={() => setConfirmingRollback(false)}
								>
									取消
								</button>
								<button
									type="button"
									className="min-h-8 rounded-md border-none px-3 text-[11px] text-white disabled:opacity-45"
									style={{ background: '#ef4444' }}
									disabled={loading}
									onClick={handleConfirmRollback}
								>
									{loading ? '回滚中...' : '确认回滚'}
								</button>
							</div>
						</div>
					) : (
						<div className="flex gap-2">
							<button
								type="button"
								className="flex-1 min-h-8 rounded-md border border-white/10 bg-white/5 text-text-primary text-xs py-1.5 hover:border-brand/30 hover:bg-brand/10 disabled:opacity-45 disabled:cursor-not-allowed"
								disabled={selectedVersion.currentPublished || loading}
								onClick={handleRollbackClick}
								title={selectedVersion.currentPublished ? '当前已是发布版本' : '回滚到此版本'}
							>
								回滚到此版本
							</button>
							<button
								type="button"
								className="flex-1 min-h-8 rounded-md border border-white/10 bg-white/5 text-text-primary text-xs py-1.5 hover:border-brand/30 hover:bg-brand/10 disabled:opacity-45"
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
