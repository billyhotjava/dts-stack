import { useEffect, useMemo, useRef, useState } from "react";
import { applyStandardPackageImport, previewStandardPackageImport } from "@/api/modelingStandardsApi";
import { Dialog as AccessibleDialog, DialogContent, DialogDescription, DialogTitle } from "@/ui/dialog";
import { ActionButton, StatusTag } from "../components/WorkspacePage";
import {
	type StandardsCatalogRow,
	type StandardsDetail,
	type StandardsEditorValues,
	type StandardsView,
	saveStandardsRow,
} from "./standardsWorkspaceAdapter";

type DialogFrameProps = {
	title: string;
	description: string;
	onClose: () => void;
	children: React.ReactNode;
	footer?: React.ReactNode;
	wide?: boolean;
};

function DialogFrame({ title, description, onClose, children, footer, wide = false }: DialogFrameProps) {
	const returnFocusRef = useRef<HTMLElement | null>(
		typeof document !== "undefined" && document.activeElement instanceof HTMLElement ? document.activeElement : null,
	);
	return (
		<AccessibleDialog open onOpenChange={(open) => !open && onClose()}>
			<DialogContent
				className={`dm-dialog ${wide ? "dm-dialog--wide" : ""}`}
				onCloseAutoFocus={(event) => {
					event.preventDefault();
					returnFocusRef.current?.focus();
				}}
			>
				<header className="dm-dialog__header">
					<div>
						<DialogTitle>{title}</DialogTitle>
						<DialogDescription>{description}</DialogDescription>
					</div>
				</header>
				<div className="dm-dialog__body">{children}</div>
				{footer ? <footer className="dm-dialog__footer">{footer}</footer> : null}
			</DialogContent>
		</AccessibleDialog>
	);
}

const fieldValue = (row: StandardsCatalogRow | null, key: string, fallback = "") => {
	const sourceValue = row?.source?.[key];
	const rowValue = row?.[key as keyof StandardsCatalogRow];
	return String(sourceValue ?? rowValue ?? fallback).trim();
};

const editorInitialValues = (row: StandardsCatalogRow | null): StandardsEditorValues => ({
	code: fieldValue(row, "code"),
	name: fieldValue(row, "name"),
	dataType: fieldValue(row, "dataType"),
	definition: fieldValue(row, "description", fieldValue(row, "definition")),
	domain: fieldValue(row, "domain"),
	version: fieldValue(row, "currentVersion", fieldValue(row, "version", "v1")),
	aliases: fieldValue(row, "aliases"),
	scope: fieldValue(row, "scope"),
});

