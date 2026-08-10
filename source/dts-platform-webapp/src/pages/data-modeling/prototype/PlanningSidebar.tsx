import { Database, FolderTree, Globe2, Layers3, Workflow } from "lucide-react";
import { NavLink } from "react-router";
import { dataArchitecturePath } from "@/pages/data-architecture/navigation";

/**
 * DataWorks-aligned planning object tree. The modeling-space entry is intentionally hidden
 * (single default space; placeholder only), matching the approved scope decision.
 */
const ARCHITECTURE_ITEMS = [
	{ view: "business-domains", label: "业务分类与数据域", icon: Globe2 },
	{ view: "processes", label: "业务过程", icon: Workflow },
	{ view: "layers", label: "数仓分层", icon: Layers3 },
	{ view: "marts", label: "数据集市", icon: Database },
	{ view: "subjects", label: "主题域", icon: FolderTree },
] as const;

export function PlanningSidebar({ activeView }: { activeView: string }) {
	return (
		<nav aria-label="数仓规划目录" className="dmx-planning-sidebar">
			<div className="dmx-planning-sidebar__group">平台数仓规划</div>
			{ARCHITECTURE_ITEMS.map((item) => {
				const Icon = item.icon;
				return (
					<NavLink
						className={() => (activeView === item.view ? "active" : "")}
						key={item.view}
						to={dataArchitecturePath(item.view)}
					>
						<Icon aria-hidden="true" className="dmx-planning-sidebar__icon" size={16} />
						<span>{item.label}</span>
					</NavLink>
				);
			})}
			<p className="dmx-planning-sidebar__note">规划目录由平台统一维护，部门角色仅可查看。</p>
		</nav>
	);
}
