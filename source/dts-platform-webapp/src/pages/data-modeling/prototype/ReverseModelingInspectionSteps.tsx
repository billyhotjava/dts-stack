import { FileArchive } from "lucide-react";
import { useMemo } from "react";
import type { DbtArchiveInspection, ModelSpecImportSemanticOverride } from "@/api/modelSpecImportApi";
import type {
	ModelingImportContextHeader,
	ModelingImportDomainBinding,
	ModelingImportSourceBinding,
} from "@/api/services/modelingImportContextService";
import type { Sprint64BusinessProcess } from "@/api/sprint64GovernanceApi";
import { type CompactColumns, CompactTable } from "@/components/table";
import type { DataMartView } from "@/features/modeling/contracts/dataMartContract";
import type { SubjectDomainView } from "@/features/modeling/contracts/subjectDomainContract";
import { Button, Status } from "./PrototypePrimitives";
import { createRenameMapping, type RenameMapping } from "./services/modelImportUiState";
import { candidateEligibility, inspectionSummary, packageProfileLabel } from "./services/reverseModelingInspection";

const SECURITY_LEVEL_OPTIONS = [
	{ value: "PUBLIC", label: "公开" },
	{ value: "INTERNAL", label: "内部" },
	{ value: "SECRET", label: "秘密" },
	{ value: "CONFIDENTIAL", label: "机密" },
];

type ImportStandardBinding = NonNullable<ModelSpecImportSemanticOverride["standardBindings"]>[number];

function hasBindingEvidence(binding: ImportStandardBinding): boolean {
	return Object.entries(binding).some(
		([key, value]) => key !== "fieldName" && value !== undefined && value !== null && value !== "",
	);
}

export function applyModelSecurityLevel(
	override: ModelSpecImportSemanticOverride,
	fieldNames: string[],
	securityLevel: string,
): ModelSpecImportSemanticOverride {
	const current = new Map((override.standardBindings || []).map((binding) => [binding.fieldName, binding]));
	const targetFields = new Set(fieldNames.filter(Boolean));
	const untouched = (override.standardBindings || []).filter((binding) => !targetFields.has(binding.fieldName));
	const updated = fieldNames
		.filter(Boolean)
		.map((fieldName) => ({
			...(current.get(fieldName) || { fieldName }),
			securityLevel: securityLevel || undefined,
		}))
		.filter(hasBindingEvidence);
	return { ...override, standardBindings: [...untouched, ...updated] };
}

export function modelSecurityLevel(override: ModelSpecImportSemanticOverride, fieldNames: string[]): string {
	if (!fieldNames.length) return "";
	const levels = fieldNames.map(
		(fieldName) => override.standardBindings?.find((binding) => binding.fieldName === fieldName)?.securityLevel || "",
	);
	return levels.every((level) => level && level === levels[0]) ? levels[0] : "";
}

const NO_FIELD_CONTRACT_HINT = "dbt 包未提供带类型的字段定义（需 contract.enforced 且各列声明 data_type），无法确认字段发布密级";

type PreviewReadinessInput = {
	inspection: DbtArchiveInspection | null;
	planId: string;
	selected: string[];
	packageDomains: string[];
	domainMappings: Record<string, string>;
	semanticOverrides: Record<string, ModelSpecImportSemanticOverride>;
};

/** Everything still blocking 生成预览, in the order a user can resolve it; empty means ready. */
export function previewReadinessIssues({
	inspection,
	planId,
	selected,
	packageDomains,
	domainMappings,
	semanticOverrides,
}: PreviewReadinessInput): string[] {
	if (!inspection) return ["请先完成包检查"];
	const issues: string[] = [];
	if (!planId) issues.push("请选择数仓规划");
	const unmapped = packageDomains.filter((code) => !domainMappings[code]);
	if (unmapped.length) issues.push(`请映射数据域：${unmapped.join("、")}`);
	if (!selected.length) issues.push("请至少勾选一个可导入模型");
	const byId = new Map(inspection.package.models.map((model) => [model.dbtUniqueId, model]));
	for (const uniqueId of selected) {
		const model = byId.get(uniqueId);
		if (!model) continue;
		const label = model.name || uniqueId;
		const override = semanticOverrides[uniqueId] || { modelUniqueId: uniqueId };
		const fieldNames = (model.columns || []).map((column) => column.name);
		if (!fieldNames.length) issues.push(`${label}：${NO_FIELD_CONTRACT_HINT}，请取消勾选或补充后重新上传`);
		else if (!modelSecurityLevel(override, fieldNames)) issues.push(`${label}：请确认发布密级`);
		const modelType = override.modelType || model.semantics?.modelType || "";
		if (modelType === "FACT" && !override.businessProcessId) issues.push(`${label}：请选择业务过程`);
		if (modelType === "APPLICATION" && (!override.dataMartId || !override.subjectDomainId)) {
			issues.push(`${label}：请选择数据集市和主题域`);
		}
	}
	return issues;
}

