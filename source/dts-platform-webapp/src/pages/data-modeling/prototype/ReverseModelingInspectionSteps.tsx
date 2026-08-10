import { FileArchive } from "lucide-react";
import { useMemo } from "react";
import type { DbtArchiveInspection, ModelSpecImportSemanticOverride } from "@/api/modelSpecImportApi";
import type {
	ModelingImportContextHeader,
	ModelingImportDomainBinding,
	ModelingImportSourceBinding,
} from "@/api/services/modelingImportContextService";
import { type CompactColumns, CompactTable } from "@/components/table";
import { Button, Status } from "./PrototypePrimitives";
import { createRenameMapping, type RenameMapping } from "./services/modelImportUiState";
import { candidateEligibility, inspectionSummary, packageProfileLabel } from "./services/reverseModelingInspection";

export function StrategyStep({ archive, onArchive }: { archive: File | null; onArchive: (file: File | null) => void }) {
	return (
		<>
			<div className="dmx-strategy-cards dmx-strategy-cards-single">
				<button className="active" disabled type="button">
					<FileArchive size={22} />
					<span>
						<strong>导入 dbt ZIP</strong>
						<small>识别包类型、结构证据与可导入范围</small>
					</span>
				</button>
			</div>
			<div className="dmx-dbt-drop">
				<FileArchive size={30} />
				<strong>选择 dbt 项目 ZIP</strong>
				<p>先检查包内容，不执行其中的 SQL 或宏；检查完成后再选择规划上下文并生成预览。</p>
				<input
					accept=".zip,application/zip"
					aria-label="选择 dbt ZIP"
					onChange={(event) => onArchive(event.target.files?.[0] || null)}
					type="file"
				/>
				{archive ? <small>已选择：{archive.name}</small> : null}
			</div>
		</>
	);
}

type ConfirmStepProps = {
	inspection: DbtArchiveInspection;
	plans: ModelingImportContextHeader[];
	planId: string;
	plansLoading: boolean;
	onPlanId: (value: string) => void;
	selected: string[];
	onSelected: (ids: string[]) => void;
	domains: ModelingImportDomainBinding[];
	domainMappings: Record<string, string>;
	onDomainMapping: (code: string, value: string) => void;
	sources: ModelingImportSourceBinding[];
	sourceMappings: Record<string, string>;
	onSourceMapping: (code: string, value: string) => void;
	renameMappings: RenameMapping[];
	onRenameMappings: (mappings: RenameMapping[]) => void;
	semanticOverrides: Record<string, ModelSpecImportSemanticOverride>;
	onSemanticOverride: (id: string, value: ModelSpecImportSemanticOverride) => void;
};

