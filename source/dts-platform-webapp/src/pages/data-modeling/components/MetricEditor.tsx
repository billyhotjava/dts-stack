import { AlertTriangle, Archive, Calculator, CheckCircle2, RefreshCw, Save, Send, Sigma, X } from "lucide-react";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { toast } from "sonner";
import { DatasetPicker } from "@/components/catalog/DatasetPicker";
import type { IndicatorEditValues } from "@/features/modeling/indicators/indicatorDefinitionContract";
import type { IndicatorPreflightResult } from "@/features/modeling/indicators/indicatorDefinitionWorkflow";
import {
	archiveIndicatorDraft,
	dependencyCodesOf,
	type IndicatorGovernanceContext,
	type IndicatorPublishPreviewSummary,
	type IndicatorWorkspaceError,
	loadIndicatorGovernanceContext,
	type MetricSelection,
	type MetricType,
	normalizeIndicatorError,
	previewIndicatorPublication,
	publishIndicatorDraft,
	saveIndicatorDraft,
	validateIndicatorDraft,
} from "../indicatorWorkspaceAdapter";
import { ActionButton, StatusTag } from "./WorkspacePage";

export type { MetricSelection, MetricType } from "../indicatorWorkspaceAdapter";

type IndicatorFormValues = {
	code: string;
	name: string;
	domain: string;
	category: string;
	definition: string;
	owner: string;
	ownerDept: string;
	dataLevel: string;
	unit: string;
	precisionScale: string;
	versionNotes: string;
	datasetId: string;
	aggregationType: string;
	measureField: string;
	expressionSql: string;
	dependencyCodes: string;
};

type MetricDatasetField = { name: string };
type Operation = "idle" | "saving" | "validating" | "previewing" | "publishing" | "archiving";
const text = (value: unknown): string => String(value ?? "");

const formFromSelection = (selection: MetricSelection): IndicatorFormValues => ({
	code: selection.code,
	name: selection.name,
	domain: selection.domain,
	category: text(selection.category),
	definition: text(selection.definition),
	owner: text(selection.owner),
	ownerDept: text(selection.ownerDept),
	dataLevel: text(selection.dataLevel) || "DATA_INTERNAL",
	unit: text(selection.unit),
	precisionScale: selection.precisionScale == null ? "" : String(selection.precisionScale),
	versionNotes: text(selection.versionNotes),
	datasetId: text(selection.datasetId),
	aggregationType: text(selection.aggregationType) || (selection.isDerived ? "DERIVED" : "SUM"),
	measureField: text(selection.measureField),
	expressionSql: text(selection.expressionSql),
	dependencyCodes: dependencyCodesOf(selection).join(", "),
});

const nullable = (value: string): string | null => value.trim() || null;

const changesFromForm = (values: IndicatorFormValues, derived: boolean): IndicatorEditValues => ({
	code: values.code.trim(),
	name: values.name.trim(),
	domain: nullable(values.domain),
	category: nullable(values.category),
	definition: nullable(values.definition),
	owner: nullable(values.owner),
	ownerDept: nullable(values.ownerDept),
	dataLevel: nullable(values.dataLevel),
	unit: nullable(values.unit),
	precisionScale: values.precisionScale.trim() ? Number(values.precisionScale) : null,
	versionNotes: nullable(values.versionNotes),
	isDerived: derived,
	datasetId: derived ? null : nullable(values.datasetId),
	aggregationType: derived ? "DERIVED" : nullable(values.aggregationType),
	measureField: derived ? null : nullable(values.measureField),
	expressionSql: nullable(values.expressionSql),
	dependencyCodes: derived
		? values.dependencyCodes
				.split(",")
				.map((item) => item.trim())
				.filter(Boolean)
		: [],
});

const statusTone = (status: unknown): "neutral" | "success" | "warning" | "info" => {
	switch (text(status).toUpperCase()) {
		case "PUBLISHED":
			return "success";
		case "ARCHIVED":
			return "neutral";
		case "DRAFT":
			return "warning";
		default:
			return "info";
	}
};

const statusLabel = (status: unknown, isNew?: boolean): string => {
	if (isNew) return "新建";
	switch (text(status).toUpperCase()) {
		case "PUBLISHED":
			return "已发布";
		case "ARCHIVED":
			return "已归档";
		case "DRAFT":
			return "草稿";
		default:
			return text(status) || "未知状态";
	}
};

