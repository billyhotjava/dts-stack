import { Tag } from "antd";
import {
	CheckCircleOutlined,
	CodeOutlined,
	ExperimentOutlined,
	CloudUploadOutlined,
	RocketOutlined,
} from "@ant-design/icons";
import { createModelPipelineSteps } from "./modelPipeline.helpers";

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
}

const STATUS_TO_STEP: Record<string, number> = {
	DRAFT: 0,
	COMMITTED: 1,
	TESTED: 2,
	PUBLISHED: 3,
};

export default function ModelPipeline({
	modelStatus,
}: ModelPipelineProps) {
	const currentStep = STATUS_TO_STEP[modelStatus || "DRAFT"] ?? 0;

	const resolveStepStatus = (stepIndex: number): StepStatus => {
		if (stepIndex < currentStep) return "finish";
		if (stepIndex === currentStep) return "process";
		return "wait";
	};
	const stepIcons: Record<string, React.ReactNode> = {
		compile: <CodeOutlined />,
		test: <ExperimentOutlined />,
		commit: <CloudUploadOutlined />,
		publish: <RocketOutlined />,
	};

	const steps: PipelineStep[] = createModelPipelineSteps().map((step, index) => ({
		key: step.key,
		title: step.title,
		icon: stepIcons[step.key],
		status: resolveStepStatus(index),
		description:
			index === 0 && currentStep > 0 ? "已通过"
				: index === 1 && currentStep > 1 ? "已通过"
					: index === 2 && currentStep > 2 ? "已提交"
						: index === 3 && currentStep >= 3 ? "已发布"
							: undefined,
	}));

	const statusIcon = (status: StepStatus) => {
		switch (status) {
			case "finish": return <CheckCircleOutlined className="text-green-500" />;
			default: return null;
		}
	};

	const stepClassName = (status: StepStatus) =>
		status === "finish"
			? "border-green-200 bg-green-50 text-green-700"
			: status === "process"
				? "border-blue-200 bg-blue-50 text-blue-700"
				: "border-border bg-background text-muted-foreground";

	return (
		<div className="flex items-center gap-1 border-b border-border bg-muted/30 px-4 py-2">
			<span className="mr-2 text-xs font-medium text-muted-foreground">流水线</span>
			{steps.map((step, idx) => (
				<div key={step.key} className="flex items-center">
					{idx > 0 && <div className="mx-1 h-px w-6 bg-border" />}
					<div
						className={`inline-flex items-center gap-1 rounded-full border px-2.5 py-1 text-xs ${stepClassName(step.status)}`}
						title={step.description || `${step.title}状态`}
					>
						{statusIcon(step.status) || step.icon}
						<span>{step.title}</span>
					</div>
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
	);
}
