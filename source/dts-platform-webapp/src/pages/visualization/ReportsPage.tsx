import { useEffect, useMemo, useState } from "react";
import { toast } from "sonner";
import { Button, Card, Col, Row, Select, Table, Tag, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import { PageHeader } from "@/components/page-header";
import reportsService, { type ReportLink } from "@/api/services/reportsService";
import { resolveBiLinkForOpen } from "@/utils/biLinkUrl";

const { Text } = Typography;

export default function Page() {
	const [reports, setReports] = useState<ReportLink[]>([]);
	const [loading, setLoading] = useState(false);
	const [typeFilter, setTypeFilter] = useState<string | undefined>(undefined);

	const loadReports = async () => {
		setLoading(true);
		try {
			const list = await reportsService.getPublishedReports({ type: typeFilter });
			setReports(Array.isArray(list) ? (list as ReportLink[]) : []);
		} catch (error: any) {
			toast.error(error?.message || "看板加载失败");
		} finally {
			setLoading(false);
		}
	};

	useEffect(() => {
		void loadReports();
	}, [typeFilter]);

	const counts = useMemo(() => {
		return {
			total: reports.length,
			cockpit: reports.filter((r) => r.reportType === "COCKPIT").length,
			dashboard: reports.filter((r) => r.reportType === "DASHBOARD").length,
		};
	}, [reports]);

	const columns: ColumnsType<ReportLink> = [
		{ title: "标题", dataIndex: "title", render: (v) => v || "-" },
		{ title: "类型", dataIndex: "reportType", width: 120, render: (v) => <Tag>{v || "-"}</Tag> },
		{ title: "引擎", dataIndex: "engine", width: 120, render: (v) => <Tag>{v || "-"}</Tag> },
		{ title: "密级", dataIndex: "classification", width: 120, render: (v) => <Tag>{v || "-"}</Tag> },
		{ title: "URL", dataIndex: "url", render: (v) => v || "-" },
		{
			title: "操作",
			width: 120,
			render: (_, record) => (
				<Button
					type="link"
					onClick={async () => {
						try {
							const resolvedUrl = resolveBiLinkForOpen(record.url, record.engine);
							await reportsService.visit({
								id: record.id,
								code: record.code,
								title: record.title,
								url: resolvedUrl,
								engine: record.engine,
								classification: record.classification,
							});
							if (resolvedUrl) {
								window.open(resolvedUrl, "_blank", "noopener,noreferrer");
							}
						} catch (error: any) {
							toast.error(error?.message || "访问失败");
						}
					}}
				>
					打开
				</Button>
			),
		},
	];

	return (
		<div className="space-y-6">
			<PageHeader
				title="BI 可视化 / 看板中心"
				description="统一管理可视化看板与外部 BI 入口。"
				actions={
					<Select
						placeholder="筛选类型"
						value={typeFilter}
						onChange={setTypeFilter}
						allowClear
						options={[
							{ label: "驾驶舱", value: "COCKPIT" },
							{ label: "主题看板", value: "DASHBOARD" },
							{ label: "分析报表", value: "REPORT" },
							{ label: "业务应用", value: "APP" },
						]}
						style={{ minWidth: 160 }}
					/>
				}
			/>

			<Row gutter={[16, 16]}>
				<Col xs={24} sm={8}>
					<Card>
						<Text type="secondary">看板总数</Text>
						<div className="text-2xl font-semibold">{counts.total}</div>
					</Card>
				</Col>
				<Col xs={24} sm={8}>
					<Card>
						<Text type="secondary">驾驶舱</Text>
						<div className="text-2xl font-semibold">{counts.cockpit}</div>
					</Card>
				</Col>
				<Col xs={24} sm={8}>
					<Card>
						<Text type="secondary">主题看板</Text>
						<div className="text-2xl font-semibold">{counts.dashboard}</div>
					</Card>
				</Col>
			</Row>

			<Card>
				<Table rowKey={(record) => record.id} columns={columns} dataSource={reports} loading={loading} />
			</Card>
		</div>
	);
}
