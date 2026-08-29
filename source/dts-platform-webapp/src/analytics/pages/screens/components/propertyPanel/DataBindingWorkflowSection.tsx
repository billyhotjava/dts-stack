import type { ComponentDataFeedback } from "../../ScreenDataFeedbackContext";
import type { DataSourceConfig, FieldMapping, ScreenComponent } from "../../types";
import { resolveDataSourceType } from "./helpers";

const MAX_SAMPLE_COLUMNS = 6;
const MAX_SAMPLE_ROWS = 5;
const MAX_CELL_LENGTH = 48;

interface DisplayColumn {
	name: string;
	displayName: string;
}

interface DataBindingWorkflowSectionProps {
	component: ScreenComponent;
	feedback?: ComponentDataFeedback;
}

const SOURCE_LABELS = {
	static: "组件内置",
	card: "分析卡片",
	metric: "指标",
	dataset: "数据集",
	sql: "SQL 查询",
	api: "接口数据",
} as const;

function formatSampleValue(value: unknown): string {
	if (value === null || value === undefined || value === "") return "—";
	let text: string;
	if (typeof value === "object") {
		try {
			text = JSON.stringify(value);
		} catch {
			text = String(value);
		}
	} else {
		text = String(value);
	}
	return text.length > MAX_CELL_LENGTH ? `${text.slice(0, MAX_CELL_LENGTH)}…` : text;
}

function resolveStoredColumns(component: ScreenComponent): DisplayColumn[] {
	const columns = component.config._sourceColumns;
	if (!Array.isArray(columns)) return [];
	return columns
		.filter((column): column is Record<string, unknown> => Boolean(column) && typeof column === "object")
		.map((column) => ({
			name: String(column.name ?? "").trim(),
			displayName: String(column.displayName ?? column.name ?? "").trim(),
		}))
		.filter((column) => column.name.length > 0);
}

function countMappedFields(component: ScreenComponent): number {
	const mapping = component.config._fieldMapping as FieldMapping | undefined;
	if (!mapping || component.config._useFieldMapping === false) return 0;
	const fields = [mapping.dimension, ...(mapping.measures ?? []), mapping.groupBy, mapping.sizeField].filter(
		(field): field is string => typeof field === "string" && field.trim().length > 0,
	);
	return new Set(fields).size;
}

