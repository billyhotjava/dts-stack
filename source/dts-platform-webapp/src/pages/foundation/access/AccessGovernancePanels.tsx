import { Alert, Button, Card, Descriptions, Input, Modal, message, Select, Space, Switch, Tag, Typography } from "antd";
import type { ColumnsType, TablePaginationConfig } from "antd/es/table";
import { useCallback, useEffect, useMemo, useState } from "react";
import {
	type IngestionExecutionDTO,
	type IngestionStagingRow,
	type IngestionTaskDTO,
	ingestionTaskAPI,
	type StagingErrorSummary,
} from "@/api/ingestion";
import { CompactTable } from "@/components/table";
import { useRouter } from "@/routes/hooks";
import { inferAccessKind } from "./accessPlanPayload";

const { Text } = Typography;

export const qualityDatasetIdFromRef = (value?: string) => {
	const normalized = String(value || "").trim();
	const match = /^dataset:([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})$/i.exec(normalized);
	return match?.[1];
};

export const resolveQualityPolicyRef = (
	task: Pick<IngestionTaskDTO, "qualityPolicyRef">,
	latestExecution: Pick<IngestionExecutionDTO, "qualityPolicyRef" | "qualityRunId"> | null,
) =>
	latestExecution?.qualityRunId
		? latestExecution.qualityPolicyRef
		: task.qualityPolicyRef || latestExecution?.qualityPolicyRef;

export const qualityRoute = (path: "/governance/rules" | "/governance/quality", datasetId?: string, runId?: string) => {
	if (runId) return `/governance/rules/runs/${encodeURIComponent(runId)}`;
	const params = new URLSearchParams();
	if (datasetId) params.set("datasetId", datasetId);
	const query = params.toString();
	return query ? `${path}?${query}` : path;
};

