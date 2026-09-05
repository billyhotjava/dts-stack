import { Search } from "lucide-react";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { applyStandardPackageImport, previewStandardPackageImport } from "@/api/modelingStandardsApi";
import catalogDomainService, { type CatalogDomain } from "@/api/services/catalogDomainService";
import { actionColumn, type CompactColumns, CompactTable } from "@/components/table";
import type { DataModelingRoute } from "../types";
import { Button, Modal, PageHeader, RequestState, Status, Toast, useTransientMessage } from "./PrototypePrimitives";
import { StandardMappingSuggestionModal } from "./StandardMappingSuggestionModal";
import { normalizeModelingRequestFailure } from "./services/planningProjectionService";
import {
	archiveStandardsRow,
	loadStandardMappingOptions,
	loadStandardsRows,
	type StandardMappingOptions,
	type StandardMappingValues,
	type StandardsEditorValues,
	type StandardsRow,
	type StandardsView,
	saveStandardMapping,
	saveStandardsRow,
	standardsCapability,
} from "./services/standardsProjectionService";
import { useDataModelingMenuGrant } from "./useDataModelingMenuGrant";

const config: Record<
	StandardsView,
	{ action: string; headers: Array<[keyof StandardsRow, string]>; placeholder: string }
> = {
	fields: {
		action: "新建字段标准",
		headers: [
			["code", "标准编码"],
			["name", "标准名称"],
			["dataType", "数据类型"],
			["definition", "业务定义"],
			["domain", "分类"],
			["version", "版本"],
			["state", "状态"],
		],
		placeholder: "搜索标准编码或中文名称",
	},
	codes: {
		action: "新建标准代码",
		headers: [
			["code", "代码集编码"],
			["name", "代码集名称"],
			["valueCount", "值数量"],
			["domain", "数据域"],
			["scope", "适用范围"],
			["version", "版本"],
			["state", "状态"],
		],
		placeholder: "搜索代码集",
	},
	roots: {
		action: "新建词根",
		headers: [
			["code", "词根编码"],
			["name", "中文词根"],
			["definition", "英文全称"],
			["scope", "英文缩写"],
			["domain", "分类"],
			["version", "版本"],
			["state", "状态"],
		],
		placeholder: "搜索词根",
	},
	dictionary: {
		action: "新建命名词条",
		headers: [
			["code", "词条编码"],
			["name", "中文名称"],
			["definition", "业务定义"],
			["scope", "别名"],
			["domain", "分类"],
			["version", "版本"],
			["state", "状态"],
		],
		placeholder: "搜索命名词条",
	},
	mappings: {
		action: "新建标准映射",
		headers: [
			["code", "字段编码"],
			["name", "字段名称"],
			["definition", "标准/码表"],
			["domain", "所属模型"],
			["dataType", "数据类型"],
			["version", "版本"],
			["state", "状态"],
		],
		placeholder: "搜索标准或模型字段",
	},
};

const emptyEditor = (): StandardsEditorValues => ({
	code: "",
	name: "",
	dataType: "",
	definition: "",
	domain: "",
	scope: "",
	version: "v1",
});

