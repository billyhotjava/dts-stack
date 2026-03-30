import { Modal } from 'antd';
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
		return <div className="text-xs opacity-70">{emptyText}</div>;
	}
	return (
		<div className="flex flex-wrap gap-1.5">
			{values.map((item) => (
				<span
					key={item}
					className="text-xs px-2 py-0.5 rounded-full border border-border-default bg-surface-muted/30"
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
		<Modal open={open} onCancel={onClose} title="版本差异详情" width={960}>
			<div className="flex justify-end gap-2 mb-2.5">
				<button
					type="button"
					className="min-h-8 rounded-md border border-white/10 bg-white/5 text-text-primary px-3.5 text-xs hover:border-brand/30 hover:bg-brand/10 disabled:opacity-45 disabled:cursor-not-allowed"
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
					className="min-h-8 rounded-md border border-white/10 bg-white/5 text-text-primary px-3.5 text-xs hover:border-brand/30 hover:bg-brand/10 disabled:opacity-45 disabled:cursor-not-allowed"
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
			<div className="grid grid-cols-4 gap-2 mb-3">
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
					<div className="text-xs opacity-70">无</div>
				) : (
					<div className="grid gap-1.5">
						{changedTypeComponents.map((item, idx) => (
							<div
								key={`${item.id || 'id'}-${idx}`}
								className="text-xs px-2.5 py-2 rounded-lg border border-border-default bg-surface-muted/30"
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
		<div className="border border-border-default rounded-lg p-2.5">
			<div className="text-xs opacity-75 mb-1.5">{title}</div>
			<div className="text-[15px] font-semibold">{value}</div>
		</div>
	);
}

function Section({ title, children }: { title: string; children: ReactNode }) {
	return (
		<div className="mb-3">
			<div className="font-semibold mb-1.5">{title}</div>
			{children}
		</div>
	);
}
