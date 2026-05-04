/**
 * Undo / Redo 按钮 — 占位实现。
 * 真正的撤销/重做逻辑放到 F5-T05（zundo 中间件接入），届时把 disabled 解除并接 store action。
 */
export function UndoRedoButtons() {
	return (
		<div className="workflow-operator__group" role="group" aria-label="撤销与重做">
			<button
				type="button"
				className="workflow-operator__btn"
				disabled
				aria-disabled="true"
				aria-label="撤销（待 F5-T05 启用）"
				title="撤销 — F5-T05 zundo 接入后可用"
			>
				↶
			</button>
			<button
				type="button"
				className="workflow-operator__btn"
				disabled
				aria-disabled="true"
				aria-label="重做（待 F5-T05 启用）"
				title="重做 — F5-T05 zundo 接入后可用"
			>
				↷
			</button>
		</div>
	);
}
