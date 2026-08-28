import {
	Alert,
	Button,
	Card,
	Col,
	Empty,
	Form,
	Input,
	Modal,
	message,
	Row,
	Segmented,
	Select,
	Space,
	Spin,
	Switch,
	Tabs,
	Tag,
	Tooltip,
	Typography,
} from "antd";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useSearchParams } from "react-router";
import {
	type IngestionTaskDesign,
	type IngestionTaskDesignUpdate,
	type IngestionTaskDTO,
	type IngestionTopologyProjection,
	type IngestionValidationResult,
	ingestionTaskAPI,
} from "@/api/ingestion";
import { getDataset, listDatasets } from "@/api/platformApi";
import dataSourcesService, { type InfraDataSource } from "@/api/services/dataSourcesService";
import { PageHeader } from "@/components/page-header";
import OrchestrationRunsTab from "./OrchestrationRunsTab";

const { Paragraph, Text } = Typography;

type OrchestrationTabKey = "design" | "runs";
type TopologyView = "DRAFT" | "ACTIVE";

type CatalogDatasetOption = {
	id: string;
	name: string;
	hiveDatabase?: string;
	hiveTable?: string;
	type?: string;
	sourceId?: string;
	lifecycleStatus?: string;
	enabled?: boolean;
};

function catalogDatasetOption(item: any): CatalogDatasetOption | null {
	const id = String(item?.id || "").trim();
	const name = String(item?.name || "").trim();
	if (!id || !name) return null;
	return {
		id,
		name,
		hiveDatabase: item.hiveDatabase ? String(item.hiveDatabase) : undefined,
		hiveTable: item.hiveTable ? String(item.hiveTable) : undefined,
		type: item.type ? String(item.type) : undefined,
		sourceId: item.sourceId ? String(item.sourceId) : undefined,
		lifecycleStatus: item.lifecycleStatus ? String(item.lifecycleStatus) : undefined,
		enabled: item.enabled !== false,
	};
}

function catalogDatasetOptions(response: any): CatalogDatasetOption[] {
	const content = Array.isArray(response?.content) ? response.content : [];
	return content.flatMap((item: any) => {
		const option = catalogDatasetOption(item);
		return option ? [option] : [];
	});
}

export type DesignFormValues = {
	taskName: string;
	description?: string;
	sourceDataSourceId?: string;
	sourceType: string;
	sourceConfigText: string;
	destinationType: string;
	destinationConfigText: string;
	targetDatasetId?: string;
	syncMode: string;
	syncSchedule?: string;
	tableMapping: Array<{ source: string; target: string }>;
	syncConfigText: string;
	postIngestionQualityEnabled: boolean;
};

const VALID_TABS: ReadonlyArray<OrchestrationTabKey> = ["design", "runs"];

function isValidTab(value: string | null): value is OrchestrationTabKey {
	return Boolean(value && (VALID_TABS as ReadonlyArray<string>).includes(value));
}

function parsePositiveId(value: string | null): number | null {
	if (!value) return null;
	const parsed = Number(value);
	return Number.isFinite(parsed) && parsed > 0 ? Math.floor(parsed) : null;
}

function stringifyJson(value: unknown): string {
	return JSON.stringify(value && typeof value === "object" ? value : {}, null, 2);
}

function parseJsonObject(value: string, label: string): Record<string, unknown> {
	try {
		const parsed = JSON.parse(value || "{}");
		if (!parsed || Array.isArray(parsed) || typeof parsed !== "object") {
			throw new Error(`${label}必须是 JSON 对象`);
		}
		return parsed as Record<string, unknown>;
	} catch (error: unknown) {
		if (error instanceof Error && error.message.endsWith("必须是 JSON 对象")) throw error;
		throw new Error(`${label}不是有效的 JSON`);
	}
}

function errorMessage(error: unknown, fallback: string): string {
	const candidate = error as {
		message?: string;
		response?: { data?: { message?: string; detail?: string; title?: string } };
	};
	return (
		candidate?.response?.data?.detail ||
		candidate?.response?.data?.message ||
		candidate?.response?.data?.title ||
		candidate?.message ||
		fallback
	);
}

