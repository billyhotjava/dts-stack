import type { BlockDef } from "../block-selector";

export function PanelHeader({
	block,
	locked,
	onLockToggle,
	onClose,
}: {
	block: BlockDef;
	locked: boolean;
	onLockToggle: () => void;
	onClose: () => void;
}) {
	return (
		<header className="workflow-panel-header">
			<span className="workflow-panel-header__icon" style={{ color: block.color }} aria-hidden="true">
				{block.icon}
			</span>
			<div className="workflow-panel-header__title">
				<strong>{block.label}</strong>
				<span>{block.description}</span>
			</div>
			<button
				type="button"
				className="workflow-panel-icon-button"
				onClick={onLockToggle}
				aria-pressed={locked}
				aria-label="锁定面板"
			>
				{locked ? "锁" : "解"}
			</button>
			<button type="button" className="workflow-panel-icon-button" onClick={onClose} aria-label="关闭面板">
				×
			</button>
		</header>
	);
}
