import { ArrowRightOutlined } from "@ant-design/icons";
import { Alert, Breadcrumb, Button, Card, Col, Row, Space, Tag, Typography } from "antd";
import { useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router";
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

	return (
		<Space direction="vertical" size={16} style={{ width: "100%" }}>
			<Breadcrumb items={[{ title: "数据治理中心" }, { title: "总览" }]} />

			<Card>
				<Typography.Title level={4} style={{ margin: 0 }}>
					数据治理中心
				</Typography.Title>
				<Typography.Paragraph type="secondary" style={{ marginTop: 8, marginBottom: 0 }}>
					主流程：标准管理 - 质量管控 - 指标治理。选择下方区块即可进入对应能力。
				</Typography.Paragraph>
			</Card>

			<Card
				title="发布门禁（最近 7 天）"
				extra={
					<Button size="small" onClick={() => void loadReleaseGate()}>
						刷新
					</Button>
				}
				loading={gateLoading}
			>
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

			<Row gutter={[16, 16]}>
				{sections.map((section) => (
					<Col key={section.title} xs={24} md={12} xl={8}>
						<Card
							title={section.title}
							style={{ height: "100%" }}
							styles={{ body: { display: "flex", flexDirection: "column", gap: 10 } }}
						>
							<Typography.Paragraph type="secondary" style={{ marginBottom: 0 }}>
								{section.description}
							</Typography.Paragraph>
							{section.entries.map((entry) => (
								<Card key={entry.path} size="small">
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
								</Card>
							))}
						</Card>
					</Col>
				))}
			</Row>
		</Space>
	);
}
