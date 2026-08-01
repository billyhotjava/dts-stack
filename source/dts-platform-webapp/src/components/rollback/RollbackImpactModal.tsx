import { DeleteOutlined, ExclamationCircleOutlined, WarningOutlined } from "@ant-design/icons";
import { Alert, Checkbox, Input, List, Modal, message, Spin, Tag } from "antd";
import { useMemo, useRef, useState } from "react";
import { rollbackAnalyze, rollbackExecute } from "@/api/platformApi";

export type RollbackRequest = {
	level: number;
	scope: string;
	taskId?: number;
	dataSourceId?: string;
	tables?: string[];
	rebuildDbt?: boolean;
};

export type RollbackImpact = {
	level: number;
	scope: string;
	taskId?: number;
	dataSourceId?: string;
	affectedTables: string[];
	affectedOdsMappings: string[];
	affectedModels: string[];
	affectedDbtFiles: string[];
	affectedDatasets: number;
	uploadFiles: string[];
	executionRecords: number;
	confirmationType: string;
	confirmationToken: string;
	confirmationText?: string;
	cascadeTaskIds: number[];
	warnings: string[];
};

export type RollbackResult = {
	success: boolean;
	actions: string[];
	errors: string[];
	dbtFullRefreshNeeded: boolean;
	affectedTaskIds: number[];
	state?: string;
	succeeded?: string[];
	failedStep?: string;
	manualRecoveryRequired?: boolean;
};

const LEVEL_LABELS: Record<number, { text: string; color: string }> = {
	1: { text: "Level 1 — 清空数据", color: "orange" },
	2: { text: "Level 2 — 重建表结构", color: "red" },
	3: { text: "Level 3 — 全链路回退", color: "#f5222d" },
};

const SUPPORTED_CONFIRMATION_TYPES = new Set(["MODAL", "TYPE_TEXT"]);

const asRecord = (value: unknown): Record<string, unknown> | null =>
	value && typeof value === "object" && !Array.isArray(value) ? (value as Record<string, unknown>) : null;

const stringList = (value: unknown): string[] =>
	Array.isArray(value) ? [...new Set(value.filter((item): item is string => typeof item === "string"))] : [];

const numberList = (value: unknown): number[] =>
	Array.isArray(value)
		? value.map(Number).filter((item): item is number => Number.isSafeInteger(item) && item > 0)
		: [];

const normalizeImpact = (value: unknown): RollbackImpact | null => {
	const record = asRecord(value);
	if (!record) return null;
	const level = Number(record.level);
	const scope = typeof record.scope === "string" ? record.scope : "";
	const confirmationType = typeof record.confirmationType === "string" ? record.confirmationType : "";
	const confirmationToken = typeof record.confirmationToken === "string" ? record.confirmationToken : "";
	const confirmationText = typeof record.confirmationText === "string" ? record.confirmationText : undefined;
	if (
		!Number.isSafeInteger(level) ||
		level < 1 ||
		!scope.trim() ||
		!confirmationType.trim() ||
		!confirmationToken.trim()
	) {
		return null;
	}
	return {
		level,
		scope,
		taskId: record.taskId == null ? undefined : Number(record.taskId),
		dataSourceId: typeof record.dataSourceId === "string" ? record.dataSourceId : undefined,
		affectedTables: stringList(record.affectedTables),
		affectedOdsMappings: stringList(record.affectedOdsMappings),
		affectedModels: stringList(record.affectedModels),
		affectedDbtFiles: stringList(record.affectedDbtFiles),
		affectedDatasets: Number.isFinite(Number(record.affectedDatasets)) ? Number(record.affectedDatasets) : 0,
		uploadFiles: stringList(record.uploadFiles),
		executionRecords: Number.isFinite(Number(record.executionRecords)) ? Number(record.executionRecords) : 0,
		confirmationType,
		confirmationToken,
		confirmationText,
		cascadeTaskIds: numberList(record.cascadeTaskIds),
		warnings: stringList(record.warnings),
	};
};

const matchesRequest = (impact: RollbackImpact, request: RollbackRequest) => {
	if (impact.level !== request.level || impact.scope !== request.scope) return false;
	if (request.scope === "task") return impact.taskId === request.taskId;
	if (request.scope === "datasource") return impact.dataSourceId === request.dataSourceId;
	return false;
};

