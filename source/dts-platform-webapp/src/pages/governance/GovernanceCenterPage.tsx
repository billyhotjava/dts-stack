import { BookOpen, CheckCircle2, ShieldCheck, Sigma } from "lucide-react";
import { Alert, Button, Card, Col, Row, Space, Tag, Typography } from "antd";
import { useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router";
import { PlatformSummaryCards } from "@/components/console-page";
import { PageHeader } from "@/components/page-header";
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
					{ title: "命名词典", description: "维护业务术语及解释", path: "/data-modeling/standards/dictionary" },
					{ title: "字段标准", description: "维护字段标准与口径", path: "/data-modeling/standards/fields" },
					{ title: "标准代码", description: "维护统一代码与映射", path: "/data-modeling/standards/codes" },
					{ title: "标准包", description: "下载模板并导入标准定义", path: "/foundation/standard-package" },
				],
			},
			{
				title: "质量管控",
				entries: [
					{ title: "质量规则", description: "定义并执行质量规则", path: "/governance/rules/catalog" },
					{ title: "质量检查", description: "运行质量检查并查看执行结果", path: "/governance/rules/runs" },
					{ title: "资产视图", description: "联动查看数据资产质量", path: "/catalog/search" },
				],
			},
			{
				title: "主题域与安全",
				entries: [
					{ title: "主题域", description: "定义数据责任域和资产归属", path: "/data-architecture?view=subjects" },
					{ title: "分级分类", description: "配置密级映射、脱敏规则和安全字段", path: "/security/data-security" },
					{ title: "权限审批", description: "处理资产访问申请和授权链路", path: "/security/dataset-access-approval" },
					{ title: "权限审计", description: "核对权限变更和访问审计证据", path: "/governance/permission-audit" },
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
			note: "标准、质量两条主线",
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
			<PageHeader
				title="数据治理中心 / 发布门禁"
				actions={
					<Space wrap>
						<Button type="primary" onClick={() => navigate("/governance/rules")}>
							新建规则
						</Button>
						<Button onClick={() => navigate("/governance/rules/runs")}>运行质量</Button>
						<Button onClick={() => navigate("/workbench/todo")}>修复阻断</Button>
						<Button onClick={() => navigate("/governance/quality")}>查看报告</Button>
					</Space>
				}
			/>

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
