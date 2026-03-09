import { useState } from "react";
import { Modal, Alert, Tag, List, Spin, message } from "antd";
import {
	ExclamationCircleOutlined,
	DeleteOutlined,
	WarningOutlined,
} from "@ant-design/icons";
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
	cascadeTaskIds: number[];
	warnings: string[];
};

export type RollbackResult = {
	success: boolean;
	actions: string[];
	errors: string[];
	dbtFullRefreshNeeded: boolean;
	affectedTaskIds: number[];
};

const LEVEL_LABELS: Record<number, { text: string; color: string }> = {
	1: { text: "Level 1 — 清空数据", color: "orange" },
	2: { text: "Level 2 — 重建表结构", color: "red" },
	3: { text: "Level 3 — 全链路回退", color: "#f5222d" },
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
	const [step, setStep] = useState<"analyze" | "confirm">("analyze");

	const doAnalyze = async () => {
		if (!request) return;
		setAnalyzing(true);
		try {
			const resp: any = await rollbackAnalyze(request);
			const data = resp?.data || resp;
			setImpact(data);
			setStep("confirm");
		} catch {
			// global interceptor handles error
		} finally {
			setAnalyzing(false);
		}
	};

	const doExecute = async () => {
		if (!request) return;
		setExecuting(true);
		try {
			const resp: any = await rollbackExecute(request);
			const result: RollbackResult = resp?.data || resp;
			if (result.success) {
				message.success("回退操作执行成功");
			} else {
				message.warning("回退操作部分完成，存在错误");
			}
			onSuccess?.(result);
			handleClose();
		} catch {
			// global interceptor handles error
		} finally {
			setExecuting(false);
		}
	};

	const handleClose = () => {
		setImpact(null);
		setStep("analyze");
		setAnalyzing(false);
		setExecuting(false);
		onClose();
	};

	const levelInfo = LEVEL_LABELS[request?.level ?? 0] ?? { text: "未知级别", color: "default" };

	const afterOpenChange = (visible: boolean) => {
		if (visible && request && step === "analyze") {
			doAnalyze();
		}
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
			okText={step === "analyze" ? "分析中..." : "确认执行回退"}
			okButtonProps={{
				danger: true,
				loading: executing,
				disabled: analyzing || !impact,
				icon: <DeleteOutlined />,
			}}
			onOk={doExecute}
			cancelText="取消"
		>
			{analyzing && (
				<div style={{ textAlign: "center", padding: 32 }}>
					<Spin tip="正在分析影响范围..." />
				</div>
			)}

			{!analyzing && impact && (
				<div style={{ display: "flex", flexDirection: "column", gap: 16 }}>
					<Alert
						type="warning"
						showIcon
						icon={<WarningOutlined />}
						message={
							<span>
								即将执行 <Tag color={levelInfo.color}>{levelInfo.text}</Tag> 操作，
								范围: <Tag>{impact.scope === "task" ? "单任务" : "数据源级"}</Tag>
							</span>
						}
					/>

					{impact.warnings.length > 0 && (
						<Alert
							type="error"
							showIcon
							message="注意事项"
							description={
								<ul style={{ margin: 0, paddingLeft: 20 }}>
									{impact.warnings.map((w, i) => (
										<li key={i}>{w}</li>
									))}
								</ul>
							}
						/>
					)}

					{impact.affectedTables.length > 0 && (
						<div>
							<strong>受影响的表 ({impact.affectedTables.length})</strong>
							<List
								size="small"
								bordered
								dataSource={impact.affectedTables}
								renderItem={(t) => <List.Item>{t}</List.Item>}
								style={{ maxHeight: 150, overflow: "auto", marginTop: 4 }}
							/>
						</div>
					)}

					{impact.cascadeTaskIds.length > 0 && (
						<div>
							<strong>受影响的任务 ID:</strong>{" "}
							{impact.cascadeTaskIds.map((id) => (
								<Tag key={id}>{id}</Tag>
							))}
						</div>
					)}

					{impact.executionRecords > 0 && (
						<div>
							<strong>执行记录:</strong> {impact.executionRecords} 条将被清除
						</div>
					)}

					{impact.uploadFiles.length > 0 && (
						<div>
							<strong>上传文件:</strong>
							<List
								size="small"
								bordered
								dataSource={impact.uploadFiles}
								renderItem={(f) => <List.Item>{f}</List.Item>}
								style={{ maxHeight: 100, overflow: "auto", marginTop: 4 }}
							/>
						</div>
					)}
				</div>
			)}
		</Modal>
	);
}
