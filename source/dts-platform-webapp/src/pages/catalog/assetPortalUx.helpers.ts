export type AssetReadinessInput = {
	classification?: string | null;
	domain?: string | null;
	domainId?: string | null;
	owner?: string | null;
	ownerDept?: string | null;
	lifecycleStatus?: string | null;
	governanceStatus?: string | null;
	matchStatus?: string | null;
	metadataSource?: string | null;
	legacyDatasetId?: string | null;
};

export type AssetReadinessState = "READY" | "WARNING" | "BLOCKED" | "FALLBACK";

export type AssetReadiness = {
	state: AssetReadinessState;
	label: string;
	color: string;
	reasons: string[];
};

const normalize = (value?: string | null) => String(value || "").trim().toUpperCase();

export function resolveAssetReadiness(asset: AssetReadinessInput): AssetReadiness {
	const classification = normalize(asset.classification);
	const lifecycleStatus = normalize(asset.lifecycleStatus);
	const governanceStatus = normalize(asset.governanceStatus);
	const matchStatus = normalize(asset.matchStatus);
	const metadataSource = normalize(asset.metadataSource);
	const reasons: string[] = [];

	if (!classification) reasons.push("缺少密级");
	if (!asset.domain && !asset.domainId) reasons.push("缺少主题域");
	if (!asset.ownerDept && !asset.owner) reasons.push("缺少归属部门");

	if (lifecycleStatus === "DISABLED") {
		return {
			state: "BLOCKED",
			label: "已停用",
			color: "red",
			reasons: ["资产生命周期已停用", ...reasons],
		};
	}

	if (governanceStatus === "PENDING_GOVERNANCE" || reasons.length > 0) {
		return {
			state: "BLOCKED",
			label: "治理阻断",
			color: "red",
			reasons,
		};
	}

	if (matchStatus && matchStatus !== "MATCHED") {
		return {
			state: "WARNING",
			label: "待确认",
			color: "gold",
			reasons: ["资产映射需要人工确认"],
		};
	}

	if (metadataSource && metadataSource !== "DTS-CATALOG" && !asset.legacyDatasetId) {
		return {
			state: "FALLBACK",
			label: "主目录缓存",
			color: "blue",
			reasons: ["仅有主目录缓存，尚未沉淀为 DTS 治理资产"],
		};
	}

	return {
		state: "READY",
		label: "可引用",
		color: "green",
		reasons: [],
	};
}

export function buildAssetGrantUrl(asset: { assetType?: string | null; assetId?: string | null }) {
	const params = new URLSearchParams();
	params.set("assetType", asset.assetType || "TABLE");
	if (asset.assetId) params.set("assetId", asset.assetId);
	return `/governance/asset-grants?${params.toString()}`;
}
