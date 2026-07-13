import { Alert, Button, Space, Tag, Typography } from "antd";
import { ArrowRight, FolderOpen, GitBranch } from "lucide-react";
import { useNavigate } from "react-router";
import type { BusinessModelingContext } from "../businessModelingContext";
import { buildBusinessModelingRoute } from "../businessModelingContext";

const { Text } = Typography;

export function BusinessModelingContextBar({
	context,
	processRequired = true,
}: {
	context: BusinessModelingContext;
	processRequired?: boolean;
}) {
	const navigate = useNavigate();
	const processLabel = context.processName || context.processId || "未选择业务过程";
	const processReady = Boolean(context.processId);
	const projectLabel = context.projectSpaceId ? `项目空间：${context.projectSpaceId}` : "项目空间：未启用（默认上下文）";
	const processRoute = buildBusinessModelingRoute("/governance/subjects?focus=business-processes", context);

	return (
		<div className="space-y-2" data-testid="business-modeling-context-bar">
			<div className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-slate-200 bg-slate-50 px-3 py-2">
				<Space wrap size="small">
					<Tag color={processReady ? "blue" : "orange"} icon={<GitBranch size={13} />}>
						业务过程：{processLabel}
					</Tag>
					<Tag color={context.projectSpaceId ? "purple" : "default"} icon={<FolderOpen size={13} />}>
						{projectLabel}
					</Tag>
					{context.warehouseLayer ? <Text type="secondary">数仓层：{context.warehouseLayer}</Text> : null}
				</Space>
				<Space size="small">
					<Button size="small" onClick={() => navigate(processRoute)}>
						业务过程目录 <ArrowRight size={14} />
					</Button>
					<Button size="small" onClick={() => navigate("/studio/projects")}>
						项目空间（可选）
					</Button>
				</Space>
			</div>
			{processRequired && !processReady ? (
				<Alert
					showIcon
					type="warning"
					message="请先选择业务过程"
					description="业务对象和模型台账按业务过程隔离；不启用项目空间不影响正常建模。"
					action={<Button size="small" onClick={() => navigate(processRoute)}>进入业务过程目录</Button>}
				/>
			) : null}
		</div>
	);
}
