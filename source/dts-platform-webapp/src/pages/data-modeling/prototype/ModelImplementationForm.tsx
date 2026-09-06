import { ModelDimensionHistoryFields } from "./ModelDimensionHistoryFields";
import { ModelImplementationBindingFields } from "./ModelImplementationBindingFields";
import { ModelImplementationExecutionFields } from "./ModelImplementationExecutionFields";
import type { ModelingWorkbenchEditorProps } from "./ModelingWorkbenchEditor";
import type { ModelSpecDraft } from "./services/modelWorkbenchService";

export function ModelImplementationForm(props: ModelingWorkbenchEditorProps & { draft: ModelSpecDraft }) {
	const { draft, context, validationErrors, onChange } = props;
	const patch = (next: Partial<ModelSpecDraft>) => onChange({ ...draft, ...next });
	return (
		<>
			<section className="dmx-editor-panel">
				<h3>目标与加载</h3>
				<div className="dmx-workbench-editor__basic-grid">
					<label>
						<span className="required">产出表英文名</span>
						<input
							aria-label="产出表英文名"
							value={draft.physicalName}
							onChange={(event) => patch({ physicalName: event.target.value })}
						/>
						{validationErrors.physicalName ? <small role="alert">{validationErrors.physicalName}</small> : null}
					</label>
					<ModelImplementationExecutionFields
						capabilities={context.implementationCapabilities}
						dimensionMode={draft.createKind === "dimension-table"}
						draft={draft}
						onChange={patch}
						partitionError={validationErrors.partitionFields}
					/>
				</div>
			</section>
			{draft.createKind === "dimension-table" ? <ModelDimensionHistoryFields draft={draft} onChange={patch} /> : null}
			<ModelImplementationBindingFields
				implementationOnly
				context={context}
				draft={draft}
				onChange={onChange}
				onSourcesChanged={props.onSourcesChanged}
				validationErrors={validationErrors}
			/>
		</>
	);
}
