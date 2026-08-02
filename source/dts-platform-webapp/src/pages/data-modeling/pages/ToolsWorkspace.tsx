import {
	ArchiveRestore,
	ArrowRight,
	FileArchive,
	GitBranch,
	ListChecks,
	type LucideIcon,
	ScrollText,
} from "lucide-react";
import { useNavigate } from "react-router";
import {
	type DataModelingToolWorkflow,
	type DataModelingToolWorkflowKey,
	getDataModelingToolWorkflows,
} from "@/features/modeling/navigation/dataModelingToolWorkflows";
import { ActionButton, EmptyState, Panel, StatusTag, WorkspacePage } from "../components/WorkspacePage";
import type { WorkspacePageProps } from "../types";
import "./tools-graphs.css";

const WORKFLOW_ICONS: Record<DataModelingToolWorkflowKey, LucideIcon> = {
	"dbt-zip-import": FileArchive,
	"standard-package-import": ArchiveRestore,
	"standard-code-governance": ListChecks,
	"lineage-import": GitBranch,
	"model-release-gates": ListChecks,
	"audit-evidence": ScrollText,
};

function WorkflowCards({ workflows }: { workflows: DataModelingToolWorkflow[] }) {
	const navigate = useNavigate();

	return (
		<div className="dm-card-list dm-tool-grid">
			{workflows.map((workflow) => {
				const Icon = WORKFLOW_ICONS[workflow.key];
				return (
					<article className="dm-card dm-tool-card" key={workflow.key}>
						<div className="dm-tool-card__icon">
							<Icon aria-hidden="true" size={21} />
						</div>
						<h3>{workflow.title}</h3>
						<p>{workflow.description}</p>
						<div className="dm-card__footer">
							<div>
								<StatusTag tone="success">真实流程</StatusTag>
								<small> {workflow.owner}</small>
							</div>
							<ActionButton onClick={() => navigate(workflow.path)}>
								进入流程 <ArrowRight aria-hidden="true" size={14} />
							</ActionButton>
						</div>
					</article>
				);
			})}
		</div>
	);
}

function Toolbox() {
	const workflows = getDataModelingToolWorkflows();

	return (
		<>
			<WorkflowCards workflows={workflows} />
			<Panel title="执行与审计边界" subtitle="没有统一的工具运行台账，也不会在前端拼接模拟历史。">
				<p>
					上传、预检、应用、发布和审计结果均由目标流程保存并展示。进入目标流程后，由其服务端执行权限校验、状态恢复和审计留痕。
				</p>
			</Panel>
		</>
	);
}

function ImportWorkflows() {
	return (
		<Panel
			subtitle="导入能力按 canonical owner 分组；运行状态、失败原因和重试入口保留在各自流程。"
			title="真实导入流程"
		>
			<WorkflowCards workflows={getDataModelingToolWorkflows("import")} />
			<div className="dm-stage-notice" role="note">
				没有统一的工具运行台账。请进入对应流程查看真实运行结果，避免跨 owner 合成不一致状态。
			</div>
		</Panel>
	);
}

function ExportBoundary() {
	return (
		<Panel title="建模导出边界">
			<EmptyState
				description="为避免生成不可追踪文件，原无 owner 的导出和辅助生成按钮已移除。后续只有在明确 canonical owner、权限和审计契约后才会提供入口。"
				title="当前没有归属明确的建模导出流程"
			/>
		</Panel>
	);
}

export function ToolsWorkspace({ route }: WorkspacePageProps) {
	return (
		<WorkspacePage description={route.description} eyebrow="数据建模 / 通用工具" title={route.title}>
			{route.view === "toolbox" ? <Toolbox /> : null}
			{route.view === "imports" ? <ImportWorkflows /> : null}
			{route.view === "exports" ? <ExportBoundary /> : null}
		</WorkspacePage>
	);
}
