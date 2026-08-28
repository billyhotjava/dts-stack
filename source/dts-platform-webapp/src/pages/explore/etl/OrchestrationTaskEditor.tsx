import { Button, Card, Col, Form, Modal, message, Row, Space, Tabs, Tag, Tooltip, Typography } from "antd";
import { useCallback, useEffect, useRef, useState } from "react";
import {
	type IngestionTaskDesign,
	type IngestionTopologyProjection,
	type IngestionValidationResult,
	ingestionTaskAPI,
} from "@/api/ingestion";
import { getDataset, listDatasets } from "@/api/platformApi";
import dataSourcesService, { type InfraDataSource } from "@/api/services/dataSourcesService";
import { PageHeader } from "@/components/page-header";
import OrchestrationDesignPanel, { type TopologyView } from "./OrchestrationDesignPanel";
import OrchestrationRunsTab from "./OrchestrationRunsTab";
import {
	type CatalogDatasetOption,
	catalogDatasetOption,
	catalogDatasetOptions,
	type DesignFormValues,
	designPayload,
	formValues,
} from "./orchestrationDesignModel";

const { Text } = Typography;

type OrchestrationTabKey = "design" | "runs";

type Props = {
	taskId: number;
	executionId: number | null;
	activeKey: OrchestrationTabKey;
	onQuery: (values: Record<string, string | number | null | undefined>) => void;
	onBack: () => void;
};

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

export default function OrchestrationTaskEditor({ taskId, executionId, activeKey, onQuery, onBack }: Props) {
	const [form] = Form.useForm<DesignFormValues>();
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

	const hydrateDesign = useCallback(
		(next: IngestionTaskDesign) => {
			setDesign(next);
			setTopology(next.topology || null);
			setTopologyView("DRAFT");
			setValidation(next.validation || null);
			hydratingRef.current = true;
			form.setFieldsValue(formValues(next));
			setDirty(false);
			queueMicrotask(() => {
				hydratingRef.current = false;
			});
		},
		[form],
	);

	const loadReferenceData = useCallback(async () => {
		const [sourceList, datasetResponse] = await Promise.all([
			dataSourcesService.list(),
			listDatasets({ page: 0, size: 200, enabledOnly: true }) as Promise<any>,
		]);
		setSources(Array.isArray(sourceList) ? sourceList : []);
		setDatasets(catalogDatasetOptions(datasetResponse));
	}, []);

	const loadDesign = useCallback(async () => {
		setLoading(true);
		try {
			const next = await ingestionTaskAPI.getTaskDesign(taskId);
			hydrateDesign(next);
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
		} catch (error: unknown) {
			setDesign(null);
			setTopology(null);
			message.error(errorMessage(error, "任务设计加载失败"));
		} finally {
			setLoading(false);
		}
	}, [hydrateDesign, taskId]);

	useEffect(() => {
		let cancelled = false;
		void Promise.all([loadReferenceData(), loadDesign()]).catch((error: unknown) => {
			if (!cancelled) message.error(errorMessage(error, "任务及资产基础数据加载失败"));
		});
		return () => {
			cancelled = true;
		};
	}, [loadDesign, loadReferenceData]);

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
			if (datasetSearchTimerRef.current !== null) window.clearTimeout(datasetSearchTimerRef.current);
		},
		[],
	);

	const handleDatasetSearch = useCallback(
		(keyword: string) => {
			if (datasetSearchTimerRef.current !== null) window.clearTimeout(datasetSearchTimerRef.current);
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

	const payloadFromForm = useCallback(async () => designPayload(await form.validateFields()), [form]);

	const handleSave = async () => {
		if (!design?.planChecksum) return;
		setSaving(true);
		try {
			const saved = await ingestionTaskAPI.updateTaskDesign(taskId, await payloadFromForm(), design.planChecksum);
			hydrateDesign(saved);
			message.success("任务草稿已保存");
		} catch (error: unknown) {
			message.error(errorMessage(error, "任务草稿保存失败"));
		} finally {
			setSaving(false);
		}
	};

	const handleReset = () => {
		if (!design) return;
		hydrateDesign(design);
		message.success("已撤销本次未保存修改");
	};

	const handleValidate = async (): Promise<boolean> => {
		if (!design) return false;
		try {
			const result = await ingestionTaskAPI.validateTaskDesign(taskId, await payloadFromForm(), design.planChecksum);
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
		if (!design?.planChecksum) return;
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
					if (!(await handleValidate())) throw new Error("任务校验未通过");
					await ingestionTaskAPI.admitTask(taskId, design.planChecksum);
					await Promise.all([loadDesign(), loadReferenceData()]);
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
		setCommandLoading(command);
		try {
			await ingestionTaskAPI.setTaskSchedule(taskId, command);
			await Promise.all([loadDesign(), loadReferenceData()]);
			message.success(command === "enable" ? "调度已启用" : "调度已暂停");
		} catch (error: unknown) {
			message.error(errorMessage(error, command === "enable" ? "启用调度失败" : "暂停调度失败"));
		} finally {
			setCommandLoading(null);
		}
	};

	const handleTopologyView = async (view: TopologyView) => {
		if (view === topologyView) return;
		setTopologyView(view);
		try {
			setTopology(await ingestionTaskAPI.getTaskTopology(taskId, view));
		} catch (error: unknown) {
			message.error(errorMessage(error, "流程拓扑加载失败"));
			setTopologyView("DRAFT");
			setTopology(design?.topology || null);
		}
	};

	const handleBack = () => {
		if (dirty && !window.confirm("当前任务有未保存修改，确认返回任务列表吗？")) return;
		setDirty(false);
		onBack();
	};

	const operationalState = (design?.operationalState || "").toLowerCase();
	const canPause = operationalState === "active";
	const canEnable = operationalState === "paused";

	return (
		<div className="space-y-4">
			<PageHeader
				title={design?.taskName ? `任务编排 · ${design.taskName}` : "任务编排"}
				actions={
					<Space wrap>
						<Button onClick={handleBack}>返回任务列表</Button>
						<Button onClick={() => void handleValidate()} disabled={activeKey !== "design"}>
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
						<Text type="secondary">当前编排任务</Text>
						<div style={{ marginTop: 6 }}>
							<Text strong>{design?.taskName || `任务 #${taskId}`}</Text>
							<Text type="secondary">{` · #${taskId}`}</Text>
						</div>
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
				onChange={(key) => onQuery({ tab: key, executionId: key === "runs" ? executionId : null })}
				items={[
					{
						key: "design",
						label: "任务设计",
						children: (
							<OrchestrationDesignPanel
								form={form}
								design={design}
								topology={topology}
								topologyView={topologyView}
								validation={validation}
								sources={sources}
								datasets={datasets}
								datasetSearching={datasetSearching}
								loading={loading}
								onDatasetSearch={handleDatasetSearch}
								onTopologyView={(view) => void handleTopologyView(view)}
								onDirty={() => {
									if (!hydratingRef.current) setDirty(true);
								}}
							/>
						),
					},
					{
						key: "runs",
						label: "运行实例",
						children: (
							<OrchestrationRunsTab
								taskId={taskId}
								taskName={design?.taskName}
								revisionNumber={design?.revisionNumber}
								executionId={executionId}
								onExecutionSelect={(nextExecutionId) => onQuery({ tab: "runs", executionId: nextExecutionId })}
							/>
						),
					},
				]}
				destroyInactiveTabPane={false}
			/>
		</div>
	);
}
