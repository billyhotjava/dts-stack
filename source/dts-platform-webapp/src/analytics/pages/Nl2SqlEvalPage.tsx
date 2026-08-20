import { useEffect, useMemo, useState } from "react";
import {
	analyticsApi,
	type Nl2SqlEvalCaseItem,
	type Nl2SqlEvalCompareResponse,
	type Nl2SqlEvalGateRunResponse,
	type Nl2SqlEvalRunRecord,
	type Nl2SqlEvalRunSummary,
} from "../api/analyticsApi";
import { ErrorNotice } from "../components/ErrorNotice";
import { SortableHeader } from "../components/SortableHeader";
import { numberComparator, stringComparator, useTableSort } from "../hooks/useTableSort";
import { PageHeader } from "@/components/page-header";
import { getEffectiveLocale, t, type Locale } from "../i18n";
import { Input, Spin, Button, Card, Tag, Select } from "antd";
type LoadState<T> =
	| { state: "loading" }
	| { state: "loaded"; value: T }
	| { state: "error"; error: unknown };

function toIdString(value?: string | number): string {
	return value == null ? "" : String(value);
}

function parseCaseIds(raw: string): Array<number> {
	return raw
		.split(",")
		.map((item) => Number.parseInt(item.trim(), 10))
		.filter((item) => Number.isFinite(item) && item > 0);
}

function parseExpectedJson(raw: string): Record<string, unknown> | null {
	const text = raw.trim();
	if (!text) return {};
	try {
		const value = JSON.parse(text);
		if (value && typeof value === "object" && !Array.isArray(value)) {
			return value as Record<string, unknown>;
		}
		return null;
	} catch {
		return null;
	}
}

