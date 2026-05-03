import { useEffect, useState } from "react";
import { Alert, Button, Card, Col, Progress, Row, Space, Statistic, Steps, Tag, Typography } from "antd";
import {
	BranchesOutlined,
	DashboardOutlined,
	DatabaseOutlined,
	FunctionOutlined,
	ProjectOutlined,
	TableOutlined,
} from "@ant-design/icons";
import { PageHeader } from "@/components/page-header";
import {
	getSemanticWorkbenchOverview,
	listSemanticBusinessObjects,
	listSemanticMetrics,
	listSemanticSubjectDomains,
	type SemanticBusinessObject,
	type SemanticMetric,
	type SemanticSubjectDomain,
	type SemanticWorkbenchOverview,
	type SemanticWorkbenchStep,
} from "@/api/semanticModelingApi";
import { useRouter } from "@/routes/hooks";
import { SemanticSectionNav } from "./SemanticSectionNav";
import { asArray, semanticSectionMeta, type SemanticModelingSection } from "./semanticModelingShared";

const { Paragraph } = Typography;

const stepStatusColor = (status?: string) => {
	const value = String(status || "").toUpperCase();
	if (value === "READY") return "green";
	if (value === "PARTIAL" || value === "WARN") return "orange";
	if (value === "EMPTY") return "default";
	return "blue";
};

const sectionCards: Record<SemanticModelingSection, { title: string; path: string; description: string }> = {
	overview: {
		title: semanticSectionMeta.overview.title,
		path: semanticSectionMeta.overview.path,
		description: "",
	},
	subjects: {
		title: semanticSectionMeta.subjects.title,
		path: semanticSectionMeta.subjects.path,
		description: "把治理中心主题域作为优先依据，但允许开发工程师自建主题域。",
	},
	objects: {
		title: semanticSectionMeta.objects.title,
		path: semanticSectionMeta.objects.path,
		description: "从 DWD 明细模型配置业务对象和多表 Join。",
	},
	metrics: {
		title: semanticSectionMeta.metrics.title,
		path: semanticSectionMeta.metrics.path,
		description: "用拖拽方式定义维度、指标和业务口径。",
	},
	models: {
		title: semanticSectionMeta.models.title,
		path: semanticSectionMeta.models.path,
		description: "把指标组合成 DWS 公共汇总模型或 ADS 应用数据集。",
	},
	publish: {
		title: semanticSectionMeta.publish.title,
		path: semanticSectionMeta.publish.path,
		description: "审核后发布到 dbt、BI 数据集、API，并写入血缘。",
	},
	runs: {
		title: semanticSectionMeta.runs.title,
		path: semanticSectionMeta.runs.path,
		description: "查看 dbt/调度运行状态，形成可运维闭环。",
	},
};

