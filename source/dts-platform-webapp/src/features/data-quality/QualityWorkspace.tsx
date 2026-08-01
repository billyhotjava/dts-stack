import {
	BarChartOutlined,
	CheckSquareOutlined,
	DashboardOutlined,
	FileSearchOutlined,
	FileTextOutlined,
	ScheduleOutlined,
	SettingOutlined,
	TableOutlined,
} from "@ant-design/icons";
import { Button, Tag, Tooltip } from "antd";
import type { ReactNode } from "react";
import { useNavigate } from "react-router";
import { QUALITY_ROUTE_SPECS, type QualityRouteKey, qualityPath, qualityPrimaryRoutesForOwner } from "./qualityRoutes";
import "./quality-workspace.css";

const PRIMARY_ICONS: Record<string, ReactNode> = {
	overview: <DashboardOutlined />,
	"rule-list": <CheckSquareOutlined />,
	"rule-template": <FileSearchOutlined />,
	"rule-by-table": <TableOutlined />,
	"rule-by-template": <SettingOutlined />,
	monitor: <ScheduleOutlined />,
	"run-records": <BarChartOutlined />,
	report: <FileTextOutlined />,
};

const PRIMARY_PARENT: Partial<Record<QualityRouteKey, QualityRouteKey>> = {
	"rule-detail": "rule-list",
	"rule-editor": "rule-list",
	"template-detail": "rule-template",
	"table-detail": "rule-by-table",
	"batch-wizard": "rule-by-table",
	"monitor-detail": "monitor",
	"monitor-editor": "monitor",
	"run-detail": "run-records",
	noise: "monitor",
	"report-editor": "report",
	"report-preview": "report",
};

export function QualityWorkspace({ routeKey, children }: { routeKey: QualityRouteKey; children: ReactNode }) {
	const navigate = useNavigate();
	const activeKey = PRIMARY_PARENT[routeKey] || routeKey;
	const current = QUALITY_ROUTE_SPECS.find((item) => item.key === routeKey);
	const showControlNavigation = current?.menuOwner === "/governance/rules";
	const primaryRoutes = qualityPrimaryRoutesForOwner(current?.menuOwner || "/governance/rules");

	return (
		<div className="dq-workspace">
			<header className="dq-workspace__header">
				<div>
					<div className="dq-workspace__eyebrow">DATA QUALITY CENTER</div>
					<div className="dq-workspace__title-row">
						<h1>数据质量</h1>
						<Tag color="blue">默认数据湖</Tag>
					</div>
					<p>规则、巡检、运行与质量分析的一体化工作台</p>
				</div>
				<div className="dq-workspace__context">
					<span>当前位置</span>
					<strong>{current?.label || "数据质量"}</strong>
				</div>
			</header>

			<div className={`dq-workspace__body${showControlNavigation ? "" : " dq-workspace__body--full"}`}>
				{showControlNavigation ? (
					<nav className="dq-workspace__nav" aria-label="数据质量工作台导航">
						{primaryRoutes.map((route) => (
							<Tooltip key={route.key} title={route.group} placement="right">
								<Button
									type={activeKey === route.key ? "primary" : "text"}
									icon={PRIMARY_ICONS[route.key]}
									onClick={() => navigate(qualityPath(route.key))}
								>
									{route.label}
								</Button>
							</Tooltip>
						))}
					</nav>
				) : null}
				<main className="dq-workspace__content">{children}</main>
			</div>
		</div>
	);
}
