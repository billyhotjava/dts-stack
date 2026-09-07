import { ModelDimensionHistoryFields } from "./ModelDimensionHistoryFields";
import { ModelImplementationBindingFields } from "./ModelImplementationBindingFields";
import { ModelImplementationExecutionFields } from "./ModelImplementationExecutionFields";
import type { ModelingWorkbenchEditorProps } from "./ModelingWorkbenchEditor";
import { Button } from "./PrototypePrimitives";
import type { ModelSpecDraft } from "./services/modelWorkbenchService";

export function ModelImplementationForm(props: ModelingWorkbenchEditorProps & { draft: ModelSpecDraft }) {
	const { draft, context, validationErrors, onChange } = props;
	const patch = (next: Partial<ModelSpecDraft>) => onChange({ ...draft, ...next });
	if (draft.implementationBase?.ownership === "DBT_MANAGED" && !draft.implementationInputMode) {
		const implementation = draft.implementationBase;
		return (
			<section className="dmx-editor-panel" aria-label="已导入的模型实现">
				<h3>已导入的模型实现</h3>
				<p>已保留导入的 SQL 和实现配置，可进入代码模式编辑，或继续构建与检查。</p>
				<div className="dmx-workbench-editor__basic-grid">
					<label>
						产出表英文名
						<input aria-label="产出表英文名" value={draft.physicalName} readOnly />
					</label>
					<label>
						存储方式
						<input
							aria-label="存储方式"
							value={
								draft.materialization === "table"
									? "表"
									: draft.materialization === "view"
										? "视图"
										: draft.materialization
							}
							readOnly
						/>
					</label>
					<label>
						加载策略
						<input aria-label="加载策略" value={draft.loadStrategy === "FULL" ? "全量" : draft.loadStrategy} readOnly />
					</label>
					<label>
						分区字段
						<input aria-label="分区字段" value={draft.partitionFields || "未设置"} readOnly />
					</label>
					<label>
						数据来源与加工方式
						<input aria-label="数据来源与加工方式" value="导入的 SQL 定义" readOnly />
					</label>
				</div>
				{implementation.inputs.map((input) =>
					"generatorType" in input && typeof input.config?.resourcePath === "string" ? (
						<p key={input.config.resourcePath}>模型文件：{input.config.resourcePath}</p>
					) : null,
				)}
				<Button onClick={() => props.onViewChange("code")}>编辑 SQL 实现</Button>
			</section>
		);
	}
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
