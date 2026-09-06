import type { ModelingWorkbenchEditorProps } from "./ModelingWorkbenchEditor";
import type { ConceptDimensionDraft } from "./services/modelWorkbenchService";
import { resolveConceptDimensionPresentation } from "./modelWorkbenchPresentation";
type ConceptDimensionFormProps = Pick<ModelingWorkbenchEditorProps, "context" | "validationErrors" | "onChange"> & {
	draft: ConceptDimensionDraft;
};
function ValidationMessage({ message }: { message?: string }) {
	return message ? (
		<small className="dmx-workbench-editor__validation" role="alert">
			{message}
		</small>
	) : null;
}
export function ConceptDimensionForm(props: ConceptDimensionFormProps) {
	const { draft, context, validationErrors, onChange } = props;
	const patch = (next: Partial<ConceptDimensionDraft>) => onChange({ ...draft, ...next });
	const presentation = resolveConceptDimensionPresentation({ draft, domains: context.domains });

	return (
		<section className="dmx-editor-panel">
			<h3>基本信息</h3>
			<div className="dmx-workbench-editor__basic-grid">
				<label>
					<span>数仓分层</span>
					<input aria-label="数仓分层" disabled value={presentation.warehouseLayer} />
				</label>
				<label>
					<span className="required">数据域</span>
					<select
						aria-label="数据域"
						disabled={Boolean(draft.definitionBase)}
						onChange={(event) => patch({ domainId: event.target.value })}
						value={draft.domainId}
					>
						<option value="">请选择数据域</option>
						{context.domains
							.filter((item) => Boolean(item.parentCode))
							.map((item) => (
								<option key={item.id} value={item.id}>
									{item.name} · {item.code}
								</option>
							))}
					</select>
					<ValidationMessage message={validationErrors.domainId} />
				</label>
				<label>
					<span>系统编码</span>
					<input aria-label="系统编码" disabled value={presentation.systemCode} />
				</label>
				<label>
					<span className="required">中文名称</span>
					<input aria-label="中文名称" onChange={(event) => patch({ name: event.target.value })} value={draft.name} />
					<ValidationMessage message={validationErrors.name} />
				</label>
				<label className="dmx-workbench-editor__wide-field">
					<span>描述</span>
					<textarea
						aria-label="描述"
						onChange={(event) => patch({ description: event.target.value })}
						value={draft.description}
					/>
				</label>
			</div>
		</section>
	);
}