export const normalizeRollbackResult = (value: unknown): RollbackResult | null => {
	const envelope = asRecord(value);
	if (!envelope) return null;
	const data = asRecord(envelope.data) || envelope;
	const state = String(data.state || envelope.code || "")
		.trim()
		.toUpperCase();
	if (state === "PARTIAL_FAILED" || Number(envelope.status) === 207) {
		const succeeded = stringList(data.succeeded);
		const rollbackResult = asRecord(data.rollbackResult);
		return {
			success: false,
			actions: succeeded.length ? succeeded : stringList(rollbackResult?.actions),
			errors: stringList(rollbackResult?.errors),
			dbtFullRefreshNeeded: Boolean(rollbackResult?.dbtFullRefreshNeeded),
			affectedTaskIds: numberList(rollbackResult?.affectedTaskIds),
			state: "PARTIAL_FAILED",
			succeeded,
			failedStep: typeof data.failedStep === "string" ? data.failedStep : undefined,
			manualRecoveryRequired: data.manualRecoveryRequired === true,
		};
	}
	if (typeof data.success !== "boolean") return null;
	return {
		success: data.success,
		actions: stringList(data.actions),
		errors: stringList(data.errors),
		dbtFullRefreshNeeded: Boolean(data.dbtFullRefreshNeeded),
		affectedTaskIds: numberList(data.affectedTaskIds),
	};
};

type Props = {
	open: boolean;
	request: RollbackRequest | null;
	onClose: () => void;
	onSuccess?: (result: RollbackResult) => void;
};

