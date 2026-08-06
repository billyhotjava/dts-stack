import { NavLink } from "react-router";
import { dataModelingPath } from "../navigation";

const ITEMS = [
	{ view: "business-categories", label: "业务分类" },
	{ view: "layers", label: "数仓分层" },
] as const;

const PUBLIC_GROUP = [
	{ view: "domains", label: "数据域" },
	{ view: "processes", label: "业务过程" },
] as const;

const APPLICATION_GROUP = [
	{ view: "marts", label: "数据集市" },
	{ view: "subjects", label: "主题域" },
] as const;

/**
 * DataWorks-aligned planning object tree. The modeling-space entry is intentionally hidden
 * (single default space; placeholder only), matching the approved scope decision.
 */
export function PlanningSidebar({ activeView }: { activeView: string }) {
	return (
		<nav aria-label="数仓规划对象" className="dmx-planning-sidebar">
			{ITEMS.map((item) => (
				<NavLink
					className={({ isActive }) => (isActive || activeView === item.view ? "active" : "")}
					key={item.view}
					to={dataModelingPath("planning", item.view)}
				>
					{item.label}
				</NavLink>
			))}
			<div className="dmx-planning-sidebar__group">公共层</div>
			{PUBLIC_GROUP.map((item) => (
				<NavLink
					className={({ isActive }) => (isActive || activeView === item.view ? "active" : "")}
					key={item.view}
					to={dataModelingPath("planning", item.view)}
				>
					{item.label}
				</NavLink>
			))}
			<div className="dmx-planning-sidebar__group">应用层</div>
			{APPLICATION_GROUP.map((item) => (
				<NavLink
					className={({ isActive }) => (isActive || activeView === item.view ? "active" : "")}
					key={item.view}
					to={dataModelingPath("planning", item.view)}
				>
					{item.label}
				</NavLink>
			))}
			<div className="dmx-planning-sidebar__group">系统管理</div>
			<NavLink
				className={({ isActive }) => (isActive || activeView === "system" ? "active" : "")}
				to={dataModelingPath("planning", "system")}
			>
				规划参数配置
			</NavLink>
		</nav>
	);
}
