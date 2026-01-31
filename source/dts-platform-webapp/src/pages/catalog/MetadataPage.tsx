import { useEffect, useMemo, useState } from "react";
import { toast } from "sonner";
import {
	Alert,
	Button,
	Card,
	Col,
	Descriptions,
	Form,
	Input,
	Modal,
	Row,
	Select,
	Space,
	Table,
	Tag,
	Typography,
} from "antd";
import type { ColumnsType } from "antd/es/table";
import { EmptyState } from "@/components/empty-state";
import { PageHeader } from "@/components/page-header";
import {
	getTechMetadataTableDetail,
	getTechMetadataTables,
	listCatalogSyncPipelines,
	listCatalogSyncRuns,
	triggerCatalogSync,
	triggerJdbcCatalogSync,
} from "@/api/platformApi";

const { Text } = Typography;

type SyncPipeline = {
	id?: string;
	sourceId?: string;
	integration?: string;
	name?: string;
	source?: string;
	schedule?: string;
	lastRun?: string;
	status?: string;
	tablesFound?: number;
	autoEnabled?: boolean;
	logLines?: string[];
	error?: string;
};

type SyncRun = {
	id?: string;
	integration?: string;
	status?: string;
	startedAt?: string;
	finishedAt?: string;
	tablesDiscovered?: number;
	tablesCreated?: number;
	columnsImported?: number;
	datasetsCreated?: number;
	datasetsUpdated?: number;
	datasetsRemoved?: number;
	error?: string;
};

type TableSummary = {
	fqn?: string;
	name?: string;
	service?: string;
	database?: string;
	schema?: string;
	owner?: string;
	domain?: string;
	tags?: string;
	description?: string;
	columnCount?: number;
};

type TableDetail = {
	enabled?: boolean;
	found?: boolean;
	message?: string;
	entity?: Record<string, any>;
};

type ColumnRow = {
	key: string;
	name: string;
	type: string;
	comment: string;
	status?: string;
};

const statusTag = (status?: string) => {
	if (!status) return <Tag>未知</Tag>;
	const normalized = status.toUpperCase();
	if (["SUCCESS", "SUCCEEDED"].includes(normalized)) return <Tag color="green">成功</Tag>;
	if (normalized === "FAILED") return <Tag color="red">失败</Tag>;
	if (normalized === "RUNNING") return <Tag color="blue">运行中</Tag>;
	return <Tag>{status}</Tag>;
};

const buildColumnRows = (detail?: TableDetail | null): ColumnRow[] => {
	if (!detail?.entity) return [];
	const columns = Array.isArray(detail.entity.columns) ? detail.entity.columns : [];
	return columns.map((item: any, idx: number) => ({
		key: String(item?.name || item?.displayName || idx),
		name: String(item?.name || item?.displayName || "-").trim(),
		type: String(item?.dataType || item?.dataTypeDisplay || "-").trim(),
		comment: String(item?.description || item?.comment || "").trim(),
		status: String(item?.status || "").trim(),
	}));
};