export function StrategyStep({ archive, onArchive }: { archive: File | null; onArchive: (file: File | null) => void }) {
	return (
		<>
			<div className="dmx-strategy-cards dmx-strategy-cards-single">
				<button className="active" disabled type="button">
					<FileArchive size={22} />
					<span>
						<strong>导入 dbt ZIP</strong>
						<small>识别包类型、结构记录与可导入范围</small>
					</span>
				</button>
			</div>
			<div className="dmx-dbt-drop">
				<input
					accept=".zip,application/zip"
					aria-label="选择 dbt ZIP"
					className="dmx-dropzone-input"
					onChange={(event) => onArchive(event.target.files?.[0] || null)}
					type="file"
				/>
				<FileArchive size={30} />
				<strong>选择 dbt 项目 ZIP</strong>
				<p>先检查包内容，不执行其中的 SQL 或宏；检查完成后再选择数仓规划并生成预览。</p>
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
	businessProcesses: Sprint64BusinessProcess[];
	dataMarts: DataMartView[];
	subjectDomains: SubjectDomainView[];
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
		businessProcesses,
		dataMarts,
		subjectDomains,
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
	const sqlNotices = diagnostics.filter((diagnostic) => {
		if (diagnostic.axis !== "IMPORT_PROJECTION" || !diagnostic.code.startsWith("SQL_")) return false;
		const ids = diagnostic.modelUniqueId ? [diagnostic.modelUniqueId] : diagnostic.affectedUniqueIds;
		const models = ids.length
			? ids.map((id) => inspection.package.models.find((model) => model.dbtUniqueId === id))
			: inspection.package.models;
		return (
			models.length > 0 &&
			models.every(
				(model) =>
					model &&
					model.conversion?.mode === "DBT_BACKED" &&
					candidateEligibility(inspection, model.dbtUniqueId) === "ELIGIBLE",
			)
		);
	});
	const remaining = diagnostics.filter((diagnostic) => !sqlNotices.includes(diagnostic));
	const groups = [
		{ title: "导入前需处理", blocking: true, items: remaining.filter((item) => item.blocksImport) },
		{
			title: "导入说明（不阻断草稿导入）",
			blocking: false,
			items: remaining.filter((item) => !item.blocksImport && item.axis !== "MATERIALIZATION"),
		},
		{
			title: "后续构建注意事项（不阻断草稿导入）",
			blocking: false,
			items: remaining.filter((item) => !item.blocksImport && item.axis === "MATERIALIZATION"),
		},
	];
	const canImport =
		inspection.compatibility.importProjection === "IMPORTABLE" &&
		summary.eligible > 0 &&
		summary.blocked === 0 &&
		summary.requiresMapping === 0 &&
		groups[0].items.length === 0;
	return (
		<>
			<div className="dmx-wizard-heading">
				<div>
					<h3>检查报告与模型映射</h3>
					<p>导入只生成模型草稿；是否可以构建，将在“构建与检查”阶段确认。</p>
				</div>
				<Status tone={inspection.compatibility.importProjection === "BLOCKED" ? "danger" : "info"}>
					{packageProfileLabel(profile)}
				</Status>
			</div>
			<div className="dmx-inspection-axes">
				<div>
					<small>包结构检查</small>
					<strong>
						{inspection.compatibility.inspection === "SUPPORTED"
							? "支持检查"
							: inspection.compatibility.inspection === "UNSUPPORTED"
								? "不支持"
								: "待确认"}
					</strong>
				</div>
				<div>
					<small>草稿导入能力</small>
					<strong>
						{inspection.compatibility.importProjection === "IMPORTABLE"
							? "可导入草稿"
							: inspection.compatibility.importProjection === "BLOCKED"
								? "导入受阻"
								: "仅可查看结构"}
					</strong>
				</div>
				<div>
					<small>后续构建环境</small>
					<strong>
						{inspection.compatibility.materialization === "CERTIFIED"
							? "已认证"
							: inspection.compatibility.materialization === "NOT_CERTIFIED"
								? "尚未认证"
								: inspection.compatibility.materialization === "UNSUPPORTED"
									? "不支持构建"
									: "待确认"}
					</strong>
				</div>
			</div>
			<div className="dmx-inspection-summary">
				<Status tone="info">识别 {summary.discovered}</Status>
				<Status>技术节点 {summary.technicalOnly}</Status>
				<Status tone="success">可导入 {summary.eligible}</Status>
				<Status tone="warning">待补充 {summary.requiresMapping}</Status>
				<Status tone={summary.blocked ? "danger" : "neutral"}>阻断 {summary.blocked}</Status>
			</div>
			<p>
				{canImport
					? "可以继续导入草稿。请完成下方模型映射并生成预览；导入不会自动发布或构建。"
					: "请处理受阻模型或补充映射，最终可导入范围以预览结果为准。"}
			</p>
			{sqlNotices.length > 0 ? (
				<div className="dmx-capability-note">
					<strong>SQL 编辑说明（不阻断草稿导入）</strong>
					<p>部分 SQL 无法完整转换为可视化配置，将保留原始 SQL。导入后请通过代码模式编辑，无需因此重新上传。</p>
				</div>
			) : null}
			{groups
				.filter((group) => group.items.length > 0)
				.map((group) => (
					<section key={group.title}>
						<h4>{group.title}</h4>
						<div className="dmx-inspection-diagnostics">
							{group.items.map((diagnostic, index) => (
								<div key={`${diagnostic.code}-${diagnostic.modelUniqueId || index}`}>
									<Status tone={group.blocking ? "danger" : "info"}>{group.blocking ? "需处理" : "提示"}</Status>
									<span>
										{diagnostic.code === "CATALOG_MISSING"
											? "包中缺少运行时字段类型信息。请在构建检查前确认字段类型，或补充 catalog.json。"
											: diagnostic.code === "DBT_RUNTIME_NOT_CERTIFIED"
												? "当前运行环境尚未通过对应认证。请在构建与检查阶段由管理员确认环境。"
												: diagnostic.message}
									</span>
									<small>
										{["CATALOG_MISSING", "DBT_RUNTIME_NOT_CERTIFIED"].includes(diagnostic.code)
											? ""
											: diagnostic.recoveryAction === "REUPLOAD"
												? group.blocking
													? "处理建议：修正受影响模型后重新上传"
													: "如需消除此提示，可调整包内容后重新上传"
												: diagnostic.recoveryAction === "CONTACT_ADMIN"
													? "处理建议：联系管理员确认"
													: diagnostic.recoveryAction
														? `处理建议：${diagnostic.recoveryAction}`
														: ""}
									</small>
								</div>
							))}
						</div>
					</section>
				))}
			{diagnostics.length > 0 ? (
				<details>
					<summary>查看技术诊断详情</summary>
					{diagnostics.map((diagnostic, index) => (
						<p key={`${diagnostic.code}-${index}`}>
							{diagnostic.axis} · {diagnostic.code}：{diagnostic.message}
						</p>
					))}
				</details>
			) : null}
			<div className="dmx-mapping-grid">
				<label className="dmx-reverse-plan">
					<span>数仓规划</span>
					<select disabled={plansLoading} onChange={(event) => onPlanId(event.target.value)} value={planId}>
						<option value="">请选择已确认的数仓规划</option>
						{plans.map((plan) => (
							<option key={plan.id} value={plan.id}>
								{plan.name || plan.id}
							</option>
						))}
					</select>
					{!plansLoading && !plans.length ? <small>当前环境尚未初始化模型导入环境。</small> : null}
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
			<label className="dmx-reverse-plan">
				<span>批量确认发布密级</span>
				<select
					aria-label="批量确认导入模型发布密级"
					onChange={(event) => {
						const securityLevel = event.target.value;
						if (!securityLevel) return;
						inspection.package.models
							.filter((model) => selected.includes(model.dbtUniqueId))
							.forEach((model) => {
								const override = semanticOverrides[model.dbtUniqueId] || { modelUniqueId: model.dbtUniqueId };
								onSemanticOverride(
									model.dbtUniqueId,
									applyModelSecurityLevel(
										override,
										(model.columns || []).map((column) => column.name),
										securityLevel,
									),
								);
							});
					}}
					value=""
				>
					<option value="">请选择并应用到已选模型</option>
					{SECURITY_LEVEL_OPTIONS.map((level) => (
						<option key={level.value} value={level.value}>
							{level.label}
						</option>
					))}
				</select>
				<small>
					{selected.length
						? "这是当前操作者的显式治理确认，不会写回或篡改原始 dbt SQL；未识别字段的模型不会被应用。"
						: "请先在下表勾选要导入的模型，再批量确认发布密级。"}
				</small>
			</label>
			<ImportSemanticsTable
				inspection={inspection}
				onSelected={onSelected}
				onSemanticOverride={onSemanticOverride}
				selected={selected}
				semanticOverrides={semanticOverrides}
				businessProcesses={businessProcesses}
				dataMarts={dataMarts}
				domainMappings={domainMappings}
				subjectDomains={subjectDomains}
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
	businessProcesses,
	dataMarts,
	domainMappings,
	subjectDomains,
}: {
	inspection: DbtArchiveInspection;
	selected: string[];
	onSelected: (ids: string[]) => void;
	semanticOverrides: Record<string, ModelSpecImportSemanticOverride>;
	onSemanticOverride: (id: string, value: ModelSpecImportSemanticOverride) => void;
	businessProcesses: Sprint64BusinessProcess[];
	dataMarts: DataMartView[];
	domainMappings: Record<string, string>;
	subjectDomains: SubjectDomainView[];
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
							aria-label={`选择模型 ${model.dbtUniqueId}`}
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
				title: "发布密级",
				key: "securityLevel",
				render: (_, { model }) => {
					const override = semanticOverrides[model.dbtUniqueId] || { modelUniqueId: model.dbtUniqueId };
					const fieldNames = (model.columns || []).map((column) => column.name);
					if (!fieldNames.length) {
						return (
							<select aria-label={`${model.dbtUniqueId} 发布密级`} disabled title={NO_FIELD_CONTRACT_HINT} value="">
								<option value="">未识别字段</option>
							</select>
						);
					}
					return (
						<select
							aria-label={`${model.dbtUniqueId} 发布密级`}
							onChange={(event) =>
								onSemanticOverride(model.dbtUniqueId, applyModelSecurityLevel(override, fieldNames, event.target.value))
							}
							value={modelSecurityLevel(override, fieldNames)}
						>
							<option value="">请确认</option>
							{SECURITY_LEVEL_OPTIONS.map((level) => (
								<option key={level.value} value={level.value}>
									{level.label}
								</option>
							))}
						</select>
					);
				},
			},
			{
				title: "规划归属",
				key: "planningContext",
				render: (_, { model }) => {
					const override = semanticOverrides[model.dbtUniqueId] || { modelUniqueId: model.dbtUniqueId };
					const patch = (next: Partial<ModelSpecImportSemanticOverride>) =>
						onSemanticOverride(model.dbtUniqueId, { ...override, ...next, modelUniqueId: model.dbtUniqueId });
					const modelType = override.modelType || model.semantics?.modelType || "";
					if (modelType === "FACT") {
						const targetDomainId = domainMappings[model.semantics?.domainCode || ""] || "";
						const options = businessProcesses.filter((process) => process.domainId === targetDomainId);
						return (
							<label>
								<span>业务过程</span>
								<select
									aria-label={`${model.dbtUniqueId} 业务过程`}
									onChange={(event) => patch({ businessProcessId: event.target.value || undefined })}
									value={override.businessProcessId || ""}
								>
									<option value="">请选择</option>
									{options.map((process) => (
										<option key={process.id} value={process.id}>
											{process.name} · {process.processId}
										</option>
									))}
								</select>
								{!targetDomainId ? (
									<small>请先映射数据域</small>
								) : !options.length ? (
									<small>该数据域无已确认业务过程</small>
								) : null}
							</label>
						);
					}
					if (modelType === "APPLICATION") {
						const subjects = subjectDomains.filter((subject) => subject.martId === override.dataMartId);
						return (
							<div>
								<select
									aria-label={`${model.dbtUniqueId} 数据集市`}
									onChange={(event) =>
										patch({ dataMartId: event.target.value || undefined, subjectDomainId: undefined })
									}
									value={override.dataMartId || ""}
								>
									<option value="">请选择数据集市</option>
									{dataMarts.map((mart) => (
										<option key={mart.id} value={mart.id}>
											{mart.name} · {mart.code}
										</option>
									))}
								</select>
								<select
									aria-label={`${model.dbtUniqueId} 主题域`}
									disabled={!override.dataMartId}
									onChange={(event) => patch({ subjectDomainId: event.target.value || undefined })}
									value={override.subjectDomainId || ""}
								>
									<option value="">请选择主题域</option>
									{subjects.map((subject) => (
										<option key={subject.id} value={subject.id}>
											{subject.name} · {subject.code}
										</option>
									))}
								</select>
							</div>
						);
					}
					return <small>由上游模型继承</small>;
				},
			},
			{
				title: "字段数",
				key: "columns",
				align: "right",
				render: (_, { model }) => model.columns?.length || 0,
			},
		],
		[
			businessProcesses,
			dataMarts,
			domainMappings,
			inspection,
			onSelected,
			onSemanticOverride,
			selected,
			semanticOverrides,
			subjectDomains,
		],
	);
	return (
		<section>
			<p>发布密级由当前操作者显式确认；选择后应用到该模型全部字段，系统不会从测试数据或包名推断。</p>
			<CompactTable
				className="dmx-import-semantics"
				columns={columns}
				dataSource={rows}
				pagination={false}
				rowKey="key"
				scroll={{ x: 1250 }}
			/>
		</section>
	);
}