export function StandardsEditorDialog({
	view,
	row,
	canManage,
	onClose,
	onSaved,
}: {
	view: StandardsView;
	row: StandardsCatalogRow | null;
	canManage: boolean;
	onClose: () => void;
	onSaved: (message: string) => void | Promise<void>;
}) {
	const [values, setValues] = useState<StandardsEditorValues>(() => editorInitialValues(row));
	const [saving, setSaving] = useState(false);
	const [error, setError] = useState("");

	useEffect(() => {
		setValues(editorInitialValues(row));
		setError("");
	}, [row]);

	const update = (key: keyof StandardsEditorValues, value: string) =>
		setValues((current) => ({ ...current, [key]: value }));

	const submit = async (event: React.FormEvent) => {
		event.preventDefault();
		if (!canManage) {
			setError("当前账号没有标准维护权限");
			return;
		}
		if (!values.code.trim() || !values.name.trim()) {
			setError("编码和名称不能为空");
			return;
		}
		setSaving(true);
		setError("");
		try {
			await saveStandardsRow(view, values, row);
			await onSaved(row ? "标准已更新" : "标准已创建");
			onClose();
		} catch (cause) {
			setError(cause instanceof Error ? cause.message : "保存失败，请稍后重试");
		} finally {
			setSaving(false);
		}
	};

	return (
		<DialogFrame
			description="保存后写入现有标准专业域，服务端统一记录审计。"
			onClose={onClose}
			title={row ? "编辑标准" : "新建标准"}
			footer={
				<>
					<ActionButton disabled={saving} onClick={onClose}>
						取消
					</ActionButton>
					<button
						className="dm-button dm-button--primary"
						disabled={saving || !canManage}
						form="dm-standard-editor"
						title={!canManage ? "当前账号没有标准维护权限" : undefined}
						type="submit"
					>
						{saving ? "保存中…" : "保存"}
					</button>
				</>
			}
		>
			<form className="dm-form-grid" id="dm-standard-editor" onSubmit={submit}>
				<div className="dm-form-field">
					<label htmlFor="dm-standard-code">标准编码 *</label>
					<input
						className="dm-input"
						id="dm-standard-code"
						onChange={(event) => update("code", event.target.value)}
						value={values.code}
					/>
				</div>
				<div className="dm-form-field">
					<label htmlFor="dm-standard-name">标准名称 *</label>
					<input
						className="dm-input"
						id="dm-standard-name"
						onChange={(event) => update("name", event.target.value)}
						value={values.name}
					/>
				</div>
				{view !== "dictionary" ? (
					<div className="dm-form-field">
						<label htmlFor="dm-standard-type">数据类型</label>
						<input
							className="dm-input"
							id="dm-standard-type"
							onChange={(event) => update("dataType", event.target.value)}
							value={values.dataType || ""}
						/>
					</div>
				) : (
					<div className="dm-form-field">
						<label htmlFor="dm-standard-aliases">别名</label>
						<input
							className="dm-input"
							id="dm-standard-aliases"
							onChange={(event) => update("aliases", event.target.value)}
							value={values.aliases || ""}
						/>
					</div>
				)}
				<div className="dm-form-field">
					<label htmlFor="dm-standard-domain">数据域</label>
					<input
						className="dm-input"
						id="dm-standard-domain"
						onChange={(event) => update("domain", event.target.value)}
						value={values.domain || ""}
					/>
				</div>
				<div className="dm-form-field">
					<label htmlFor="dm-standard-version">版本</label>
					<input
						className="dm-input"
						id="dm-standard-version"
						onChange={(event) => update("version", event.target.value)}
						value={values.version || ""}
					/>
				</div>
				{view !== "dictionary" ? (
					<div className="dm-form-field">
						<label htmlFor="dm-standard-scope">适用范围</label>
						<input
							className="dm-input"
							id="dm-standard-scope"
							onChange={(event) => update("scope", event.target.value)}
							value={values.scope || ""}
						/>
					</div>
				) : null}
				{view !== "codes" ? (
					<div className="dm-form-field dm-form-field--wide">
						<label htmlFor="dm-standard-definition">业务定义</label>
						<textarea
							className="dm-textarea"
							id="dm-standard-definition"
							onChange={(event) => update("definition", event.target.value)}
							rows={4}
							value={values.definition || ""}
						/>
					</div>
				) : null}
			</form>
			{error ? <div className="dm-pending-callout">{error}</div> : null}
			{!canManage ? <div className="dm-pending-callout">当前账号仅可查看，不能维护标准。</div> : null}
		</DialogFrame>
	);
}

type PackagePreview = {
	runId?: string;
	packageName?: string;
	blocking?: boolean;
	totalErrors?: number;
	files?: Array<{
		file?: string;
		present?: boolean;
		total?: number;
		toCreate?: number;
		toUpdate?: number;
		errorCount?: number;
		errors?: Array<{ row?: number; message?: string }>;
	}>;
};

type PackageApplyResult = {
	status?: string;
	totalCreated?: number;
	totalUpdated?: number;
};

