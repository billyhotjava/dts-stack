import type { ModelServingSyncStatus } from "@/api/modelSpecApi";

export const MODEL_SERVING_SYNC_PRESENTATION: Record<
	ModelServingSyncStatus["syncStatus"],
	{ label: string; tone: "neutral" | "info" | "success" | "danger" }
> = {
	NOT_REGISTERED: { label: "目录待登记", tone: "neutral" },
	SYNC_PENDING: { label: "目录同步中", tone: "info" },
	SYNCED: { label: "目录同步成功", tone: "success" },
	SYNC_FAILED: { label: "目录同步失败", tone: "danger" },
};

export const resolveModelServingPhysicalAssetId = (status?: ModelServingSyncStatus | null) =>
	String(
		(status?.servingRef as { physicalAssetId?: string } | null)?.physicalAssetId ||
			(status?.latestPublishedRef as { physicalAssetId?: string } | null)?.physicalAssetId ||
			"",
	).trim();

export const formatModelServingSyncTime = (value?: string | null) => {
	if (!value) return "—";
	const parsed = new Date(value);
	return Number.isNaN(parsed.getTime()) ? value : parsed.toLocaleString("zh-CN", { hour12: false });
};
