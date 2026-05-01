import { Modal } from 'antd';
import { ClassificationSelect } from './ClassificationSelect';
import { ScreenGrantManager } from './ScreenGrantManager';

type Props = { open: boolean; screenId?: string | number; onClose: () => void; isOwner?: boolean };

export function ScreenAclPanel({ open, screenId, onClose, isOwner }: Props) {
	return (
		<Modal open={open} onCancel={onClose} title="权限管理" footer={null} width={780}
			styles={{ body: { maxHeight: '72vh', overflowY: 'auto' } }}>
			{/*
				Sprint-24 重构：「密级」是大屏元属性 + 权限维度，从编辑器画布设置面板
				迁移到这里。owner 在权限管理一处统一调整密级 + 共享名单 + 越级授权，
				避免视觉配置和访问控制混淆。
			*/}
			{screenId && (
				<div
					style={{
						marginBottom: 16,
						padding: '12px 14px',
						borderRadius: 8,
						border: '1px solid var(--color-border, rgba(0,0,0,0.08))',
						background: 'var(--color-surface-secondary, rgba(0,0,0,0.02))',
					}}
				>
					<div style={{ fontSize: 13, fontWeight: 600, marginBottom: 8 }}>大屏密级</div>
					<ClassificationSelect screenId={screenId} isOwner={isOwner} />
					<div style={{ fontSize: 12, color: 'var(--color-text-secondary, #6b7280)', marginTop: 8 }}>
						决定哪些人员密级可访问本大屏；可在下方共享名单对个别用户授予越级共享。
					</div>
				</div>
			)}
			<ScreenGrantManager screenId={screenId} isOwner={isOwner} />
		</Modal>
	);
}