export default function Nl2SqlEvalPage() {
	const locale: Locale = useMemo(() => getEffectiveLocale(), []);
	const [casesState, setCasesState] = useState<LoadState<Nl2SqlEvalCaseItem[]>>({ state: "loading" });
	const [runsState, setRunsState] = useState<LoadState<Nl2SqlEvalRunRecord[]>>({ state: "loading" });

	const [runSummary, setRunSummary] = useState<Nl2SqlEvalRunSummary | null>(null);
	const [gateSummary, setGateSummary] = useState<Nl2SqlEvalGateRunResponse | null>(null);
	const [compareState, setCompareState] = useState<LoadState<Nl2SqlEvalCompareResponse> | null>(null);

	const [caseName, setCaseName] = useState("");
	const [caseDomain, setCaseDomain] = useState("");
	const [casePrompt, setCasePrompt] = useState("");
	const [caseNotes, setCaseNotes] = useState("");
	const [caseExpected, setCaseExpected] = useState("{}");

	const [enabledOnly, setEnabledOnly] = useState(true);
	const [limit, setLimit] = useState("100");
	const [caseIdsCsv, setCaseIdsCsv] = useState("");
	const [baselineRunId, setBaselineRunId] = useState("");
	const [candidateRunId, setCandidateRunId] = useState("");

	const [saving, setSaving] = useState(false);
	const [actionError, setActionError] = useState<unknown>(null);
	const [actionMessage, setActionMessage] = useState("");

	const caseRows = casesState.state === "loaded" ? casesState.value : [];
	const caseSortColumns = useMemo(
		() => ({
			id: stringComparator<Nl2SqlEvalCaseItem>((c) => toIdString(c.id)),
			name: stringComparator<Nl2SqlEvalCaseItem>((c) => c.name),
			domain: stringComparator<Nl2SqlEvalCaseItem>((c) => c.domain),
			enabled: numberComparator<Nl2SqlEvalCaseItem>((c) => (c.enabled ? 1 : 0)),
		}),
		[],
	);
	const {
		sortedItems: sortedCases,
		sortState: caseSortState,
		requestSort: requestCaseSort,
	} = useTableSort(caseRows, {
		columns: caseSortColumns,
		defaultSort: { key: "id", direction: "asc" },
	});

	const runRows = runsState.state === "loaded" ? runsState.value : [];
	const runSortColumns = useMemo(
		() => ({
			id: stringComparator<Nl2SqlEvalRunRecord>((r) => toIdString(r.id)),
			passRate: numberComparator<Nl2SqlEvalRunRecord>((r) => r.passRate),
			averageScore: numberComparator<Nl2SqlEvalRunRecord>((r) => r.averageScore),
			gatePassed: numberComparator<Nl2SqlEvalRunRecord>(
				(r) => (r.gatePassed == null ? null : r.gatePassed ? 1 : 0),
			),
		}),
		[],
	);
	const {
		sortedItems: sortedRuns,
		sortState: runSortState,
		requestSort: requestRunSort,
	} = useTableSort(runRows, {
		columns: runSortColumns,
		defaultSort: { key: "id", direction: "desc" },
	});

	const loadCases = async () => {
		try {
			setCasesState({ state: "loading" });
			const rows = await analyticsApi.listNl2SqlEvalCases(false, 500);
			setCasesState({ state: "loaded", value: Array.isArray(rows) ? rows : [] });
		} catch (e) {
			setCasesState({ state: "error", error: e });
		}
	};

	const loadRuns = async () => {
		try {
			setRunsState({ state: "loading" });
			const rows = await analyticsApi.listNl2SqlEvalRuns(100);
			const safeRows = Array.isArray(rows) ? rows : [];
			setRunsState({ state: "loaded", value: safeRows });
			if (!baselineRunId && safeRows.length > 0) {
				setBaselineRunId(toIdString(safeRows[0]?.id));
			}
			if (!candidateRunId && safeRows.length > 1) {
				setCandidateRunId(toIdString(safeRows[1]?.id));
			}
		} catch (e) {
			setRunsState({ state: "error", error: e });
		}
	};

	useEffect(() => {
		void Promise.all([loadCases(), loadRuns()]);
	}, []);

	const buildRunPayload = () => {
		const safeLimit = Number.parseInt(limit, 10);
		return {
			enabledOnly,
			limit: Number.isFinite(safeLimit) && safeLimit > 0 ? safeLimit : 100,
			caseIds: parseCaseIds(caseIdsCsv),
		};
	};

	const createCase = async () => {
		if (saving) return;
		const name = caseName.trim();
		const promptText = casePrompt.trim();
		if (!name || !promptText) {
			setActionError(new Error("name 和 promptText 不能为空"));
			return;
		}
		const expected = parseExpectedJson(caseExpected);
		if (expected == null) {
			setActionError(new Error("expected 必须是 JSON 对象"));
			return;
		}
		setSaving(true);
		setActionError(null);
		setActionMessage("");
		try {
			await analyticsApi.createNl2SqlEvalCase({
				name,
				domain: caseDomain.trim() || null,
				promptText,
				notes: caseNotes.trim() || null,
				expected,
				enabled: true,
			});
			setCaseName("");
			setCaseDomain("");
			setCasePrompt("");
			setCaseNotes("");
			setCaseExpected("{}");
			await loadCases();
			setActionMessage("评测样例已创建");
		} catch (e) {
			setActionError(e);
		} finally {
			setSaving(false);
		}
	};

	const runEval = async () => {
		if (saving) return;
		setSaving(true);
		setActionError(null);
		setActionMessage("");
		try {
			const result = await analyticsApi.runNl2SqlEvaluation(buildRunPayload());
			setRunSummary(result);
			setGateSummary(null);
			await loadRuns();
			setActionMessage("评测执行完成");
		} catch (e) {
			setActionError(e);
		} finally {
			setSaving(false);
		}
	};

	const runEvalGated = async () => {
		if (saving) return;
		setSaving(true);
		setActionError(null);
		setActionMessage("");
		try {
			const result = await analyticsApi.runNl2SqlEvaluationWithGate({
				...buildRunPayload(),
				version: {
					label: "ui-gated",
				},
			});
			setGateSummary(result);
			setRunSummary(result.summary ?? null);
			await loadRuns();
			setActionMessage("门禁评测执行完成");
		} catch (e) {
			setActionError(e);
		} finally {
			setSaving(false);
		}
	};

	const compareRuns = async () => {
		if (!baselineRunId || !candidateRunId) return;
		try {
			setCompareState({ state: "loading" });
			const value = await analyticsApi.compareNl2SqlEvalRuns(baselineRunId, candidateRunId);
			setCompareState({ state: "loaded", value });
		} catch (e) {
			setCompareState({ state: "error", error: e });
		}
	};

	const runOptions = runsState.state === "loaded"
		? runsState.value.map((run) => ({
			value: toIdString(run.id),
			label: `#${toIdString(run.id)} ${run.label || ""}`.trim(),
		}))
		: [{ value: "", label: t(locale, "loading") }];

	return (
		<div className="space-y-4">
			<PageHeader
				title="增强分析工具 / NL2SQL 评测辅助工具"
				actions={
					<Button type="default" onClick={() => void Promise.all([loadCases(), loadRuns()])}>
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
				<Card title="评测样例管理">
						<div className="flex flex-col gap-md" style={{ gap: "var(--spacing-sm)" }}>
							<div>
								<label style={{ display: "block", marginBottom: 4, fontWeight: 500 }}>{t(locale, "common.name")}</label>
								<Input value={caseName} onChange={(event) => setCaseName(event.target.value)} placeholder="制造日报趋势评测" />
							</div>
							<div>
								<label style={{ display: "block", marginBottom: 4, fontWeight: 500 }}>业务域</label>
								<Input value={caseDomain} onChange={(event) => setCaseDomain(event.target.value)} placeholder="manufacturing" />
							</div>
							<div>
								<label style={{ display: "block", marginBottom: 4, fontWeight: 500 }}>提问内容</label>
								<Input.TextArea value={casePrompt} onChange={(event) => setCasePrompt(event.target.value)} rows={3} />
							</div>
							<div>
								<label style={{ display: "block", marginBottom: 4, fontWeight: 500 }}>{t(locale, "common.description")}</label>
								<Input.TextArea value={caseNotes} onChange={(event) => setCaseNotes(event.target.value)} rows={2} />
							</div>
							<div>
								<label style={{ display: "block", marginBottom: 4, fontWeight: 500 }}>预期结果（JSON）</label>
								<Input.TextArea value={caseExpected} onChange={(event) => setCaseExpected(event.target.value)} rows={3} />
							</div>
						</div>
					<div style={{ display: "flex", justifyContent: "flex-end", paddingTop: "var(--spacing-md)", marginTop: "var(--spacing-md)", borderTop: "1px solid var(--color-border)" }}>
						<Button type="primary" onClick={createCase} loading={saving}>
							创建样例
						</Button>
					</div>
				</Card>

				<Card title="执行参数">
						<div className="flex flex-col gap-md" style={{ gap: "var(--spacing-sm)" }}>
							<label style={{ display: "inline-flex", gap: "var(--spacing-xs)", alignItems: "center", fontSize: "var(--font-size-sm)" }}>
								<input type="checkbox" checked={enabledOnly} onChange={(event) => setEnabledOnly(event.target.checked)} />
								仅执行已启用样例
							</label>
							<div>
								<label style={{ display: "block", marginBottom: 4, fontWeight: 500 }}>样例上限</label>
								<Input value={limit} onChange={(event) => setLimit(event.target.value)} />
							</div>
							<div>
								<label style={{ display: "block", marginBottom: 4, fontWeight: 500 }}>指定样例编号（逗号分隔，可选）</label>
								<Input value={caseIdsCsv} onChange={(event) => setCaseIdsCsv(event.target.value)} placeholder="1,2,5" />
							</div>
						</div>
					<div style={{ display: "flex", justifyContent: "flex-end", paddingTop: "var(--spacing-md)", marginTop: "var(--spacing-md)", borderTop: "1px solid var(--color-border)" }}>
						<div style={{ display: "flex", gap: "var(--spacing-sm)" }}>
							<Button type="default" onClick={runEval} loading={saving}>执行评测</Button>
							<Button type="primary" onClick={runEvalGated} loading={saving}>执行门禁评测</Button>
						</div>
					</div>
				</Card>
			</div>

			<Card style={{ marginBottom: "var(--spacing-lg)" }}
				title="样例列表"
				extra={casesState.state === "loaded" ? <Tag>{casesState.value.length}</Tag> : null}
			>
					{casesState.state === "loading" && (
						<div className="loading-container" style={{ padding: "var(--spacing-lg)" }}>
							<Spin />
						</div>
					)}
					{casesState.state === "error" && <ErrorNotice locale={locale} error={casesState.error} />}
					{casesState.state === "loaded" && casesState.value.length === 0 && (
						<div className="text-secondary">{t(locale, "common.empty")}</div>
					)}
					{casesState.state === "loaded" && casesState.value.length > 0 && (
						<table>
							<thead>
								<tr>
									<SortableHeader sortKey="id" sortState={caseSortState} onSort={requestCaseSort}>
										ID
									</SortableHeader>
									<SortableHeader sortKey="name" sortState={caseSortState} onSort={requestCaseSort}>
										{t(locale, "common.name")}
									</SortableHeader>
									<SortableHeader sortKey="domain" sortState={caseSortState} onSort={requestCaseSort}>
										业务域
									</SortableHeader>
									<SortableHeader sortKey="enabled" sortState={caseSortState} onSort={requestCaseSort}>
										是否启用
									</SortableHeader>
								</tr>
							</thead>
							<tbody>
								{sortedCases.map((item, idx) => (
									<tr key={`${toIdString(item.id)}-${idx}`}>
										<td>{toIdString(item.id)}</td>
										<td>{item.name || "-"}</td>
										<td>{item.domain || "-"}</td>
										<td>{item.enabled ? "是" : "否"}</td>
									</tr>
								))}
							</tbody>
						</table>
					)}
			</Card>

			<div className="grid grid-cols-2 gap-md">
				<Card
				title="运行历史与对比"
					extra={runsState.state === "loaded" ? <Tag>{runsState.value.length}</Tag> : null}
				>
						{runsState.state === "loading" && (
							<div className="loading-container" style={{ padding: "var(--spacing-lg)" }}>
								<Spin />
							</div>
						)}
						{runsState.state === "error" && <ErrorNotice locale={locale} error={runsState.error} />}
						{runsState.state === "loaded" && (
							<div className="flex flex-col gap-md" style={{ gap: "var(--spacing-sm)" }}>
								<div className="grid grid-cols-2 gap-md">
									<div>
										<label style={{ display: "block", marginBottom: 4, fontWeight: 500 }}>基准运行</label>
										<Select value={baselineRunId} onChange={(value) => setBaselineRunId(value)} options={runOptions} style={{ width: "100%" }} />
									</div>
									<div>
										<label style={{ display: "block", marginBottom: 4, fontWeight: 500 }}>候选运行</label>
										<Select value={candidateRunId} onChange={(value) => setCandidateRunId(value)} options={runOptions} style={{ width: "100%" }} />
									</div>
								</div>
								<Button type="primary" onClick={() => void compareRuns()} disabled={!baselineRunId || !candidateRunId}>
									执行运行对比
								</Button>
								<table>
									<thead>
										<tr>
											<SortableHeader sortKey="id" sortState={runSortState} onSort={requestRunSort}>
												ID
											</SortableHeader>
											<SortableHeader sortKey="passRate" sortState={runSortState} onSort={requestRunSort}>
												PassRate
											</SortableHeader>
											<SortableHeader sortKey="averageScore" sortState={runSortState} onSort={requestRunSort}>
												AvgScore
											</SortableHeader>
											<SortableHeader sortKey="gatePassed" sortState={runSortState} onSort={requestRunSort}>
												Gate
											</SortableHeader>
										</tr>
									</thead>
									<tbody>
										{sortedRuns.map((run, idx) => (
											<tr key={`${toIdString(run.id)}-${idx}`}>
												<td>{toIdString(run.id)}</td>
												<td>{run.passRate == null ? "-" : run.passRate.toFixed(3)}</td>
												<td>{run.averageScore == null ? "-" : run.averageScore.toFixed(2)}</td>
												<td>{run.gatePassed == null ? "-" : (run.gatePassed ? "pass" : "fail")}</td>
											</tr>
										))}
									</tbody>
								</table>
							</div>
						)}
				</Card>

				<Card title="执行与对比结果">
						{compareState?.state === "loading" && (
							<div className="loading-container" style={{ padding: "var(--spacing-lg)" }}>
								<Spin />
							</div>
						)}
						{compareState?.state === "error" && <ErrorNotice locale={locale} error={compareState.error} />}
						<pre style={{
							margin: 0,
							padding: "var(--spacing-sm)",
							background: "var(--color-bg-tertiary)",
							borderRadius: "var(--radius-sm)",
							whiteSpace: "pre-wrap",
						}}>
							{JSON.stringify({
								lastRunSummary: runSummary,
								lastGateSummary: gateSummary,
								compare: compareState?.state === "loaded" ? compareState.value : null,
							}, null, 2)}
						</pre>
				</Card>
			</div>
		</div>
	);
}
