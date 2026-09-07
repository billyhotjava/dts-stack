import {
	isModelSpecReferenceTargetAllowed,
	type ModelSpecType,
	type ModelSpecView,
} from "@/features/modeling/contracts/modelSpecV2Contract";
import type { ModelSpecDraft } from "./services/modelWorkbenchService";

type Props = {
	draft: ModelSpecDraft;
	modelType: ModelSpecType;
	models: ModelSpecView[];
	readOnly?: boolean;
	onChange: (draft: ModelSpecDraft) => void;
};

/** Logical design references never select physical inputs or start an implementation. */
export function ModelLogicalDependencies({ draft, modelType, models, readOnly, onChange }: Props) {
	if (modelType === "SOURCE" || modelType === "DIMENSION") return null;
	const candidates = models.filter((model) => model.id !== draft.base?.id &&
		isModelSpecReferenceTargetAllowed({ modelType, planId: draft.planId }, model, "DEPENDENCY"));
	const replace = (id: string, revision: number | null) => onChange({
		...draft,
		dependsOn: [
			...draft.dependsOn.filter((reference) => reference.modelSpecId !== id),
			...(revision === null ? [] : [{ modelSpecId: id, revision }]),
		],
	});
	const missing = draft.dependsOn.filter((reference) => !candidates.some((model) => model.id === reference.modelSpecId));
	return (
		<section className="dmx-editor-panel" aria-label="上游模型设计">
			<h3>上游模型设计</h3>
			{candidates.map((model) => {
				const pinned = draft.dependsOn.find((reference) => reference.modelSpecId === model.id);
				return (
					<div key={model.id}>
						<label>
							<input type="checkbox" disabled={readOnly} checked={Boolean(pinned)}
								onChange={(event) => replace(model.id, event.target.checked ? model.revision : null)} />
							{model.name} · {model.layer} · 设计版本 r{pinned?.revision ?? model.revision}
						</label>
						{pinned && pinned.revision !== model.revision ? (
							<button type="button" disabled={readOnly} onClick={() => replace(model.id, model.revision)}>
								更新引用至 r{model.revision}
							</button>
						) : null}
					</div>
				);
			})}
			{missing.map((reference) => (
				<div key={reference.modelSpecId} role="alert">
					上游模型不可用，已保留设计版本 r{reference.revision}
					<button type="button" disabled={readOnly} onClick={() => replace(reference.modelSpecId, null)}>移除引用</button>
				</div>
			))}
			{!candidates.length && !missing.length ? <p>暂无可引用的上游模型设计</p> : null}
		</section>
	);
}
