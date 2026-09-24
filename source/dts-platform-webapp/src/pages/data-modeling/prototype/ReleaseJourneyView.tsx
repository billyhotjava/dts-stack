import { AlertCircle, AlertTriangle, Check, Circle, Clock3, RotateCcw } from "lucide-react";
import { Button } from "./PrototypePrimitives";
import type { JourneyAction, JourneyStageState, ReleaseJourney } from "./releaseJourney";

const STATE_TEXT: Record<JourneyStageState, string> = {
	passed: "已完成",
	active: "进行中",
	warning: "有提示",
	failed: "需处理",
	"rolled-back": "已回滚",
	waiting: "等待",
};

const POLICY_TEXT = { ADVISORY: "质量策略：提示（不阻断发布）", BLOCKING: "质量策略：阻断" } as const;

function StageIcon({ state }: { state: JourneyStageState }) {
	if (state === "passed") return <Check size={16} />;
	if (state === "failed") return <AlertTriangle size={16} />;
	if (state === "warning") return <AlertCircle size={16} />;
	if (state === "rolled-back") return <RotateCcw size={16} />;
	if (state === "active") return <Clock3 size={16} />;
	return <Circle size={12} />;
}

/** F15-T02: the one place that says where the release stands and what to click next. */
export function ReleaseJourneyHeader({
	journey,
	busyLabel,
	onAction,
}: {
	journey: ReleaseJourney;
	busyLabel?: string | null;
	onAction: (action: JourneyAction) => void;
}) {
	const current = journey.stages.find((stage) => stage.key === journey.current);
	const technical = current?.technical;
	const hasTechnical = Boolean(technical && (technical.code || technical.runId || technical.detail));
	const actionButton = (action: JourneyAction | undefined, primary: boolean) =>
		action ? (
			<Button
				danger={action.tone === "danger"}
				disabled={Boolean(busyLabel)}
				onClick={() => onAction(action)}
				primary={primary && action.tone === "primary"}
			>
				{primary && busyLabel ? busyLabel : action.label}
			</Button>
		) : null;
	return (
		<section
			aria-label="当前发布进度"
			aria-live="polite"
			className={`dmx-journey-header dmx-journey-header--${current?.state || "waiting"}`}
		>
			<div className="dmx-journey-header__text">
				<strong>{journey.headline}</strong>
				{journey.policy ? <span className="dmx-journey-header__policy">{POLICY_TEXT[journey.policy]}</span> : null}
				{hasTechnical ? (
					<details className="dmx-journey-technical">
						<summary>查看技术详情</summary>
						<dl>
							{technical?.code ? (
								<>
									<dt>错误码</dt>
									<dd>{technical.code}</dd>
								</>
							) : null}
							{technical?.runId ? (
								<>
									<dt>执行编号</dt>
									<dd>{technical.runId}</dd>
								</>
							) : null}
							{technical?.detail ? (
								<>
									<dt>详细信息</dt>
									<dd>{technical.detail}</dd>
								</>
							) : null}
						</dl>
					</details>
				) : null}
			</div>
			{journey.primaryAction || journey.secondaryAction ? (
				<div className="dmx-journey-header__actions">
					{actionButton(journey.secondaryAction, false)}
					{actionButton(journey.primaryAction, true)}
				</div>
			) : null}
		</section>
	);
}

export function ReleaseJourneySteps({ journey }: { journey: ReleaseJourney }) {
	return (
		<ol aria-label="发布与构建步骤" className="dmx-release-steps dmx-journey-steps">
			{journey.stages.map((stage, index) => (
				<li
					aria-current={stage.key === journey.current ? "step" : undefined}
					className={`dmx-release-step dmx-release-step--${stage.state}${stage.key === journey.current ? " dmx-release-step--current" : ""}`}
					key={stage.key}
				>
					<span className="dmx-release-step__icon">
						<StageIcon state={stage.state} />
					</span>
					<span>
						<strong>
							{index + 1} {stage.label}
						</strong>
						<small title={stage.summary}>
							{STATE_TEXT[stage.state]} · {stage.summary}
						</small>
					</span>
				</li>
			))}
		</ol>
	);
}
