import { useEffect, useState } from "react";
import type { ReleaseCandidateDeliveryStatus } from "@/api/modelSpecApi";
import { Button } from "./PrototypePrimitives";

/** Statuses the server advances on its own; the dialog keeps refreshing while one is active. */
const IN_FLIGHT_STATUSES = new Set<ReleaseCandidateDeliveryStatus>(["BUILDING", "QUALITY_RUNNING", "PUBLISHING"]);

export const IN_FLIGHT_REFRESH_MS = 5000;

export const isCandidateInFlight = (status?: ReleaseCandidateDeliveryStatus | null) =>
	Boolean(status && IN_FLIGHT_STATUSES.has(status));

/**
 * Explains a running build and offers the server-granted exit when its dispatch stopped making
 * progress. Abandoning needs a second, explicit confirmation because a late Airflow run is fenced.
 */
export function ModelBuildProgressNotice({
	unconfirmedCode,
	canAbandon,
	busy,
	onAbandon,
}: {
	unconfirmedCode?: string | null;
	canAbandon: boolean;
	busy: boolean;
	onAbandon: () => Promise<boolean>;
}) {
	const [confirming, setConfirming] = useState(false);
	// The server can withdraw the exit (the build made progress); never keep a stale confirmation.
	useEffect(() => {
		if (!canAbandon) setConfirming(false);
	}, [canAbandon]);
	return (
		<section aria-live="polite" className="dmx-request-state">
			<p>
				{unconfirmedCode
					? `Airflow 派发结果待确认（错误码 ${unconfirmedCode}），系统正在按退避间隔自动重试；约 20 分钟仍无法确认时将判定为构建失败。`
					: "构建进行中，状态每 5 秒自动刷新。"}
			</p>
			{!canAbandon ? (
				<p>构建持续 10 分钟没有进展时，可以在这里放弃本次构建。</p>
			) : confirming ? (
				<>
					<p>放弃后本次构建记为失败，可重试构建或关闭发布单；Airflow 中迟到的本次运行结果将被拒收，不会写入发布单。</p>
					<div className="dmx-dialog-actions">
						<Button disabled={busy} onClick={() => setConfirming(false)}>
							暂不放弃
						</Button>
						<Button
							danger
							disabled={busy}
							onClick={() =>
								void onAbandon().then((done) => {
									if (done) setConfirming(false);
								})
							}
						>
							{busy ? "处理中…" : "确认放弃本次构建"}
						</Button>
					</div>
				</>
			) : (
				<div className="dmx-dialog-actions">
					<Button disabled={busy} onClick={() => setConfirming(true)}>
						放弃本次构建
					</Button>
				</div>
			)}
		</section>
	);
}