function formValues(design: IngestionTaskDesign): DesignFormValues {
	return {
		taskName: design.taskName,
		description: design.description,
		sourceDataSourceId: design.source?.dataSourceId,
		sourceType: design.source?.type || "",
		sourceConfigText: stringifyJson(design.source?.config),
		destinationType: design.destination?.type || "",
		destinationConfigText: stringifyJson(design.destination?.config),
		targetDatasetId: design.destination?.assetRef?.datasetId,
		syncMode: design.syncMode || "full_refresh",
		syncSchedule: design.syncSchedule,
		tableMapping: Array.isArray(design.tableMapping)
			? design.tableMapping.map((item) => ({
					source: String(item.source || ""),
					target: String(item.target || ""),
				}))
			: [],
		syncConfigText: stringifyJson(design.syncConfig),
		postIngestionQualityEnabled: Boolean(design.postIngestionQuality?.enabled),
	};
}

export function designPayload(values: DesignFormValues): IngestionTaskDesignUpdate {
	const targetDatasetId = values.targetDatasetId?.trim() || undefined;
	return {
		taskName: values.taskName.trim(),
		description: values.description?.trim() || undefined,
		sourceDataSourceId: values.sourceDataSourceId,
		sourceType: values.sourceType.trim(),
		sourceConfig: parseJsonObject(values.sourceConfigText, "来源参数"),
		destinationType: values.destinationType.trim(),
		destinationConfig: parseJsonObject(values.destinationConfigText, "目标参数"),
		targetDatasetId,
		syncMode: values.syncMode,
		syncSchedule: values.syncSchedule?.trim() || undefined,
		tableMapping: (values.tableMapping || []).map((item) => ({
			source: item.source.trim(),
			target: item.target.trim(),
		})),
		syncConfig: parseJsonObject(values.syncConfigText, "同步参数"),
		postIngestionQualityEnabled: Boolean(values.postIngestionQualityEnabled),
		qualityPolicyRef: values.postIngestionQualityEnabled && targetDatasetId ? `dataset:${targetDatasetId}` : undefined,
	};
}

function statusColor(status?: string): string {
	switch ((status || "").toUpperCase()) {
		case "ACTIVE":
		case "READY":
		case "BOUND":
		case "CONFIGURED":
		case "ENABLED":
			return "green";
		case "DRAFT":
		case "PAUSED":
			return "gold";
		case "UNRESOLVED":
		case "INVALID":
			return "red";
		default:
			return "blue";
	}
}

function ReadonlyTopology({ topology }: { topology: IngestionTopologyProjection | null }) {
	if (!topology) return <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无可展示的流程拓扑" />;
	return (
		<div data-testid="readonly-topology" style={{ overflowX: "auto", padding: "12px 4px" }}>
			<div style={{ display: "flex", alignItems: "center", minWidth: 620 }}>
				{topology.nodes.map((node, index) => (
					<div key={node.id} style={{ display: "flex", alignItems: "center", flex: 1 }}>
						<div
							style={{
								minWidth: 112,
								padding: "12px 10px",
								border: "1px solid #d9e2ec",
								borderRadius: 8,
								background: "#f8fafc",
								textAlign: "center",
							}}
						>
							<div style={{ fontWeight: 600 }}>{node.label}</div>
							<Tag color={statusColor(node.state)} style={{ marginTop: 8, marginInlineEnd: 0 }}>
								{node.state}
							</Tag>
						</div>
						{index < topology.nodes.length - 1 ? (
							<div aria-hidden="true" style={{ flex: 1, textAlign: "center", color: "#64748b" }}>
								→
							</div>
						) : null}
					</div>
				))}
			</div>
		</div>
	);
}

