import { BookOpen, CheckCircle2, ShieldCheck, Sigma } from "lucide-react";
import { Alert, Button, Card, Col, Row, Space, Tag, Typography } from "antd";
import { useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router";
import { PlatformSummaryCards } from "@/components/console-page";
import { getGovernanceReleaseGate } from "@/api/platformApi";

type Entry = {
	title: string;
	description: string;
	path: string;
};

type Section = {
	title: string;
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
				entries: [
					{ title: "术语表", description: "维护业务术语及解释", path: "/governance/standards/glossary" },
					{ title: "数据元", description: "维护字段标准与口径", path: "/governance/standards/elements" },
					{ title: "参考码表", description: "维护统一代码与映射", path: "/governance/standards/reference" },
					{ title: "模板管理", description: "维护标准模板与字段约束", path: "/governance/templates" },
				],
			},
			{
				title: "质量管控",
				entries: [
					{ title: "质量规则", description: "定义并执行质量规则", path: "/governance/rules" },
					{ title: "质量看板", description: "查看执行结果与趋势", path: "/governance/quality" },
					{ title: "资产视图", description: "联动查看数据资产质量", path: "/catalog/quality" },
				],
			},
			{
				title: "指标中心",
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
			<div className="flex items-center justify-between">
				<h1 className="text-xl font-semibold">数据治理中心</h1>
				<Space>
					<Button onClick={() => navigate("/governance/rules")}>
						质量规则
					</Button>
					<Button onClick={() => navigate("/governance/standards/glossary")}>
						术语表
					</Button>
					<Button onClick={() => navigate("/governance/indicators/dictionary")}>
						指标字典
					</Button>
				</Space>
			</div>

			<PlatformSummaryCards items={summaryCards} />

			<Card
				title="发布门禁（最近 7 天）"
				extra={
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
			</Card>

			<Row gutter={[16, 16]}>
				{sections.map((section) => (
					<Col key={section.title} xs={24} md={12} xl={8}>
						<Card title={section.title} className="h-full">
							<div>
							{section.entries.map((entry, idx) => (
								<div
									key={entry.path}
									style={{
										display: "flex",
										alignItems: "center",
										justifyContent: "space-between",
										padding: "12px 0",
										borderBottom: idx < section.entries.length - 1 ? "1px solid #f0f0f0" : "none",
									}}
								>
									<div>
										<span style={{ fontWeight: 600 }}>{entry.title}</span>
										<span style={{ color: "#999", marginLeft: 12 }}>{entry.description}</span>
									</div>
									<Button type="link" onClick={() => navigate(entry.path)}>
										进入
									</Button>
								</div>
							))}
							</div>
						</Card>
					</Col>
				))}
			</Row>
		</div>
	);
}
