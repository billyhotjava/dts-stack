import { type ReactNode } from "react";
import { useGpmc, GPMC_SCREENS, type GpmcScreenId } from "./GpmcApp";
import GpmcAssistRail from "./GpmcAssistRail";

const TOPIC_LABELS: Record<GpmcScreenId, string> = {
	overview: "综合态势",
	execution: "执行监控",
	quality: "质量跟进",
	"tech-state": "技术状态",
	cost: "成本控制",
	risk: "风险预警",
};

function TopicTabs() {
	const { screen, setScreen } = useGpmc();
	return (
		<div className="gpmc__topic-tabs" aria-label="专题切换">
			{GPMC_SCREENS.map((item) => (
				<button
					type="button"
					key={item.id}
					className={`gpmc__topic-tab${screen === item.id ? " gpmc__topic-tab--active" : ""}`}
					onClick={() => setScreen(item.id)}
				>
					{TOPIC_LABELS[item.id]}
					{item.badge != null && <span className="gpmc__topic-badge">{item.badge}</span>}
				</button>
			))}
		</div>
	);
}

function TopBar({ title, description }: { title: string; description: string }) {
	const { theme, toggleTheme, fullscreen, toggleFullscreen, layerLabel } = useGpmc();
	return (
		<div className="gpmc__topbar">
			<div>
				<h1 className="gpmc__topbar-title">{title}</h1>
				<div className="gpmc__topbar-desc">{description}</div>
			</div>
			<div className="gpmc__topbar-controls">
				<div className="gpmc__theme-toggle">
					<button
						type="button"
						className={`gpmc__theme-btn${theme === "light" ? " gpmc__theme-btn--active" : ""}`}
						onClick={theme !== "light" ? toggleTheme : undefined}
					>
						LIGHT
					</button>
					<button
						type="button"
						className={`gpmc__theme-btn${theme === "dark" ? " gpmc__theme-btn--active" : ""}`}
						onClick={theme !== "dark" ? toggleTheme : undefined}
					>
						DARK
					</button>
				</div>
				<div className="gpmc__filter-bar">
					<span>{layerLabel}</span>
				</div>
				<button
					type="button"
					className="gpmc__theme-btn"
					onClick={toggleFullscreen}
					title={fullscreen ? "退出全屏" : "全屏模式"}
				>
					{fullscreen ? "退出" : "全屏"}
				</button>
			</div>
		</div>
	);
}

type Props = {
	title: string;
	description: string;
	children: ReactNode;
};

export default function GpmcLayout({ title, description, children }: Props) {
	const { layer, setLayer } = useGpmc();
	return (
		<>
			<TopBar title={title} description={description} />
			<TopicTabs />
			<div className="gpmc__content">
				<div className="gpmc__content-shell">
					<div className="gpmc__content-main">
						{layer !== "strategic" && (
							<button type="button" className="gpmc__back-to-bigscreen" onClick={() => setLayer("strategic")}>
								<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M19 12H5M12 19l-7-7 7-7" /></svg>
								返回大屏
							</button>
						)}
						{children}
					</div>
					<GpmcAssistRail />
				</div>
			</div>
		</>
	);
}
