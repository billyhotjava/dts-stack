import type { ReactNode } from "react";
import { Card, Empty, List, Skeleton, Tag } from "antd";
import type { LeaderOverviewResponse } from "@/api/services/workbenchService";
import reportsService from "@/api/services/reportsService";
import type { WorkbenchRole } from "../hooks/useWorkbenchRole";
import { classificationColor } from "../hooks/classification";
import { relativeTime } from "../hooks/relativeTime";
import { humanizeBizDomain } from "../hooks/bizDomain";

/**
 * Sprint-15 F5/T01 — Left 2-column TOP reports block.
 *
 * Rows are sorted (by backend) on visits desc; max 10 items. A click both
 * records the visit (`reports/visit` endpoint) and opens the report URL in
 * a new tab. The visit call is best-effort — failures do not block the open.
 */

export type TopReportItem = LeaderOverviewResponse["topReports"][number];

export interface TopReportsBlockProps {
	role: WorkbenchRole;
	items: TopReportItem[];
	loading: boolean;
	onEmpty?: () => ReactNode;
}

function titleFor(role: TopReportsBlockProps["role"]): string {
	if (role === "EMP") return "我常用的报表";
	if (role === "DEPT_LEADER") return "本部门 TOP 报表";
	return "全所 TOP 报表";
}

function emptyNodeFor(role: TopReportsBlockProps["role"]): ReactNode {
	if (role === "EMP") {
		return (
			<div>
				还没有访问过任何报表，
				<a href="/reports">去报表中心看看</a>
			</div>
		);
	}
	return <div>暂无已发布报表</div>;
}

export function TopReportsBlock({ role, items, loading, onEmpty }: TopReportsBlockProps) {
	const title = titleFor(role);

	const handleRowClick = async (item: TopReportItem): Promise<void> => {
		try {
			await reportsService.visit({
				id: item.id,
				title: item.title,
				classification: item.classification,
			});
		} catch {
			// Best-effort logging — do not block navigation on audit failure.
		}
		// TODO: align with `resolveRouteForOpen` once a canonical report-open helper ships.
		window.open(`/reports/${item.id}`, "_blank", "noopener,noreferrer");
	};

	return (
		<Card title={title} extra={<a href="/reports">查看全部 →</a>}>
			{loading ? (
				<Skeleton active paragraph={{ rows: 6 }} />
			) : items.length === 0 ? (
				(onEmpty?.() ?? <Empty description={emptyNodeFor(role)} />)
			) : (
				<List
					dataSource={items}
					renderItem={(r) => (
						<List.Item
							style={{ cursor: "pointer" }}
							onClick={() => {
								void handleRowClick(r);
							}}
						>
							<List.Item.Meta
								title={r.title}
								description={
									<span>
										访问 {r.visits.toLocaleString()} · {r.lastVisitedAt ? relativeTime(r.lastVisitedAt) : "—"}
									</span>
								}
							/>
							<div>
								{(() => {
									const label = humanizeBizDomain(r.bizDomain);
									return label ? <Tag color="geekblue">{label}</Tag> : null;
								})()}
								<Tag color={classificationColor(r.classification)}>{r.classification}</Tag>
							</div>
						</List.Item>
					)}
				/>
			)}
		</Card>
	);
}

export default TopReportsBlock;