export function StandardPackageImportDialog({
	canManage,
	onClose,
	onApplied,
}: {
	canManage: boolean;
	onClose: () => void;
	onApplied: (message: string) => void | Promise<void>;
}) {
	const [file, setFile] = useState<File | null>(null);
	const [preview, setPreview] = useState<PackagePreview | null>(null);
	const [result, setResult] = useState<PackageApplyResult | null>(null);
	const [busy, setBusy] = useState<"preview" | "apply" | null>(null);
	const [error, setError] = useState("");

	const failureReasons = useMemo(
		() =>
			(preview?.files || []).flatMap((entry) =>
				(entry.errors || []).map(
					(item) => `${entry.file || "未知文件"}${item.row ? ` 第 ${item.row} 行` : ""}：${item.message || "校验失败"}`,
				),
			),
		[preview],
	);

	const previewPackage = async () => {
		if (!canManage) {
			setError("当前账号没有标准包导入权限");
			return;
		}
		if (!file) {
			setError("请先选择 zip 标准包");
			return;
		}
		setBusy("preview");
		setError("");
		setPreview(null);
		setResult(null);
		try {
			const formData = new FormData();
			formData.append("file", file);
			setPreview((await previewStandardPackageImport(formData)) as PackagePreview);
		} catch (cause) {
			setError(cause instanceof Error ? cause.message : "标准包预检失败，请重试");
		} finally {
			setBusy(null);
		}
	};

	const applyPackage = async () => {
		if (!preview?.runId || preview.blocking) return;
		setBusy("apply");
		setError("");
		try {
			const applied = (await applyStandardPackageImport(preview.runId)) as PackageApplyResult;
			setResult(applied);
			await onApplied(`标准包已应用：新增 ${applied.totalCreated ?? 0}，更新 ${applied.totalUpdated ?? 0}`);
		} catch (cause) {
			setError(cause instanceof Error ? cause.message : "标准包应用失败，请重试");
		} finally {
			setBusy(null);
		}
	};

	return (
		<DialogFrame
			description="仅支持标准包 zip；必须先预检，确认无阻断错误后才能应用。"
			onClose={onClose}
			title="导入数据标准包"
			wide
			footer={
				<>
					<ActionButton disabled={Boolean(busy)} onClick={onClose}>
						关闭
					</ActionButton>
					<ActionButton disabled={!file || Boolean(busy) || !canManage} onClick={previewPackage}>
						{busy === "preview" ? "预检中…" : "开始预检"}
					</ActionButton>
					<ActionButton
						disabled={!preview?.runId || Boolean(preview?.blocking) || Boolean(busy) || !canManage || Boolean(result)}
						kind="primary"
						onClick={applyPackage}
						title={preview?.blocking ? "预检存在阻断错误，不能应用" : undefined}
					>
						{busy === "apply" ? "应用中…" : "确认应用"}
					</ActionButton>
				</>
			}
		>
			<label className="dm-form-field" htmlFor="dm-standard-package-file">
				<span>标准包文件</span>
				<input
					accept=".zip,application/zip"
					disabled={!canManage || Boolean(busy)}
					id="dm-standard-package-file"
					onChange={(event) => {
						setFile(event.target.files?.[0] || null);
						setPreview(null);
						setResult(null);
						setError("");
					}}
					type="file"
				/>
			</label>
			{error ? (
				<div className="dm-pending-callout" role="alert">
					{error}{" "}
					<button onClick={() => void previewPackage()} type="button">
						重试预检
					</button>
				</div>
			) : null}
			{preview ? (
				<div>
					<p>
						<StatusTag tone={preview.blocking ? "danger" : "success"}>
							{preview.blocking ? "预检未通过" : "预检通过"}
						</StatusTag>{" "}
						{preview.packageName || file?.name} · 错误 {preview.totalErrors ?? failureReasons.length} 条
					</p>
					<div className="dm-table-wrap">
						<table className="dm-table">
							<thead>
								<tr>
									<th>文件</th>
									<th>记录数</th>
									<th>新增</th>
									<th>更新</th>
									<th>错误</th>
								</tr>
							</thead>
							<tbody>
								{(preview.files || []).map((entry) => (
									<tr key={entry.file}>
										<td>{entry.file}</td>
										<td>{entry.total ?? 0}</td>
										<td>{entry.toCreate ?? 0}</td>
										<td>{entry.toUpdate ?? 0}</td>
										<td>{entry.errorCount ?? 0}</td>
									</tr>
								))}
							</tbody>
						</table>
					</div>
					{failureReasons.length ? (
						<div className="dm-pending-callout">
							<strong>失败原因</strong>
							<ul>
								{failureReasons.map((reason) => (
									<li key={reason}>{reason}</li>
								))}
							</ul>
						</div>
					) : null}
				</div>
			) : null}
			{result ? (
				<output className="dm-pending-callout">
					标准包应用成功：新增 {result.totalCreated ?? 0}，更新 {result.totalUpdated ?? 0}。
				</output>
			) : null}
			{!canManage ? <div className="dm-pending-callout">当前账号仅可查看，不能导入标准包。</div> : null}
		</DialogFrame>
	);
}

const displayValue = (value: unknown) => {
	if (value == null || value === "") return "—";
	if (Array.isArray(value)) return value.join("、") || "—";
	if (typeof value === "object") return JSON.stringify(value);
	return String(value);
};

export function StandardsDetailDialog({
	detail,
	loading,
	error,
	onRetry,
	onClose,
}: {
	detail: StandardsDetail | null;
	loading: boolean;
	error: string;
	onRetry: () => void;
	onClose: () => void;
}) {
	return (
		<DialogFrame
			description="详情和引用证据直接读取专业域，不维护第二套台账。"
			onClose={onClose}
			title={detail?.title || "标准详情"}
			wide
			footer={<ActionButton onClick={onClose}>关闭</ActionButton>}
		>
			{loading ? <output>详情加载中…</output> : null}
			{error ? (
				<div className="dm-pending-callout" role="alert">
					{error}{" "}
					<button onClick={onRetry} type="button">
						重新加载
					</button>
				</div>
			) : null}
			{detail ? (
				<>
					<div className="dm-form-grid">
						{Object.entries(detail.primary)
							.slice(0, 16)
							.map(([key, value]) => (
								<div className="dm-form-field" key={key}>
									<strong>{key}</strong>
									<span>{displayValue(value)}</span>
								</div>
							))}
					</div>
					{detail.sections.map((section) => (
						<section key={section.title}>
							<h3>{section.title}</h3>
							{section.rows.length ? (
								<ul>
									{section.rows.map((row, index) => (
										<li key={String(row.id ?? index)}>{displayValue(row)}</li>
									))}
								</ul>
							) : (
								<p>暂无记录</p>
							)}
						</section>
					))}
				</>
			) : null}
		</DialogFrame>
	);
}
