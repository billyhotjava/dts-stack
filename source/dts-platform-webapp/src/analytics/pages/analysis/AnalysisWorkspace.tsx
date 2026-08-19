import {
	Alert,
	Button,
	Card,
	Checkbox,
	Col,
	Collapse,
	Empty,
	Input,
	InputNumber,
	Row,
	Select,
	Space,
	Switch,
	Tag,
	Typography,
} from "antd";
import { useMemo, useState, type DragEvent } from "react";
import type { AnalysisDatasetDetail } from "@/api/sql-workbench";
import { ChartRenderer, type VisualizationType } from "../../components/charts";
import type {
	AnalysisFilterSelection,
	AnalysisQueryResult,
	AnalysisQuerySpec,
} from "../../api/analysisApi";
import {
	CANONICAL_VISUALIZATIONS,
	placeFieldOnShelf,
	removeFieldFromAnalysis,
	setVisualizationSetting,
	type AnalysisShelf,
	type AnalysisWorkspaceField,
} from "../analysisWorkspaceModel";

const { Text } = Typography;

export type AnalysisWorkspaceQueryState =
	| { status: "idle" }
	| { status: "queued"; queryId: string }
	| { status: "running"; queryId: string }
	| { status: "success"; result: AnalysisQueryResult }
	| { status: "denied" | "timeout" | "cancelled" | "throttled" | "error"; message: string; queryId?: string };

type Props = {
	contract: AnalysisDatasetDetail;
	spec: AnalysisQuerySpec;
	name: string;
	description: string;
	canWrite: boolean;
	autoPreview: boolean;
	queryState: AnalysisWorkspaceQueryState;
	onNameChange: (value: string) => void;
	onDescriptionChange: (value: string) => void;
	onSpecChange: (spec: AnalysisQuerySpec) => void;
	onAutoPreviewChange: (enabled: boolean) => void;
	onExecute: () => void;
	onCancel: () => void;
};

function metricCode(metric: Record<string, unknown>): string {
	return String(metric.code ?? "");
}

function metricLabel(metric: Record<string, unknown>): string {
	return String(metric.label ?? metric.code ?? "未命名度量");
}

function queryStatusLabel(queryState: AnalysisWorkspaceQueryState): string {
	switch (queryState.status) {
		case "queued": return "排队中";
		case "running": return "执行中";
		case "success": return queryState.result.truncated ? "结果已截断" : "执行成功";
		case "denied": return "无权访问";
		case "timeout": return "查询超时";
		case "cancelled": return "已取消";
		case "throttled": return "查询繁忙";
		case "error": return "执行失败";
		default: return "等待执行";
	}
}

function selectedCodes(spec: AnalysisQuerySpec): string[] {
	return [
		...spec.dimensions.map((item) => item.field),
		...spec.metrics.map((item) => item.code),
		...spec.derivedMetrics.map((item) => item.code),
	];
}

function fieldDragPayload(event: DragEvent, field: AnalysisWorkspaceField) {
	event.dataTransfer.effectAllowed = "copy";
	event.dataTransfer.setData("application/x-dts-analysis-field", JSON.stringify(field));
}

function parseDroppedField(event: DragEvent): AnalysisWorkspaceField | null {
	try {
		const value = JSON.parse(event.dataTransfer.getData("application/x-dts-analysis-field"));
		if (!value || !["dimension", "metric", "derived"].includes(value.kind) || typeof value.code !== "string") return null;
		return value as AnalysisWorkspaceField;
	} catch {
		return null;
	}
}

const PALETTES = [
	["#4E7DF2", "#65C3BA", "#F6BD5B", "#ED7D75", "#8A76D2"],
	["#2B6CB0", "#2F855A", "#B7791F", "#C53030", "#6B46C1"],
	["#0F6CBD", "#038387", "#986F0B", "#C50F1F", "#8764B8"],
];