export default function SemanticOverviewPage() {
	const router = useRouter();
	const [domains, setDomains] = useState<SemanticSubjectDomain[]>([]);
	const [objects, setObjects] = useState<SemanticBusinessObject[]>([]);
	const [metrics, setMetrics] = useState<SemanticMetric[]>([]);
	const [workbench, setWorkbench] = useState<SemanticWorkbenchOverview | null>(null);
	const [loading, setLoading] = useState(false);

	useEffect(() => {
		let active = true;
		setLoading(true);
		Promise.all([
			getSemanticWorkbenchOverview().catch(() => null),
			listSemanticSubjectDomains().catch(() => []),
			listSemanticBusinessObjects().catch(() => []),
			listSemanticMetrics().catch(() => []),
		]).then(([workbenchPayload, domainPayload, objectPayload, metricPayload]) => {
			if (!active) return;
			setWorkbench(workbenchPayload);
			setDomains(asArray<SemanticSubjectDomain>(domainPayload));
			setObjects(asArray<SemanticBusinessObject>(objectPayload));
			setMetrics(asArray<SemanticMetric>(metricPayload));
		}).finally(() => {
			if (active) setLoading(false);
		});
		return () => {
			active = false;
		};
	}, []);

	const workflowSteps = (workbench?.steps || []) as SemanticWorkbenchStep[];
	const readySteps = workflowSteps.filter((step) => String(step.status || "").toUpperCase() === "READY").length;

	return (
		<div className="space-y-5 p-5" data-testid="semantic-overview-page">
			<PageHeader
				title={semanticSectionMeta.overview.title}
				actions={(
					<Space wrap>
						<Button icon={<DashboardOutlined />} onClick={() => router.push("/metrics/center")}>指标工作台</Button>
						<Button icon={<FunctionOutlined />} onClick={() => router.push("/metrics/dictionary")}>指标字典</Button>
					</Space>
				)}
			/>

			<SemanticSectionNav activeSection="overview" />

			<Alert
				type="info"
				showIcon
				message="设计口径"
				description="指标可视化设计从 DWD 明细模型开始做业务对象、Join、维度和指标定义；面向 BI、大屏和 API 消费时，只发布 DWS 公共汇总模型或 ADS 应用数据集。DWD 是建模输入，不直接作为可视化消费层。"
			/>

			<Row gutter={[16, 16]}>
				<Col xs={24} lg={16}>
					<Card title="端到端流程">
						<Steps
							current={0}
							items={[
								{ title: "治理主题域", description: "优先复用治理中心主题域", icon: <ProjectOutlined /> },
								{ title: "DWD 业务对象", description: "选择明细模型并配置 Join", icon: <DatabaseOutlined /> },
								{ title: "指标定义", description: "字段拖拽生成维度和指标", icon: <FunctionOutlined /> },
								{ title: "DWS/ADS", description: "生成可消费数据集", icon: <TableOutlined /> },
								{ title: "发布消费", description: "dbt、BI、API、血缘", icon: <BranchesOutlined /> },
							]}
						/>
					</Card>
				</Col>
				<Col xs={24} lg={8}>
					<Card title="当前资产" loading={loading}>
						<Row gutter={12}>
							<Col span={8}><Statistic title="主题域" value={domains.length} /></Col>
							<Col span={8}><Statistic title="业务对象" value={objects.length} /></Col>
							<Col span={8}><Statistic title="指标" value={metrics.length} /></Col>
						</Row>
					</Card>
				</Col>
			</Row>

			<Card
				title="菜单与后端能力诊断"
				extra={workflowSteps.length ? <Tag color="blue">{readySteps}/{workflowSteps.length} READY</Tag> : null}
				loading={loading}
			>
				{workflowSteps.length ? (
					<Row gutter={[12, 12]}>
						{workflowSteps.map((step) => {
							const total = Number(step.total || 0);
							const ready = Number(step.ready || 0);
							const percent = total > 0 ? Math.min(100, Math.round((ready / total) * 100)) : 0;
							return (
								<Col xs={24} md={12} xl={8} key={step.key}>
									<button
										type="button"
										onClick={() => router.push(step.path)}
										className="w-full rounded-md border border-border bg-background p-4 text-left transition hover:border-primary/60 hover:bg-primary/5"
									>
										<div className="mb-3 flex items-center justify-between gap-2">
											<Typography.Text strong>{step.title}</Typography.Text>
											<Tag color={stepStatusColor(step.status)}>{step.status || "UNKNOWN"}</Tag>
										</div>
										<Progress percent={percent} showInfo={false} />
										<div className="mt-3 text-xs text-muted-foreground">
											{step.primaryApi || "-"}
										</div>
										<div className="mt-1 text-xs text-muted-foreground">
											{step.nextAction || "-"}
										</div>
									</button>
								</Col>
							);
						})}
					</Row>
				) : (
					<Paragraph type="secondary" className="mb-0">
						后端诊断接口暂无数据，保留静态流程入口。
					</Paragraph>
				)}
			</Card>

			<Row gutter={[16, 16]}>
				{(["subjects", "objects", "metrics", "models", "publish", "runs"] as SemanticModelingSection[]).map((key, index) => (
					<Col xs={24} md={12} xl={8} key={key}>
						<Card
							title={`${index + 1}. ${sectionCards[key].title}`}
							extra={<Button size="small" onClick={() => router.push(sectionCards[key].path)}>进入</Button>}
						>
							<Paragraph type="secondary" className="mb-0">
								{sectionCards[key].description}
							</Paragraph>
						</Card>
					</Col>
				))}
			</Row>
		</div>
	);
}