export function ConfirmStep(props: ConfirmStepProps) {
	const {
		inspection,
		plans,
		planId,
		plansLoading,
		onPlanId,
		selected,
		onSelected,
		domains,
		domainMappings,
		onDomainMapping,
		sources,
		sourceMappings,
		onSourceMapping,
		renameMappings,
		onRenameMappings,
		semanticOverrides,
		onSemanticOverride,
	} = props;
	const summary = inspectionSummary(inspection);
	const profile =
		inspection.report?.packageProfile ||
		(inspection.package.dbt.manifestVersion === "source-project/v1" ? "SOURCE_ONLY" : "ARTIFACT_RICH");
	const packageDomains = Array.from(
		new Set(
			inspection.package.models
				.map((model) => model.semantics?.domainCode?.trim())
				.filter((value): value is string => Boolean(value)),
		),
	);
	const packageSources = Array.from(
		new Map([
			...inspection.package.sources.map((source) => [source.dbtUniqueId, source.name] as const),
			...inspection.package.models
				.flatMap((model) => model.semantics?.sourceRefs || [])
				.filter((source) => Boolean(source.ref))
				.map((source) => [String(source.ref), String(source.ref)] as const),
		]).entries(),
	);
	const diagnostics =
		inspection.report?.diagnostics ||
		inspection.compatibility.issues.map((issue) => ({
			...issue,
			axis: "IMPORT_PROJECTION" as const,
			severity: "ERROR" as const,
			blocksImport: true,
			modelUniqueId: null,
			affectedUniqueIds: [],
		}));
	return (
		<>
			<div className="dmx-wizard-heading">
				<div>
					<h3>检查报告与模型映射</h3>
					<p>检查、导入投影、物化能力分别判断；源项目包不会再被误报为数据库适配器不支持。</p>
				</div>
				<Status tone={inspection.compatibility.importProjection === "BLOCKED" ? "danger" : "info"}>
					{packageProfileLabel(profile)}
				</Status>
			</div>
			<div className="dmx-inspection-axes">
				<div>
					<small>包结构检查</small>
					<strong>{inspection.compatibility.inspection}</strong>
				</div>
				<div>
					<small>导入投影</small>
					<strong>{inspection.compatibility.importProjection}</strong>
				</div>
				<div>
					<small>dbt 物化</small>
					<strong>{inspection.compatibility.materialization}</strong>
				</div>
			</div>
			<div className="dmx-inspection-summary">
				<Status tone="info">识别 {summary.discovered}</Status>
				<Status>技术节点 {summary.technicalOnly}</Status>
				<Status tone="success">可导入 {summary.eligible}</Status>
				<Status tone="warning">待补充 {summary.requiresMapping}</Status>
				<Status tone={summary.blocked ? "danger" : "neutral"}>阻断 {summary.blocked}</Status>
			</div>
			{diagnostics.length ? (
				<div className="dmx-inspection-diagnostics">
					{diagnostics.map((diagnostic, index) => (
						<div key={`${diagnostic.code}-${diagnostic.modelUniqueId || index}`}>
							<Status
								tone={diagnostic.blocksImport ? "danger" : diagnostic.severity === "WARNING" ? "warning" : "info"}
							>
								{diagnostic.axis}
							</Status>
							<span>
								<strong>{diagnostic.code}</strong>：{diagnostic.message}
							</span>
							<small>{diagnostic.recoveryAction ? `处理建议：${diagnostic.recoveryAction}` : ""}</small>
						</div>
					))}
				</div>
			) : null}
			<div className="dmx-mapping-grid">
				<label className="dmx-reverse-plan">
					<span>规划上下文</span>
					<select disabled={plansLoading} onChange={(event) => onPlanId(event.target.value)} value={planId}>
						<option value="">请选择已确认的规划上下文</option>
						{plans.map((plan) => (
							<option key={plan.id} value={plan.id}>
								{plan.name || plan.id}
							</option>
						))}
					</select>
					{!plansLoading && !plans.length ? <small>当前环境尚未初始化模型导入上下文。</small> : null}
				</label>
				{packageDomains.map((code) => (
					<label key={code}>
						<span>数据域 {code}</span>
						<select onChange={(event) => onDomainMapping(code, event.target.value)} value={domainMappings[code] || ""}>
							<option value="">请选择已确认数据域</option>
							{domains.map((domain) => (
								<option key={domain.domainId} value={domain.domainId}>
									{domain.name || domain.code || domain.domainId}
								</option>
							))}
						</select>
					</label>
				))}
				{packageSources.map(([sourceId, sourceName]) => (
					<label key={sourceId}>
						<span>来源 {sourceName}</span>
						<select
							onChange={(event) => onSourceMapping(sourceId, event.target.value)}
							value={sourceMappings[sourceId] || ""}
						>
							<option value="">自动匹配（可能阻断）</option>
							{sources.map((binding) => (
								<option key={binding.bindingId} value={binding.bindingId}>
									{binding.displayName || binding.sourceId || binding.bindingId}
								</option>
							))}
						</select>
					</label>
				))}
			</div>
			<section className="dmx-rename-mappings">
				<header>
					<div>
						<strong>重新导入重命名映射</strong>
						<p>只处理明确确认的 old unique_id → new unique_id。</p>
					</div>
					<Button onClick={() => onRenameMappings([...renameMappings, createRenameMapping()])}>新增映射</Button>
				</header>
				{renameMappings.map((mapping, index) => (
					<div className="dmx-rename-mapping-row" key={mapping._clientId}>
						<input
							aria-label={`旧 unique_id ${index + 1}`}
							onChange={(event) =>
								onRenameMappings(
									renameMappings.map((item, row) =>
										row === index ? { ...item, oldUniqueId: event.target.value } : item,
									),
								)
							}
							placeholder="旧 unique_id"
							value={mapping.oldUniqueId}
						/>
						<span>→</span>
						<input
							aria-label={`新 unique_id ${index + 1}`}
							onChange={(event) =>
								onRenameMappings(
									renameMappings.map((item, row) =>
										row === index ? { ...item, newUniqueId: event.target.value } : item,
									),
								)
							}
							placeholder="新 unique_id"
							value={mapping.newUniqueId}
						/>
						<Button danger onClick={() => onRenameMappings(renameMappings.filter((_, row) => row !== index))}>
							删除
						</Button>
					</div>
				))}
			</section>
			<ImportSemanticsTable
				inspection={inspection}
				onSelected={onSelected}
				onSemanticOverride={onSemanticOverride}
				selected={selected}
				semanticOverrides={semanticOverrides}
			/>
		</>
	);
}