export default function MetadataPage() {
	const [form] = Form.useForm();
	const [helpOpen, setHelpOpen] = useState(false);
	const [pipelines, setPipelines] = useState<SyncPipeline[]>([]);
	const [selectedPipelineId, setSelectedPipelineId] = useState<string | undefined>();
	const [runs, setRuns] = useState<SyncRun[]>([]);
	const [tables, setTables] = useState<TableSummary[]>([]);
	const [selectedFqn, setSelectedFqn] = useState<string | undefined>();
	const [tableDetail, setTableDetail] = useState<TableDetail | null>(null);
	const [loadingPipelines, setLoadingPipelines] = useState(false);
	const [loadingRuns, setLoadingRuns] = useState(false);
	const [loadingTables, setLoadingTables] = useState(false);
	const [keyword, setKeyword] = useState("");

	const selectedPipeline = useMemo(() => {
		if (!pipelines.length) return null;
		return pipelines.find((item) => String(item.id) === String(selectedPipelineId)) || pipelines[0];
	}, [pipelines, selectedPipelineId]);

	useEffect(() => {
		void loadPipelines();
		void loadTables("");
	}, []);

	useEffect(() => {
		if (!selectedPipeline && pipelines.length) {
			setSelectedPipelineId(pipelines[0]?.id);
		}
	}, [pipelines, selectedPipeline]);

	useEffect(() => {
		if (!selectedPipeline?.integration) {
			setRuns([]);
			return;
		}
		void loadRuns(selectedPipeline.integration);
	}, [selectedPipeline?.integration, selectedPipeline?.sourceId]);

	useEffect(() => {
		if (!selectedFqn) {
			setTableDetail(null);
			return;
		}
		void loadTableDetail(selectedFqn);
	}, [selectedFqn]);

	const loadPipelines = async () => {
		setLoadingPipelines(true);
		try {
			const resp: any = await listCatalogSyncPipelines();
			const list = Array.isArray(resp) ? resp : [];
			setPipelines(list as SyncPipeline[]);
			if (list.length && !selectedPipelineId) {
				setSelectedPipelineId(list[0]?.id);
			}
		} catch (error: any) {
			toast.error(error?.message || "采集任务加载失败");
		} finally {
			setLoadingPipelines(false);
		}
	};

	const loadRuns = async (integration: string) => {
		setLoadingRuns(true);
		try {
			const resp: any = await listCatalogSyncRuns({
				integration,
				limit: 20,
				includeDetails: false,
				sourceId: integration === "JDBC" ? selectedPipeline?.sourceId : undefined,
			});
			setRuns(Array.isArray(resp) ? (resp as SyncRun[]) : []);
		} catch (error: any) {
			toast.error(error?.message || "采集历史加载失败");
		} finally {
			setLoadingRuns(false);
		}
	};

	const loadTables = async (nextKeyword: string) => {
		setLoadingTables(true);
		try {
			const resp: any = await getTechMetadataTables({ keyword: nextKeyword || undefined, size: 50 });
			const items = Array.isArray(resp?.items) ? resp.items : [];
			setTables(items as TableSummary[]);
			if (items.length) {
				setSelectedFqn((prev) => prev || items[0]?.fqn);
			} else {
				setSelectedFqn(undefined);
			}
		} catch (error: any) {
			toast.error(error?.message || "元数据资产加载失败");
			setTables([]);
			setSelectedFqn(undefined);
		} finally {
			setLoadingTables(false);
		}
	};

	const loadTableDetail = async (fqn: string) => {
		if (!fqn) return;
		try {
			const resp: any = await getTechMetadataTableDetail(fqn);
			setTableDetail(resp || null);
		} catch (error: any) {
			toast.error(error?.message || "元数据详情加载失败");
			setTableDetail(null);
		}
	};

	const handleTrigger = async () => {
		if (!selectedPipeline) {
			toast.error("请先选择采集任务");
			return;
		}
		const reason = String(form.getFieldValue("reason") || "manual").trim();
		try {
			if (selectedPipeline.integration === "JDBC") {
				const sourceId = selectedPipeline.sourceId || selectedPipeline.id;
				if (!sourceId) {
					toast.error("缺少数据源标识");
					return;
				}
				await triggerJdbcCatalogSync(sourceId, { reason });
			} else {
				await triggerCatalogSync({ includePrimary: true, includeJdbc: false, reason });
			}
			toast.success("已触发采集任务");
			if (selectedPipeline.integration) {
				void loadRuns(selectedPipeline.integration);
			}
		} catch (error: any) {
			toast.error(error?.message || "触发采集失败");
		}
	};

	const runColumns: ColumnsType<SyncRun> = [
		{ title: "开始时间", dataIndex: "startedAt" },
		{ title: "结束时间", dataIndex: "finishedAt" },
		{ title: "状态", dataIndex: "status", render: statusTag },
		{ title: "发现表", dataIndex: "tablesDiscovered" },
		{ title: "新增表", dataIndex: "tablesCreated" },
		{ title: "更新表", dataIndex: "datasetsUpdated" },
		{ title: "删除表", dataIndex: "datasetsRemoved" },
		{ title: "新增字段", dataIndex: "columnsImported" },
		{ title: "错误", dataIndex: "error", render: (value) => <Text type="danger">{value || "-"}</Text> },
	];

	const columnColumns: ColumnsType<ColumnRow> = [
		{ title: "字段", dataIndex: "name" },
		{ title: "类型", dataIndex: "type" },
		{
			title: "状态",
			dataIndex: "status",
			render: (value) => {
				const normalized = String(value || "").toUpperCase();
				if (!normalized) return <Tag>未知</Tag>;
				if (normalized === "DRAFT") return <Tag color="orange">草稿</Tag>;
				if (normalized === "ACTIVE") return <Tag color="green">正式</Tag>;
				return <Tag>{value}</Tag>;
			},
		},
		{ title: "备注", dataIndex: "comment" },
	];

	const selectedSummary = useMemo(
		() => tables.find((item) => item.fqn === selectedFqn) || null,
		[tables, selectedFqn],
	);
	const columnRows = useMemo(() => buildColumnRows(tableDetail), [tableDetail]);
	const columnStatusStats = useMemo(() => {
		let draft = 0;
		let active = 0;
		let other = 0;
		columnRows.forEach((row) => {
			const label = String(row.status || "").toUpperCase();
			if (label === "DRAFT") draft += 1;
			else if (label === "ACTIVE") active += 1;
			else other += 1;
		});
		return { draft, active, other };
	}, [columnRows]);

	return (
		<div className="space-y-4">
			<PageHeader
				title="元数据采集"
				description="基于数据源连接触发结构扫描，并在资产门户同步表/字段信息。"
				actions={
					<Space>
						<Button onClick={() => void loadPipelines()}>刷新任务</Button>
						<Button onClick={() => setHelpOpen(true)}>使用说明</Button>
					</Space>
				}
			/>

			<Alert
				type="info"
				showIcon
				message="采集任务会同步表/字段/索引等结构信息，供资产门户、质量校验与入湖配置复用。"
			/>

			<Row gutter={[16, 16]} align="top">
				<Col xs={24} xl={12}>
					<Card title="采集任务与触发" loading={loadingPipelines}>
						{pipelines.length ? (
							<Form form={form} layout="vertical">
								<Form.Item label="选择采集任务">
									<Select
										value={selectedPipeline?.id}
										onChange={(value) => setSelectedPipelineId(value)}
										options={pipelines.map((item) => ({
											label: `${item.name || "采集任务"} · ${item.source || ""}`.trim(),
											value: item.id,
										}))}
									/>
								</Form.Item>
								<Form.Item name="reason" label="触发说明">
									<Input placeholder="例如：测试同步" />
								</Form.Item>
								<Descriptions size="small" column={1} bordered>
									<Descriptions.Item label="来源">{selectedPipeline?.source || "-"}</Descriptions.Item>
									<Descriptions.Item label="调度策略">{selectedPipeline?.schedule || "-"}</Descriptions.Item>
									<Descriptions.Item label="最近状态">{statusTag(selectedPipeline?.status)}</Descriptions.Item>
									<Descriptions.Item label="最近发现表">{selectedPipeline?.tablesFound ?? "-"}</Descriptions.Item>
								</Descriptions>
								{selectedPipeline?.error ? (
									<div className="mt-3 text-sm text-red-500">错误：{selectedPipeline.error}</div>
								) : null}
								{selectedPipeline?.logLines && selectedPipeline.logLines.length ? (
									<div className="mt-3 rounded border bg-muted/20 p-3 text-xs text-muted-foreground">
										<div className="mb-2 font-medium text-foreground">最近日志</div>
										<ul className="list-disc space-y-1 pl-4">
											{selectedPipeline.logLines.slice(0, 10).map((line, idx) => (
												<li key={idx}>{line}</li>
											))}
										</ul>
									</div>
								) : null}
								<Space className="mt-4">
									<Button type="primary" onClick={handleTrigger}>立即采集</Button>
									<Button onClick={() => selectedPipeline?.integration && loadRuns(selectedPipeline.integration)}>
										刷新历史
									</Button>
								</Space>
							</Form>
						) : (
							<EmptyState title="暂无采集任务" description="请先配置数据源或数据湖连接。" />
						)}
					</Card>
				</Col>
				<Col xs={24} xl={12}>
					<Card title="元数据结果预览" loading={loadingTables}>
						<Space direction="vertical" className="w-full" size={12}>
							<Space className="w-full" align="start">
								<Input
									placeholder="搜索表名或关键字"
									value={keyword}
									onChange={(e) => setKeyword(e.target.value)}
									allowClear
								/>
								<Button onClick={() => void loadTables(keyword)}>搜索</Button>
							</Space>
							{tables.length ? (
								<>
									<Form layout="vertical">
										<Form.Item label="已发现表">
											<Select
												value={selectedFqn}
												onChange={(value) => setSelectedFqn(value)}
												options={tables.map((item) => ({
													label:
														[item.database, item.schema, item.name].filter(Boolean).join(".") ||
														item.name ||
														item.fqn ||
														"-",
													value: item.fqn,
												}))}
											/>
										</Form.Item>
									</Form>
									<Descriptions size="small" bordered column={1}>
										<Descriptions.Item label="服务">{selectedSummary?.service || "-"}</Descriptions.Item>
										<Descriptions.Item label="库/Schema">
											{[selectedSummary?.database, selectedSummary?.schema].filter(Boolean).join(".") || "-"}
										</Descriptions.Item>
										<Descriptions.Item label="表名">{selectedSummary?.name || "-"}</Descriptions.Item>
										<Descriptions.Item label="描述">{selectedSummary?.description || "-"}</Descriptions.Item>
										<Descriptions.Item label="字段数">{selectedSummary?.columnCount ?? "-"}</Descriptions.Item>
									</Descriptions>
									{columnRows.length ? (
										<Space size={6} className="mt-3 flex flex-wrap">
											<Tag color="orange">草稿 {columnStatusStats.draft}</Tag>
											<Tag color="green">正式 {columnStatusStats.active}</Tag>
											{columnStatusStats.other ? <Tag>其他 {columnStatusStats.other}</Tag> : null}
										</Space>
									) : null}
									<div>
										<Text type="secondary">字段列表</Text>
										<Table
											size="small"
											pagination={false}
											columns={columnColumns}
											dataSource={columnRows}
											rowKey={(row) => row.key}
										/>
									</div>
								</>
								) : (
									<EmptyState title="暂无元数据" description="请先完成元数据采集或检查元数据服务连接。" />
								)}
						</Space>
					</Card>
				</Col>
			</Row>

			<Card title="采集历史" extra={<Button onClick={() => selectedPipeline?.integration && loadRuns(selectedPipeline.integration)}>刷新</Button>}>
				<Table
					rowKey={(row) => row.id || `${row.startedAt}-${row.finishedAt}`}
					columns={runColumns}
					dataSource={runs}
					loading={loadingRuns}
					pagination={{ pageSize: 8 }}
				/>
			</Card>

			<Modal
				open={helpOpen}
				title="使用说明"
				onCancel={() => setHelpOpen(false)}
				footer={[
					<Button key="close" onClick={() => setHelpOpen(false)}>
						关闭
					</Button>,
				]}
			>
					<div className="space-y-2 text-sm text-slate-600">
						<div>1. 采集任务来自当前已启用的数据源或主数据连接。</div>
						<div>2. 触发采集后可在“采集历史”查看执行结果与错误信息。</div>
						<div>3. 元数据结果预览来自平台采集或 OpenMetadata 服务。</div>
					</div>
				</Modal>
		</div>
	);
}