export function AnalysisWorkspace({
	contract,
	spec,
	name,
	description,
	canWrite,
	autoPreview,
	queryState,
	onNameChange,
	onDescriptionChange,
	onSpecChange,
	onAutoPreviewChange,
	onExecute,
	onCancel,
}: Props) {
	const [derivedCode, setDerivedCode] = useState("");
	const [derivedExpression, setDerivedExpression] = useState("");
	const [derivedError, setDerivedError] = useState<string | null>(null);
	const selected = selectedCodes(spec);
	const fieldOptions = contract.dimensions.map((field) => ({ label: field.label || field.code, value: field.code }));
	const orderOptions = selected.map((code) => ({ label: code, value: code }));
	const timeDimensions = contract.dimensions.filter((field) => (field.timeGrains ?? []).length > 0);
	const queryResult = queryState.status === "success" ? queryState.result : null;
	const chartData = useMemo(() => {
		if (!queryResult) return null;
		return {
			cols: queryResult.columns.map((column, index) => ({
				name: String(column.name ?? column.display_name ?? column.label ?? selected[index] ?? `column_${index + 1}`),
				display_name: String(column.display_name ?? column.label ?? column.name ?? selected[index] ?? `column_${index + 1}`),
				base_type: typeof column.base_type === "string" ? column.base_type : undefined,
			})),
			rows: queryResult.rows,
		};
	}, [queryResult, selected]);

	const updateSpec = (mutate: (current: AnalysisQuerySpec) => AnalysisQuerySpec) => {
		if (canWrite) onSpecChange(mutate(spec));
	};

	const place = (field: AnalysisWorkspaceField, shelf: AnalysisShelf) => {
		updateSpec((current) => placeFieldOnShelf(current, field, shelf));
	};

	const onDrop = (event: DragEvent, shelf: AnalysisShelf) => {
		event.preventDefault();
		const field = parseDroppedField(event);
		if (field) place(field, shelf);
	};

	const shelf = (label: string, key: AnalysisShelf, codes: string[], hint: string) => (
		<div
			data-testid={`analysis-shelf-${key}`}
			onDragOver={(event) => event.preventDefault()}
			onDrop={(event) => onDrop(event, key)}
			style={{ minHeight: 50, padding: 8, border: "1px dashed #9aa8bc", borderRadius: 6, background: "#f7f9fc" }}
		>
			<Space wrap size={[4, 4]}>
				<Text strong>{label}</Text>
				{codes.length === 0 ? <Text type="secondary">{hint}</Text> : null}
				{codes.map((code) => (
					<Tag key={`${key}-${code}`} closable={canWrite} onClose={() => updateSpec((current) => removeFieldFromAnalysis(current, code))}>
						{code}
					</Tag>
				))}
			</Space>
		</div>
	);

	const updateFilter = (index: number, patch: Partial<AnalysisFilterSelection>) => {
		updateSpec((current) => ({
			...current,
			filters: current.filters.map((item, itemIndex) => (itemIndex === index ? { ...item, ...patch } : item)),
		}));
	};

	const addDerived = () => {
		const code = derivedCode.trim();
		const expression = derivedExpression.trim();
		const reservedCodes = [
			...contract.dimensions.map((item) => item.code),
			...contract.metrics.map(metricCode),
		];
		if (!/^[A-Za-z_][A-Za-z0-9_]{0,127}$/.test(code)) {
			setDerivedError("编码必须以字母或下划线开头，只包含字母、数字和下划线。");
			return;
		}
		if (!expression || spec.derivedMetrics.some((item) => item.code === code) || reservedCodes.includes(code)) {
			setDerivedError(!expression ? "请输入计算表达式。" : "字段编码已存在。");
			return;
		}
		const withDerived = { ...spec, derivedMetrics: [...spec.derivedMetrics, { code, expression, format: null }] };
		onSpecChange(placeFieldOnShelf(withDerived, { kind: "derived", code }, "y"));
		setDerivedCode("");
		setDerivedExpression("");
		setDerivedError(null);
	};

	const busy = queryState.status === "queued" || queryState.status === "running";
	const status = queryStatusLabel(queryState);
	const graphDimensions = (spec.visualization.settings["graph.dimensions"] as string[] | undefined) ?? spec.dimensions.map((item) => item.field);
	const graphMetrics = (spec.visualization.settings["graph.metrics"] as string[] | undefined) ?? [
		...spec.metrics.map((item) => item.code),
		...spec.derivedMetrics.map((item) => item.code),
	];

	return (
		<Row gutter={[12, 12]} style={{ flex: 1 }}>
			<Col xs={24} xl={5}>
				<Card title="字段" size="small" style={{ height: "100%" }}>
					{contract.dimensions.length === 0 && contract.metrics.length === 0 ? (
						<Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无可分析字段" />
					) : (
						<Space direction="vertical" size={10} style={{ width: "100%" }}>
							<Text strong>维度</Text>
							{contract.dimensions.map((field) => (
								<div data-testid={`analysis-field-dimension-${field.code}`} key={field.code} draggable={canWrite} onDragStart={(event) => fieldDragPayload(event, { kind: "dimension", code: field.code })}>
									<Button block disabled={!canWrite} onClick={() => place({ kind: "dimension", code: field.code }, "x")}>
										{field.label || field.code} <Text type="secondary">({field.code})</Text>
									</Button>
								</div>
							))}
							<Text strong>度量</Text>
							{contract.metrics.map((metric) => {
								const code = metricCode(metric);
								return (
									<div data-testid={`analysis-field-metric-${code}`} key={code} draggable={canWrite} onDragStart={(event) => fieldDragPayload(event, { kind: "metric", code })}>
										<Button block disabled={!canWrite} onClick={() => place({ kind: "metric", code }, "y")}>
											{metricLabel(metric)} <Text type="secondary">({code})</Text>
										</Button>
									</div>
								);
							})}
						</Space>
					)}
				</Card>
			</Col>

			<Col xs={24} xl={12}>
				<Space direction="vertical" size={12} style={{ width: "100%" }}>
					<Card title="字段货架" size="small">
						<Space direction="vertical" size={8} style={{ width: "100%" }}>
							{shelf("横轴", "x", graphDimensions, "拖入维度")}
							{shelf("纵轴", "y", graphMetrics, "拖入度量或派生指标")}
						</Space>
					</Card>
					<Card
						title="实时图表"
						size="small"
						extra={(
							<Space>
								<Text type="secondary">{status}</Text>
								<Switch checked={autoPreview} disabled={!canWrite} onChange={onAutoPreviewChange} checkedChildren="自动" unCheckedChildren="手动" />
								<Button type="primary" disabled={busy || selected.length === 0} onClick={onExecute}>执行查询</Button>
								{busy ? <Button danger onClick={onCancel}>取消查询</Button> : null}
							</Space>
						)}
					>
						{selected.length === 0 ? (
							<Empty description="拖入字段后自动执行受治理查询" />
						) : (
							<Space direction="vertical" size={10} style={{ width: "100%" }}>
								{queryState.status === "idle" ? <Alert type="info" showIcon message="查询尚未执行" /> : null}
								{"message" in queryState ? <Alert type={queryState.status === "cancelled" ? "warning" : "error"} showIcon message={status} description={queryState.message} /> : null}
								<ChartRenderer
									data={chartData}
									display={spec.visualization.type as VisualizationType}
									settings={spec.visualization.settings}
									loading={busy}
									style={{ height: 390 }}
								/>
								{queryResult ? (
									<Text type="secondary">{queryResult.rowCount} 行 · {queryResult.durationMs} ms · {queryResult.cacheHit ? "缓存命中" : "实时查询"}</Text>
								) : null}
							</Space>
						)}
					</Card>
				</Space>
			</Col>

			<Col xs={24} xl={7}>
				<Card title="分析配置" size="small" style={{ height: "100%" }}>
					<Space direction="vertical" size={10} style={{ width: "100%" }}>
						<Input value={name} maxLength={255} disabled={!canWrite} placeholder="分析名称" onChange={(event) => onNameChange(event.target.value)} />
						<Input.TextArea value={description} rows={2} disabled={!canWrite} placeholder="分析说明" onChange={(event) => onDescriptionChange(event.target.value)} />
						<Select
							value={spec.visualization.type}
							disabled={!canWrite}
							options={CANONICAL_VISUALIZATIONS.map((item) => ({ ...item }))}
							onChange={(type) => updateSpec((current) => ({ ...current, visualization: { ...current.visualization, type } }))}
							style={{ width: "100%" }}
						/>
						<Collapse
							defaultActiveKey={["style", "calculation"]}
							items={[
								{
									key: "style",
									label: "图表样式",
									children: (
										<Space direction="vertical" size={8} style={{ width: "100%" }}>
											<Select
												value={JSON.stringify(spec.visualization.settings["graph.colors"] ?? PALETTES[0])}
												disabled={!canWrite}
												options={PALETTES.map((palette, index) => ({ label: `配色 ${index + 1}`, value: JSON.stringify(palette) }))}
												onChange={(value) => updateSpec((current) => setVisualizationSetting(current, "graph.colors", JSON.parse(value)))}
												style={{ width: "100%" }}
											/>
											<Checkbox checked={spec.visualization.settings["graph.show_values"] === true} disabled={!canWrite} onChange={(event) => updateSpec((current) => setVisualizationSetting(current, "graph.show_values", event.target.checked))}>显示数值</Checkbox>
											<Checkbox checked={spec.visualization.settings["graph.smooth"] !== false} disabled={!canWrite} onChange={(event) => updateSpec((current) => setVisualizationSetting(current, "graph.smooth", event.target.checked))}>平滑曲线</Checkbox>
											<Checkbox checked={spec.visualization.settings["stackable.stack_type"] === "stacked"} disabled={!canWrite} onChange={(event) => updateSpec((current) => setVisualizationSetting(current, "stackable.stack_type", event.target.checked ? "stacked" : null))}>堆叠展示</Checkbox>
											<Checkbox checked={spec.visualization.settings["graph.show_legend"] !== false} disabled={!canWrite} onChange={(event) => updateSpec((current) => setVisualizationSetting(setVisualizationSetting(current, "graph.show_legend", event.target.checked), "pie.show_legend", event.target.checked))}>显示图例</Checkbox>
											<Checkbox checked={spec.visualization.settings["graph.x_axis.axis_enabled"] !== false} disabled={!canWrite} onChange={(event) => updateSpec((current) => setVisualizationSetting(current, "graph.x_axis.axis_enabled", event.target.checked))}>显示横轴</Checkbox>
											<Checkbox checked={spec.visualization.settings["graph.y_axis.axis_enabled"] !== false} disabled={!canWrite} onChange={(event) => updateSpec((current) => setVisualizationSetting(current, "graph.y_axis.axis_enabled", event.target.checked))}>显示纵轴</Checkbox>
											<Space.Compact block>
												<Input value={String(spec.visualization.settings["graph.x_axis.title_text"] ?? "")} disabled={!canWrite} placeholder="横轴标题" onChange={(event) => updateSpec((current) => setVisualizationSetting(current, "graph.x_axis.title_text", event.target.value))} />
												<Input value={String(spec.visualization.settings["graph.y_axis.title_text"] ?? "")} disabled={!canWrite} placeholder="纵轴标题" onChange={(event) => updateSpec((current) => setVisualizationSetting(current, "graph.y_axis.title_text", event.target.value))} />
											</Space.Compact>
											<InputNumber min={-90} max={90} value={Number(spec.visualization.settings["graph.x_axis.label_rotate"] ?? 0)} disabled={!canWrite} addonBefore="横轴标签角度" style={{ width: "100%" }} onChange={(value) => updateSpec((current) => setVisualizationSetting(current, "graph.x_axis.label_rotate", Number(value ?? 0)))} />
											<Select value={String(spec.visualization.settings["pie.percent_visibility"] ?? "legend")} disabled={!canWrite} options={[{ label: "饼图百分比：关闭", value: "off" }, { label: "饼图百分比：外侧", value: "legend" }, { label: "饼图百分比：内部", value: "inside" }]} onChange={(value) => updateSpec((current) => setVisualizationSetting(current, "pie.percent_visibility", value))} style={{ width: "100%" }} />
										</Space>
									),
								},
								{
									key: "calculation",
									label: `派生指标（${spec.derivedMetrics.length}/20）`,
									children: (
										<Space direction="vertical" size={8} style={{ width: "100%" }}>
											{spec.derivedMetrics.map((item, index) => (
												<Card key={item.code} size="small" title={item.code} extra={<Button type="text" danger size="small" disabled={!canWrite} onClick={() => updateSpec((current) => removeFieldFromAnalysis(current, item.code))}>删除</Button>}>
													<Space direction="vertical" size={6} style={{ width: "100%" }}>
														<Input.TextArea value={item.expression} rows={2} disabled={!canWrite} onChange={(event) => updateSpec((current) => ({ ...current, derivedMetrics: current.derivedMetrics.map((metric, metricIndex) => metricIndex === index ? { ...metric, expression: event.target.value } : metric) }))} />
														<Select allowClear value={item.format ?? undefined} disabled={!canWrite} placeholder="显示格式" options={[{ label: "数值", value: "NUMBER" }, { label: "百分比", value: "PERCENT" }, { label: "金额", value: "CURRENCY" }]} onChange={(format) => updateSpec((current) => ({ ...current, derivedMetrics: current.derivedMetrics.map((metric, metricIndex) => metricIndex === index ? { ...metric, format: format ?? null } : metric) }))} style={{ width: "100%" }} />
													</Space>
												</Card>
											))}
											<Input value={derivedCode} disabled={!canWrite} placeholder="编码，如 conversion_rate" onChange={(event) => setDerivedCode(event.target.value)} />
											<Input.TextArea value={derivedExpression} disabled={!canWrite} rows={2} placeholder="表达式，如 approved_count / NULLIF(total_count, 0)" onChange={(event) => setDerivedExpression(event.target.value)} />
											{derivedError ? <Alert type="error" showIcon message={derivedError} /> : null}
											<Button block disabled={!canWrite || spec.derivedMetrics.length >= 20} onClick={addDerived}>添加计算指标</Button>
										</Space>
									),
								},
								{
									key: "filter",
									label: `筛选与时间（${spec.filters.length}/50）`,
									children: (
										<Space direction="vertical" size={8} style={{ width: "100%" }}>
											{spec.filters.map((filter, index) => {
												const dimension = contract.dimensions.find((item) => item.code === filter.field);
												return (
													<Space.Compact key={`${filter.field}-${index}`} block>
														<Select value={filter.field} options={fieldOptions} disabled={!canWrite} style={{ width: "34%" }} onChange={(field) => updateFilter(index, { field, op: contract.dimensions.find((item) => item.code === field)?.filterOps?.[0] ?? "EQ" })} />
														<Select value={filter.op} options={(dimension?.filterOps ?? ["EQ"]).map((value) => ({ label: value, value }))} disabled={!canWrite} style={{ width: "26%" }} onChange={(op) => updateFilter(index, { op })} />
														<Input value={filter.values.join(",")} disabled={!canWrite || ["IS_NULL", "IS_NOT_NULL"].includes(filter.op)} style={{ width: "40%" }} onChange={(event) => updateFilter(index, { values: event.target.value.split(",").map((value) => value.trim()).filter(Boolean) })} />
														<Button disabled={!canWrite} onClick={() => updateSpec((current) => ({ ...current, filters: current.filters.filter((_, itemIndex) => itemIndex !== index) }))}>删</Button>
													</Space.Compact>
												);
											})}
											<Button block disabled={!canWrite || contract.dimensions.length === 0 || spec.filters.length >= 50} onClick={() => updateSpec((current) => ({ ...current, filters: [...current.filters, { field: contract.dimensions[0]?.code ?? "", op: contract.dimensions[0]?.filterOps?.[0] ?? "EQ", values: [] }] }))}>添加筛选条件</Button>
											{timeDimensions.length > 0 ? (
												<>
												<Space.Compact block>
													<Select allowClear placeholder="时间字段" value={spec.timeRange?.field} options={timeDimensions.map((field) => ({ label: field.label || field.code, value: field.code }))} disabled={!canWrite} style={{ width: "60%" }} onChange={(field) => updateSpec((current) => ({ ...current, timeRange: field ? { field, grain: contract.dimensions.find((item) => item.code === field)?.timeGrains?.[0] ?? "DAY", start: null, end: null } : null }))} />
													<Select placeholder="粒度" value={spec.timeRange?.grain} options={(timeDimensions.find((field) => field.code === spec.timeRange?.field)?.timeGrains ?? []).map((value) => ({ label: value, value }))} disabled={!canWrite || !spec.timeRange} style={{ width: "40%" }} onChange={(grain) => updateSpec((current) => ({ ...current, timeRange: current.timeRange ? { ...current.timeRange, grain } : null }))} />
												</Space.Compact>
												<Space.Compact block>
													<Input addonBefore="开始" type="date" value={spec.timeRange?.start ?? ""} disabled={!canWrite || !spec.timeRange} onChange={(event) => updateSpec((current) => ({ ...current, timeRange: current.timeRange ? { ...current.timeRange, start: event.target.value || null } : null }))} />
													<Input addonBefore="结束" type="date" value={spec.timeRange?.end ?? ""} disabled={!canWrite || !spec.timeRange} onChange={(event) => updateSpec((current) => ({ ...current, timeRange: current.timeRange ? { ...current.timeRange, end: event.target.value || null } : null }))} />
												</Space.Compact>
												</>
											) : null}
										</Space>
									),
								},
								{
									key: "order",
									label: `排序与行数（${spec.orderBy.length}/10）`,
									children: (
										<Space direction="vertical" size={8} style={{ width: "100%" }}>
											{spec.orderBy.map((order, index) => (
												<Space.Compact key={`${order.field}-${index}`} block>
													<Select value={order.field} options={orderOptions} disabled={!canWrite} style={{ width: "58%" }} onChange={(field) => updateSpec((current) => ({ ...current, orderBy: current.orderBy.map((item, itemIndex) => itemIndex === index ? { ...item, field } : item) }))} />
													<Select value={order.direction} options={[{ label: "升序", value: "ASC" }, { label: "降序", value: "DESC" }]} disabled={!canWrite} style={{ width: "30%" }} onChange={(direction) => updateSpec((current) => ({ ...current, orderBy: current.orderBy.map((item, itemIndex) => itemIndex === index ? { ...item, direction } : item) }))} />
													<Button disabled={!canWrite} onClick={() => updateSpec((current) => ({ ...current, orderBy: current.orderBy.filter((_, itemIndex) => itemIndex !== index) }))}>删</Button>
												</Space.Compact>
											))}
											<Button block disabled={!canWrite || selected.length === 0 || spec.orderBy.length >= 10} onClick={() => updateSpec((current) => ({ ...current, orderBy: [...current.orderBy, { field: selected[0], direction: "ASC" }] }))}>添加排序</Button>
											<InputNumber min={1} max={10000} value={spec.limit} disabled={!canWrite} addonBefore="最大行数" style={{ width: "100%" }} onChange={(value) => updateSpec((current) => ({ ...current, limit: Number(value ?? 5000) }))} />
										</Space>
									),
								},
							]}
						/>
					</Space>
				</Card>
			</Col>
		</Row>
	);
}
