import { Archive, Download, Pencil, Plus, Search } from "lucide-react";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { applyStandardPackageImport, previewStandardPackageImport } from "@/api/modelingStandardsApi";
import { type CompactColumns, CompactTable } from "@/components/table";
import type { DataModelingRoute } from "../types";
import { Button, Modal, PageHeader, RequestState, Status, Toast, useTransientMessage } from "./PrototypePrimitives";
import { normalizeModelingRequestFailure } from "./services/planningProjectionService";
import {
	archiveStandardsRow,
	loadStandardsRows,
	type StandardsEditorValues,
	type StandardsRow,
	type StandardsView,
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
			["domain", "数据域"],
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
	useEffect(() => {
		if (previousView.current !== view) {
			setQuery("");
			setEditorRow(null);
			setImportOpen(false);
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
						{
							title: "操作",
							dataIndex: "actions",
							render: (_: unknown, row: StandardsRow) => (
								<div className="dmx-row-actions">
									{capability.edit ? (
										<Button
											disabled={!canMaintain}
											onClick={() => setEditorRow(row)}
											title={canMaintain ? undefined : "当前账号无标准维护权限"}
											type="link"
										>
											<Pencil size={14} />
											编辑
										</Button>
									) : null}
									{capability.archive ? (
										<ArchiveAction
											disabled={!canMaintain}
											onComplete={async () => {
												show("标准对象已归档");
												await load();
											}}
											row={row}
											view={view}
										/>
									) : null}
								</div>
							),
						},
					]
				: []),
		],
		[page, capability.edit, capability.archive, canMaintain, view, load, show],
	);

	return (
		<main className="dmx-page dmx-catalog-page">
			<PageHeader
				actions={
					<>
						<Button
							disabled={!canMaintain || !capability.importPackage}
							onClick={() => setImportOpen(true)}
							title={canMaintain ? undefined : "当前账号无标准维护权限"}
						>
							<Download size={15} />
							导入标准包
						</Button>
						<Button
							disabled={!canMaintain || !capability.create}
							primary
							onClick={() => setEditorRow("new")}
							title={canMaintain ? capability.disabledReason : "当前账号无标准维护权限"}
						>
							<Plus size={15} />
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
						description={capability.disabledReason || "当前 owner 未返回任何标准记录。"}
						kind="empty"
						title={`暂无${route.title}`}
					/>
				)}
			</section>
			{editorRow ? (
				<StandardsEditor
					onClose={() => setEditorRow(null)}
					onComplete={async () => {
						setEditorRow(null);
						show(editorRow === "new" ? "标准草稿已创建" : "标准对象已更新");
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
			<Toast message={message} />
		</main>
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
				}
			: emptyEditor(),
	);
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
			title={row ? "编辑标准对象" : "新建标准对象"}
		>
			<div className="dmx-form-grid">
				{(["code", "name", "dataType", "domain", "scope", "version"] as const).map((key) => (
					<label key={key}>
						<span>
							{
								{
									code: "编码 *",
									name: "名称 *",
									dataType: "数据类型",
									domain: "数据域",
									scope: "适用范围",
									version: "版本",
								}[key]
							}
						</span>
						<input
							disabled={saving || (Boolean(row) && key === "code")}
							onChange={(event) => set(key, event.target.value)}
							value={values[key]}
						/>
					</label>
				))}
				<label className="dmx-form-field--wide">
					<span>业务定义</span>
					<textarea
						disabled={saving}
						onChange={(event) => set("definition", event.target.value)}
						value={values.definition}
					/>
				</label>
			</div>
			{error ? (
				<div className="dmx-inline-error" role="alert">
					{error}
				</div>
			) : null}
		</Modal>
	);
}

function ArchiveAction({
	view,
	row,
	onComplete,
	disabled,
}: {
	view: StandardsView;
	row: StandardsRow;
	onComplete: () => Promise<void>;
	disabled: boolean;
}) {
	const [busy, setBusy] = useState(false);
	const [error, setError] = useState("");
	const run = async () => {
		if (!window.confirm(`确认归档“${row.name}”？`)) return;
		setBusy(true);
		setError("");
		try {
			await archiveStandardsRow(view, row);
			await onComplete();
		} catch (cause) {
			setError(normalizeModelingRequestFailure(cause, "归档失败").message);
		} finally {
			setBusy(false);
		}
	};
	return (
		<span title={error || undefined}>
			<Button disabled={disabled || busy} onClick={() => void run()} type="link">
				<Archive size={14} />
				{busy ? "归档中…" : "归档"}
			</Button>
		</span>
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
