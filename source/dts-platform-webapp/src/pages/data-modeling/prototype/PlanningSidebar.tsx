import { NavLink } from "react-router";
import { dataArchitecturePath } from "@/pages/data-architecture/navigation";
import { dataModelingPath } from "../navigation";

const ITEMS = [
	{ view: "business-categories", architectureView: "business-domains", label: "业务分类" },
	{ view: "layers", architectureView: "layers", label: "数仓分层" },
] as const;

const PUBLIC_GROUP = [
	{ view: "domains", architectureView: "business-domains", label: "数据域" },
	{ view: "processes", architectureView: "processes", label: "业务过程" },
] as const;

const APPLICATION_GROUP = [
	{ view: "marts", architectureView: "marts", label: "数据集市" },
	{ view: "subjects", architectureView: "subjects", label: "主题域" },
] as const;

/**
 * DataWorks-aligned planning object tree. The modeling-space entry is intentionally hidden
 * (single default space; placeholder only), matching the approved scope decision.
 */
const ARCHITECTURE_ITEMS = [
	{ view: "business-domains", label: "业务分类与数据域" },
	{ view: "processes", label: "业务过程" },
	{ view: "layers", label: "数仓分层" },
	{ view: "marts", label: "数据集市" },
	{ view: "subjects", label: "主题域" },
] as const;

export function PlanningSidebar({
	activeView,
	surface = "modeling",
}: {
	activeView: string;
	surface?: "modeling" | "architecture";
}) {
	if (surface === "architecture") {
		return (
			<nav aria-label="数据架构对象" className="dmx-planning-sidebar">
				<div className="dmx-planning-sidebar__group">平台全局架构</div>
				{ARCHITECTURE_ITEMS.map((item) => (
					<NavLink
						className={activeView === item.view ? "active" : ""}
						key={item.view}
						to={dataArchitecturePath(item.view)}
					>
						{item.label}
					</NavLink>
				))}
				<p className="dmx-planning-sidebar__note">架构字典由平台统一维护，部门角色仅可查看。</p>
			</nav>
		);
	}
	return (
		<nav aria-label="数仓规划对象" className="dmx-planning-sidebar">
			{ITEMS.map((item) => (
				<NavLink
					className={({ isActive }) => (isActive || activeView === item.view ? "active" : "")}
					key={item.view}
					to={dataArchitecturePath(item.architectureView)}
				>
					{item.label}
				</NavLink>
			))}
			<div className="dmx-planning-sidebar__group">公共层</div>
			{PUBLIC_GROUP.map((item) => (
				<NavLink
					className={({ isActive }) => (isActive || activeView === item.view ? "active" : "")}
					key={item.view}
					to={dataArchitecturePath(item.architectureView)}
				>
					{item.label}
				</NavLink>
			))}
			<div className="dmx-planning-sidebar__group">应用层</div>
			{APPLICATION_GROUP.map((item) => (
				<NavLink
					className={({ isActive }) => (isActive || activeView === item.view ? "active" : "")}
					key={item.view}
					to={dataArchitecturePath(item.architectureView)}
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
