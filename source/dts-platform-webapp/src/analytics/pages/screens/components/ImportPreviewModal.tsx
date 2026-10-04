import { useState } from 'react';
import { Modal } from 'antd';
import type { ScreenConfig } from '../types';

type ImportAction = 'replace' | 'create-screen' | 'register-template';

type TemplateMeta = {
	name: string;
	description?: string;
	category?: string;
	tags?: string[];
};

export type ImportPreviewModalProps = {
	isOpen: boolean;
	onClose: () => void;
	fileName: string;
	parsedSpec: ScreenConfig;
	templateMeta?: TemplateMeta;
	validation: { errors: string[]; warnings: string[] };
	resourcesInlined: boolean;
	inlinedResourceCount: number;
	restoredResourceCount?: number;
	mode: 'editor' | 'marketplace' | 'list';
	onConfirm: (action: ImportAction) => void;
	allowedActions?: ImportAction[];
};

export function ImportPreviewModal({
	isOpen,
	onClose,
	fileName,
	parsedSpec,
	templateMeta,
	validation,
	resourcesInlined,
	inlinedResourceCount,
	restoredResourceCount = 0,
	mode,
	onConfirm,
	allowedActions,
}: ImportPreviewModalProps) {
	const defaultActions: Array<{ value: ImportAction; label: string; description: string }> =
		mode === 'editor'
			? [
				{ value: 'replace', label: '替换当前大屏', description: '用导入内容替换当前编辑中的大屏配置' },
				{ value: 'create-screen', label: '创建为新大屏', description: '保留当前大屏，将导入内容创建为新大屏' },
			]
			: mode === 'list'
			? [
				{ value: 'create-screen', label: '创建为新大屏', description: '使用导入内容创建一个新的大屏草稿，导入后自动跳转编辑器' },
			]
			: [
				{ value: 'register-template', label: '注册为模板', description: '将导入内容添加到模板库中' },
				{ value: 'create-screen', label: '创建为大屏', description: '直接用导入内容创建一个新大屏' },
			];
	const actions = allowedActions && allowedActions.length > 0
		? defaultActions.filter((item) => allowedActions.includes(item.value))
		: defaultActions;
	const [selectedAction, setSelectedAction] = useState<ImportAction>(
		actions[0]?.value ?? (mode === 'editor' ? 'replace' : 'create-screen')
	);
	const hasErrors = validation.errors.length > 0;

	const componentCount = parsedSpec.components?.length ?? 0;
	const pageCount = parsedSpec.pages?.length ?? 0;

	return (
		<Modal
			open={isOpen}
			onCancel={onClose}
			onOk={() => onConfirm(selectedAction)}
			title="导入预览"
			width={520}
			okText="确认导入"
			cancelText="取消"
			okButtonProps={{ disabled: hasErrors }}
		>
			<div className="grid" style={{ gap: 16 }}>
				<div className="text-sm text-secondary">
					文件: {fileName}
				</div>

				<div className="rounded-md" style={{ padding: '12px 16px', background: 'var(--color-bg-secondary)' }}>
					<div className="font-semibold" style={{ marginBottom: 8 }}>基本信息</div>
					<div className="grid grid-cols-2 text-sm" style={{ gap: '6px 24px' }}>
						<div>名称: <strong>{templateMeta?.name || parsedSpec.name || '未命名'}</strong></div>
						<div>尺寸: <strong>{parsedSpec.width ?? '?'} × {parsedSpec.height ?? '?'}</strong></div>
						<div>组件数: <strong>{componentCount}</strong>{pageCount > 1 ? ` (${pageCount} 页)` : ''}</div>
						<div>主题: <strong>{parsedSpec.theme || '默认'}</strong></div>
						{resourcesInlined && (
							<div>资源内联: <strong>是 ({inlinedResourceCount} 张图片)</strong></div>
						)}
						{restoredResourceCount > 0 && (
							<div>资源恢复: <strong>是 ({restoredResourceCount} 个资源)</strong></div>
						)}
						{templateMeta?.category && (
							<div>分类: <strong>{templateMeta.category}</strong></div>
						)}
					</div>
					{templateMeta?.tags && templateMeta.tags.length > 0 && (
						<div className="text-xs" style={{ marginTop: 8 }}>
							标签: {templateMeta.tags.map((tag) => (
								<span
									key={tag}
									className="inline-block rounded-xs"
									style={{ padding: '1px 8px', marginRight: 4, background: 'var(--color-bg-tertiary)' }}
								>
									{tag}
								</span>
							))}
						</div>
					)}
				</div>

				<div
					className="rounded-md"
					style={{
						padding: '12px 16px',
						background: hasErrors ? 'rgba(239,68,68,0.06)' : 'rgba(16,185,129,0.06)',
						border: hasErrors ? '1px solid rgba(239,68,68,0.2)' : '1px solid rgba(16,185,129,0.2)',
					}}
				>
					<div className="font-semibold" style={{ marginBottom: 4 }}>校验结果</div>
					{hasErrors ? (
						<div className="text-sm" style={{ color: '#b91c1c' }}>
							{validation.errors.map((e, i) => <div key={i}>✗ {e}</div>)}
						</div>
					) : (
						<div className="text-sm" style={{ color: '#047857' }}>✓ 配置格式合法</div>
					)}
					{validation.warnings.length > 0 && (
						<div className="text-sm" style={{ color: '#92400e', marginTop: 4 }}>
							{validation.warnings.map((w, i) => <div key={i}>⚠ {w}</div>)}
						</div>
					)}
				</div>

				{!hasErrors && (
					<div>
						<div className="font-semibold" style={{ marginBottom: 8 }}>导入方式</div>
						<div className="grid gap-sm">
							{actions.map((action) => (
								<label
									key={action.value}
									className="flex items-start cursor-pointer rounded-md"
									style={{
										gap: 8,
										padding: '10px 14px',
										border: selectedAction === action.value
											? '2px solid var(--color-brand)'
											: '1px solid var(--color-border)',
										background: selectedAction === action.value ? 'rgba(59,130,246,0.04)' : undefined,
									}}
								>
									<input
										type="radio"
										name="import-action"
										value={action.value}
										checked={selectedAction === action.value}
										onChange={() => setSelectedAction(action.value)}
										style={{ marginTop: 2 }}
									/>
									<div>
										<div className="font-medium">{action.label}</div>
										<div className="text-xs text-secondary">{action.description}</div>
									</div>
								</label>
							))}
						</div>
					</div>
				)}
			</div>
		</Modal>
	);
}
