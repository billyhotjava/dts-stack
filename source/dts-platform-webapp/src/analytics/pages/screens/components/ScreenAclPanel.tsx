import { Modal } from 'antd';
import { ScreenGrantManager } from './ScreenGrantManager';

type Props = { open: boolean; screenId?: string | number; onClose: () => void; isOwner?: boolean };

export function ScreenAclPanel({ open, screenId, onClose, isOwner }: Props) {
	return (
		<Modal open={open} onCancel={onClose} title="权限管理" footer={null} width={780}
			styles={{ body: { maxHeight: '72vh', overflowY: 'auto' } }}>
			<ScreenGrantManager screenId={screenId} isOwner={isOwner} />
		</Modal>
	);
}