export default function RollbackImpactModal({ open, request, onClose, onSuccess }: Props) {
	const [impact, setImpact] = useState<RollbackImpact | null>(null);
	const [analyzing, setAnalyzing] = useState(false);
	const [executing, setExecuting] = useState(false);
	const [analyzeError, setAnalyzeError] = useState("");
	const [acknowledged, setAcknowledged] = useState(false);
	const [confirmationInput, setConfirmationInput] = useState("");
	const [confirmationConsumed, setConfirmationConsumed] = useState(false);
	const [executionResult, setExecutionResult] = useState<RollbackResult | null>(null);
	const analyzeRequestIdRef = useRef(0);

	const confirmationType = useMemo(() => impact?.confirmationType.trim().toUpperCase() || "", [impact]);
	const confirmationReady = Boolean(
		!confirmationConsumed &&
			impact?.confirmationToken.trim() &&
			((confirmationType === "MODAL" && acknowledged) ||
				(confirmationType === "TYPE_TEXT" && confirmationInput === impact.confirmationText)),
	);

	const doAnalyze = async () => {
		if (!request) return;
		const currentRequest = { ...request };
		const requestId = ++analyzeRequestIdRef.current;
		setAnalyzing(true);
		setAnalyzeError("");
		setImpact(null);
		setAcknowledged(false);
		setConfirmationInput("");
		setConfirmationConsumed(false);
		setExecutionResult(null);
		try {
			const payload: any = await rollbackAnalyze(currentRequest);
			if (analyzeRequestIdRef.current !== requestId) return;
			const nextImpact = normalizeImpact(payload?.data || payload);
			if (!nextImpact || !matchesRequest(nextImpact, currentRequest)) {
				setAnalyzeError("回退确认凭据无效，无法执行回退。");
				return;
			}
			const nextType = nextImpact.confirmationType.trim().toUpperCase();
			if (!SUPPORTED_CONFIRMATION_TYPES.has(nextType)) {
				setAnalyzeError("暂不支持当前回退确认方式，无法执行回退。");
				return;
			}
			if (nextType === "TYPE_TEXT" && !nextImpact.confirmationText?.trim()) {
				setAnalyzeError("回退确认凭据无效，无法执行回退。");
				return;
			}
			setImpact(nextImpact);
		} catch {
			if (analyzeRequestIdRef.current === requestId) {
				setAnalyzeError("回退影响分析失败，请稍后重试。");
			}
		} finally {
			if (analyzeRequestIdRef.current === requestId) setAnalyzing(false);
		}
	};

	const doExecute = async () => {
		if (!request || !impact || !confirmationReady || confirmationConsumed || !matchesRequest(impact, request)) return;
		setConfirmationConsumed(true);
		setExecuting(true);
		try {
			const executeRequest = {
				...request,
				confirmationType: impact.confirmationType,
				confirmationToken: impact.confirmationToken,
				...(confirmationType === "TYPE_TEXT" ? { confirmationText: confirmationInput } : {}),
			};
			const payload: any = await rollbackExecute(executeRequest);
			const result = normalizeRollbackResult(payload);
			if (!result) {
				message.error("回退执行结果无效；确认令牌已提交，请刷新任务和审计记录核对状态。");
				return;
			}
			setExecutionResult(result);
			if (result.state === "PARTIAL_FAILED") {
				message.warning("回退操作部分完成，请按页面提示人工处置失败步骤");
				onSuccess?.(result);
				return;
			}
			if (result.success) {
				message.success("回退操作执行成功");
			} else {
				message.warning("回退操作部分完成，请到审计记录核对结果");
			}
			onSuccess?.(result);
			handleClose();
		} catch {
			message.error("回退请求结果未知；确认令牌已提交，禁止再次执行整个回退，请核对审计记录。");
		} finally {
			setExecuting(false);
		}
	};

	const handleClose = () => {
		analyzeRequestIdRef.current += 1;
		setImpact(null);
		setAnalyzeError("");
		setAcknowledged(false);
		setConfirmationInput("");
		setConfirmationConsumed(false);
		setExecutionResult(null);
		setAnalyzing(false);
		setExecuting(false);
		onClose();
	};

	const levelInfo = LEVEL_LABELS[request?.level ?? 0] ?? { text: "未知级别", color: "default" };

	const afterOpenChange = (visible: boolean) => {
		if (visible && request) void doAnalyze();
	};

	return (
		<Modal
			title={
				<span>
					<ExclamationCircleOutlined style={{ color: "#faad14", marginRight: 8 }} />
					回退影响分析
				</span>
			}
			open={open}
			onCancel={handleClose}
			afterOpenChange={afterOpenChange}
			width={640}
			okText={
				analyzing
					? "分析中..."
					: executionResult?.state === "PARTIAL_FAILED"
						? "已部分完成"
						: confirmationConsumed
							? "已提交，禁止重复执行"
							: impact
								? "确认执行回退"
								: "无法执行回退"
			}
			okButtonProps={{
				danger: true,
				loading: executing,
				disabled: !confirmationReady || confirmationConsumed || analyzing || executing,
				icon: <DeleteOutlined />,
			}}
			cancelButtonProps={{ disabled: executing }}
			closable={!executing}
			maskClosable={!executing}
			onOk={doExecute}
			cancelText="取消"
		>
			{analyzing ? (
				<div style={{ textAlign: "center", padding: 32 }}>
					<Spin tip="正在分析影响范围..." />
				</div>
			) : null}

			{!analyzing && analyzeError ? <Alert type="error" showIcon message={analyzeError} /> : null}

			{executionResult?.state === "PARTIAL_FAILED" ? (
				<Alert
					type="warning"
					showIcon
					message="回退部分完成"
					description={
						<ul style={{ margin: 0, paddingLeft: 20 }}>
							<li>已完成步骤：{executionResult.succeeded?.join("、") || "后端未返回明细"}</li>
							<li>失败步骤：{executionResult.failedStep || "未记录"}</li>
							<li>需要人工恢复：{executionResult.manualRecoveryRequired ? "是" : "否"}</li>
							<li>确认令牌已消费，禁止再次执行整个回退。</li>
						</ul>
					}
				/>
			) : null}

			{!analyzing && impact ? (
				<div style={{ display: "flex", flexDirection: "column", gap: 16 }}>
					<Alert
						type="warning"
						showIcon
						icon={<WarningOutlined />}
						message={
							<span>
								即将执行 <Tag color={levelInfo.color}>{levelInfo.text}</Tag> 操作，范围:
								<Tag>{impact.scope === "task" ? "单任务" : "数据源级"}</Tag>
							</span>
						}
					/>

					{impact.warnings.length > 0 ? (
						<Alert
							type="error"
							showIcon
							message="注意事项"
							description={
								<ul style={{ margin: 0, paddingLeft: 20 }}>
									{impact.warnings.map((warning) => (
										<li key={warning}>{warning}</li>
									))}
								</ul>
							}
						/>
					) : null}

					{impact.affectedTables.length > 0 ? (
						<div>
							<strong>受影响的表 ({impact.affectedTables.length})</strong>
							<List
								size="small"
								bordered
								dataSource={impact.affectedTables}
								renderItem={(table) => <List.Item>{table}</List.Item>}
								style={{ maxHeight: 150, overflow: "auto", marginTop: 4 }}
							/>
						</div>
					) : null}

					{impact.cascadeTaskIds.length > 0 ? (
						<div>
							<strong>受影响的任务 ID:</strong>{" "}
							{impact.cascadeTaskIds.map((id) => (
								<Tag key={id}>{id}</Tag>
							))}
						</div>
					) : null}

					{impact.executionRecords > 0 ? (
						<div>
							<strong>执行记录:</strong> {impact.executionRecords} 条将被清除
						</div>
					) : null}

					{impact.uploadFiles.length > 0 ? (
						<div>
							<strong>上传文件:</strong>
							<List
								size="small"
								bordered
								dataSource={impact.uploadFiles}
								renderItem={(file) => <List.Item>{file}</List.Item>}
								style={{ maxHeight: 100, overflow: "auto", marginTop: 4 }}
							/>
						</div>
					) : null}

					{confirmationType === "MODAL" ? (
						<Checkbox checked={acknowledged} onChange={(event) => setAcknowledged(event.target.checked)}>
							我已核对以上影响范围，并确认执行不可逆回退
						</Checkbox>
					) : null}

					{confirmationType === "TYPE_TEXT" ? (
						<div>
							<Alert
								type="error"
								showIcon
								message="需要输入确认文本"
								description={
									<span>
										请完整输入：<code>{impact.confirmationText}</code>
									</span>
								}
							/>
							<Input
								className="mt-3"
								value={confirmationInput}
								onChange={(event) => setConfirmationInput(event.target.value)}
								placeholder="严格按上方文本输入"
								autoComplete="off"
							/>
						</div>
					) : null}
				</div>
			) : null}
		</Modal>
	);
}