export function StandardsPage({ route }: { route: DataModelingRoute }) {
	const canMaintain = useDataModelingMenuGrant();
	const view = (route.view in config ? route.view : "fields") as StandardsView;
	const page = config[view];
	const capability = standardsCapability(view);
	const requestEpoch = useRef(0);
	const [rows, setRows] = useState<StandardsRow[]>([]);
	const [query, setQuery] = useState("");
	const [loading, setLoading] = useState(true);
	const [failure, setFailure] = useState<{ kind: "permission" | "request"; message: string } | null>(null);
	const [editorRow, setEditorRow] = useState<StandardsRow | "new" | null>(null);
	const [importOpen, setImportOpen] = useState(false);
	const [suggestionsOpen, setSuggestionsOpen] = useState(false);
	// 归档动作原先封在行内组件 ArchiveAction 里各自持有 state，改用 RowActions 后上提到页面级。
	const [archivingId, setArchivingId] = useState<string | null>(null);
	const [archiveError, setArchiveError] = useState("");
	const previousView = useRef(view);
	const { message, show } = useTransientMessage();
	const load = useCallback(async () => {
		const epoch = ++requestEpoch.current;
		setLoading(true);
		setFailure(null);
		try {
			const next = await loadStandardsRows(view, query);
			if (requestEpoch.current === epoch) setRows(next);
		} catch (error) {
			if (requestEpoch.current !== epoch) return;
			setRows([]);
			setFailure(normalizeModelingRequestFailure(error, `${route.title}读取失败，请稍后重试。`));
		} finally {
			if (requestEpoch.current === epoch) setLoading(false);
		}
	}, [query, route.title, view]);
	const archiveRow = useCallback(
		async (row: StandardsRow) => {
			if (!window.confirm(`确认归档“${row.name}”？`)) return;
			setArchivingId(row.id);
			setArchiveError("");
			try {
				await archiveStandardsRow(view, row);
				show("标准对象已归档");
				await load();
			} catch (cause) {
				setArchiveError(normalizeModelingRequestFailure(cause, "归档失败").message);
			} finally {
				setArchivingId(null);
			}
		},
		[load, show, view],
	);
	useEffect(() => {
		if (previousView.current !== view) {
			setQuery("");
			setEditorRow(null);
			setImportOpen(false);
			setSuggestionsOpen(false);
			previousView.current = view;
		}
	}, [view]);
	useEffect(() => {
		void load();
		return () => {
			requestEpoch.current += 1;
		};
	}, [load]);

	const columns = useMemo<CompactColumns<StandardsRow>>(
		() => [
			...page.headers.map(([key, header]) =>
				key === "state"
					? {
							title: header,
							dataIndex: key,
							render: (value: StandardsRow["state"]) => (
								<Status
									tone={value.includes("生效") || value.includes("发布") || value === "有效" ? "success" : "warning"}
								>
									{value}
								</Status>
							),
						}
					: { title: header, dataIndex: key },
			),
			...(capability.edit || capability.archive
				? [
						actionColumn<StandardsRow>(
							(row) => [
								{
									key: "edit",
									label: "编辑",
									hidden: !capability.edit,
									disabled: !canMaintain || (view === "mappings" && row.state !== "草稿"),
									tooltip: canMaintain
										? view === "mappings" && row.state !== "草稿"
											? "仅草稿模型可以修改标准映射"
											: undefined
										: "当前账号无标准维护权限",
									onClick: () => setEditorRow(row),
								},
								{
									key: "archive",
									label: archivingId === row.id ? "归档中…" : "归档",
									hidden: !capability.archive,
									disabled: !canMaintain || archivingId === row.id,
									tooltip: archiveError || undefined,
									onClick: () => void archiveRow(row),
								},
							],
							{ maxActions: 2 },
						),
					]
				: []),
		],
		[page, capability.edit, capability.archive, canMaintain, archivingId, archiveError, archiveRow, view],
	);

	return (
		<main className="dmx-page dmx-catalog-page">
			<PageHeader
				actions={
					<>
						{view === "mappings" ? (
							<Button
								disabled={!canMaintain}
								onClick={() => setSuggestionsOpen(true)}
								title={canMaintain ? undefined : "当前账号无标准维护权限"}
							>
								智能补全标准
							</Button>
						) : null}
						<Button
							disabled={!canMaintain || !capability.importPackage}
							onClick={() => setImportOpen(true)}
							title={canMaintain ? undefined : "当前账号无标准维护权限"}
						>
							导入标准包
						</Button>
						<Button
							disabled={!canMaintain || !capability.create}
							primary
							onClick={() => setEditorRow("new")}
							title={canMaintain ? capability.disabledReason : "当前账号无标准维护权限"}
						>
							{page.action}
						</Button>
					</>
				}
				description={route.description}
				title={route.title}
				trail="数据建模 / 数据标准"
			/>
			<section className="dmx-catalog-panel dmx-catalog-panel--list">
				{!canMaintain ? <div className="dmx-capability-note">当前账号只有标准目录查看权限。</div> : null}
				{capability.disabledReason ? <div className="dmx-capability-note">{capability.disabledReason}</div> : null}
				{capability.archiveDisabledReason ? (
					<div className="dmx-capability-note">{capability.archiveDisabledReason}</div>
				) : null}
				<div className="dmx-list-toolbar">
					<label>
						<Search size={15} />
						<input onChange={(event) => setQuery(event.target.value)} placeholder={page.placeholder} value={query} />
					</label>
					<Button disabled={loading} onClick={() => void load()}>
						查询
					</Button>
					<span>共 {rows.length} 条</span>
				</div>
				{loading ? (
					<RequestState description="正在读取标准治理事实。" kind="loading" title="正在加载" />
				) : failure ? (
					<RequestState
						description={failure.message}
						kind={failure.kind === "permission" ? "permission" : "error"}
						onRetry={failure.kind === "request" ? () => void load() : undefined}
						title={failure.kind === "permission" ? "无权访问" : "读取失败"}
					/>
				) : rows.length ? (
					<div className="dmx-table-scroll">
						<CompactTable<StandardsRow>
							columns={columns}
							dataSource={rows}
							pagination={{ pageSize: 10 }}
							rowKey={(row) => row.id || row.code}
						/>
					</div>
				) : (
					<RequestState
						description={capability.disabledReason || "当前功能入口未返回任何标准记录。"}
						kind="empty"
						title={`暂无${route.title}`}
					/>
				)}
			</section>
			{editorRow && view === "mappings" ? (
				<StandardMappingEditor
					onClose={() => setEditorRow(null)}
					onComplete={async () => {
						setEditorRow(null);
						show(editorRow === "new" ? "标准映射已创建" : "标准映射已更新");
						await load();
					}}
					row={editorRow === "new" ? null : editorRow}
				/>
			) : editorRow ? (
				<StandardsEditor
					onClose={() => setEditorRow(null)}
					onComplete={async () => {
						setEditorRow(null);
						show(
							view === "roots"
								? editorRow === "new"
									? "词根已创建"
									: "词根已更新"
								: editorRow === "new"
									? "标准草稿已创建"
									: "标准对象已更新",
						);
						await load();
					}}
					row={editorRow === "new" ? null : editorRow}
					view={view}
				/>
			) : null}
			{importOpen ? (
				<StandardPackageImport
					onClose={() => setImportOpen(false)}
					onComplete={async () => {
						setImportOpen(false);
						show("标准包已应用");
						await load();
					}}
				/>
			) : null}
			{suggestionsOpen ? (
				<StandardMappingSuggestionModal
					onClose={() => setSuggestionsOpen(false)}
					onComplete={async (successCount, failureCount) => {
						setSuggestionsOpen(false);
						show(
							failureCount
								? `标准映射已补全 ${successCount} 个模型，${failureCount} 个模型失败，可重新执行`
								: `标准映射已补全 ${successCount} 个模型`,
						);
						await load();
					}}
				/>
			) : null}
			<Toast message={message} />
		</main>
	);
}

