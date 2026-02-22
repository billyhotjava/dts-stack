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
			setActionMessage("Gated 评测执行完成");
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
		<PageContainer>
			<PageHeader
				title={t(locale, "nl2sqlEval.title")}
				subtitle={t(locale, "nl2sqlEval.subtitle")}
				actions={
					<Button variant="secondary" size="sm" onClick={() => void Promise.all([loadCases(), loadRuns()])}>
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
					<CardHeader title="评测样例管理" />
					<CardBody>
						<div className="col" style={{ gap: "var(--spacing-sm)" }}>
							<Input label={t(locale, "common.name")} value={caseName} onChange={(event) => setCaseName(event.target.value)} placeholder="制造日报趋势评测" />
							<Input label="业务域" value={caseDomain} onChange={(event) => setCaseDomain(event.target.value)} placeholder="manufacturing" />
							<TextArea label="Prompt" value={casePrompt} onChange={(event) => setCasePrompt(event.target.value)} rows={3} />
							<TextArea label={t(locale, "common.description")} value={caseNotes} onChange={(event) => setCaseNotes(event.target.value)} rows={2} />
							<TextArea label="Expected(JSON)" value={caseExpected} onChange={(event) => setCaseExpected(event.target.value)} rows={3} />
						</div>
					</CardBody>
					<CardFooter align="right">
						<Button variant="primary" onClick={createCase} loading={saving}>
							创建样例
						</Button>
					</CardFooter>
				</Card>

				<Card>
					<CardHeader title="执行参数" />
					<CardBody>
						<div className="col" style={{ gap: "var(--spacing-sm)" }}>
							<label style={{ display: "inline-flex", gap: "var(--spacing-xs)", alignItems: "center", fontSize: "var(--font-size-sm)" }}>
								<input type="checkbox" checked={enabledOnly} onChange={(event) => setEnabledOnly(event.target.checked)} />
								仅执行 enabled 样例
							</label>
							<Input label="样例上限" value={limit} onChange={(event) => setLimit(event.target.value)} />
							<Input label="指定 CaseIds(逗号分隔，可选)" value={caseIdsCsv} onChange={(event) => setCaseIdsCsv(event.target.value)} placeholder="1,2,5" />
						</div>
					</CardBody>
					<CardFooter align="right">
						<div style={{ display: "flex", gap: "var(--spacing-sm)" }}>
							<Button variant="secondary" onClick={runEval} loading={saving}>执行评测</Button>
							<Button variant="primary" onClick={runEvalGated} loading={saving}>执行 Gated</Button>
						</div>
					</CardFooter>
				</Card>
			</div>

			<Card style={{ marginBottom: "var(--spacing-lg)" }}>
				<CardHeader
					title="样例列表"
					action={casesState.state === "loaded" ? <Badge>{casesState.value.length}</Badge> : null}
				/>
				<CardBody>
					{casesState.state === "loading" && (
						<div className="loading-container" style={{ padding: "var(--spacing-lg)" }}>
							<Spinner size="md" />
						</div>
					)}
					{casesState.state === "error" && <ErrorNotice locale={locale} error={casesState.error} />}
					{casesState.state === "loaded" && casesState.value.length === 0 && (
						<div className="muted">{t(locale, "common.empty")}</div>
					)}
					{casesState.state === "loaded" && casesState.value.length > 0 && (
						<table className="table">
							<thead>
								<tr>
									<th>ID</th>
									<th>{t(locale, "common.name")}</th>
									<th>Domain</th>
									<th>Enabled</th>
								</tr>
							</thead>
							<tbody>
								{casesState.value.map((item, idx) => (
									<tr key={`${toIdString(item.id)}-${idx}`}>
										<td>{toIdString(item.id)}</td>
										<td>{item.name || "-"}</td>
										<td>{item.domain || "-"}</td>
										<td>{item.enabled ? "yes" : "no"}</td>
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
						title="Run 历史与对比"
						action={runsState.state === "loaded" ? <Badge>{runsState.value.length}</Badge> : null}
					/>
					<CardBody>
						{runsState.state === "loading" && (
							<div className="loading-container" style={{ padding: "var(--spacing-lg)" }}>
								<Spinner size="md" />
							</div>
						)}
						{runsState.state === "error" && <ErrorNotice locale={locale} error={runsState.error} />}
						{runsState.state === "loaded" && (
							<div className="col" style={{ gap: "var(--spacing-sm)" }}>
								<div className="grid2">
									<NativeSelect label="Baseline Run" value={baselineRunId} onChange={(event) => setBaselineRunId(event.target.value)} options={runOptions} />
									<NativeSelect label="Candidate Run" value={candidateRunId} onChange={(event) => setCandidateRunId(event.target.value)} options={runOptions} />
								</div>
								<Button variant="primary" onClick={() => void compareRuns()} disabled={!baselineRunId || !candidateRunId}>
									执行 Run 对比
								</Button>
								<table className="table">
									<thead>
										<tr>
											<th>ID</th>
											<th>PassRate</th>
											<th>AvgScore</th>
											<th>Gate</th>
										</tr>
									</thead>
									<tbody>
										{runsState.value.map((run, idx) => (
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
					</CardBody>
				</Card>

				<Card>
					<CardHeader title="执行与对比结果" />
					<CardBody>
						{compareState?.state === "loading" && (
							<div className="loading-container" style={{ padding: "var(--spacing-lg)" }}>
								<Spinner size="md" />
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
					</CardBody>
				</Card>
			</div>
		</PageContainer>
	);
}