export function AccessQualityPanel({
	task,
	latestExecution,
	onTaskChanged,
}: {
	task: IngestionTaskDTO;
	latestExecution: IngestionExecutionDTO | null;
	onTaskChanged?: () => void | Promise<void>;
}) {
	const router = useRouter();
	const kind = inferAccessKind(task);
	const datasetId = qualityDatasetIdFromRef(resolveQualityPolicyRef(task, latestExecution));
	const runId = latestExecution?.qualityRunId;
	const stage = kind === "file" ? "可选文件质量检测" : "同步批次写入后检查";
	const taskId = task.id;
	const [operation, setOperation] = useState<"parse" | "check" | "recheck" | "drop" | "update" | null>(null);
	const [hasStaging, setHasStaging] = useState(Boolean(task.stagingTableName));
	const [stagingRows, setStagingRows] = useState<IngestionStagingRow[]>([]);
	const [stagingSummary, setStagingSummary] = useState<StagingErrorSummary>();
	const [stagingLoading, setStagingLoading] = useState(false);
	const [errorsOnly, setErrorsOnly] = useState(false);
	const [stagingPage, setStagingPage] = useState({ current: 1, pageSize: 10, total: 0 });
	const [editingRow, setEditingRow] = useState<IngestionStagingRow>();
	const [editingColumn, setEditingColumn] = useState<string>();
	const [editingValue, setEditingValue] = useState("");

	useEffect(() => {
		setHasStaging(Boolean(task.stagingTableName));
	}, [task.stagingTableName]);

	const refreshTask = useCallback(async () => {
		await onTaskChanged?.();
	}, [onTaskChanged]);

	const loadStaging = useCallback(async () => {
		if (!taskId || !hasStaging) {
			setStagingRows([]);
			setStagingSummary(undefined);
			setStagingPage((current) => ({ ...current, total: 0 }));
			return;
		}
		setStagingLoading(true);
		try {
			const [page, summary] = await Promise.all([
				ingestionTaskAPI.getStagingRows(taskId, {
					errorsOnly,
					page: stagingPage.current - 1,
					size: stagingPage.pageSize,
					sort: "_row_num,asc",
				}),
				ingestionTaskAPI.getStagingErrorSummary(taskId),
			]);
			setStagingRows(Array.isArray(page?.content) ? page.content : []);
			setStagingSummary(summary);
			setStagingPage((current) => ({ ...current, total: Number(page?.totalElements || 0) }));
		} catch {
			setStagingRows([]);
			setStagingSummary(undefined);
		} finally {
			setStagingLoading(false);
		}
	}, [errorsOnly, hasStaging, stagingPage.current, stagingPage.pageSize, taskId]);

	useEffect(() => {
		if (kind === "file" && hasStaging) void loadStaging();
	}, [hasStaging, kind, loadStaging]);

	const runOperation = async (next: Exclude<typeof operation, "update">) => {
		if (!taskId || !next) return;
		setOperation(next);
		try {
			if (next === "parse") {
				const result = await ingestionTaskAPI.parseStagingFile(taskId);
				setHasStaging(true);
				message.success(`已生成预检暂存区，共 ${result.totalRows || 0} 行`);
			} else if (next === "check") {
				const result = await ingestionTaskAPI.preCheckStaging(taskId);
				if (result.status === "PASSED") {
					message.success("文件质量检测通过");
				} else message.warning(`文件预检未通过，异常 ${result.failedRows || 0} 行`);
			} else if (next === "recheck") {
				const result = await ingestionTaskAPI.reCheckStaging(taskId);
				if (result.status === "PASSED") {
					message.success("重新检测通过");
				} else message.warning(`重新检查仍有 ${result.failedRows || 0} 行异常`);
			} else {
				await ingestionTaskAPI.dropStaging(taskId);
				setHasStaging(false);
				setStagingRows([]);
				setStagingSummary(undefined);
				message.success("预检暂存区已清理");
			}
			await refreshTask();
			if (next !== "drop") await loadStaging();
		} finally {
			setOperation(null);
		}
	};

	const businessColumns = useMemo(
		() => Array.from(new Set(stagingRows.flatMap((row) => Object.keys(row).filter((name) => !name.startsWith("_"))))),
		[stagingRows],
	);

	const stagingColumns: ColumnsType<IngestionStagingRow> = [
		{ title: "行号", dataIndex: "_row_num", key: "_row_num", fixed: "left", width: 80 },
		{
			title: "状态",
			dataIndex: "_status",
			key: "_status",
			fixed: "left",
			width: 90,
			render: (value) => (
				<Tag color={String(value).toUpperCase() === "ERROR" ? "error" : "success"}>{String(value || "CLEAN")}</Tag>
			),
		},
		...businessColumns.map((name) => ({
			title: name,
			dataIndex: name,
			key: name,
			width: 180,
			ellipsis: true,
			render: (value: unknown) =>
				value == null ? "—" : typeof value === "object" ? JSON.stringify(value) : String(value),
		})),
		{
			title: "问题",
			dataIndex: "_errors",
			key: "_errors",
			width: 260,
			ellipsis: true,
			render: (value) => (value == null ? "—" : typeof value === "string" ? value : JSON.stringify(value)),
		},
		{
			title: "操作",
			key: "actions",
			fixed: "right",
			width: 90,
			render: (_, row) => (
				<Button
					type="link"
					disabled={!businessColumns.length}
					onClick={() => {
						const column = businessColumns[0];
						setEditingRow(row);
						setEditingColumn(column);
						setEditingValue(row[column] == null ? "" : String(row[column]));
					}}
				>
					修正
				</Button>
			),
		},
	];

	const submitCellUpdate = async () => {
		const rowNum = Number(editingRow?._row_num);
		if (!taskId || !Number.isInteger(rowNum) || !editingColumn) return;
		setOperation("update");
		try {
			await ingestionTaskAPI.updateStagingCell(taskId, rowNum, { column: editingColumn, value: editingValue });
			message.success("暂存数据已修正，请重新检查");
			setEditingRow(undefined);
			await refreshTask();
			await loadStaging();
		} finally {
			setOperation(null);
		}
	};

	return (
		<div style={{ display: "grid", gap: 16 }}>
			<Card title={kind === "file" ? "文件质量检测（可选）" : "异常数据与运行后检查"}>
				<Descriptions bordered size="small" column={{ xs: 1, md: 2 }}>
					<Descriptions.Item label="检查阶段">{stage}</Descriptions.Item>
					<Descriptions.Item label="绑定状态">
						<Tag color={datasetId ? "success" : "warning"}>{datasetId ? "已冻结" : "未绑定"}</Tag>
					</Descriptions.Item>
					<Descriptions.Item label="质量数据集">{datasetId || "未记录"}</Descriptions.Item>
					<Descriptions.Item label="最近质量运行">{runId || "尚无运行"}</Descriptions.Item>
					{kind === "file" ? (
						<Descriptions.Item label="质量检测状态" span={2}>
							{task.preCheckStatus || "尚未执行"}
						</Descriptions.Item>
					) : null}
				</Descriptions>
				{datasetId ? (
					<Text type="secondary" style={{ display: "block", marginTop: 12 }}>
						任务运行时按该数据集已发布的规则绑定触发检查，不在接入侧复制规则。
					</Text>
				) : (
					<Alert
						style={{ marginTop: 12 }}
						type="info"
						showIcon
						message="质量规则未配置（可选）"
						description={
							kind === "file"
								? "质量检测为可选项，不影响接入计划保存、生效和执行；如需质量证据，可为目标数据集发布规则后再执行检测。"
								: "任务可以运行，但当前配置不会产生正式质量运行。请为目标数据集发布规则绑定后重新保存计划。"
						}
					/>
				)}
				<Space wrap style={{ marginTop: 16 }}>
					<Button onClick={() => router.push(qualityRoute("/governance/rules", datasetId))}>配置数据质量规则</Button>
					<Button
						type="primary"
						disabled={!datasetId && !runId}
						onClick={() => router.push(qualityRoute("/governance/quality", datasetId, runId))}
					>
						查看质量运行
					</Button>
				</Space>
			</Card>
			{kind === "file" ? (
				<Card title="质量检测暂存区（可选）">
					<Space wrap style={{ marginBottom: 16 }}>
						<Button
							type="primary"
							disabled={!taskId || operation !== null}
							loading={operation === "parse"}
							onClick={() => void runOperation("parse")}
						>
							{hasStaging ? "重新解析文件" : "生成检测暂存区"}
						</Button>
						<Button
							disabled={!hasStaging || operation !== null}
							loading={operation === "check"}
							onClick={() => void runOperation("check")}
						>
							执行质量检测
						</Button>
						<Button
							disabled={!hasStaging || operation !== null}
							loading={operation === "recheck"}
							onClick={() => void runOperation("recheck")}
						>
							重新检查
						</Button>
						<Button
							danger
							disabled={!hasStaging || operation !== null}
							loading={operation === "drop"}
							onClick={() =>
								Modal.confirm({
									title: "清理预检暂存区？",
									content: "暂存修正和预检结果将一并清除，源文件制品不会删除。",
									okType: "danger",
									onOk: () => runOperation("drop"),
								})
							}
						>
							清理暂存区
						</Button>
						<Space size={4}>
							<Text type="secondary">仅看异常行</Text>
							<Switch
								checked={errorsOnly}
								disabled={!hasStaging}
								onChange={(checked) => {
									setErrorsOnly(checked);
									setStagingPage((current) => ({ ...current, current: 1 }));
								}}
							/>
						</Space>
					</Space>
					{hasStaging ? (
						<>
							<Descriptions size="small" bordered column={{ xs: 1, md: 3 }} style={{ marginBottom: 16 }}>
								<Descriptions.Item label="总行数">{stagingSummary?.totalRows ?? stagingPage.total}</Descriptions.Item>
								<Descriptions.Item label="正常行">{stagingSummary?.cleanRows ?? "—"}</Descriptions.Item>
								<Descriptions.Item label="异常行">{stagingSummary?.errorRows ?? "—"}</Descriptions.Item>
							</Descriptions>
							<CompactTable<IngestionStagingRow>
								rowKey={(row) => String(row._row_num)}
								loading={stagingLoading}
								dataSource={stagingRows}
								columns={stagingColumns}
								pagination={{ ...stagingPage }}
								onChange={(next: TablePaginationConfig) =>
									setStagingPage((current) => ({
										current: next.current || 1,
										pageSize: next.pageSize || current.pageSize,
										total: current.total,
									}))
								}
							/>
						</>
					) : (
						<Alert
							type="info"
							showIcon
							message="尚未生成质量检测暂存区"
							description="质量检测为可选项；如需检测，可解析文件并调用数据质量模块中已发布的规则。"
						/>
					)}
				</Card>
			) : null}
			<Modal
				title={`修正暂存行 #${editingRow?._row_num || ""}`}
				open={Boolean(editingRow)}
				confirmLoading={operation === "update"}
				onCancel={() => setEditingRow(undefined)}
				onOk={() => void submitCellUpdate()}
				okText="保存修正"
			>
				<Space direction="vertical" style={{ width: "100%" }}>
					<Select
						value={editingColumn}
						options={businessColumns.map((column) => ({ value: column, label: column }))}
						onChange={(column) => {
							setEditingColumn(column);
							setEditingValue(editingRow?.[column] == null ? "" : String(editingRow?.[column]));
						}}
						style={{ width: "100%" }}
					/>
					<Input.TextArea
						value={editingValue}
						onChange={(event) => setEditingValue(event.target.value)}
						autoSize={{ minRows: 3, maxRows: 8 }}
					/>
				</Space>
			</Modal>
		</div>
	);
}
