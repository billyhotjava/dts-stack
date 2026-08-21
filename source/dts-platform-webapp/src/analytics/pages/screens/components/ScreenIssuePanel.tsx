import { Drawer } from "antd";
import type { ScreenAuthoringIssue } from "../screenAuthoringIssues";

interface ScreenIssuePanelProps {
	open: boolean;
	issues: ScreenAuthoringIssue[];
	onClose: () => void;
	onLocate: (issue: ScreenAuthoringIssue) => void;
}

const CATEGORY_LABELS: Record<ScreenAuthoringIssue["category"], string> = {
	canvas: "画布与治理",
	data: "数据配置",
	variable: "变量配置",
	interaction: "交互配置",
};

export function ScreenIssuePanel({ open, issues, onClose, onLocate }: ScreenIssuePanelProps) {
	const blockerCount = issues.filter((issue) => issue.level === "blocker").length;
	const warningCount = issues.length - blockerCount;
	return (
		<Drawer
			title="大屏问题中心"
			placement="right"
			width={460}
			open={open}
			onClose={onClose}
			zIndex={10020}
			destroyOnClose
			styles={{
				header: { background: "#1f2330", color: "#e2e8f0", borderBottomColor: "rgba(148,163,184,0.2)" },
				body: { background: "#1f2330", color: "#e2e8f0" },
			}}
		>
			<div data-testid="analytics-screen-issue-center" className="flex h-full flex-col gap-4">
				<div
					className="rounded-lg border p-3 text-sm"
					style={{ borderColor: "rgba(148,163,184,0.24)", background: "#181c28", color: "#94a3b8" }}
				>
					<div className="font-semibold" style={{ color: "#e2e8f0" }}>
						发布前检查
					</div>
					<div className="mt-1">
						{blockerCount} 项须处理，{warningCount} 项建议关注
					</div>
				</div>

				{issues.length === 0 ? (
					<div className="flex flex-1 items-center justify-center text-sm" style={{ color: "#94a3b8" }}>
						当前未发现影响发布的问题
					</div>
				) : (
					<div className="flex flex-col gap-2 overflow-y-auto pb-4">
						{issues.map((issue) => (
							<button
								key={issue.id}
								type="button"
								className="w-full rounded-lg border p-3 text-left transition-colors hover:border-[#509ee3] hover:bg-[#293044]"
								style={{ borderColor: "rgba(148,163,184,0.24)", background: "#181c28" }}
								onClick={() => onLocate(issue)}
								disabled={!issue.componentId && !issue.tab}
							>
								<div className="flex items-center justify-between gap-3">
									<span
										className="rounded px-2 py-0.5 text-xs font-semibold"
										style={
											issue.level === "blocker"
												? { color: "#ef4444", background: "rgba(239,68,68,0.12)" }
												: { color: "#d97706", background: "rgba(245,158,11,0.14)" }
										}
									>
										{issue.level === "blocker" ? "须处理" : "建议关注"}
									</span>
									<span className="text-xs" style={{ color: "#94a3b8" }}>
										{CATEGORY_LABELS[issue.category]}
									</span>
								</div>
								<div className="mt-2 text-sm font-medium" style={{ color: "#e2e8f0" }}>
									{issue.message}
								</div>
								{issue.pageName || issue.componentName ? (
									<div className="mt-1 text-xs" style={{ color: "#94a3b8" }}>
										{[issue.pageName, issue.componentName].filter(Boolean).join(" / ")}
									</div>
								) : null}
							</button>
						))}
					</div>
				)}
			</div>
		</Drawer>
	);
}
