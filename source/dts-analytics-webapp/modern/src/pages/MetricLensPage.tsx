import { useEffect, useMemo, useState } from "react";
import { analyticsApi, type MetricLensCompare, type MetricLensDetail, type MetricLensSummary } from "../api/analyticsApi";
import { ErrorNotice } from "../components/ErrorNotice";
import { PageContainer, PageHeader } from "../components/PageContainer/PageContainer";
import { getEffectiveLocale, t, type Locale } from "../i18n";
import { Badge } from "../ui/Badge/Badge";
import { Button } from "../ui/Button/Button";
import { Card, CardBody, CardHeader } from "../ui/Card/Card";
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

export default function MetricLensPage() {
	const locale: Locale = useMemo(() => getEffectiveLocale(), []);
	const [listState, setListState] = useState<LoadState<MetricLensSummary[]>>({ state: "loading" });
	const [conflictState, setConflictState] = useState<LoadState<Array<Record<string, unknown>>>>({ state: "loading" });
	const [detailState, setDetailState] = useState<LoadState<MetricLensDetail> | null>(null);
	const [compareState, setCompareState] = useState<LoadState<MetricLensCompare> | null>(null);

	const [selectedMetricId, setSelectedMetricId] = useState("");
	const [leftVersion, setLeftVersion] = useState("");
	const [rightVersion, setRightVersion] = useState("");

	const loadList = async () => {
		try {
			setListState({ state: "loading" });
			const rows = await analyticsApi.listMetricLens();
			const safeRows = Array.isArray(rows) ? rows : [];
			setListState({ state: "loaded", value: safeRows });
			if (!selectedMetricId && safeRows.length > 0) {
				setSelectedMetricId(toIdString(safeRows[0]?.metricId));
			}
		} catch (e) {
			setListState({ state: "error", error: e });
		}
	};

	const loadConflicts = async () => {
		try {
			setConflictState({ state: "loading" });
			const rows = await analyticsApi.getMetricLensConflicts();
			setConflictState({ state: "loaded", value: Array.isArray(rows) ? rows : [] });
		} catch (e) {
			setConflictState({ state: "error", error: e });
		}
	};

	const loadDetail = async (metricId: string) => {
		if (!metricId) {
			setDetailState(null);
			return;
		}
		try {
			setDetailState({ state: "loading" });
			const detail = await analyticsApi.getMetricLens(metricId);
			setDetailState({ state: "loaded", value: detail });
			const versions = Array.isArray(detail.versions) ? detail.versions : [];
			setLeftVersion(versions[0] ? String(versions[0]) : "");
			setRightVersion(versions[1] ? String(versions[1]) : versions[0] ? String(versions[0]) : "");
			setCompareState(null);
		} catch (e) {
			setDetailState({ state: "error", error: e });
		}
	};

	useEffect(() => {
		void Promise.all([loadList(), loadConflicts()]);
	}, []);

	useEffect(() => {
		if (!selectedMetricId) return;
		void loadDetail(selectedMetricId);
	}, [selectedMetricId]);

	const runCompare = async () => {
		if (!selectedMetricId || !leftVersion || !rightVersion) return;
		try {
			setCompareState({ state: "loading" });
			const result = await analyticsApi.compareMetricLensVersions(selectedMetricId, leftVersion, rightVersion);
			setCompareState({ state: "loaded", value: result });
		} catch (e) {
			setCompareState({ state: "error", error: e });
		}
	};

	const metricOptions = listState.state === "loaded"
		? listState.value.map((item) => ({
			value: toIdString(item.metricId),
			label: `${item.name || "未命名指标"} (#${toIdString(item.metricId)})`,
		}))
		: [{ value: "", label: t(locale, "loading") }];

	const detail = detailState?.state === "loaded" ? detailState.value : null;
	const versions = detail && Array.isArray(detail.versions) ? detail.versions.map((item) => String(item)) : [];
	const versionOptions = versions.map((item) => ({ value: item, label: item }));

	return (
		<PageContainer>
			<PageHeader
				title={t(locale, "metricLens.title")}
				actions={
					<Button variant="secondary" size="sm" onClick={() => void Promise.all([loadList(), loadConflicts()])}>
						{t(locale, "common.refresh")}
					</Button>
				}
			/>

			<div className="grid2" style={{ marginBottom: "var(--spacing-lg)" }}>
				<Card>
					<CardHeader
						title="指标清单"
						action={listState.state === "loaded" ? <Badge>{listState.value.length}</Badge> : null}
					/>
					<CardBody>
						{listState.state === "loading" && (
							<div className="loading-container" style={{ padding: "var(--spacing-lg)" }}>
								<Spinner size="md" />
							</div>
						)}
						{listState.state === "error" && <ErrorNotice locale={locale} error={listState.error} />}
						{listState.state === "loaded" && listState.value.length === 0 && (
							<div className="muted">{t(locale, "common.empty")}</div>
						)}
						{listState.state === "loaded" && listState.value.length > 0 && (
							<>
								<NativeSelect
									label="选择指标"
									value={selectedMetricId}
									onChange={(event) => setSelectedMetricId(event.target.value)}
									options={metricOptions}
								/>
								<table className="table" style={{ marginTop: "var(--spacing-md)" }}>
									<thead>
										<tr>
											<th>{t(locale, "common.name")}</th>
											<th>聚合</th>
											<th>时间口径</th>
										</tr>
									</thead>
									<tbody>
										{listState.value.map((row, idx) => {
											const rowId = toIdString(row.metricId);
											return (
												<tr
													key={`${rowId}-${idx}`}
													onClick={() => setSelectedMetricId(rowId)}
													style={{ cursor: "pointer", background: selectedMetricId === rowId ? "var(--color-bg-hover)" : undefined }}
												>
													<td>{row.name || "-"}</td>
													<td>{row.aggregation || "-"}</td>
													<td>{row.timeGrain || "-"}</td>
												</tr>
											);
										})}
									</tbody>
								</table>
							</>
						)}
					</CardBody>
				</Card>

				<Card>
					<CardHeader title="冲突检测" />
					<CardBody>
						{conflictState.state === "loading" && (
							<div className="loading-container" style={{ padding: "var(--spacing-lg)" }}>
								<Spinner size="md" />
							</div>
						)}
						{conflictState.state === "error" && <ErrorNotice locale={locale} error={conflictState.error} />}
						{conflictState.state === "loaded" && conflictState.value.length === 0 && (
							<div className="muted">当前未发现口径冲突。</div>
						)}
						{conflictState.state === "loaded" && conflictState.value.length > 0 && (
							<table className="table">
								<thead>
									<tr>
										<th>指标名</th>
										<th>等级</th>
										<th>冲突类型</th>
									</tr>
								</thead>
								<tbody>
									{conflictState.value.map((row, idx) => (
										<tr key={`conflict-${idx}`}>
											<td>{String(row.metricName || "-")}</td>
											<td>{String(row.conflictLevel || "-")}</td>
											<td>{Array.isArray(row.type) ? row.type.map(String).join(", ") : "-"}</td>
										</tr>
									))}
								</tbody>
							</table>
						)}
					</CardBody>
				</Card>
			</div>

			<div className="grid2">
				<Card>
					<CardHeader title="指标透视详情" />
					<CardBody>
						{detailState == null && <div className="muted">选择指标查看详情。</div>}
						{detailState?.state === "loading" && (
							<div className="loading-container" style={{ padding: "var(--spacing-lg)" }}>
								<Spinner size="md" />
							</div>
						)}
						{detailState?.state === "error" && <ErrorNotice locale={locale} error={detailState.error} />}
						{detail && (
							<pre style={{
								margin: 0,
								padding: "var(--spacing-sm)",
								background: "var(--color-bg-tertiary)",
								borderRadius: "var(--radius-sm)",
								whiteSpace: "pre-wrap",
							}}>
								{JSON.stringify({
									metricId: detail.metricId,
									name: detail.name,
									aggregation: detail.aggregation,
									timeGrain: detail.timeGrain,
									version: detail.version,
									versions: detail.versions,
									aclScope: detail.aclScope,
									lineage: detail.lineage,
									conflicts: detail.conflicts,
								}, null, 2)}
							</pre>
						)}
					</CardBody>
				</Card>

				<Card>
					<CardHeader title="版本对比" />
					<CardBody>
						<div className="col" style={{ gap: "var(--spacing-sm)", marginBottom: "var(--spacing-sm)" }}>
							<NativeSelect
								label="左版本"
								value={leftVersion}
								onChange={(event) => setLeftVersion(event.target.value)}
								options={versionOptions}
								disabled={versions.length === 0}
							/>
							<NativeSelect
								label="右版本"
								value={rightVersion}
								onChange={(event) => setRightVersion(event.target.value)}
								options={versionOptions}
								disabled={versions.length === 0}
							/>
							<Button variant="primary" onClick={() => void runCompare()} disabled={!leftVersion || !rightVersion || !selectedMetricId}>
								执行对比
							</Button>
						</div>
						{compareState?.state === "loading" && (
							<div className="loading-container" style={{ padding: "var(--spacing-lg)" }}>
								<Spinner size="md" />
							</div>
						)}
						{compareState?.state === "error" && <ErrorNotice locale={locale} error={compareState.error} />}
						{compareState?.state === "loaded" && (
							<pre style={{
								margin: 0,
								padding: "var(--spacing-sm)",
								background: "var(--color-bg-tertiary)",
								borderRadius: "var(--radius-sm)",
								whiteSpace: "pre-wrap",
							}}>
								{JSON.stringify(compareState.value, null, 2)}
							</pre>
						)}
					</CardBody>
				</Card>
			</div>
		</PageContainer>
	);
}
