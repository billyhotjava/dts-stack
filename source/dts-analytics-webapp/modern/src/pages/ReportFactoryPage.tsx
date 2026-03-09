import { useEffect, useMemo, useState } from "react";
import { analyticsApi, type ReportRunItem, type ReportTemplateItem } from "../api/analyticsApi";
import { ErrorNotice } from "../components/ErrorNotice";
import { PageContainer, PageHeader } from "../components/PageContainer/PageContainer";
import { getEffectiveLocale, t, type Locale } from "../i18n";
import { Badge } from "../ui/Badge/Badge";
import { Button } from "../ui/Button/Button";
import { Card, CardBody, CardFooter, CardHeader } from "../ui/Card/Card";
import { Input, TextArea } from "../ui/Input/Input";
import { NativeSelect } from "../ui/Input/Select";
import { Spinner } from "../ui/Loading/Spinner";
import "./page.css";

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
		<PageContainer>
			<PageHeader
				title={t(locale, "reportFactory.title")}
				actions={
					<Button variant="secondary" size="sm" onClick={() => void Promise.all([loadTemplates(), loadRuns()])}>
						{t(locale, "common.refresh")}
					</Button>
				}
			/>

			{Boolean(actionError) ? <ErrorNotice locale={locale} error={actionError} /> : null}
			{actionMessage && (
				<div style={{ marginBottom: "var(--spacing-md)" }}>
					<Badge variant="success">{actionMessage}</Badge>
				</div>
			)}

			<div className="grid2" style={{ marginBottom: "var(--spacing-lg)" }}>
				<Card>
					<CardHeader title="模板管理" />
					<CardBody>
						<div className="col" style={{ gap: "var(--spacing-sm)" }}>
							<Input
								label={t(locale, "common.name")}
								value={templateName}
								onChange={(event) => setTemplateName(event.target.value)}
								placeholder="周报模板"
							/>
							<TextArea
								label={t(locale, "common.description")}
								value={templateDesc}
								onChange={(event) => setTemplateDesc(event.target.value)}
								rows={3}
							/>
							<Input
								label="章节(逗号分隔)"
								value={templateSections}
								onChange={(event) => setTemplateSections(event.target.value)}
								placeholder="问题背景,关键洞察,风险与建议"
							/>
						</div>
					</CardBody>
					<CardFooter align="right">
						<Button variant="primary" onClick={createTemplate} loading={saving}>
							创建模板
						</Button>
					</CardFooter>
				</Card>

				<Card>
					<CardHeader title="报告生成" />
					<CardBody>
						<div className="col" style={{ gap: "var(--spacing-sm)" }}>
							<NativeSelect
								label="模板"
								value={generateTemplateId}
								onChange={(event) => setGenerateTemplateId(event.target.value)}
								options={templateOptions}
							/>
							<div className="grid2">
								<NativeSelect
									label="来源类型"
									value={sourceType}
									onChange={(event) => setSourceType(event.target.value)}
									options={sourceTypeOptions}
								/>
								<Input
									label="来源ID"
									value={sourceId}
									onChange={(event) => setSourceId(event.target.value)}
									placeholder="输入 sessionId 或 screenId"
								/>
							</div>
							<NativeSelect
								label="输出格式"
								value={outputFormat}
								onChange={(event) => setOutputFormat(event.target.value)}
								options={formatOptions}
							/>
							<TextArea
								label="分发配置(JSON)"
								value={distributionJson}
								onChange={(event) => setDistributionJson(event.target.value)}
								rows={3}
							/>
						</div>
					</CardBody>
					<CardFooter align="right">
						<Button variant="primary" onClick={generateReport} loading={saving}>
							生成报告
						</Button>
					</CardFooter>
				</Card>
			</div>

			<Card style={{ marginBottom: "var(--spacing-lg)" }}>
				<CardHeader
					title="模板列表"
					action={templates.state === "loaded" ? <Badge>{templates.value.length}</Badge> : null}
				/>
				<CardBody>
					{templates.state === "loading" && (
						<div className="loading-container" style={{ padding: "var(--spacing-lg)" }}>
							<Spinner size="md" />
						</div>
					)}
					{templates.state === "error" && <ErrorNotice locale={locale} error={templates.error} />}
					{templates.state === "loaded" && templates.value.length === 0 && (
						<div className="muted">{t(locale, "common.empty")}</div>
					)}
					{templates.state === "loaded" && templates.value.length > 0 && (
						<table className="table">
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
				</CardBody>
			</Card>

			<div className="grid2">
				<Card>
					<CardHeader
						title="生成任务"
						action={runs.state === "loaded" ? <Badge>{runs.value.length}</Badge> : null}
					/>
					<CardBody>
						{runs.state === "loading" && (
							<div className="loading-container" style={{ padding: "var(--spacing-lg)" }}>
								<Spinner size="md" />
							</div>
						)}
						{runs.state === "error" && <ErrorNotice locale={locale} error={runs.error} />}
						{runs.state === "loaded" && runs.value.length === 0 && (
							<div className="muted">{t(locale, "common.empty")}</div>
						)}
						{runs.state === "loaded" && runs.value.length > 0 && (
							<table className="table">
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
														<Button size="sm" variant="tertiary" onClick={() => void openRunDetail(row.id)}>
															详情
														</Button>
														<a
															href={analyticsApi.getReportRunExportUrl(row.id || "", "html")}
															target="_blank"
															rel="noreferrer"
															className="link"
														>
															HTML
														</a>
														<a
															href={analyticsApi.getReportRunExportUrl(row.id || "", "markdown")}
															target="_blank"
															rel="noreferrer"
															className="link"
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
					</CardBody>
				</Card>

				<Card>
					<CardHeader title="任务详情" />
					<CardBody>
						{selectedRun == null && <div className="muted">点击左侧“详情”查看任务信息。</div>}
						{selectedRun?.state === "loading" && (
							<div className="loading-container" style={{ padding: "var(--spacing-lg)" }}>
								<Spinner size="md" />
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
					</CardBody>
				</Card>
			</div>
		</PageContainer>
	);
}
