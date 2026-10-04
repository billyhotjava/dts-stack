import { useMemo, useState } from "react";
import type { DbtArchiveInspection, ModelSpecImportSemanticOverride } from "@/api/modelSpecImportApi";
import type { ModelingImportContext } from "@/api/services/modelingImportContextService";
import { applyModelSecurityLevel } from "./ReverseModelingInspectionSteps";

export type ImportBatchDefaults = {
	domainId: string;
	businessProcessId: string;
	dataMartId: string;
	subjectDomainId: string;
	securityLevel: string;
};
export const EMPTY_IMPORT_DEFAULTS: ImportBatchDefaults = {
	domainId: "",
	businessProcessId: "",
	dataMartId: "",
	subjectDomainId: "",
	securityLevel: "",
};
export function initialImportOverrides(inspection: DbtArchiveInspection) {
	return Object.fromEntries(
		inspection.package.models.map((model) => [
			model.dbtUniqueId,
			{
				modelUniqueId: model.dbtUniqueId,
				modelType: model.semantics?.modelType || undefined,
				layer: model.semantics?.layer || undefined,
				businessName: model.name,
				businessDefinition: model.description || undefined,
				grain: model.semantics?.grain?.statement
					? { statement: model.semantics.grain.statement, keys: model.semantics.grain.keys || [] }
					: undefined,
			},
		]),
	);
}
export function resolveImportDefaults(
	inspection: DbtArchiveInspection | null,
	overrides: Record<string, ModelSpecImportSemanticOverride>,
	defaults: ImportBatchDefaults,
	custom: string[],
) {
	return Object.fromEntries(
		(inspection?.package.models || []).map((model) => {
			const current = overrides[model.dbtUniqueId] || { modelUniqueId: model.dbtUniqueId };
			if (custom.includes(model.dbtUniqueId)) return [model.dbtUniqueId, current];
			const type = current.modelType || model.semantics?.modelType;
			const next = {
				...current,
				businessProcessId: type === "FACT" ? defaults.businessProcessId || undefined : undefined,
				dataMartId: type === "APPLICATION" ? defaults.dataMartId || undefined : undefined,
				subjectDomainId: type === "APPLICATION" ? defaults.subjectDomainId || undefined : undefined,
			};
			return [
				model.dbtUniqueId,
				applyModelSecurityLevel(
					next,
					(model.columns || []).map((c) => c.name),
					defaults.securityLevel,
				),
			];
		}),
	);
}
export function useImportBatchSettings(
	inspection: DbtArchiveInspection | null,
	overrides: Record<string, ModelSpecImportSemanticOverride>,
) {
	const [defaults, setDefaults] = useState<ImportBatchDefaults>(EMPTY_IMPORT_DEFAULTS);
	const [custom, setCustom] = useState<string[]>([]);
	const effective = useMemo(
		() => resolveImportDefaults(inspection, overrides, defaults, custom),
		[inspection, overrides, defaults, custom],
	);
	return {
		defaults,
		setDefaults,
		custom,
		effective,
		customize: (id: string) => setCustom((current) => (current.includes(id) ? current : [...current, id])),
		inherit: (id: string) => setCustom((current) => current.filter((value) => value !== id)),
		reset: () => {
			setDefaults(EMPTY_IMPORT_DEFAULTS);
			setCustom([]);
		},
	};
}
export type ImportBatchControls = {
	defaults: ImportBatchDefaults;
	custom: string[];
	onChange: (next: ImportBatchDefaults) => void;
	onInherit: (id: string) => void;
};
export function ReverseImportBatchSettings({
	controls,
	context,
}: {
	controls: ImportBatchControls;
	context: Pick<ModelingImportContext, "domains" | "businessProcesses" | "dataMarts" | "subjectDomains">;
}) {
	const { defaults, onChange } = controls;
	const patch = (value: Partial<ImportBatchDefaults>) => onChange({ ...defaults, ...value });
	return (
		<section className="dmx-import-batch-settings">
			<h4>整包统一设置</h4>
			<p>设置一次，模型默认继承。修改单个模型后保留其自定义配置，可随时恢复整包设置。</p>
			<div className="dmx-mapping-grid">
				<label>
					<span>数据域</span>
					<select
						aria-label="整包数据域"
						value={defaults.domainId}
						onChange={(e) => patch({ domainId: e.target.value, businessProcessId: "" })}
					>
						<option value="">请选择数据域</option>
						{context.domains.map((d) => (
							<option key={d.domainId} value={d.domainId}>
								{d.name || d.code}
							</option>
						))}
					</select>
				</label>
				<label>
					<span>业务过程（明细表）</span>
					<select
						aria-label="整包业务过程"
						value={defaults.businessProcessId}
						onChange={(e) => patch({ businessProcessId: e.target.value })}
					>
						<option value="">请选择业务过程</option>
						{context.businessProcesses
							.filter((p) => p.domainId === defaults.domainId)
							.map((p) => (
								<option key={p.id} value={p.id}>
									{p.name} · {p.processId}
								</option>
							))}
					</select>
				</label>
				<label>
					<span>数据集市（应用表）</span>
					<select
						aria-label="整包数据集市"
						value={defaults.dataMartId}
						onChange={(e) => patch({ dataMartId: e.target.value, subjectDomainId: "" })}
					>
						<option value="">请选择数据集市</option>
						{context.dataMarts.map((m) => (
							<option key={m.id} value={m.id}>
								{m.name} · {m.code}
							</option>
						))}
					</select>
				</label>
				<label>
					<span>主题域（应用表）</span>
					<select
						aria-label="整包主题域"
						disabled={!defaults.dataMartId}
						value={defaults.subjectDomainId}
						onChange={(e) => patch({ subjectDomainId: e.target.value })}
					>
						<option value="">请选择主题域</option>
						{context.subjectDomains
							.filter((s) => s.martId === defaults.dataMartId)
							.map((s) => (
								<option key={s.id} value={s.id}>
									{s.name} · {s.code}
								</option>
							))}
					</select>
				</label>
				<label>
					<span>发布密级</span>
					<select
						aria-label="整包发布密级"
						value={defaults.securityLevel}
						onChange={(e) => patch({ securityLevel: e.target.value })}
					>
						<option value="">请明确确认发布密级</option>
						{[
							["PUBLIC", "公开"],
							["INTERNAL", "内部"],
							["SECRET", "秘密"],
							["CONFIDENTIAL", "机密"],
						].map(([value, label]) => (
							<option key={value} value={value}>
								{label}
							</option>
						))}
					</select>
					<small>由当前操作者确认，应用到继承整包设置的模型全部字段。</small>
				</label>
			</div>
		</section>
	);
}
