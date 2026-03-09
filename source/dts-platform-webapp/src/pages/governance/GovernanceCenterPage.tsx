import { ArrowRightOutlined } from "@ant-design/icons";
import { BookOpen, CheckCircle2, ShieldCheck, Sigma } from "lucide-react";
import { Alert, Button, Card, Col, Row, Space, Tag, Typography } from "antd";
import { useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router";
import {
	PlatformMetaPill,
	PlatformPageHero,
	PlatformSectionCard,
	PlatformSummaryCards,
} from "@/components/console-page";
import { getGovernanceReleaseGate } from "@/api/platformApi";

type Entry = {
	title: string;
	description: string;
	path: string;
};

type Section = {
	title: string;
	description: string;
	entries: Entry[];
};

type ReleaseGate = {
	readyForRelease?: boolean;
	blockerFailed?: number;
	checks?: Array<{
		code?: string;
		name?: string;
		passed?: boolean;
		actual?: any;
		threshold?: string;
		severity?: string;
	}>;
	checkedAt?: string;
};

export default function GovernanceCenterPage() {
	const navigate = useNavigate();
	const [gateLoading, setGateLoading] = useState(false);
	const [releaseGate, setReleaseGate] = useState<ReleaseGate | null>(null);

	const sections = useMemo<Section[]>(
		() => [
			{
				title: "标准管理",
				description: "统一术语、数据元、模板和参考码表，形成标准底座。",
				entries: [
					{ title: "术语表", description: "维护业务术语及解释", path: "/governance/standards/glossary" },
					{ title: "数据元", description: "维护字段标准与口径", path: "/governance/standards/elements" },
					{ title: "参考码表", description: "维护统一代码与映射", path: "/governance/standards/reference" },
					{ title: "模板管理", description: "维护标准模板与字段约束", path: "/governance/templates" },
				],
			},
			{
				title: "质量管控",
				description: "围绕规则、执行与问题闭环开展质量治理。",
				entries: [
					{ title: "质量规则", description: "定义并执行质量规则", path: "/governance/rules" },
					{ title: "质量看板", description: "查看执行结果与趋势", path: "/governance/quality" },
					{ title: "资产视图", description: "联动查看数据资产质量", path: "/catalog/quality" },
				],
			},
			{
				title: "指标中心",
				description: "统一指标定义、维度管理、发布与追溯。",
				entries: [
					{ title: "指标字典", description: "管理指标与维度定义", path: "/governance/indicators/dictionary" },
					{ title: "数据资产", description: "按资产回看指标依赖", path: "/catalog/assets" },
					{ title: "血缘分析", description: "查看指标上下游关系", path: "/catalog/lineage" },
				],
			},
		],
		[],
	);

	const loadReleaseGate = async () => {
		setGateLoading(true);
		try {
			const resp = (await getGovernanceReleaseGate({ days: 7 })) as ReleaseGate;
			setReleaseGate(resp || null);
		} catch {
			setReleaseGate(null);
		} finally {
			setGateLoading(false);
		}
	};

	useEffect(() => {
		void loadReleaseGate();
	}, []);

	const entryCount = sections.reduce((sum, section) => sum + section.entries.length, 0);
	const summaryCards = [
		{
			label: "治理分区",
			value: sections.length,
			note: "标准、质量、指标三条主线",
			icon: <BookOpen className="h-5 w-5" />,
		},
		{
			label: "能力入口",
			value: entryCount,
			note: "全部入口已落到真实业务页",
			icon: <Sigma className="h-5 w-5" />,
			tone: "info" as const,
		},
		{
			label: "发布门禁",
			value: releaseGate?.readyForRelease ? "通过" : "待处理",
			note: releaseGate?.checkedAt ? `最近校验 ${new Date(releaseGate.checkedAt).toLocaleString()}` : "暂无门禁快照",
			icon: <CheckCircle2 className="h-5 w-5" />,
			tone: "success" as const,
		},
		{
			label: "阻断项",
			value: releaseGate?.blockerFailed ?? 0,
			note: "最近 7 天门禁阻断数",
			icon: <ShieldCheck className="h-5 w-5" />,
			tone: "warning" as const,
		},
	];

	return (
		<div className="space-y-6">
			<PlatformPageHero
				title="数据治理中心"
				description="把标准管理、质量管控和指标治理的真实入口放到同一个总览页里，先看门禁，再进具体治理动作。"
				eyebrow="Governance Overview"
				actions={
					<div className="flex flex-wrap items-center gap-2">
						<Button className="rounded-2xl" onClick={() => navigate("/governance/rules")}>
							质量规则
						</Button>
						<Button className="rounded-2xl" onClick={() => navigate("/governance/standards/glossary")}>
							术语表
						</Button>
						<Button className="rounded-2xl" onClick={() => navigate("/governance/indicators/dictionary")}>
							指标字典
						</Button>
					</div>
				}
				meta={
					<>
						<PlatformMetaPill>主流程：标准管理 - 质量管控 - 指标治理</PlatformMetaPill>
						<PlatformMetaPill>最近 7 天发布门禁</PlatformMetaPill>
						<PlatformMetaPill>全部入口均落到真实业务页</PlatformMetaPill>
					</>
				}
			/>

			<PlatformSummaryCards items={summaryCards} />

			<PlatformSectionCard
				title="发布门禁（最近 7 天）"
				description="先判断当前治理基线是否具备发布条件，再决定进入哪个治理区块处理问题。"
				action={
					<Button size="small" onClick={() => void loadReleaseGate()}>
						刷新
					</Button>
				}
			>
				<Card loading={gateLoading} bordered={false} bodyStyle={{ padding: 0 }}>
					{releaseGate ? (
					<Space direction="vertical" size={10} style={{ width: "100%" }}>
						<Alert
							type={releaseGate.readyForRelease ? "success" : "warning"}
							showIcon
							message={releaseGate.readyForRelease ? "门禁通过，可发布" : `门禁未通过，阻断项 ${releaseGate.blockerFailed || 0} 个`}
						/>
						<Space wrap>
							{(releaseGate.checks || []).map((check) => (
								<Tag key={check.code} color={check.passed ? "green" : "red"}>
									{`${check.name || check.code}: ${String(check.actual ?? "-")} (阈值 ${check.threshold || "-"})`}
								</Tag>
							))}
						</Space>
					</Space>
				) : (
					<Typography.Text type="secondary">暂无门禁数据。</Typography.Text>
				)}
				</Card>
			</PlatformSectionCard>

			<Row gutter={[16, 16]}>
				{sections.map((section) => (
					<Col key={section.title} xs={24} md={12} xl={8}>
						<PlatformSectionCard
							title={section.title}
							description={section.description}
							className="h-full"
							bodyClassName="flex flex-col gap-3"
						>
							{section.entries.map((entry) => (
								<div key={entry.path} className="rounded-[22px] border border-border/70 bg-muted/35 p-4">
									<Space direction="vertical" size={6} style={{ width: "100%" }}>
										<Typography.Text strong>{entry.title}</Typography.Text>
										<Typography.Text type="secondary">{entry.description}</Typography.Text>
										<Button
											type="link"
											style={{ padding: 0, width: "fit-content" }}
											icon={<ArrowRightOutlined />}
											onClick={() => navigate(entry.path)}
										>
											进入
										</Button>
									</Space>
								</div>
							))}
						</PlatformSectionCard>
					</Col>
				))}
			</Row>
		</div>
	);
}
