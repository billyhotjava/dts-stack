import { useEffect, useMemo, useState } from "react";
import { analyticsApi, type ReportRunItem, type ReportTemplateItem } from "../api/analyticsApi";
import { ErrorNotice } from "../components/ErrorNotice";
import { PageHeader } from "@/components/page-header";
import { getEffectiveLocale, t, type Locale } from "../i18n";
import { Input, Spin, Button, Card, Tag, Select } from "antd";
type LoadState<T> =
	| { state: "loading" }
	| { state: "loaded"; value: T }
	| { state: "error"; error: unknown };

function toIdString(value?: string | number | null): string {
	return value == null ? "" : String(value);
}

function formatTime(raw?: string): string {
	if (!raw) return "-";
	const time = new Date(raw);
	if (Number.isNaN(time.getTime())) return raw;
	return time.toLocaleString();
}

export default function ReportFactoryPage() {
	const locale: Locale = useMemo(() => getEffectiveLocale(), []);
	const [templates, setTemplates] = useState<LoadState<ReportTemplateItem[]>>({ state: "loading" });
	const [runs, setRuns] = useState<LoadState<ReportRunItem[]>>({ state: "loading" });
	const [selectedRun, setSelectedRun] = useState<LoadState<ReportRunItem> | null>(null);

	const [templateName, setTemplateName] = useState("");
	const [templateDesc, setTemplateDesc] = useState("");
	const [templateSections, setTemplateSections] = useState("问题背景,关键洞察,风险与建议");

	const [generateTemplateId, setGenerateTemplateId] = useState("");
	const [sourceType, setSourceType] = useState("session");
	const [sourceId, setSourceId] = useState("");
	const [outputFormat, setOutputFormat] = useState("html");
	const [distributionJson, setDistributionJson] = useState("{}");

	const [saving, setSaving] = useState(false);
	const [actionError, setActionError] = useState<unknown>(null);
	const [actionMessage, setActionMessage] = useState("");

	const loadTemplates = async () => {
		try {
			setTemplates({ state: "loading" });
			const rows = await analyticsApi.listReportTemplates(200);
			setTemplates({ state: "loaded", value: Array.isArray(rows) ? rows : [] });
		} catch (e) {
			setTemplates({ state: "error", error: e });
		}
	};

	const loadRuns = async () => {
		try {
			setRuns({ state: "loading" });
			const rows = await analyticsApi.listReportRuns(200);
			setRuns({ state: "loaded", value: Array.isArray(rows) ? rows : [] });
		} catch (e) {
			setRuns({ state: "error", error: e });
		}
	};

	useEffect(() => {
		void Promise.all([loadTemplates(), loadRuns()]);
	}, []);

	const createTemplate = async () => {
		if (saving) return;
		const name = templateName.trim();
		if (!name) {
			setActionError(new Error("模板名称不能为空"));
			return;
		}
		const sections = templateSections
			.split(",")
			.map((item) => item.trim())
			.filter((item) => item.length > 0);
		setSaving(true);
		setActionError(null);
		setActionMessage("");
		try {
			await analyticsApi.createReportTemplate({
				name,
				description: templateDesc.trim() || null,
				published: false,
				spec: {
					layout: "default",
					sections: sections.length > 0 ? sections : ["问题背景", "关键洞察", "风险与建议"],
				},
			});
			setTemplateName("");
			setTemplateDesc("");
			await loadTemplates();
			setActionMessage("报告模板已创建");
		} catch (e) {
			setActionError(e);
		} finally {
			setSaving(false);
		}
	};

	const parseDistribution = (): Record<string, unknown> | null => {
		const raw = distributionJson.trim();
		if (!raw) return {};
		try {
			const value = JSON.parse(raw);
			if (value && typeof value === "object" && !Array.isArray(value)) {
				return value as Record<string, unknown>;
			}
			return null;
		} catch {
			return null;
		}
	};

	const generateReport = async () => {
		if (saving) return;
		const safeSourceId = Number.parseInt(sourceId, 10);
		if (!Number.isFinite(safeSourceId) || safeSourceId <= 0) {
			setActionError(new Error("sourceId 必须是正整数"));
			return;
		}
		const distribution = parseDistribution();
		if (distribution == null) {
			setActionError(new Error("分发配置必须是 JSON 对象"));
			return;
		}
		setSaving(true);
		setActionError(null);
		setActionMessage("");
		try {
			await analyticsApi.generateReportRun({
				templateId: generateTemplateId ? Number.parseInt(generateTemplateId, 10) : null,
				sourceType,
				sourceId: safeSourceId,
				outputFormat,
				distribution,
			});
			await loadRuns();
			setActionMessage("报告生成任务已提交");
		} catch (e) {
			setActionError(e);
		} finally {
			setSaving(false);
		}
	};

	const openRunDetail = async (runId: string | number | undefined) => {
		const id = toIdString(runId);
		if (!id) return;
		try {
			setSelectedRun({ state: "loading" });
			const row = await analyticsApi.getReportRun(id);
			setSelectedRun({ state: "loaded", value: row });
		} catch (e) {
			setSelectedRun({ state: "error", error: e });
		}
	};

	const templateOptions = templates.state === "loaded"
		? [{ value: "", label: "默认模板(内置)" }, ...templates.value.map((item) => ({
			value: toIdString(item.id),
			label: `${item.name || "未命名模板"} (${item.versionNo ?? 1})`,
		}))]
		: [{ value: "", label: t(locale, "loading") }];

	const sourceTypeOptions = [
		{ value: "session", label: "会话(session)" },
		{ value: "screen", label: "大屏(screen)" },
	];

	const formatOptions = [
		{ value: "html", label: "HTML" },
		{ value: "markdown", label: "Markdown" },
	];

	return (
		<div className="space-y-4">
			<PageHeader
				title={t(locale, "reportFactory.title")}
				actions={
					<Button type="default" onClick={() => void Promise.all([loadTemplates(), loadRuns()])}>
						{t(locale, "common.refresh")}
					</Button>
				}
			/>

			{Boolean(actionError) ? <ErrorNotice locale={locale} error={actionError} /> : null}
			{actionMessage && (
				<div style={{ marginBottom: "var(--spacing-md)" }}>
					<Tag color="success">{actionMessage}</Tag>
				</div>
			)}

			<div className="grid grid-cols-2 gap-md" style={{ marginBottom: "var(--spacing-lg)" }}>
				<Card title="模板管理">
						<div className="flex flex-col gap-md" style={{ gap: "var(--spacing-sm)" }}>
							<div>
								<label style={{ display: "block", marginBottom: 4, fontWeight: 500 }}>{t(locale, "common.name")}</label>
								<Input
									value={templateName}
									onChange={(event) => setTemplateName(event.target.value)}
									placeholder="周报模板"
								/>
							</div>
							<div>
								<label style={{ display: "block", marginBottom: 4, fontWeight: 500 }}>{t(locale, "common.description")}</label>
								<Input.TextArea
									value={templateDesc}
									onChange={(event) => setTemplateDesc(event.target.value)}
									rows={3}
								/>
							</div>
							<div>
								<label style={{ display: "block", marginBottom: 4, fontWeight: 500 }}>章节(逗号分隔)</label>
								<Input
									value={templateSections}
									onChange={(event) => setTemplateSections(event.target.value)}
									placeholder="问题背景,关键洞察,风险与建议"
								/>
							</div>
						</div>
					<div style={{ display: "flex", justifyContent: "flex-end", paddingTop: "var(--spacing-md)", marginTop: "var(--spacing-md)", borderTop: "1px solid var(--color-border)" }}>
						<Button type="primary" onClick={createTemplate} loading={saving}>
							创建模板
						</Button>
					</div>
				</Card>

				<Card title="报告生成">
						<div className="flex flex-col gap-md" style={{ gap: "var(--spacing-sm)" }}>
							<div>
								<label style={{ display: "block", marginBottom: 4, fontWeight: 500 }}>模板</label>
								<Select
									value={generateTemplateId}
									onChange={(value) => setGenerateTemplateId(value)}
									options={templateOptions}
									style={{ width: "100%" }}
								/>
							</div>
							<div className="grid grid-cols-2 gap-md">
								<div>
									<label style={{ display: "block", marginBottom: 4, fontWeight: 500 }}>来源类型</label>
									<Select
										value={sourceType}
										onChange={(value) => setSourceType(value)}
										options={sourceTypeOptions}
										style={{ width: "100%" }}
									/>
								</div>
								<div>
									<label style={{ display: "block", marginBottom: 4, fontWeight: 500 }}>来源ID</label>
									<Input
										value={sourceId}
										onChange={(event) => setSourceId(event.target.value)}
										placeholder="输入 sessionId 或 screenId"
									/>
								</div>
							</div>
							<div>
								<label style={{ display: "block", marginBottom: 4, fontWeight: 500 }}>输出格式</label>
								<Select
									value={outputFormat}
									onChange={(value) => setOutputFormat(value)}
									options={formatOptions}
									style={{ width: "100%" }}
								/>
							</div>
							<div>
								<label style={{ display: "block", marginBottom: 4, fontWeight: 500 }}>分发配置(JSON)</label>
								<Input.TextArea
									value={distributionJson}
									onChange={(event) => setDistributionJson(event.target.value)}
									rows={3}
								/>
							</div>
						</div>
					<div style={{ display: "flex", justifyContent: "flex-end", paddingTop: "var(--spacing-md)", marginTop: "var(--spacing-md)", borderTop: "1px solid var(--color-border)" }}>
						<Button type="primary" onClick={generateReport} loading={saving}>
							生成报告
						</Button>
					</div>
				</Card>
			</div>

			<Card style={{ marginBottom: "var(--spacing-lg)" }}
				title="模板列表"
				extra={templates.state === "loaded" ? <Tag>{templates.value.length}</Tag> : null}
			>
					{templates.state === "loading" && (
						<div className="loading-container" style={{ padding: "var(--spacing-lg)" }}>
							<Spin />
						</div>
					)}
					{templates.state === "error" && <ErrorNotice locale={locale} error={templates.error} />}
					{templates.state === "loaded" && templates.value.length === 0 && (
						<div className="text-secondary">{t(locale, "common.empty")}</div>
					)}
					{templates.state === "loaded" && templates.value.length > 0 && (
						<table>
							<thead>
								<tr>
									<th>{t(locale, "common.name")}</th>
									<th>版本</th>
									<th>发布</th>
									<th>更新时间</th>
								</tr>
							</thead>
							<tbody>
								{templates.value.map((row, idx) => (
									<tr key={`${toIdString(row.id)}-${idx}`}>
										<td>{row.name || "未命名模板"}</td>
										<td>{row.versionNo ?? 1}</td>
										<td>{row.published ? "yes" : "no"}</td>
										<td>{formatTime(row.updatedAt)}</td>
									</tr>
								))}
							</tbody>
						</table>
					)}
			</Card>

			<div className="grid grid-cols-2 gap-md">
				<Card
					title="生成任务"
					extra={runs.state === "loaded" ? <Tag>{runs.value.length}</Tag> : null}
				>
						{runs.state === "loading" && (
							<div className="loading-container" style={{ padding: "var(--spacing-lg)" }}>
								<Spin />
							</div>
						)}
						{runs.state === "error" && <ErrorNotice locale={locale} error={runs.error} />}
						{runs.state === "loaded" && runs.value.length === 0 && (
							<div className="text-secondary">{t(locale, "common.empty")}</div>
						)}
						{runs.state === "loaded" && runs.value.length > 0 && (
							<table>
								<thead>
									<tr>
										<th>ID</th>
										<th>来源</th>
										<th>状态</th>
										<th>{t(locale, "common.actions")}</th>
									</tr>
								</thead>
								<tbody>
									{runs.value.map((row, idx) => {
										const id = toIdString(row.id);
										return (
											<tr key={`${id}-${idx}`}>
												<td>{id || "-"}</td>
												<td>{`${row.sourceType || "-"}:${row.sourceId || "-"}`}</td>
												<td>{row.status || "-"}</td>
												<td>
													<div style={{ display: "flex", gap: "var(--spacing-xs)", flexWrap: "wrap" }}>
														<Button type="text" onClick={() => void openRunDetail(row.id)}>
															详情
														</Button>
														<a
															href={analyticsApi.getReportRunExportUrl(row.id || "", "html")}
															target="_blank"
															rel="noreferrer"
															className="text-brand no-underline"
														>
															HTML
														</a>
														<a
															href={analyticsApi.getReportRunExportUrl(row.id || "", "markdown")}
															target="_blank"
															rel="noreferrer"
															className="text-brand no-underline"
														>
															MD
														</a>
													</div>
												</td>
											</tr>
										);
									})}
								</tbody>
							</table>
						)}
				</Card>

				<Card title="任务详情">
						{selectedRun == null && <div className="text-secondary">点击左侧“详情”查看任务信息。</div>}
						{selectedRun?.state === "loading" && (
							<div className="loading-container" style={{ padding: "var(--spacing-lg)" }}>
								<Spin />
							</div>
						)}
						{selectedRun?.state === "error" && <ErrorNotice locale={locale} error={selectedRun.error} />}
						{selectedRun?.state === "loaded" && (
							<pre style={{
								margin: 0,
								padding: "var(--spacing-sm)",
								background: "var(--color-bg-tertiary)",
								borderRadius: "var(--radius-sm)",
								whiteSpace: "pre-wrap",
							}}>
								{JSON.stringify({
									id: selectedRun.value.id,
									status: selectedRun.value.status,
									sourceType: selectedRun.value.sourceType,
									sourceId: selectedRun.value.sourceId,
									templateId: selectedRun.value.templateId,
									outputFormat: selectedRun.value.outputFormat,
									createdAt: formatTime(selectedRun.value.createdAt),
									summary: selectedRun.value.summary ?? {},
									distribution: selectedRun.value.distribution ?? {},
								}, null, 2)}
							</pre>
						)}
				</Card>
			</div>
		</div>
	);
}
