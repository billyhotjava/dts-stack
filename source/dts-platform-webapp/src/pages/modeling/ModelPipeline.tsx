import { useCallback, useState } from "react";
import { Button, Tag, Tooltip, Modal, Input } from "antd";
import {
	CheckCircleOutlined,
	CloseCircleOutlined,
	LoadingOutlined,
	CodeOutlined,
	ExperimentOutlined,
	CloudUploadOutlined,
	RocketOutlined,
} from "@ant-design/icons";
import { toast } from "sonner";
import {
	commitDbtChanges,
	triggerDbtTest,
	triggerDbtRun,
	syncDbtModels,
} from "@/api/platformApi";

type StepStatus = "wait" | "process" | "finish" | "error";

interface PipelineStep {
	key: string;
	title: string;
	icon: React.ReactNode;
	status: StepStatus;
	description?: string;
}

interface ModelPipelineProps {
	modelStatus?: string;
	modelSelector?: string;
	disabled?: boolean;
	onStatusChange?: (newStatus: string) => void;
	onRefresh?: () => void;
	userName?: string;
	userEmail?: string;
}

const STATUS_TO_STEP: Record<string, number> = {
	DRAFT: 0,
	COMMITTED: 1,
	TESTED: 2,
	PUBLISHED: 3,
};

export default function ModelPipeline({
	modelStatus,
	modelSelector,
	disabled,
	onStatusChange,
	onRefresh,
	userName,
	userEmail,
}: ModelPipelineProps) {
	const [running, setRunning] = useState<string | null>(null);
	const [commitModalOpen, setCommitModalOpen] = useState(false);
	const [commitMessage, setCommitMessage] = useState("");

	const currentStep = STATUS_TO_STEP[modelStatus || "DRAFT"] ?? 0;

	const resolveStepStatus = (stepIndex: number): StepStatus => {
		if (running) {
			const runningStep = ["compile", "test", "commit", "publish"].indexOf(running);
			if (stepIndex === runningStep) return "process";
			if (stepIndex < runningStep) return "finish";
			return "wait";
		}
		if (stepIndex < currentStep) return "finish";
		if (stepIndex === currentStep) return "process";
		return "wait";
	};

	const handleCompile = useCallback(async () => {
		if (!modelSelector) {
			toast.warning("请先选择模型");
			return;
		}
		setRunning("compile");
		try {
			await triggerDbtRun({ operation: "compile", models: modelSelector });
			toast.success("编译完成");
		} catch (err: any) {
			toast.error(err?.message || "编译失败");
		} finally {
			setRunning(null);
			onRefresh?.();
		}
	}, [modelSelector, onRefresh]);

	const handleTest = useCallback(async () => {
		if (!modelSelector) {
			toast.warning("请先选择模型");
			return;
		}
		setRunning("test");
		try {
			await triggerDbtTest({ models: modelSelector });
			toast.success("测试已触发");
			onStatusChange?.("TESTED");
		} catch (err: any) {
			toast.error(err?.message || "测试失败");
		} finally {
			setRunning(null);
			onRefresh?.();
		}
	}, [modelSelector, onStatusChange, onRefresh]);

	const handleCommitOpen = useCallback(() => {
		setCommitMessage("");
		setCommitModalOpen(true);
	}, []);

	const handleCommitConfirm = useCallback(async () => {
		const message = commitMessage.trim() || "提交模型变更";
		setCommitModalOpen(false);
		setRunning("commit");
		try {
			await commitDbtChanges({
				message,
				authorName: userName || "DTS Platform",
				authorEmail: userEmail || "dts@localhost",
			});
			toast.success("提交成功");
			onStatusChange?.("COMMITTED");
		} catch (err: any) {
			toast.error(err?.message || "提交失败");
		} finally {
			setRunning(null);
			onRefresh?.();
		}
	}, [commitMessage, userName, userEmail, onStatusChange, onRefresh]);

	const handlePublish = useCallback(async () => {
		if (!modelSelector) {
			toast.warning("请先选择模型");
			return;
		}
		setRunning("publish");
		try {
			await triggerDbtRun({ operation: "run", models: modelSelector });
			toast.success("发布执行已触发，模型同步中...");
			try {
				await syncDbtModels();
			} catch {
				// auto-sync best effort
			}
			onStatusChange?.("PUBLISHED");
		} catch (err: any) {
			toast.error(err?.message || "发布失败");
		} finally {
			setRunning(null);
			onRefresh?.();
		}
	}, [modelSelector, onStatusChange, onRefresh]);

	const steps: PipelineStep[] = [
		{
			key: "compile",
			title: "编译",
			icon: <CodeOutlined />,
			status: resolveStepStatus(0),
			description: currentStep > 0 ? "已通过" : undefined,
		},
		{
			key: "test",
			title: "测试",
			icon: <ExperimentOutlined />,
			status: resolveStepStatus(1),
			description: currentStep > 1 ? "已通过" : undefined,
		},
		{
			key: "commit",
			title: "提交变更",
			icon: <CloudUploadOutlined />,
			status: resolveStepStatus(2),
			description: currentStep > 2 ? "已提交" : undefined,
		},
		{
			key: "publish",
			title: "发布上线",
			icon: <RocketOutlined />,
			status: resolveStepStatus(3),
			description: currentStep >= 3 ? "已发布" : undefined,
		},
	];

	const statusIcon = (status: StepStatus) => {
		switch (status) {
			case "finish": return <CheckCircleOutlined className="text-green-500" />;
			case "process": return running ? <LoadingOutlined className="text-blue-500" /> : null;
			case "error": return <CloseCircleOutlined className="text-red-500" />;
			default: return null;
		}
	};

	return (
		<>
			<div className="flex items-center gap-1 px-4 py-2 border-b border-border bg-muted/30">
				<span className="text-xs font-medium text-muted-foreground mr-2">流水线</span>
				{steps.map((step, idx) => (
					<div key={step.key} className="flex items-center">
						{idx > 0 && <div className="w-6 h-px bg-border mx-1" />}
						<Tooltip title={step.description || step.title}>
							<Button
								size="small"
								type={step.status === "finish" ? "link" : step.status === "process" ? "primary" : "default"}
								icon={statusIcon(step.status) || step.icon}
								loading={running === step.key}
								disabled={disabled || (running != null && running !== step.key)}
								onClick={() => {
									switch (step.key) {
										case "compile": handleCompile(); break;
										case "test": handleTest(); break;
										case "commit": handleCommitOpen(); break;
										case "publish": handlePublish(); break;
									}
								}}
								className="text-xs"
							>
								{step.title}
							</Button>
						</Tooltip>
					</div>
				))}
				{modelStatus && (
					<Tag
						color={
							modelStatus === "PUBLISHED" ? "green"
								: modelStatus === "TESTED" ? "orange"
									: modelStatus === "COMMITTED" ? "blue"
										: "default"
						}
						className="ml-auto text-xs"
					>
						{modelStatus === "PUBLISHED" ? "已发布"
							: modelStatus === "TESTED" ? "已测试"
								: modelStatus === "COMMITTED" ? "已提交"
									: "草稿"}
					</Tag>
				)}
			</div>

			<Modal
				title="提交变更"
				open={commitModalOpen}
				onOk={handleCommitConfirm}
				onCancel={() => setCommitModalOpen(false)}
				okText="提交"
				cancelText="取消"
			>
				<Input.TextArea
					rows={3}
					placeholder="请输入提交说明（可选）"
					value={commitMessage}
					onChange={(e) => setCommitMessage(e.target.value)}
				/>
			</Modal>
		</>
	);
}
