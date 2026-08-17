import { RefreshCw } from "lucide-react";
import { useCallback, useEffect, useState } from "react";
import {
	getModelServingSyncStatus,
	retryModelServingSync,
	type ModelServingSyncStatus as ServingSyncStatus,
} from "@/api/modelSpecApi";
import { useRouter } from "@/routes/hooks";
import { Button, Status } from "./PrototypePrimitives";
import { normalizeModelingRequestFailure } from "./services/planningProjectionService";

const PRESENTATION: Record<
	ServingSyncStatus["syncStatus"],
	{ label: string; tone: "neutral" | "info" | "success" | "danger" }
> = {
	NOT_REGISTERED: { label: "目录待登记", tone: "neutral" },
	SYNC_PENDING: { label: "目录同步中", tone: "info" },
	SYNCED: { label: "目录同步成功", tone: "success" },
	SYNC_FAILED: { label: "目录同步失败", tone: "danger" },
};

const formatTime = (value?: string | null) => {
	if (!value) return "—";
	const parsed = new Date(value);
	return Number.isNaN(parsed.getTime()) ? value : parsed.toLocaleString("zh-CN", { hour12: false });
};

export function ModelServingSyncStatus({ modelSpecId, canMaintain }: { modelSpecId: string; canMaintain: boolean }) {
	const router = useRouter();
	const [status, setStatus] = useState<ServingSyncStatus | null>(null);
	const [loading, setLoading] = useState(true);
	const [retrying, setRetrying] = useState(false);
	const [failure, setFailure] = useState("");

	const load = useCallback(async () => {
		setLoading(true);
		setFailure("");
		try {
			setStatus(await getModelServingSyncStatus(modelSpecId));
		} catch (error) {
			setStatus(null);
			setFailure(normalizeModelingRequestFailure(error, "目录同步状态读取失败。").message);
		} finally {
			setLoading(false);
		}
	}, [modelSpecId]);

	useEffect(() => {
		void load();
	}, [load]);

	const retry = async () => {
		if (!status || status.syncStatus !== "SYNC_FAILED") return;
		setRetrying(true);
		setFailure("");
		try {
			const result = await retryModelServingSync(status);
			setStatus(result.status);
		} catch (error) {
			setFailure(normalizeModelingRequestFailure(error, "目录同步重试失败。").message);
		} finally {
			setRetrying(false);
		}
	};

	const presentation = status ? PRESENTATION[status.syncStatus] : null;
	const physicalAssetId = String(
		(status?.servingRef as { physicalAssetId?: string } | null)?.physicalAssetId ||
			(status?.latestPublishedRef as { physicalAssetId?: string } | null)?.physicalAssetId ||
			"",
	).trim();
	return (
		<div aria-label="目录同步状态" className="dmx-serving-sync-status">
			<RefreshCw className={loading || retrying ? "spin" : ""} size={13} />
			{failure && !status ? (
				<>
					<span title={failure}>目录同步读取失败</span>
					<Button onClick={() => void load()} type="text">
						重试读取
					</Button>
				</>
			) : (
				<>
					<Status tone={presentation?.tone || "neutral"}>
						{loading ? "目录同步读取中" : presentation?.label || "—"}
					</Status>
					{status?.updatedAt ? <span>更新 {formatTime(status.updatedAt)}</span> : null}
					{physicalAssetId ? (
						<Button onClick={() => router.push(`/catalog/datasets/${encodeURIComponent(physicalAssetId)}`)} type="text">
							查看资产
						</Button>
					) : null}
					{status?.syncStatus === "SYNC_FAILED" ? (
						<>
							<span title={status.lastSyncError || undefined}>{status.lastSyncError || "同步失败"}</span>
							<span>第 {status.syncAttempts} 次</span>
							{status.nextSyncAt ? <span>下次 {formatTime(status.nextSyncAt)}</span> : <span>已停止自动重试</span>}
							<Button disabled={!canMaintain || retrying} onClick={() => void retry()} type="text">
								{retrying ? "重试中…" : "重试同步"}
							</Button>
						</>
					) : null}
					{failure ? <span title={failure}>重试失败</span> : null}
				</>
			)}
		</div>
	);
}
