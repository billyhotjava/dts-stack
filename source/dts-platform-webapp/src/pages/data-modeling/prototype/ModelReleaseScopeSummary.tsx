import type { ReleaseCandidate, ReleaseCandidateEntryEvidence } from "@/api/modelSpecApi";

export function ModelReleaseScopeNotice({
	candidate,
	evidence,
	selectedCount,
	exactScope,
	confirmed,
	disabled,
	onConfirm,
}: {
	candidate: ReleaseCandidate | null;
	evidence: ReleaseCandidateEntryEvidence[];
	selectedCount: number;
	exactScope: boolean;
	confirmed: boolean;
	disabled: boolean;
	onConfirm: (confirmed: boolean) => void;
}) {
	if (!candidate || candidate.entries.length <= selectedCount) return null;
	return (
		<section aria-label="所属发布单操作范围" className="dmx-request-state">
			<p>
				当前查看 {selectedCount} 个模型，所属发布单包含 {candidate.entries.length}{" "}
				个模型。重新构建、质量验证、评审和发布操作作用于整个发布单。重新构建后需重新完成质量检查。
			</p>
			<details>
				<summary>查看本发布单全部 {candidate.entries.length} 个模型</summary>
				<ul style={{ maxHeight: 240, overflowY: "auto", overflowWrap: "anywhere" }}>
					{candidate.entries.map((entry) => (
						<li key={entry.modelSpecId}>
							{evidence.find((item) => item.modelSpecId === entry.modelSpecId)?.modelName || entry.modelSpecId} · r
							{entry.revision}
						</li>
					))}
				</ul>
			</details>
			{!exactScope ? (
				<label>
					<input
						type="checkbox"
						checked={confirmed}
						disabled={disabled}
						onChange={(event) => onConfirm(event.target.checked)}
					/>
					<span>我确认按以上全部模型执行重新构建、质量验证、评审和发布操作</span>
				</label>
			) : null}
		</section>
	);
}

export function ModelReleaseScopeSummary({
	status,
	actions,
	blocker,
}: {
	status: string;
	actions: string[];
	blocker?: string | null;
}) {
	return (
		<dl className="dmx-summary-list dmx-summary-list--compact">
			<dt>发布单状态</dt>
			<dd>{status}</dd>
			<dt>允许动作</dt>
			<dd>{actions.join("、") || "等待服务端推进或当前职责无可执行动作"}</dd>
			<dt>主要阻断</dt>
			<dd>{blocker || "无"}</dd>
		</dl>
	);
}
