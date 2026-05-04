/**
 * Screenshot 按钮 — 占位实现。
 * 引入 html-to-image 会增加 ~30KB gz；本 Sprint 走 YAGNI 路线，先 disabled 留 hook，
 * 等 F4 真正落地保存/分享流程时再补依赖（见 worklog/F1-T06 文档）。
 */
export function ScreenshotButton() {
	return (
		<button
			type="button"
			className="workflow-operator__btn"
			disabled
			aria-disabled="true"
			aria-label="导出 PNG（功能开发中）"
			title="截图导出 — 待引入 html-to-image 后启用"
		>
			📷
		</button>
	);
}