function StandardMappingEditor({
	row,
	onClose,
	onComplete,
}: {
	row: StandardsRow | null;
	onClose: () => void;
	onComplete: () => Promise<void>;
}) {
	const [options, setOptions] = useState<StandardMappingOptions | null>(null);
	const [values, setValues] = useState<StandardMappingValues>({
		modelId: String(row?.source.modelId ?? ""),
		fieldName: String(row?.source.fieldName ?? ""),
		standardId: String(row?.source.standardId ?? ""),
	});
	const [loading, setLoading] = useState(true);
	const [saving, setSaving] = useState(false);
	const [error, setError] = useState("");
	useEffect(() => {
		let active = true;
		void loadStandardMappingOptions()
			.then((next) => {
				if (active) setOptions(next);
			})
			.catch((cause) => {
				if (active) setError(normalizeModelingRequestFailure(cause, "映射选项读取失败，请重试。").message);
			})
			.finally(() => {
				if (active) setLoading(false);
			});
		return () => {
			active = false;
		};
	}, []);
	const fields = options?.models.find((model) => model.id === values.modelId)?.fields || [];
	const save = async () => {
		setSaving(true);
		setError("");
		try {
			await saveStandardMapping(values);
			await onComplete();
		} catch (cause) {
			setError(normalizeModelingRequestFailure(cause, "标准映射保存失败，请重试。").message);
		} finally {
			setSaving(false);
		}
	};
	return (
		<Modal
			footer={
				<>
					<Button disabled={saving} onClick={onClose}>
						取消
					</Button>
					<Button disabled={saving || loading} primary onClick={() => void save()}>
						{saving ? "保存中…" : "保存映射"}
					</Button>
				</>
			}
			onClose={onClose}
			title={row ? "编辑标准映射" : "新建标准映射"}
		>
			<div className="dmx-form-grid">
				<label>
					<span>所属模型 *</span>
					<select
						disabled={saving || loading || Boolean(row)}
						onChange={(event) => setValues({ ...values, modelId: event.target.value, fieldName: "" })}
						value={values.modelId}
					>
						<option value="">请选择模型</option>
						{options?.models.map((model) => (
							<option key={model.id} value={model.id}>
								{model.name}（{model.status}）
							</option>
						))}
					</select>
				</label>
				<label>
					<span>模型字段 *</span>
					<select
						disabled={saving || loading || !values.modelId || Boolean(row)}
						onChange={(event) => setValues({ ...values, fieldName: event.target.value })}
						value={values.fieldName}
					>
						<option value="">请选择模型字段</option>
						{fields.map((field) => (
							<option key={field.name} value={field.name}>
								{field.displayName}（{field.name}，{field.dataType}）
							</option>
						))}
					</select>
				</label>
				<label className="dmx-form-field--wide">
					<span>数据元标准 *</span>
					<select
						disabled={saving || loading}
						onChange={(event) => setValues({ ...values, standardId: event.target.value })}
						value={values.standardId}
					>
						<option value="">请选择数据元标准</option>
						{options?.standards.map((standard) => (
							<option key={standard.id} value={standard.id}>
								{standard.name}（{standard.code}，v{standard.version}）
							</option>
						))}
					</select>
				</label>
			</div>
			{!loading && options && (!options.models.length || !options.standards.length) ? (
				<div className="dmx-capability-note">请先准备可编辑模型、模型字段和数据元标准。</div>
			) : null}
			{error ? (
				<div className="dmx-inline-error" role="alert">
					{error}
				</div>
			) : null}
		</Modal>
	);
}

