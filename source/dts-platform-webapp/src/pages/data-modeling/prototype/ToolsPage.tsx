import { FileArchive, GitBranch, ListChecks, ScrollText } from "lucide-react";
import { useNavigate } from "react-router";
import {
	type DataModelingToolWorkflow,
	getDataModelingToolWorkflows,
} from "@/features/modeling/navigation/dataModelingToolWorkflows";
import type { DataModelingRoute } from "../types";
import { Button, PageHeader, RequestState, Status } from "./PrototypePrimitives";

const iconFor = (workflow: DataModelingToolWorkflow) => {
	if (workflow.key.includes("dbt") || workflow.key.includes("import")) return FileArchive;
	if (workflow.key.includes("lineage")) return GitBranch;
	if (workflow.key.includes("audit")) return ScrollText;
	return ListChecks;
};

export function ToolsPage({ route }: { route: DataModelingRoute }) {
	const navigate = useNavigate();
	const workflows =
		route.view === "exports" ? [] : getDataModelingToolWorkflows(route.view === "imports" ? "import" : undefined);
	return (
		<main className="dmx-page dmx-tools-page">
			<PageHeader description={route.description} title={route.title} trail="数据建模 / 通用工具" />
			{workflows.length ? (
				<section className="dmx-tool-grid">
					{workflows.map((workflow) => {
						const Icon = iconFor(workflow);
						return (
							<article key={workflow.key}>
								<span>
									<Icon size={20} />
								</span>
								<div>
									<h2>{workflow.title}</h2>
									<p>{workflow.description}</p>
									<small>{workflow.owner}</small>
								</div>
								<div className="dmx-tool-card__footer">
									<Status tone="success">真实流程</Status>
									<Button onClick={() => navigate(workflow.path)}>进入流程</Button>
								</div>
							</article>
						);
					})}
				</section>
			) : (
				<RequestState
					description="为避免生成不可追踪文件，原型中的无 owner 导出与辅助生成按钮不进入生产页面。"
					kind="empty"
					title="当前没有归属明确的建模导出流程"
				/>
			)}
			<section className="dmx-panel dmx-tools-records">
				<header>
					<h2>执行与审计边界</h2>
				</header>
				<p>上传、预检、应用、发布和审计结果由目标流程保存并展示；本页不创建统一工具运行台账，也不拼接模拟历史。</p>
			</section>
		</main>
	);
}
