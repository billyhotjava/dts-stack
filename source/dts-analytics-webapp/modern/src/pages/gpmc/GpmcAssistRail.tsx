import { useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router";
import { useGpmc } from "./GpmcApp";
import { getGpmcAssistContent, type GpmcAssistCard } from "./gpmcAssistContent";

type AssistPanelKey = "explanation" | "guide" | null;

function AssistCard({ card }: { card: GpmcAssistCard }) {
	const navigate = useNavigate();

	return (
		<div className="gpmc__assist-card">
			<div className="gpmc__assist-card-title">{card.title}</div>
			<div className="gpmc__assist-card-summary">{card.summary}</div>
			<div className="gpmc__assist-card-list">
				{card.bullets.map((item) => (
					<div key={item} className="gpmc__assist-card-item">
						<span className="gpmc__assist-card-dot" />
						<span>{item}</span>
					</div>
				))}
			</div>
			{card.actions?.length ? (
				<div className="gpmc__assist-card-actions">
					{card.actions.map((action) => (
						<button
							key={`${action.label}-${action.href}`}
							type="button"
							className="gpmc__assist-link"
							onClick={() => navigate(action.href)}
						>
							<span className="gpmc__assist-link-title">{action.label}</span>
							<span className="gpmc__assist-link-desc">{action.description}</span>
						</button>
					))}
				</div>
			) : null}
		</div>
	);
}

export default function GpmcAssistRail() {
	const { screen, layer } = useGpmc();
	const [activePanel, setActivePanel] = useState<AssistPanelKey>(null);
	const assistContent = useMemo(() => getGpmcAssistContent(screen, layer), [screen, layer]);
	const guideAvailable = Boolean(assistContent.guide);

	useEffect(() => {
		setActivePanel(null);
	}, [screen, layer]);

	return (
		<aside
			className={`gpmc__assist-rail${activePanel ? " gpmc__assist-rail--expanded" : ""}`}
			aria-label="页面说明与导览"
		>
			<div className="gpmc__assist-tabs" aria-label="说明与导览切换">
				<button
					type="button"
					className={`gpmc__assist-tab${activePanel === "explanation" ? " gpmc__assist-tab--active" : ""}`}
					onClick={() => setActivePanel((current) => (current === "explanation" ? null : "explanation"))}
					aria-pressed={activePanel === "explanation"}
				>
					说明
				</button>
				{guideAvailable ? (
					<button
						type="button"
						className={`gpmc__assist-tab${activePanel === "guide" ? " gpmc__assist-tab--active" : ""}`}
						onClick={() => setActivePanel((current) => (current === "guide" ? null : "guide"))}
						aria-pressed={activePanel === "guide"}
					>
						导览
					</button>
				) : null}
			</div>
			{activePanel ? (
				<div className="gpmc__assist-panel">
					<div className="gpmc__assist-panel-header">
						<div className="gpmc__assist-panel-label">{activePanel === "explanation" ? "说明卡" : "导览卡"}</div>
						<button type="button" className="gpmc__assist-close" onClick={() => setActivePanel(null)} aria-label="收起">
							×
						</button>
					</div>
					<AssistCard card={activePanel === "explanation" ? assistContent.explanation : assistContent.guide!} />
				</div>
			) : null}
		</aside>
	);
}