function StandardsEditor({
	view,
	row,
	onClose,
	onComplete,
}: {
	view: StandardsView;
	row: StandardsRow | null;
	onClose: () => void;
	onComplete: () => Promise<void>;
}) {
	const fields =
		view === "roots"
			? (["code", "name", "definition", "scope", "domain", "version"] as const)
			: (["code", "name", "dataType", "domain", "scope", "version"] as const);
	const labels: Partial<Record<keyof StandardsEditorValues, string>> =
		view === "roots"
			? {
					code: "词根编码 *",
					name: "中文词根 *",
					definition: "英文全称 *",
					scope: "英文缩写 *",
					domain: "分类",
					version: "版本",
				}
			: {
					code: "编码 *",
					name: "名称 *",
					dataType: "数据类型",
					domain: "数据域",
					scope: "适用范围",
					version: "版本",
				};
	const [values, setValues] = useState<StandardsEditorValues>(() =>
		row
			? {
					code: row.code,
					name: row.name,
					dataType: row.dataType === "—" ? "" : row.dataType,
					definition: row.definition === "—" ? "" : row.definition,
					domain: row.domain === "—" ? "" : row.domain,
					scope: row.scope === "—" ? "" : row.scope,
					version: row.version === "—" ? "v1" : row.version,
					status: String(row.source.status ?? 0),
				}
			: emptyEditor(),
	);
	const [domains, setDomains] = useState<CatalogDomain[]>([]);
	const [domainsLoading, setDomainsLoading] = useState(view === "codes");
	const [domainsError, setDomainsError] = useState("");
	useEffect(() => {
		if (view !== "codes") return;
		let active = true;
		void catalogDomainService.list().then(
			(items) => {
				if (active) {
					setDomains(items);
					setDomainsLoading(false);
				}
			},
			() => {
				if (active) {
					setDomainsError("数据域加载失败，请关闭后重试。");
					setDomainsLoading(false);
				}
			},
		);
		return () => {
			active = false;
		};
	}, [view]);
	const dataTypes = ["STRING", "BOOLEAN", "INT", "BIGINT", "DECIMAL", "DATE", "TIMESTAMP"];
	if (values.dataType && !dataTypes.includes(values.dataType)) dataTypes.push(values.dataType);
	const [saving, setSaving] = useState(false);
	const [error, setError] = useState("");
	const set = (key: keyof StandardsEditorValues, value: string) =>
		setValues((current) => ({ ...current, [key]: value }));
	const save = async () => {
		setSaving(true);
		setError("");
		try {
			await saveStandardsRow(view, values, row);
			await onComplete();
		} catch (cause) {
			setError(normalizeModelingRequestFailure(cause, "标准保存失败，请重试。").message);
		} finally {
			setSaving(false);
		}
	};
	return (
		<Modal
			footer={
				<>
					<Button disabled={saving} onClick={onClose}>
						取消
					</Button>
					<Button disabled={saving} primary onClick={() => void save()}>
						{saving ? "保存中…" : "保存"}
					</Button>
				</>
			}
			onClose={onClose}
			title={view === "roots" ? (row ? "编辑词根" : "新建词根") : row ? "编辑标准对象" : "新建标准对象"}
		>
			<div className="dmx-form-grid">
				{fields.map((key) => (
					<label key={key} htmlFor={`standard-${view}-${key}`}>
						<span>{labels[key]}</span>
						{view === "codes" && key === "dataType" ? (
							<select
								id={`standard-${view}-${key}`}
								disabled={saving}
								onChange={(event) => set(key, event.target.value)}
								value={values[key]}
							>
								<option value="">请选择数据类型</option>
								{dataTypes.map((type) => (
									<option key={type} value={type}>
										{type}
									</option>
								))}
							</select>
						) : view === "codes" && key === "domain" ? (
							<select
								id={`standard-${view}-${key}`}
								disabled={saving || domainsLoading || Boolean(domainsError)}
								onChange={(event) => set(key, event.target.value)}
								value={values[key]}
							>
								<option value="">{domainsLoading ? "加载数据域…" : "请选择数据域"}</option>
								{values.domain && !domains.some((domain) => domain.code === values.domain) ? (
									<option value={values.domain}>{values.domain}（已保存）</option>
								) : null}
								{domains.map((domain) => (
									<option key={domain.id} value={domain.code}>
										{domain.name} · {domain.code}
									</option>
								))}
							</select>
						) : (
							<input
								id={`standard-${view}-${key}`}
								disabled={saving || (Boolean(row) && key === "code")}
								onChange={(event) => set(key, event.target.value)}
								value={values[key]}
							/>
						)}
					</label>
				))}
				{view === "codes" ? (
					<label>
						<span>状态</span>
						<select
							disabled={saving}
							value={values.status ?? "0"}
							onChange={(event) => set("status", event.target.value)}
						>
							<option value="0">草稿</option>
							<option value="1">已发布</option>
							<option value="2">已废弃</option>
						</select>
					</label>
				) : null}
				{domainsError ? <div role="alert">{domainsError}</div> : null}
				{view !== "roots" ? (
					<label className="dmx-form-field--wide">
						<span>业务定义</span>
						<textarea
							disabled={saving}
							onChange={(event) => set("definition", event.target.value)}
							value={values.definition}
						/>
					</label>
				) : null}
			</div>
			{error ? (
				<div className="dmx-inline-error" role="alert">
					{error}
				</div>
			) : null}
		</Modal>
	);
}

