import { useEffect, useMemo, useState } from "react";
import { analyticsApi, type MetricLensCompare, type MetricLensDetail, type MetricLensSummary } from "../api/analyticsApi";
import { ErrorNotice } from "../components/ErrorNotice";
import { SortableHeader } from "../components/SortableHeader";
import { stringComparator, useTableSort } from "../hooks/useTableSort";
import { PageHeader } from "@/components/page-header";
import { getEffectiveLocale, t, type Locale } from "../i18n";
import { Spin, Button, Card, Tag, Select } from "antd";
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

	const metricRows = listState.state === "loaded" ? listState.value : [];
	const metricSortColumns = useMemo(
		() => ({
			name: stringComparator<MetricLensSummary>((m) => m.name),
			aggregation: stringComparator<MetricLensSummary>((m) => m.aggregation),
			timeGrain: stringComparator<MetricLensSummary>((m) => m.timeGrain),
		}),
		[],
	);
	const {
		sortedItems: sortedMetrics,
		sortState: metricSortState,
		requestSort: requestMetricSort,
	} = useTableSort(metricRows, {
		columns: metricSortColumns,
		defaultSort: { key: "name", direction: "asc" },
	});

	const conflictRows = conflictState.state === "loaded" ? conflictState.value : [];
	const conflictSortColumns = useMemo(
		() => ({
			metricName: stringComparator<Record<string, unknown>>((r) => String(r.metricName ?? "")),
			conflictLevel: stringComparator<Record<string, unknown>>((r) => String(r.conflictLevel ?? "")),
			type: stringComparator<Record<string, unknown>>(
				(r) => (Array.isArray(r.type) ? r.type.map(String).join(", ") : ""),
			),
		}),
		[],
	);
	const {
		sortedItems: sortedConflicts,
		sortState: conflictSortState,
		requestSort: requestConflictSort,
	} = useTableSort(conflictRows, {
		columns: conflictSortColumns,
		defaultSort: { key: "conflictLevel", direction: "desc" },
	});

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
		<div className="space-y-4">
			<PageHeader
				title="增强分析工具 / MetricLens 辅助工具"
				actions={
					<Button type="default" onClick={() => void Promise.all([loadList(), loadConflicts()])}>
						{t(locale, "common.refresh")}
					</Button>
				}
			/>

			<div className="grid grid-cols-2 gap-md" style={{ marginBottom: "var(--spacing-lg)" }}>
				<Card
					title="指标清单"
					extra={listState.state === "loaded" ? <Tag>{listState.value.length}</Tag> : null}
				>
						{listState.state === "loading" && (
							<div className="loading-container" style={{ padding: "var(--spacing-lg)" }}>
								<Spin />
							</div>
						)}
						{listState.state === "error" && <ErrorNotice locale={locale} error={listState.error} />}
						{listState.state === "loaded" && listState.value.length === 0 && (
							<div className="text-secondary">{t(locale, "common.empty")}</div>
						)}
						{listState.state === "loaded" && listState.value.length > 0 && (
							<>
								<div>
									<label style={{ display: "block", marginBottom: 4, fontWeight: 500 }}>选择指标</label>
									<Select
										value={selectedMetricId}
										onChange={(value) => setSelectedMetricId(value)}
										options={metricOptions}
										style={{ width: "100%" }}
									/>
								</div>
								<table style={{ marginTop: "var(--spacing-md)" }}>
									<thead>
										<tr>
											<SortableHeader sortKey="name" sortState={metricSortState} onSort={requestMetricSort}>
												{t(locale, "common.name")}
											</SortableHeader>
											<SortableHeader sortKey="aggregation" sortState={metricSortState} onSort={requestMetricSort}>
												聚合
											</SortableHeader>
											<SortableHeader sortKey="timeGrain" sortState={metricSortState} onSort={requestMetricSort}>
												时间口径
											</SortableHeader>
										</tr>
									</thead>
									<tbody>
										{sortedMetrics.map((row, idx) => {
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
				</Card>

				<Card title="冲突检测">
						{conflictState.state === "loading" && (
							<div className="loading-container" style={{ padding: "var(--spacing-lg)" }}>
								<Spin />
							</div>
						)}
						{conflictState.state === "error" && <ErrorNotice locale={locale} error={conflictState.error} />}
						{conflictState.state === "loaded" && conflictState.value.length === 0 && (
							<div className="text-secondary">当前未发现口径冲突。</div>
						)}
						{conflictState.state === "loaded" && conflictState.value.length > 0 && (
							<table>
								<thead>
									<tr>
										<SortableHeader sortKey="metricName" sortState={conflictSortState} onSort={requestConflictSort}>
											指标名
										</SortableHeader>
										<SortableHeader sortKey="conflictLevel" sortState={conflictSortState} onSort={requestConflictSort}>
											等级
										</SortableHeader>
										<SortableHeader sortKey="type" sortState={conflictSortState} onSort={requestConflictSort}>
											冲突类型
										</SortableHeader>
									</tr>
								</thead>
								<tbody>
									{sortedConflicts.map((row, idx) => (
										<tr key={`conflict-${idx}`}>
											<td>{String(row.metricName || "-")}</td>
											<td>{String(row.conflictLevel || "-")}</td>
											<td>{Array.isArray(row.type) ? row.type.map(String).join(", ") : "-"}</td>
										</tr>
									))}
								</tbody>
							</table>
						)}
				</Card>
			</div>

			<div className="grid grid-cols-2 gap-md">
				<Card title="指标透视详情">
						{detailState == null && <div className="text-secondary">选择指标查看详情。</div>}
						{detailState?.state === "loading" && (
							<div className="loading-container" style={{ padding: "var(--spacing-lg)" }}>
								<Spin />
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
				</Card>

				<Card title="版本对比">
						<div className="flex flex-col gap-md" style={{ gap: "var(--spacing-sm)", marginBottom: "var(--spacing-sm)" }}>
							<div>
								<label style={{ display: "block", marginBottom: 4, fontWeight: 500 }}>左版本</label>
								<Select
									value={leftVersion}
									onChange={(value) => setLeftVersion(value)}
									options={versionOptions}
									disabled={versions.length === 0}
									style={{ width: "100%" }}
								/>
							</div>
							<div>
								<label style={{ display: "block", marginBottom: 4, fontWeight: 500 }}>右版本</label>
								<Select
									value={rightVersion}
									onChange={(value) => setRightVersion(value)}
									options={versionOptions}
									disabled={versions.length === 0}
									style={{ width: "100%" }}
								/>
							</div>
							<Button type="primary" onClick={() => void runCompare()} disabled={!leftVersion || !rightVersion || !selectedMetricId}>
								执行对比
							</Button>
						</div>
						{compareState?.state === "loading" && (
							<div className="loading-container" style={{ padding: "var(--spacing-lg)" }}>
								<Spin />
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
				</Card>
			</div>
		</div>
	);
}
