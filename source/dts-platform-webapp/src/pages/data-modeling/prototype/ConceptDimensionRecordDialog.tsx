import { Button, Modal, RequestState, Status } from "./PrototypePrimitives";
import type { ConceptDimensionDraft } from "./services/modelWorkbenchService";

export type ConceptDimensionRecordDialogKind = "versions" | "releases" | null;

export function ConceptDimensionRecordDialog({
	dialog,
	draft,
	canMaintain,
	saving,
	onConfirm,
	onClose,
}: {
	dialog: ConceptDimensionRecordDialogKind;
	draft: ConceptDimensionDraft | null;
	canMaintain: boolean;
	saving: boolean;
	onConfirm: () => void;
	onClose: () => void;
}) {
	const definition = draft?.definitionBase;
	if (!dialog || !draft || !definition) return null;

	const versionFooter = (
		<>
			{dialog === "versions" && definition.status === "DRAFT" ? (
				<Button disabled={!canMaintain || saving} onClick={onConfirm} primary>
					{saving ? "确认中…" : "确认当前版本"}
				</Button>
			) : null}
			<Button disabled={saving} onClick={onClose}>
				关闭
			</Button>
		</>
	);

	return (
		<Modal footer={versionFooter} onClose={onClose} title={dialog === "versions" ? "维度版本管理" : "维度发布记录"}>
			<div className="dmx-capability-note">
				<p>系统编码：{definition.systemCode}</p>
				<p>
					当前版本：
					<Status tone={definition.status === "CURRENT" ? "success" : "warning"}>
						{definition.status} · r{definition.revision}
					</Status>
				</p>
			</div>
			{dialog === "versions" ? (
				<p>
					{definition.status === "DRAFT"
						? "确认后，该版本将成为维度表可绑定的当前版本。"
						: "当前版本已可供维度表绑定。"}
				</p>
			) : (
				<RequestState
					description="当前维度定义接口未提供独立发布记录；这里不展示模拟数据。"
					kind="empty"
					title="暂无发布记录"
				/>
			)}
		</Modal>
	);
}