function ImportSemanticsTable({
	inspection,
	selected,
	onSelected,
	semanticOverrides,
	onSemanticOverride,
}: {
	inspection: DbtArchiveInspection;
	selected: string[];
	onSelected: (ids: string[]) => void;
	semanticOverrides: Record<string, ModelSpecImportSemanticOverride>;
	onSemanticOverride: (id: string, value: ModelSpecImportSemanticOverride) => void;
}) {
	const rows = useMemo(
		() =>
			inspection.package.models.map((model, index) => ({
				key: model.dbtUniqueId,
				model,
				index,
			})),
		[inspection.package.models],
	);
	const columns = useMemo<CompactColumns<(typeof rows)[number]>>(
		() => [
			{
				title: "选择",
				key: "select",
				width: 56,
				align: "center",
				render: (_, { model }) => {
					const eligibility = candidateEligibility(inspection, model.dbtUniqueId);
					const checked = selected.includes(model.dbtUniqueId);
					return (
						<input
							checked={checked}
							disabled={eligibility === "BLOCKED"}
							onChange={(event) =>
								onSelected(
									event.target.checked
										? [...selected, model.dbtUniqueId]
										: selected.filter((id) => id !== model.dbtUniqueId),
								)
							}
							type="checkbox"
						/>
					);
				},
			},
			{
				title: "dbt 对象",
				key: "object",
				render: (_, { model }) => {
					const eligibility = candidateEligibility(inspection, model.dbtUniqueId);
					return (
						<>
							{model.dbtUniqueId}
							<Status
								tone={eligibility === "BLOCKED" ? "danger" : eligibility === "REQUIRES_MAPPING" ? "warning" : "success"}
							>
								{eligibility === "BLOCKED" ? "不可导入" : eligibility === "REQUIRES_MAPPING" ? "待补充" : "可导入"}
							</Status>
							<small>{model.conversion?.reasonCodes?.join("、")}</small>
						</>
					);
				},
			},
			{
				title: "业务名称",
				key: "businessName",
				render: (_, { model }) => {
					const override = semanticOverrides[model.dbtUniqueId] || { modelUniqueId: model.dbtUniqueId };
					const patch = (next: Partial<ModelSpecImportSemanticOverride>) =>
						onSemanticOverride(model.dbtUniqueId, { ...override, ...next, modelUniqueId: model.dbtUniqueId });
					return (
						<input
							onChange={(event) => patch({ businessName: event.target.value })}
							value={override.businessName || ""}
						/>
					);
				},
			},
			{
				title: "模型类型",
				key: "modelType",
				render: (_, { model }) => {
					const override = semanticOverrides[model.dbtUniqueId] || { modelUniqueId: model.dbtUniqueId };
					const patch = (next: Partial<ModelSpecImportSemanticOverride>) =>
						onSemanticOverride(model.dbtUniqueId, { ...override, ...next, modelUniqueId: model.dbtUniqueId });
					return (
						<select
							onChange={(event) => patch({ modelType: event.target.value || undefined })}
							value={override.modelType || ""}
						>
							<option value="">请选择</option>
							<option value="DIMENSION">维度表</option>
							<option value="FACT">明细表</option>
							<option value="SUMMARY">汇总表</option>
							<option value="APPLICATION">应用表</option>
						</select>
					);
				},
			},
			{
				title: "目标分层",
				key: "layer",
				render: (_, { model }) => {
					const override = semanticOverrides[model.dbtUniqueId] || { modelUniqueId: model.dbtUniqueId };
					const patch = (next: Partial<ModelSpecImportSemanticOverride>) =>
						onSemanticOverride(model.dbtUniqueId, { ...override, ...next, modelUniqueId: model.dbtUniqueId });
					return (
						<select
							onChange={(event) => patch({ layer: event.target.value || undefined })}
							value={override.layer || ""}
						>
							<option value="">请选择</option>
							<option value="DWD">DWD</option>
							<option value="DWS">DWS</option>
							<option value="ADS">ADS</option>
						</select>
					);
				},
			},
			{
				title: "粒度说明",
				key: "grain",
				render: (_, { model }) => {
					const override = semanticOverrides[model.dbtUniqueId] || { modelUniqueId: model.dbtUniqueId };
					const patch = (next: Partial<ModelSpecImportSemanticOverride>) =>
						onSemanticOverride(model.dbtUniqueId, { ...override, ...next, modelUniqueId: model.dbtUniqueId });
					return (
						<input
							onChange={(event) =>
								patch({ grain: { statement: event.target.value, keys: override.grain?.keys || [] } })
							}
							value={override.grain?.statement || ""}
						/>
					);
				},
			},
			{
				title: "业务主键",
				key: "businessKeys",
				render: (_, { model }) => {
					const override = semanticOverrides[model.dbtUniqueId] || { modelUniqueId: model.dbtUniqueId };
					const patch = (next: Partial<ModelSpecImportSemanticOverride>) =>
						onSemanticOverride(model.dbtUniqueId, { ...override, ...next, modelUniqueId: model.dbtUniqueId });
					return (
						<input
							onChange={(event) => {
								const keys = event.target.value
									.split(",")
									.map((item) => item.trim())
									.filter(Boolean);
								patch({ businessKeys: keys, grain: { statement: override.grain?.statement, keys } });
							}}
							placeholder="逗号分隔"
							value={(override.businessKeys || override.grain?.keys || []).join(",")}
						/>
					);
				},
			},
			{
				title: "字段数",
				key: "columns",
				align: "right",
				render: (_, { model }) => model.columns?.length || 0,
			},
		],
		[inspection, onSelected, onSemanticOverride, selected, semanticOverrides],
	);
	return (
		<CompactTable
			className="dmx-import-semantics"
			columns={columns}
			dataSource={rows}
			pagination={false}
			rowKey="key"
		/>
	);
}