export function DataBindingWorkflowSection({ component, feedback }: DataBindingWorkflowSectionProps) {
	const sourceType = resolveDataSourceType(component.dataSource as DataSourceConfig | undefined);
	const sourceLabel = SOURCE_LABELS[sourceType];
	const dataColumns: DisplayColumn[] = (feedback?.data?.cols ?? []).map((column) => ({
		name: column.name,
		displayName: column.display_name || column.name,
	}));
	const columns = dataColumns.length > 0 ? dataColumns : resolveStoredColumns(component);
	const sampleColumns = dataColumns.slice(0, MAX_SAMPLE_COLUMNS);
	const sampleRows = (feedback?.data?.rows ?? []).slice(0, MAX_SAMPLE_ROWS);
	const mappedFieldCount = countMappedFields(component);
	const hasLoadedData = Boolean(feedback?.data);
	const readStepStatus = feedback?.error
		? "读取失败"
		: feedback?.loading
			? "读取中"
			: hasLoadedData
				? "已读取"
				: columns.length > 0
					? "已识别字段"
					: "待读取";

	return (
		<section
			className="property-section border-b border-border-default"
			style={{ padding: "12px 12px 14px" }}
			aria-label="数据配置流程"
		>
			<div className="text-xs font-semibold text-text-secondary" style={{ marginBottom: 8 }}>
				数据配置流程
			</div>
			<div
				style={{
					display: "grid",
					gridTemplateColumns: "repeat(3, minmax(0, 1fr))",
					gap: 6,
					marginBottom: 10,
				}}
			>
				{[
					["1 数据来源", sourceLabel],
					["2 样例校验", readStepStatus],
					["3 字段映射", mappedFieldCount > 0 ? `${mappedFieldCount} 项` : "待映射"],
				].map(([label, status]) => (
					<div
						key={label}
						style={{
							minWidth: 0,
							padding: "7px 6px",
							border: "1px solid var(--color-border-default)",
							borderRadius: 6,
							background: "rgba(255,255,255,0.04)",
						}}
					>
						<div className="text-text-muted" style={{ fontSize: 10, whiteSpace: "nowrap" }}>
							{label}
						</div>
						<div className="text-text-primary truncate" style={{ fontSize: 11, marginTop: 3 }} title={status}>
							{status}
						</div>
					</div>
				))}
			</div>

			{feedback?.error ? (
				<div
					role="alert"
					className="text-xs rounded"
					style={{ padding: "8px 10px", color: "#fca5a5", background: "rgba(239,68,68,0.12)" }}
				>
					<strong>数据读取失败：</strong>
					{formatSampleValue(feedback.error)}
				</div>
			) : feedback?.loading && !hasLoadedData ? (
				<output className="text-xs text-text-secondary" aria-live="polite">
					正在读取样例数据…
				</output>
			) : hasLoadedData ? (
				<>
					<output
						className="text-xs text-text-secondary"
						aria-live="polite"
						style={{ display: "block", marginBottom: 7 }}
					>
						已读取 {dataColumns.length} 个字段 · {feedback?.data?.rows.length ?? 0} 行样例
						{feedback?.loading ? "，正在更新" : ""}
					</output>
					{sampleColumns.length > 0 ? (
						<div style={{ overflowX: "auto", border: "1px solid var(--color-border-default)", borderRadius: 6 }}>
							<table style={{ width: "100%", borderCollapse: "collapse", fontSize: 11 }}>
								<caption className="sr-only">当前组件样例数据</caption>
								<thead>
									<tr>
										{sampleColumns.map((column) => (
											<th
												key={column.name}
												style={{
													padding: "5px 7px",
													textAlign: "left",
													whiteSpace: "nowrap",
													color: "var(--color-text-secondary)",
													background: "rgba(255,255,255,0.04)",
												}}
											>
												{column.displayName}
											</th>
										))}
									</tr>
								</thead>
								<tbody>
									{sampleRows.map((row, rowIndex) => (
										// biome-ignore lint/suspicious/noArrayIndexKey: query rows have no stable identity and this snapshot never reorders in place.
										<tr key={rowIndex}>
											{sampleColumns.map((column, columnIndex) => (
												<td
													key={column.name}
													title={formatSampleValue(row[columnIndex])}
													style={{
														maxWidth: 120,
														padding: "5px 7px",
														color: "var(--color-text-primary)",
														borderTop: "1px solid var(--color-border-default)",
														whiteSpace: "nowrap",
														overflow: "hidden",
														textOverflow: "ellipsis",
													}}
												>
													{formatSampleValue(row[columnIndex])}
												</td>
											))}
										</tr>
									))}
								</tbody>
							</table>
							{sampleRows.length === 0 ? (
								<div className="text-xs text-text-muted" style={{ padding: 8 }}>
									查询成功，暂无样例行。
								</div>
							) : null}
						</div>
					) : null}
					<div className="text-text-muted" style={{ fontSize: 10, marginTop: 7 }}>
						样例数据仅用于当前编辑会话，不写入大屏配置。
					</div>
				</>
			) : sourceType === "static" ? (
				<div className="text-xs text-text-muted">当前使用组件内置数据；需要接入业务数据时，可在下方切换数据来源。</div>
			) : columns.length > 0 ? (
				<div className="text-xs text-text-secondary">已识别 {columns.length} 个字段，等待画布返回最新样例数据。</div>
			) : (
				<div className="text-xs text-text-muted">完成下方数据源配置后，画布会自动读取样例数据。</div>
			)}

			{mappedFieldCount > 0 ? (
				<div className="text-xs" style={{ color: "#7dd3fc", marginTop: 8 }}>
					已映射 {mappedFieldCount} 个展示字段，可在下方继续调整。
				</div>
			) : columns.length > 0 ? (
				<div className="text-xs text-text-muted" style={{ marginTop: 8 }}>
					字段已就绪，请在下方设置图表的维度和度量。
				</div>
			) : null}
		</section>
	);
}
