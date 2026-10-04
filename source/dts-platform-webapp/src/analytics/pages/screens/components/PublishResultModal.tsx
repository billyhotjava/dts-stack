import { Modal, Button, ConfigProvider, message, theme as antdTheme } from 'antd';
import { CopyOutlined, CheckCircleOutlined } from '@ant-design/icons';
import { ScreenGrantManager } from './ScreenGrantManager';

type PublishInfo = {
	screenId: string | number;
	versionNo: number | string;
	previewUrl: string;
	publicUrl?: string;
	warmupText?: string;
};

type Props = {
	open: boolean;
	onClose: () => void;
	publishInfo: PublishInfo | null;
	isOwner?: boolean;
};

export function PublishResultModal({ open, onClose, publishInfo, isOwner }: Props) {
	if (!publishInfo) return null;

	const copyUrl = (url: string) => {
		navigator.clipboard.writeText(url).then(() => message.success('已复制'));
	};

	const LIGHT_TEXT = 'rgba(15, 23, 42, 0.92)';
	const LIGHT_TEXT_MUTED = 'rgba(15, 23, 42, 0.6)';
	return (
		<ConfigProvider
			theme={{
				algorithm: antdTheme.defaultAlgorithm,
				token: { zIndexPopupBase: 10050 },
			}}
		>
			<Modal open={open} onCancel={onClose} title={null} width={720}
				rootClassName="publish-result-modal-root"
				footer={<div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8 }}><Button onClick={onClose}>关闭</Button></div>}
				styles={{ body: { maxHeight: '76vh', overflowY: 'auto', color: LIGHT_TEXT } }}>
				<div className="publish-result-modal" style={{ display: 'grid', gap: 16, color: LIGHT_TEXT }}>
					{/* Publish info banner */}
					<div style={{ display: 'flex', alignItems: 'center', gap: 8, padding: '12px 16px', borderRadius: 8, background: 'rgba(34,197,94,0.08)', border: '1px solid rgba(34,197,94,0.2)' }}>
						<CheckCircleOutlined style={{ color: '#22c55e', fontSize: 20 }} />
						<div>
							<div style={{ fontWeight: 600, color: LIGHT_TEXT }}>已发布 v{publishInfo.versionNo}（大屏 #{publishInfo.screenId}）</div>
							{publishInfo.warmupText && <div style={{ fontSize: 12, color: LIGHT_TEXT_MUTED, marginTop: 2 }}>{publishInfo.warmupText}</div>}
						</div>
					</div>

					{/* Links */}
					<div>
						<div style={{ fontWeight: 500, marginBottom: 8, color: LIGHT_TEXT }}>链接地址</div>
						<div style={{ display: 'grid', gap: 8 }}>
							<LinkRow label="预览链接" url={publishInfo.previewUrl} onCopy={copyUrl} />
							{publishInfo.publicUrl && <LinkRow label="公开链接" url={publishInfo.publicUrl} onCopy={copyUrl} />}
						</div>
					</div>

					{/* Sharing & Permissions */}
					<div>
						<div style={{ fontWeight: 500, marginBottom: 8, color: LIGHT_TEXT }}>分享与权限</div>
						<ScreenGrantManager screenId={publishInfo.screenId} isOwner={isOwner} />
					</div>
				</div>
			</Modal>
		</ConfigProvider>
	);
}

function LinkRow({ label, url, onCopy }: { label: string; url: string; onCopy: (url: string) => void }) {
	return (
		<div style={{ display: 'flex', alignItems: 'center', gap: 8, padding: '6px 12px', borderRadius: 6, background: 'rgba(15, 23, 42, 0.04)', border: '1px solid rgba(15, 23, 42, 0.06)' }}>
			<span style={{ fontSize: 12, color: 'rgba(15, 23, 42, 0.6)', width: 60, flexShrink: 0 }}>{label}</span>
			<a href={url} target="_blank" rel="noopener noreferrer" style={{ flex: 1, fontSize: 12, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{url}</a>
			<Button size="small" icon={<CopyOutlined />} onClick={() => onCopy(url)}>复制</Button>
		</div>
	);
}
