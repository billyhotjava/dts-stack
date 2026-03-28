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
	mode: 'editor' | 'marketplace';
	onConfirm: (action: ImportAction) => void;
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
	mode,
	onConfirm,
}: ImportPreviewModalProps) {
	const [selectedAction, setSelectedAction] = useState<ImportAction>(
		mode === 'editor' ? 'replace' : 'create-screen'
	);
	const hasErrors = validation.errors.length > 0;

	const actions: Array<{ value: ImportAction; label: string; description: string }> =
		mode === 'editor'
			? [
				{ value: 'replace', label: '替换当前大屏', description: '用导入内容替换当前编辑中的大屏配置' },
				{ value: 'create-screen', label: '创建为新大屏', description: '保留当前大屏，将导入内容创建为新大屏' },
			]
			: [
				{ value: 'register-template', label: '注册为模板', description: '将导入内容添加到模板库中' },
				{ value: 'create-screen', label: '创建为大屏', description: '直接用导入内容创建一个新大屏' },
			];

	const componentCount = parsedSpec.components?.length ?? 0;
	const pageCount = parsedSpec.pages?.length ?? 0;

	return (
		<Modal
			open={isOpen}
			onCancel={onClose}
			title="导入预览"
			width={520}
			footer={
				<div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
					<button type="button" className="header-btn" onClick={onClose}>取消</button>
					<button
						type="button"
						className="header-btn save-btn"
						disabled={hasErrors}
						onClick={() => onConfirm(selectedAction)}
					>
						确认导入
					</button>
				</div>
			}
		>
			<div style={{ display: 'grid', gap: 16 }}>
				<div style={{ fontSize: 13, color: 'var(--color-text-secondary, #6b7280)' }}>
					文件: {fileName}
				</div>

				<div style={{ padding: '12px 16px', background: 'var(--color-bg-secondary, #f8f9fa)', borderRadius: 8 }}>
					<div style={{ fontWeight: 600, marginBottom: 8 }}>基本信息</div>
					<div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '6px 24px', fontSize: 13 }}>
						<div>名称: <strong>{templateMeta?.name || parsedSpec.name || '未命名'}</strong></div>
						<div>尺寸: <strong>{parsedSpec.width ?? '?'} × {parsedSpec.height ?? '?'}</strong></div>
						<div>组件数: <strong>{componentCount}</strong>{pageCount > 1 ? ` (${pageCount} 页)` : ''}</div>
						<div>主题: <strong>{parsedSpec.theme || '默认'}</strong></div>
						{resourcesInlined && (
							<div>资源内联: <strong>是 ({inlinedResourceCount} 张图片)</strong></div>
						)}
						{templateMeta?.category && (
							<div>分类: <strong>{templateMeta.category}</strong></div>
						)}
					</div>
					{templateMeta?.tags && templateMeta.tags.length > 0 && (
						<div style={{ marginTop: 8, fontSize: 12 }}>
							标签: {templateMeta.tags.map((tag) => (
								<span key={tag} style={{ display: 'inline-block', padding: '1px 8px', marginRight: 4, background: 'var(--color-bg-tertiary, #e5e7eb)', borderRadius: 4 }}>{tag}</span>
							))}
						</div>
					)}
				</div>

				<div style={{ padding: '12px 16px', background: hasErrors ? 'rgba(239,68,68,0.06)' : 'rgba(16,185,129,0.06)', borderRadius: 8, border: hasErrors ? '1px solid rgba(239,68,68,0.2)' : '1px solid rgba(16,185,129,0.2)' }}>
					<div style={{ fontWeight: 600, marginBottom: 4 }}>校验结果</div>
					{hasErrors ? (
						<div style={{ color: '#b91c1c', fontSize: 13 }}>
							{validation.errors.map((e, i) => <div key={i}>✗ {e}</div>)}
						</div>
					) : (
						<div style={{ color: '#047857', fontSize: 13 }}>✓ 配置格式合法</div>
					)}
					{validation.warnings.length > 0 && (
						<div style={{ color: '#92400e', fontSize: 13, marginTop: 4 }}>
							{validation.warnings.map((w, i) => <div key={i}>⚠ {w}</div>)}
						</div>
					)}
				</div>

				{!hasErrors && (
					<div>
						<div style={{ fontWeight: 600, marginBottom: 8 }}>导入方式</div>
						<div style={{ display: 'grid', gap: 8 }}>
							{actions.map((action) => (
								<label
									key={action.value}
									style={{
										display: 'flex',
										alignItems: 'flex-start',
										gap: 8,
										padding: '10px 14px',
										borderRadius: 8,
										border: selectedAction === action.value
											? '2px solid var(--color-primary, #3B82F6)'
											: '1px solid var(--color-border, #e0e0e0)',
										cursor: 'pointer',
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
										<div style={{ fontWeight: 500 }}>{action.label}</div>
										<div style={{ fontSize: 12, color: 'var(--color-text-secondary, #6b7280)' }}>{action.description}</div>
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