export function MetricEditor({
	type,
	selection,
	canManage,
	onChanged,
}: {
	type: MetricType;
	selection: MetricSelection | null;
	canManage: boolean;
	onChanged: (selection: MetricSelection) => void;
}) {
	const [values, setValues] = useState<IndicatorFormValues | null>(() =>
		selection ? formFromSelection(selection) : null,
	);
	const [datasetFields, setDatasetFields] = useState<MetricDatasetField[]>([]);
	const [dirty, setDirty] = useState(false);
	const [operation, setOperation] = useState<Operation>("idle");
	const [error, setError] = useState<IndicatorWorkspaceError | null>(null);
	const [success, setSuccess] = useState<string | null>(null);
	const [preflight, setPreflight] = useState<IndicatorPreflightResult | null>(null);
	const [publishPreview, setPublishPreview] = useState<IndicatorPublishPreviewSummary | null>(null);
	const [context, setContext] = useState<IndicatorGovernanceContext | null>(null);
	const [contextLoading, setContextLoading] = useState(false);
	const [contextError, setContextError] = useState<IndicatorWorkspaceError | null>(null);
	const contextRequestEpochRef = useRef(0);

	useEffect(() => {
		setValues(selection ? formFromSelection(selection) : null);
		setDatasetFields([]);
		setDirty(false);
		setError(null);
		setSuccess(null);
		setPreflight(null);
		setPublishPreview(null);
	}, [selection]);

	const refreshContext = useCallback(async (id: string) => {
		const requestEpoch = ++contextRequestEpochRef.current;
		setContextLoading(true);
		setContextError(null);
		try {
			const nextContext = await loadIndicatorGovernanceContext(id);
			if (requestEpoch !== contextRequestEpochRef.current) return;
			setContext(nextContext);
		} catch (cause) {
			if (requestEpoch !== contextRequestEpochRef.current) return;
			setContext(null);
			setContextError(normalizeIndicatorError(cause));
		} finally {
			if (requestEpoch === contextRequestEpochRef.current) setContextLoading(false);
		}
	}, []);

	const selectedIndicatorId = selection?.id;
	useEffect(() => {
		if (!selectedIndicatorId) {
			contextRequestEpochRef.current += 1;
			setContext(null);
			setContextError(null);
			setContextLoading(false);
			return;
		}
		void refreshContext(selectedIndicatorId);
		return () => {
			contextRequestEpochRef.current += 1;
		};
	}, [refreshContext, selectedIndicatorId]);

	const derived = Boolean(selection?.isDerived);
	const busy = operation !== "idle";
	const published = text(selection?.status).toUpperCase() === "PUBLISHED";
	const archived = text(selection?.status).toUpperCase() === "ARCHIVED";
	const permissionReason = canManage ? undefined : "无指标维护权限";
	const codeLocked = Boolean(selection?.id);
	const categoryLocked = type === "复合指标" || type === "修饰词" || type === "时间周期";

	const measureOptions = useMemo(() => {
		const options = datasetFields.map((field) => field.name).filter(Boolean);
		if (values?.measureField && !options.includes(values.measureField)) options.unshift(values.measureField);
		return options;
	}, [datasetFields, values?.measureField]);

	const updateValue = (field: keyof IndicatorFormValues, value: string) => {
		setValues((current) => (current ? { ...current, [field]: value } : current));
		setDirty(true);
		setError(null);
		setSuccess(null);
		setPreflight(null);
		setPublishPreview(null);
	};

	const execute = async (nextOperation: Operation, work: () => Promise<void>) => {
		setOperation(nextOperation);
		setError(null);
		setSuccess(null);
		try {
			await work();
		} catch (cause) {
			setError(normalizeIndicatorError(cause));
		} finally {
			setOperation("idle");
		}
	};

	const save = () => {
		if (!selection || !values || !canManage) return;
		void execute("saving", async () => {
			const saved = await saveIndicatorDraft(selection, changesFromForm(values, derived));
			onChanged(saved);
			setDirty(false);
			setSuccess("保存成功，已重新读取服务端版本");
			toast.success("指标保存成功");
		});
	};

	const validate = () => {
		if (!selection?.id || !canManage || dirty) return;
		void execute("validating", async () => {
			const result = await validateIndicatorDraft(selection);
			setPreflight(result);
			if (!result.valid) throw new Error(result.issues[0]?.message || result.message || "指标校验未通过");
			setSuccess("指标校验通过");
			toast.success("指标校验通过");
		});
	};

	const previewPublication = () => {
		const id = selection?.id;
		if (!id || !canManage || dirty) return;
		void execute("previewing", async () => {
			const result = await previewIndicatorPublication(id);
			setPublishPreview(result);
			if (!result.readyToPublish) {
				throw new Error(result.blockingIssues[0]?.message || "发布预检未通过");
			}
			setSuccess("发布预检通过");
			toast.success("发布预检通过");
		});
	};

	const publish = () => {
		if (!selection?.id || !values || !canManage) return;
		void execute("publishing", async () => {
			const saved = await publishIndicatorDraft(selection, changesFromForm(values, derived));
			onChanged(saved);
			setDirty(false);
			setSuccess(published ? "新版本发布成功" : "发布成功");
			toast.success(published ? "指标新版本已发布" : "指标已发布");
		});
	};

	const archive = () => {
		if (!selection?.id || !canManage || archived) return;
		if (!window.confirm(`确认归档指标“${selection.name || selection.code}”？`)) return;
		void execute("archiving", async () => {
			const saved = await archiveIndicatorDraft(selection);
			onChanged(saved);
			setDirty(false);
			setSuccess("归档成功");
			toast.success("指标已归档");
		});
	};

	if (!selection || !values) {
		return (
			<section className="dm-metric-editor">
				<div className="dm-representation-state">
					<strong>暂无指标</strong>
					<p>请从真实目录选择指标，或在有维护权限时创建指标草稿。</p>
				</div>
			</section>
		);
	}

	const formDisabled = busy || !canManage;
	const saveDisabled = formDisabled || !dirty || published;
	const validateDisabled = formDisabled || !selection.id || dirty;
	const publishDisabled = formDisabled || !selection.id || (published && !dirty);
	const archiveDisabled = formDisabled || !selection.id || archived || dirty;

	return (
		<section className="dm-metric-editor">
			<div className="dm-editor-tabs">
				<div className="dm-editor-tab is-active">
					<Sigma aria-hidden="true" size={15} />
					<strong>{selection.isNew ? `新建${type}` : selection.name || selection.code}</strong>
					<span>{type}</span>
					<X aria-hidden="true" size={13} />
				</div>
			</div>
			<div className="dm-editor-toolbar">
				<ActionButton
					disabled={saveDisabled}
					kind="primary"
					onClick={save}
					title={
						permissionReason ||
						(published ? "已发布指标请通过“发布新版本”提交变更" : !dirty ? "没有待保存变更" : undefined)
					}
				>
					<Save aria-hidden="true" size={14} />
					{operation === "saving" ? "保存中…" : "保存"}
				</ActionButton>
				<ActionButton
					disabled={validateDisabled}
					onClick={validate}
					title={permissionReason || (!selection.id ? "请先保存指标草稿" : dirty ? "请先保存最新配置" : undefined)}
				>
					<CheckCircle2 aria-hidden="true" size={14} />
					{operation === "validating" ? "校验中…" : "校验"}
				</ActionButton>
				<ActionButton
					disabled={validateDisabled}
					onClick={previewPublication}
					title={permissionReason || (!selection.id ? "请先保存指标草稿" : dirty ? "请先保存最新配置" : undefined)}
				>
					<RefreshCw aria-hidden="true" size={14} />
					{operation === "previewing" ? "预检中…" : "发布预检"}
				</ActionButton>
				<ActionButton
					disabled={publishDisabled}
					kind="primary"
					onClick={publish}
					title={
						permissionReason ||
						(!selection.id ? "请先保存指标草稿" : published && !dirty ? "当前已是发布版本" : undefined)
					}
				>
					<Send aria-hidden="true" size={14} />
					{operation === "publishing" ? "发布中…" : published ? "发布新版本" : "发布"}
				</ActionButton>
				<ActionButton
					disabled={archiveDisabled}
					kind="danger"
					onClick={archive}
					title={
						permissionReason ||
						(archived
							? "指标已归档"
							: !selection.id
								? "未保存指标无需归档"
								: dirty
									? "请先保存或放弃未提交变更"
									: undefined)
					}
				>
					<Archive aria-hidden="true" size={14} />
					{operation === "archiving" ? "归档中…" : "归档"}
				</ActionButton>
				<span className="dm-editor-toolbar__status">
					<StatusTag tone={statusTone(selection.status)}>{statusLabel(selection.status, selection.isNew)}</StatusTag>
				</span>
			</div>
			{permissionReason ? (
				<div className="dm-model-context" role="note">
					<AlertTriangle aria-hidden="true" size={14} />
					无指标维护权限；当前以只读方式展示真实指标。
				</div>
			) : null}
			{error ? (
				<div className="dm-representation-state is-error" role="alert">
					<strong>{error.kind === "permission" ? "操作权限不足" : "指标操作失败"}</strong>
					<p>{error.message}</p>
				</div>
			) : null}
			{success ? (
				<output className="dm-model-context">
					<CheckCircle2 aria-hidden="true" size={14} />
					{success}
				</output>
			) : null}
			<div className="dm-metric-editor__scroll">
				<section className="dm-metric-form-section">
					<h2>
						<Sigma aria-hidden="true" size={16} />
						指标基本信息
					</h2>
					<div className="dm-metric-form">
						<div className="dm-metric-form__row">
							<span>
								<b>*</b>指标编码：
							</span>
							<div>
								<input
									aria-label="指标编码"
									className="dm-input"
									disabled={formDisabled || codeLocked}
									onChange={(event) => updateValue("code", event.target.value)}
									placeholder="字母开头，仅允许字母、数字和下划线"
									value={values.code}
								/>
								<p className="dm-metric-warning">
									<AlertTriangle aria-hidden="true" size={13} />
									指标编码保存后不可修改。
								</p>
							</div>
						</div>
						<div className="dm-metric-form__row">
							<span>
								<b>*</b>指标名称：
							</span>
							<div>
								<input
									aria-label="指标名称"
									className="dm-input"
									disabled={formDisabled}
									onChange={(event) => updateValue("name", event.target.value)}
									placeholder="请输入指标名称"
									value={values.name}
								/>
							</div>
						</div>
						<div className="dm-metric-form__row dm-metric-form__row--textarea">
							<span>业务口径：</span>
							<div>
								<textarea
									aria-label="业务口径"
									className="dm-textarea"
									disabled={formDisabled}
									onChange={(event) => updateValue("definition", event.target.value)}
									placeholder="说明统计对象、范围和业务含义"
									rows={3}
									value={values.definition}
								/>
							</div>
						</div>
						<div className="dm-metric-form__row">
							<span>数据域编码：</span>
							<div>
								<input
									aria-label="数据域编码"
									className="dm-input"
									disabled={formDisabled}
									onChange={(event) => updateValue("domain", event.target.value)}
									placeholder="使用已存在的数据域编码；可留空"
									value={values.domain}
								/>
							</div>
						</div>
						<div className="dm-metric-form__row">
							<span>业务分类：</span>
							<div>
								<input
									aria-label="业务分类"
									className="dm-input"
									disabled={formDisabled || categoryLocked}
									onChange={(event) => updateValue("category", event.target.value)}
									placeholder="可选分类编码"
									value={values.category}
								/>
							</div>
						</div>
						<div className="dm-metric-form__row">
							<span>负责人：</span>
							<div>
								<input
									aria-label="负责人"
									className="dm-input"
									disabled={formDisabled}
									onChange={(event) => updateValue("owner", event.target.value)}
									placeholder="负责人账号或名称"
									value={values.owner}
								/>
							</div>
						</div>
						<div className="dm-metric-form__row">
							<span>责任部门：</span>
							<div>
								<input
									aria-label="责任部门"
									className="dm-input"
									disabled={formDisabled}
									onChange={(event) => updateValue("ownerDept", event.target.value)}
									placeholder="留空时由当前部门上下文补全"
									value={values.ownerDept}
								/>
							</div>
						</div>
						<div className="dm-metric-form__row">
							<span>数据密级：</span>
							<div>
								<select
									aria-label="数据密级"
									className="dm-select"
									disabled={formDisabled}
									onChange={(event) => updateValue("dataLevel", event.target.value)}
									value={values.dataLevel}
								>
									<option value="DATA_PUBLIC">公开</option>
									<option value="DATA_INTERNAL">内部</option>
									<option value="DATA_SENSITIVE">敏感</option>
									<option value="DATA_CONFIDENTIAL">机密</option>
								</select>
							</div>
						</div>
						<div className="dm-metric-form__row">
							<span>数据单位：</span>
							<div>
								<input
									aria-label="数据单位"
									className="dm-input"
									disabled={formDisabled}
									onChange={(event) => updateValue("unit", event.target.value)}
									placeholder="例如元、个、百分比"
									value={values.unit}
								/>
							</div>
						</div>
						<div className="dm-metric-form__row">
							<span>小数位数：</span>
							<div>
								<input
									aria-label="小数位数"
									className="dm-input"
									disabled={formDisabled}
									min="0"
									onChange={(event) => updateValue("precisionScale", event.target.value)}
									step="1"
									type="number"
									value={values.precisionScale}
								/>
							</div>
						</div>
						<div className="dm-metric-form__row dm-metric-form__row--textarea">
							<span>版本说明：</span>
							<div>
								<textarea
									aria-label="版本说明"
									className="dm-textarea"
									disabled={formDisabled}
									onChange={(event) => updateValue("versionNotes", event.target.value)}
									placeholder="说明本次变更原因"
									rows={2}
									value={values.versionNotes}
								/>
							</div>
						</div>
					</div>
				</section>

				<section className="dm-metric-form-section">
					<h2>
						<Calculator aria-hidden="true" size={16} />
						{derived ? "派生计算规则" : "原子指标计算规则"}
					</h2>
					<div className="dm-metric-form">
						{derived ? (
							<>
								<div className="dm-metric-form__row">
									<span>
										<b>*</b>依赖指标编码：
									</span>
									<div>
										<input
											aria-label="依赖指标编码"
											className="dm-input"
											disabled={formDisabled}
											onChange={(event) => updateValue("dependencyCodes", event.target.value)}
											placeholder="多个编码用逗号分隔"
											value={values.dependencyCodes}
										/>
									</div>
								</div>
								<div className="dm-metric-form__row dm-metric-form__row--textarea">
									<span>
										<b>*</b>受控计算表达式：
									</span>
									<div>
										<textarea
											aria-label="受控计算表达式"
											className="dm-textarea"
											disabled={formDisabled}
											onChange={(event) => updateValue("expressionSql", event.target.value)}
											placeholder="使用已发布依赖指标编码编写受控表达式"
											rows={4}
											value={values.expressionSql}
										/>
									</div>
								</div>
							</>
						) : (
							<>
								<div className="dm-metric-form__row">
									<span>
										<b>*</b>绑定数据集：
									</span>
									<div>
										<DatasetPicker
											disabled={formDisabled}
											onChange={(value) => updateValue("datasetId", value ?? "")}
											onFieldsLoaded={setDatasetFields}
											placeholder="搜索并选择有权访问的数据集"
											value={values.datasetId || undefined}
										/>
									</div>
								</div>
								<div className="dm-metric-form__row">
									<span>
										<b>*</b>聚合方式：
									</span>
									<div>
										<select
											aria-label="聚合方式"
											className="dm-select"
											disabled={formDisabled}
											onChange={(event) => updateValue("aggregationType", event.target.value)}
											value={values.aggregationType}
										>
											{["SUM", "COUNT", "AVG", "MIN", "MAX", "COUNT_DISTINCT"].map((item) => (
												<option key={item} value={item}>
													{item}
												</option>
											))}
										</select>
									</div>
								</div>
								<div className="dm-metric-form__row">
									<span>
										<b>*</b>度量字段：
									</span>
									<div>
										<select
											aria-label="度量字段"
											className="dm-select"
											disabled={formDisabled || !values.datasetId}
											onChange={(event) => updateValue("measureField", event.target.value)}
											value={values.measureField}
										>
											<option value="">请选择度量字段</option>
											{measureOptions.map((item) => (
												<option key={item} value={item}>
													{item}
												</option>
											))}
										</select>
									</div>
								</div>
								<div className="dm-metric-form__row dm-metric-form__row--textarea">
									<span>
										<b>*</b>计算 SQL：
									</span>
									<div>
										<textarea
											aria-label="计算 SQL"
											className="dm-textarea"
											disabled={formDisabled}
											onChange={(event) => updateValue("expressionSql", event.target.value)}
											placeholder="只允许绑定数据集范围内的只读查询"
											rows={4}
											value={values.expressionSql}
										/>
									</div>
								</div>
							</>
						)}
					</div>
				</section>

				<section className="dm-metric-form-section">
					<h2>
						<CheckCircle2 aria-hidden="true" size={16} />
						治理上下文
					</h2>
					<div className="dm-metric-form">
						<div className="dm-metric-form__row">
							<span>当前版本：</span>
							<div>
								<code>{selection.version || "未保存"}</code>
							</div>
						</div>
						<div className="dm-metric-form__row">
							<span>最近校验：</span>
							<div>
								{selection.lastValidationStatus || "尚未校验"}
								{selection.lastValidationMessage ? ` · ${selection.lastValidationMessage}` : ""}
							</div>
						</div>
						{preflight ? (
							<div className="dm-metric-form__row dm-metric-form__row--textarea">
								<span>校验结果：</span>
								<div>
									{preflight.valid
										? "通过"
										: preflight.issues.map((item) => `${item.code}: ${item.message}`).join("；")}
								</div>
							</div>
						) : null}
						{publishPreview ? (
							<div className="dm-metric-form__row dm-metric-form__row--textarea">
								<span>发布预检：</span>
								<div>
									{publishPreview.readyToPublish
										? "通过"
										: publishPreview.blockingIssues
												.map((item) => `${item.code || "BLOCKER"}: ${item.message || "未通过"}`)
												.join("；")}
								</div>
							</div>
						) : null}
					</div>
					{contextLoading ? <div className="dm-representation-state">正在加载版本和引用…</div> : null}
					{contextError ? (
						<div className="dm-representation-state is-error" role="alert">
							<p>{contextError.message}</p>
							{selectedIndicatorId ? (
								<button className="dm-button" onClick={() => void refreshContext(selectedIndicatorId)} type="button">
									重试
								</button>
							) : null}
						</div>
					) : null}
					{context ? (
						<>
							<div className="dm-field-table-wrap">
								<table className="dm-field-table">
									<thead>
										<tr>
											<th>版本</th>
											<th>状态</th>
											<th>变更说明</th>
											<th>创建人</th>
											<th>创建时间</th>
										</tr>
									</thead>
									<tbody>
										{context.versions.length ? (
											context.versions.map((item, index) => (
												<tr key={item.id || `${item.version}-${index}`}>
													<td>{item.version || "—"}</td>
													<td>{item.status || "—"}</td>
													<td>{item.changeSummary || "—"}</td>
													<td>{item.createdBy || "—"}</td>
													<td>{item.createdDate || "—"}</td>
												</tr>
											))
										) : (
											<tr>
												<td colSpan={5}>暂无版本快照</td>
											</tr>
										)}
									</tbody>
								</table>
							</div>
							<div className="dm-field-table-wrap" style={{ marginTop: 12 }}>
								<table className="dm-field-table">
									<thead>
										<tr>
											<th>引用类型</th>
											<th>引用目标</th>
											<th>引用名称</th>
											<th>说明</th>
										</tr>
									</thead>
									<tbody>
										{context.references.length ? (
											context.references.map((item, index) => (
												<tr key={item.id || `${item.refType}-${item.refTarget}-${index}`}>
													<td>{item.refType || "—"}</td>
													<td>{item.refTarget || "—"}</td>
													<td>{item.refName || "—"}</td>
													<td>{item.notes || "—"}</td>
												</tr>
											))
										) : (
											<tr>
												<td colSpan={4}>暂无 ModelSpec 字段或其他治理引用</td>
											</tr>
										)}
									</tbody>
								</table>
							</div>
						</>
					) : null}
				</section>
			</div>
		</section>
	);
}