function StandardPackageImport({ onClose, onComplete }: { onClose: () => void; onComplete: () => Promise<void> }) {
	const [file, setFile] = useState<File | null>(null);
	const [runId, setRunId] = useState("");
	const [summary, setSummary] = useState("");
	const [busy, setBusy] = useState<"preview" | "apply" | "">("");
	const [error, setError] = useState("");
	const preview = async () => {
		if (!file) return setError("请选择标准包文件");
		setBusy("preview");
		setError("");
		try {
			const data = new FormData();
			data.append("file", file);
			const result = (await previewStandardPackageImport(data)) as Record<string, unknown>;
			const id = String(result.runId || "");
			if (!id) throw new Error("预检未返回 runId");
			setRunId(id);
			setSummary(String(result.summary || result.status || "预检完成，可应用标准包。"));
		} catch (cause) {
			setError(normalizeModelingRequestFailure(cause, "标准包预检失败，请重试。").message);
		} finally {
			setBusy("");
		}
	};
	const apply = async () => {
		if (!runId) return;
		setBusy("apply");
		setError("");
		try {
			await applyStandardPackageImport(runId);
			await onComplete();
		} catch (cause) {
			setError(normalizeModelingRequestFailure(cause, "标准包应用失败，请重试。").message);
		} finally {
			setBusy("");
		}
	};
	return (
		<Modal
			footer={
				<>
					<Button disabled={Boolean(busy)} onClick={onClose}>
						取消
					</Button>
					{runId ? (
						<Button disabled={Boolean(busy)} primary onClick={() => void apply()}>
							{busy === "apply" ? "应用中…" : "应用标准包"}
						</Button>
					) : (
						<Button disabled={Boolean(busy) || !file} primary onClick={() => void preview()}>
							{busy === "preview" ? "预检中…" : "预检"}
						</Button>
					)}
				</>
			}
			onClose={onClose}
			title="导入标准包"
		>
			<div className="dmx-dropzone">
				<input
					accept=".zip,application/zip"
					aria-label="选择标准包"
					className="dmx-dropzone-input"
					onChange={(event) => {
						setFile(event.target.files?.[0] || null);
						setRunId("");
						setSummary("");
					}}
					type="file"
				/>
				<span>{file?.name || "选择 ZIP 标准包后先执行预检"}</span>
			</div>
			{summary ? <div className="dmx-capability-note">{summary}</div> : null}
			{error ? (
				<div className="dmx-inline-error" role="alert">
					{error}
				</div>
			) : null}
		</Modal>
	);
}