export default function OrchestrationPage() {
	const [form] = Form.useForm<DesignFormValues>();
	const [searchParams, setSearchParams] = useSearchParams();
	const requestedTaskId = parsePositiveId(searchParams.get("taskId"));
	const requestedExecutionId = parsePositiveId(searchParams.get("executionId"));
	const requestedTab = searchParams.get("tab");
	const activeKey: OrchestrationTabKey = isValidTab(requestedTab) ? requestedTab : "design";

	const [tasks, setTasks] = useState<IngestionTaskDTO[]>([]);
	const [sources, setSources] = useState<InfraDataSource[]>([]);
	const [datasets, setDatasets] = useState<CatalogDatasetOption[]>([]);
	const [datasetSearching, setDatasetSearching] = useState(false);
	const [design, setDesign] = useState<IngestionTaskDesign | null>(null);
	const [topology, setTopology] = useState<IngestionTopologyProjection | null>(null);
	const [topologyView, setTopologyView] = useState<TopologyView>("DRAFT");
	const [validation, setValidation] = useState<IngestionValidationResult | null>(null);
	const [loading, setLoading] = useState(true);
	const [saving, setSaving] = useState(false);
	const [commandLoading, setCommandLoading] = useState<string | null>(null);
	const [dirty, setDirty] = useState(false);
	const hydratingRef = useRef(false);
	const datasetSearchTimerRef = useRef<number | null>(null);

	const updateQuery = useCallback(
		(values: Record<string, string | number | null | undefined>) => {
			setSearchParams(
				(current) => {
					const next = new URLSearchParams(current);
					Object.entries(values).forEach(([key, value]) => {
						if (value === null || value === undefined || value === "") next.delete(key);
						else next.set(key, String(value));
					});
					return next;
				},
				{ replace: true },
			);
		},
		[setSearchParams],
	);

	const loadReferenceData = useCallback(async () => {
		const [taskPage, sourceList, datasetResponse] = await Promise.all([
			ingestionTaskAPI.getTasks({ page: 0, size: 200, sort: "lastModifiedDate,desc" }),
			dataSourcesService.list(),
			listDatasets({ page: 0, size: 200, enabledOnly: true }) as Promise<any>,
		]);
		setTasks(taskPage.content || []);
		setSources(Array.isArray(sourceList) ? sourceList : []);
		setDatasets(catalogDatasetOptions(datasetResponse));
		return taskPage.content || [];
	}, []);

	const loadDesign = useCallback(
		async (taskId: number) => {
			setLoading(true);
			try {
				const next = await ingestionTaskAPI.getTaskDesign(taskId);
				setDesign(next);
				setTopology(next.topology || null);
				setTopologyView("DRAFT");
				setValidation(next.validation || null);
				hydratingRef.current = true;
				form.setFieldsValue(formValues(next));
				const selectedDatasetId = next.destination?.assetRef?.datasetId;
				if (selectedDatasetId) {
					void (getDataset(selectedDatasetId) as Promise<any>)
						.then((item) => {
							const selected = catalogDatasetOption(item);
							if (!selected) return;
							setDatasets((current) =>
								current.some((candidate) => candidate.id === selected.id) ? current : [selected, ...current],
							);
						})
						.catch(() => undefined);
				}
				setDirty(false);
				queueMicrotask(() => {
					hydratingRef.current = false;
				});
			} catch (error: unknown) {
				setDesign(null);
				setTopology(null);
				message.error(errorMessage(error, "任务设计加载失败"));
			} finally {
				setLoading(false);
			}
		},
		[form],
	);

	useEffect(() => {
		let cancelled = false;
		setLoading(true);
		void loadReferenceData()
			.then((loadedTasks) => {
				if (cancelled) return;
				const selected = requestedTaskId || loadedTasks[0]?.id || null;
				if (selected && selected !== requestedTaskId) {
					updateQuery({ taskId: selected });
					return;
				}
				if (selected) void loadDesign(selected);
				else setLoading(false);
			})
			.catch((error: unknown) => {
				if (!cancelled) {
					setLoading(false);
					message.error(errorMessage(error, "任务及资产基础数据加载失败"));
				}
			});
		return () => {
			cancelled = true;
		};
	}, [loadDesign, loadReferenceData, requestedTaskId, updateQuery]);

	useEffect(() => {
		const warn = (event: BeforeUnloadEvent) => {
			if (!dirty) return;
			event.preventDefault();
			event.returnValue = "";
		};
		window.addEventListener("beforeunload", warn);
		return () => window.removeEventListener("beforeunload", warn);
	}, [dirty]);

	useEffect(
		() => () => {
			if (datasetSearchTimerRef.current !== null) {
				window.clearTimeout(datasetSearchTimerRef.current);
			}
		},
		[],
	);

	const handleDatasetSearch = useCallback(
		(keyword: string) => {
			if (datasetSearchTimerRef.current !== null) {
				window.clearTimeout(datasetSearchTimerRef.current);
			}
			datasetSearchTimerRef.current = window.setTimeout(() => {
				setDatasetSearching(true);
				void (
					listDatasets({
						page: 0,
						size: keyword.trim() ? 50 : 200,
						enabledOnly: true,
						keyword: keyword.trim() || undefined,
					}) as Promise<any>
				)
					.then((response) => {
						const options = catalogDatasetOptions(response);
						setDatasets((current) => {
							const selectedId = form.getFieldValue("targetDatasetId");
							const selected = current.find((item) => item.id === selectedId);
							return selected && !options.some((item) => item.id === selected.id) ? [selected, ...options] : options;
						});
					})
					.catch((error: unknown) => message.error(errorMessage(error, "目标资产搜索失败")))
					.finally(() => setDatasetSearching(false));
			}, 300);
		},
		[form],
	);

	const payloadFromForm = useCallback(async () => {
		const values = await form.validateFields();
		return designPayload(values);
	}, [form]);

	const handleSave = async () => {
		if (!requestedTaskId || !design?.planChecksum) return;
		setSaving(true);
		try {
			const payload = await payloadFromForm();
			const saved = await ingestionTaskAPI.updateTaskDesign(requestedTaskId, payload, design.planChecksum);
			setDesign(saved);
			setTopology(saved.topology || null);
			setValidation(saved.validation || null);
			hydratingRef.current = true;
			form.setFieldsValue(formValues(saved));
			setDirty(false);
			queueMicrotask(() => {
				hydratingRef.current = false;
			});
			message.success("任务草稿已保存");
		} catch (error: unknown) {
			message.error(errorMessage(error, "任务草稿保存失败"));
		} finally {
			setSaving(false);
		}
	};

	const handleReset = () => {
		if (!design) return;
		hydratingRef.current = true;
		form.setFieldsValue(formValues(design));
		setTopology(design.topology || null);
		setTopologyView("DRAFT");
		setValidation(design.validation || null);
		setDirty(false);
		queueMicrotask(() => {
			hydratingRef.current = false;
		});
		message.success("已撤销本次未保存修改");
	};

	const handleValidate = async (): Promise<boolean> => {
		if (!requestedTaskId || !design) return false;
		try {
			const payload = await payloadFromForm();
			const result = await ingestionTaskAPI.validateTaskDesign(requestedTaskId, payload, design.planChecksum);
			setValidation(result);
			if (result.valid) message.success("校验通过，可以保存并发布");
			else message.warning(`发现 ${result.issues.length} 项待完善内容`);
			return result.valid;
		} catch (error: unknown) {
			message.error(errorMessage(error, "任务校验失败"));
			return false;
		}
	};

	const handlePublish = () => {
		if (!requestedTaskId || !design?.planChecksum) return;
		if (dirty) {
			message.warning("请先保存当前草稿，再发布版本");
			return;
		}
		Modal.confirm({
			title: "发布当前任务版本？",
			content: "发布后将形成新的可执行版本；已有运行实例仍保留原版本和质量证据。",
			okText: "确认发布",
			cancelText: "取消",
			onOk: async () => {
				setCommandLoading("publish");
				try {
					const valid = await handleValidate();
					if (!valid) throw new Error("任务校验未通过");
					await ingestionTaskAPI.admitTask(requestedTaskId, design.planChecksum);
					await Promise.all([loadDesign(requestedTaskId), loadReferenceData()]);
					message.success("任务版本已发布");
				} catch (error: unknown) {
					message.error(errorMessage(error, "任务版本发布失败"));
				} finally {
					setCommandLoading(null);
				}
			},
		});
	};

	const handleSchedule = async (command: "enable" | "pause") => {
		if (!requestedTaskId) return;
		setCommandLoading(command);
		try {
			await ingestionTaskAPI.setTaskSchedule(requestedTaskId, command);
			await Promise.all([loadDesign(requestedTaskId), loadReferenceData()]);
			message.success(command === "enable" ? "调度已启用" : "调度已暂停");
		} catch (error: unknown) {
			message.error(errorMessage(error, command === "enable" ? "启用调度失败" : "暂停调度失败"));
		} finally {
			setCommandLoading(null);
		}
	};

	const handleTopologyView = async (view: TopologyView) => {
		if (!requestedTaskId || view === topologyView) return;
		setTopologyView(view);
		try {
			setTopology(await ingestionTaskAPI.getTaskTopology(requestedTaskId, view));
		} catch (error: unknown) {
			message.error(errorMessage(error, "流程拓扑加载失败"));
			setTopologyView("DRAFT");
			setTopology(design?.topology || null);
		}
	};

	const handleTaskChange = (nextTaskId: number) => {
		if (dirty && !window.confirm("当前任务有未保存修改，确认切换任务吗？")) return;
		setDirty(false);
		updateQuery({ taskId: nextTaskId, executionId: null });
	};

	const watchedTargetDatasetId = Form.useWatch("targetDatasetId", form);
	const watchedSourceType = Form.useWatch("sourceType", form);
	const qualityEnabled = Form.useWatch("postIngestionQualityEnabled", form);
	const selectedDataset = useMemo(
		() => datasets.find((item) => item.id === watchedTargetDatasetId),
		[datasets, watchedTargetDatasetId],
	);
	const selectedDatasetMatchesDesign =
		Boolean(watchedTargetDatasetId) && watchedTargetDatasetId === design?.destination?.assetRef?.datasetId;
	const operationalState = (design?.operationalState || "").toLowerCase();
	const canPause = operationalState === "active";
	const canEnable = operationalState === "paused";
	const sourceCanOmitProfile = /file|excel|csv/i.test(watchedSourceType || "");
	const taskOptions = tasks
		.filter((task) => task.id)
		.map((task) => ({
			value: task.id as number,
			label: `${task.name}${task.revisionNumber ? ` · R${task.revisionNumber}` : ""}`,
		}));

	return (
		<div className="space-y-4">
			<PageHeader
				title="数据集成流程"
				actions={
					<Space wrap>
						<Button onClick={() => void handleValidate()} disabled={!requestedTaskId || activeKey !== "design"}>
							校验
						</Button>
						<Button
							type="primary"
							loading={saving}
							onClick={() => void handleSave()}
							disabled={!dirty || activeKey !== "design"}
						>
							保存草稿
						</Button>
						<Button onClick={handleReset} disabled={!dirty || activeKey !== "design"}>
							撤销未保存修改
						</Button>
						<Button
							loading={commandLoading === "publish"}
							onClick={handlePublish}
							disabled={!design || activeKey !== "design"}
						>
							发布版本
						</Button>
						<Button
							loading={commandLoading === "enable"}
							onClick={() => void handleSchedule("enable")}
							disabled={!canEnable}
						>
							启用调度
						</Button>
						<Button
							danger
							loading={commandLoading === "pause"}
							onClick={() => void handleSchedule("pause")}
							disabled={!canPause}
						>
							暂停调度
						</Button>
					</Space>
				}
			/>

			<Card size="small">
				<Row gutter={[16, 12]} align="middle">
					<Col xs={24} lg={10}>
						<Text type="secondary">接入任务</Text>
						<Select
							showSearch
							optionFilterProp="label"
							style={{ width: "100%", marginTop: 6 }}
							placeholder="请选择接入任务"
							value={requestedTaskId || undefined}
							options={taskOptions}
							onChange={handleTaskChange}
						/>
					</Col>
					<Col xs={24} lg={14}>
						<Space wrap>
							<Tag color={statusColor(design?.revisionState)}>{design?.revisionState || "未形成版本"}</Tag>
							<Tag color={statusColor(design?.operationalState)}>{design?.operationalState || "状态未知"}</Tag>
							<Text>版本：{design?.revisionNumber ? `R${design.revisionNumber}` : "-"}</Text>
							<Tooltip title={design?.planChecksum}>
								<Text type="secondary">计划校验值：{design?.planChecksum?.slice(0, 12) || "-"}</Text>
							</Tooltip>
							{dirty ? <Tag color="orange">有未保存修改</Tag> : null}
						</Space>
					</Col>
				</Row>
			</Card>

			<Tabs
				activeKey={activeKey}
				onChange={(key) => {
					if (isValidTab(key)) updateQuery({ tab: key, executionId: key === "runs" ? requestedExecutionId : null });
				}}
				items={[
					{
						key: "design",
						label: "任务设计",
						children: (
							<Spin spinning={loading}>
								{requestedTaskId && design ? (
									<Form<DesignFormValues>
										form={form}
										layout="vertical"
										onValuesChange={() => {
											if (!hydratingRef.current) setDirty(true);
										}}
									>
										<Row gutter={[16, 16]}>
											<Col xs={24} xl={15}>
												<Space direction="vertical" size={16} style={{ width: "100%" }}>
													<Card title="1. 任务与来源" size="small">
														<Row gutter={16}>
															<Col xs={24} md={12}>
																<Form.Item
																	name="taskName"
																	label="任务名称"
																	rules={[{ required: true, message: "请输入任务名称" }]}
																>
																	<Input maxLength={200} />
																</Form.Item>
															</Col>
															<Col xs={24} md={12}>
																<Form.Item
																	name="sourceDataSourceId"
																	label="来源数据源"
																	rules={[{ required: !sourceCanOmitProfile, message: "请选择来源数据源" }]}
																>
																	<Select
																		showSearch
																		optionFilterProp="label"
																		options={sources.map((source) => ({
																			value: source.id,
																			label: `${source.name} · ${source.type}`,
																		}))}
																	/>
																</Form.Item>
															</Col>
														</Row>
														<Form.Item name="description" label="业务说明">
															<Input.TextArea rows={2} maxLength={2000} showCount />
														</Form.Item>
														<Form.Item
															name="sourceType"
															label="来源连接器类型"
															rules={[{ required: true, message: "请输入来源连接器类型" }]}
														>
															<Input maxLength={50} />
														</Form.Item>
														<Form.Item name="sourceConfigText" label="来源参数（JSON）" rules={[{ required: true }]}>
															<Input.TextArea rows={6} spellCheck={false} />
														</Form.Item>
													</Card>

													<Card title="2. 目标资产与表映射" size="small">
														<Row gutter={16}>
															<Col xs={24} md={12}>
																<Form.Item
																	name="destinationType"
																	label="目标连接器类型"
																	rules={[{ required: true, message: "请输入目标连接器类型" }]}
																>
																	<Input maxLength={50} />
																</Form.Item>
															</Col>
															<Col xs={24} md={12}>
																<Form.Item
																	name="targetDatasetId"
																	label="目标数据资产"
																	rules={[{ required: true, message: "请选择唯一的目标数据资产" }]}
																>
																	<Select
																		showSearch
																		filterOption={false}
																		loading={datasetSearching}
																		onSearch={handleDatasetSearch}
																		placeholder="请选择 Catalog 中已登记的数据集"
																		options={datasets.map((dataset) => ({
																			value: dataset.id,
																			label: `${dataset.name}${dataset.hiveTable ? ` · ${dataset.hiveDatabase || "-"}.${dataset.hiveTable}` : ""} · ${dataset.type || "DATASET"} · ${dataset.id.slice(-8)}`,
																		}))}
																	/>
																</Form.Item>
															</Col>
														</Row>
														{selectedDataset ? (
															<Alert
																type="info"
																showIcon
																message={`已关联资产：${selectedDataset.name}`}
																description={`物理对象：${selectedDataset.hiveDatabase || "-"}.${selectedDataset.hiveTable || "-"}；平台将在保存、校验和发布时核对物理目标。`}
															/>
														) : null}
														<Form.Item
															name="destinationConfigText"
															label="目标参数（JSON）"
															rules={[{ required: true }]}
															style={{ marginTop: 16 }}
														>
															<Input.TextArea rows={6} spellCheck={false} />
														</Form.Item>
														<Form.List name="tableMapping">
															{(fields, { add, remove }) => (
																<Space direction="vertical" style={{ width: "100%" }}>
																	{fields.map((field) => (
																		<Row gutter={8} key={field.key} align="middle">
																			<Col span={10}>
																				<Form.Item
																					{...field}
																					name={[field.name, "source"]}
																					rules={[{ required: true, message: "请输入来源表" }]}
																					style={{ marginBottom: 8 }}
																				>
																					<Input placeholder="来源表，如 schema.table" />
																				</Form.Item>
																			</Col>
																			<Col span={10}>
																				<Form.Item
																					{...field}
																					name={[field.name, "target"]}
																					rules={[{ required: true, message: "请输入目标表" }]}
																					style={{ marginBottom: 8 }}
																				>
																					<Input placeholder="目标表，如 ods.table" />
																				</Form.Item>
																			</Col>
																			<Col span={4}>
																				<Button danger type="link" onClick={() => remove(field.name)}>
																					移除
																				</Button>
																			</Col>
																		</Row>
																	))}
																	<Button block type="dashed" onClick={() => add({ source: "", target: "" })}>
																		新增表映射
																	</Button>
																</Space>
															)}
														</Form.List>
													</Card>

													<Card title="3. 同步与调度" size="small">
														<Row gutter={16}>
															<Col xs={24} md={10}>
																<Form.Item name="syncMode" label="同步方式" rules={[{ required: true }]}>
																	<Select
																		options={[
																			{ value: "full_refresh", label: "全量刷新" },
																			{ value: "incremental", label: "增量同步" },
																		]}
																	/>
																</Form.Item>
															</Col>
															<Col xs={24} md={14}>
																<Form.Item name="syncSchedule" label="调度表达式">
																	<Input placeholder="例如：0 0 2 * * *" />
																</Form.Item>
															</Col>
														</Row>
														<Form.Item name="syncConfigText" label="同步参数（JSON）" rules={[{ required: true }]}>
															<Input.TextArea rows={5} spellCheck={false} />
														</Form.Item>
													</Card>

													<Card title="4. 接入后质量验证" size="small">
														<Form.Item name="postIngestionQualityEnabled" label="运行方式" valuePropName="checked">
															<Switch
																checkedChildren="接入成功后执行"
																unCheckedChildren="暂不执行"
																disabled={!watchedTargetDatasetId}
															/>
														</Form.Item>
														<Alert
															type={qualityEnabled ? "info" : "warning"}
															showIcon
															message={
																qualityEnabled
																	? "质量策略将在数据写入成功后运行"
																	: "目标资产仍会登记，但不会形成可信验证证据"
															}
															description={
																qualityEnabled
																	? `已发布质量规则：${selectedDatasetMatchesDesign ? (design.assetProjection?.qualityBindingCount ?? "校验时确认") : "校验时确认"}；质量未通过不会把资产标记为可信可用。`
																	: "接入过程负责落地数据；质量模块在接入成功后验证，并将结果投影到资产可消费状态。"
															}
														/>
													</Card>
												</Space>
											</Col>

											<Col xs={24} xl={9}>
												<Space direction="vertical" size={16} style={{ width: "100%" }}>
													<Card
														title="只读执行拓扑"
														extra={
															<Segmented
																value={topologyView}
																options={[
																	{ label: "草稿", value: "DRAFT" },
																	{ label: "已发布", value: "ACTIVE" },
																]}
																onChange={(value) => void handleTopologyView(value as TopologyView)}
															/>
														}
													>
														<ReadonlyTopology topology={topology} />
														<Paragraph type="secondary" style={{ marginBottom: 0 }}>
															拓扑由任务版本自动生成，仅用于核对“读取、写入、资产、质量、完成”链路，不支持自由拖拽或绕过版本发布。
														</Paragraph>
													</Card>

													<Card title="发布前检查" size="small">
														{validation?.valid ? (
															<Alert type="success" showIcon message="当前设计校验通过" />
														) : (
															<Alert
																type="warning"
																showIcon
																message={`仍有 ${validation?.issues?.length || 0} 项待完善`}
																description={
																	<ul style={{ margin: "8px 0 0", paddingLeft: 20 }}>
																		{(validation?.issues || []).map((issue) => (
																			<li key={`${issue.code}-${issue.field}`}>{issue.message}</li>
																		))}
																	</ul>
																}
															/>
														)}
														{design.legacyDsl?.present ? (
															<Alert
																style={{ marginTop: 12 }}
																type="info"
																showIcon
																message={design.legacyDsl.message || "历史画布仅供兼容查看"}
															/>
														) : null}
													</Card>
												</Space>
											</Col>
										</Row>
									</Form>
								) : (
									<Empty description={tasks.length ? "请选择接入任务" : "暂无可编排的接入任务"} />
								)}
							</Spin>
						),
					},
					{
						key: "runs",
						label: "运行实例",
						children: (
							<OrchestrationRunsTab
								key={requestedTaskId ?? "no-task"}
								taskId={requestedTaskId}
								executionId={requestedExecutionId}
								onExecutionSelect={(executionId) => updateQuery({ tab: "runs", executionId })}
							/>
						),
					},
				]}
				destroyInactiveTabPane={false}
			/>
		</div>
	);
}
