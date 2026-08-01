import { Alert, Button, Descriptions, Drawer, Space, Tabs, Tag } from "antd";
import { useMemo, useState } from "react";
import type { ClassificationFactView } from "@/api/platformApi";
import { AssetTagChips } from "@/components/catalog/tags/AssetTagChips";
import { GovernedAssetTagPanel } from "@/components/catalog/tags/GovernedAssetTagPanel";
import { useRouter } from "@/routes/hooks";
import { resolveAssetReadiness } from "../assetPortalUx.helpers";
import { ASSET_TYPE_DICT, resolveEnumLabel } from "./assetEnumLabels";
import type { AssetRow } from "./assetPageShared";
import { classificationText, LAYER_META, normalizeLayer } from "./assetPageShared";

type GovernanceTaskTarget = "DETAIL" | "LIFECYCLE" | "TAGS";

export type AssetGovernanceTask = {
	title: string;
	description: string;
	actionLabel: string;
	target: GovernanceTaskTarget;
	detailTab?: string;
};

export function resolveAssetGovernanceTask(
	asset: AssetRow,
	classificationFact?: ClassificationFactView,
): AssetGovernanceTask {
	const readiness = resolveAssetReadiness(asset);
	const identityReady = Boolean(asset.assetType && asset.assetKey);
	if (!identityReady) {
		return {
			title: "修复资产身份映射",
			description: "当前资产缺少统一资产类型或资产键，标签和跨模块治理暂不可执行。",
			actionLabel: "查看资产档案",
			target: "DETAIL",
			detailTab: "overview",
		};
	}
	if (readiness.reasons.some((reason) => reason.includes("生命周期"))) {
		return {
			title: "处理失效生命周期",
			description: readiness.reasons.join("；"),
			actionLabel: "继续治理",
			target: "LIFECYCLE",
		};
	}
	if (readiness.reasons.some((reason) => reason.includes("主题域") || reason.includes("归属部门"))) {
		return {
			title: "补齐治理责任",
			description: readiness.reasons.join("；"),
			actionLabel: "继续治理",
			target: "DETAIL",
			detailTab: "governance",
		};
	}
	if (
		readiness.reasons.some((reason) => reason.includes("密级")) ||
		!(classificationFact?.effectiveLevel || asset.classification)
	) {
		return {
			title: "核验密级与生命周期",
			description: "确认有效密级、上游最高密级和生命周期状态；密级只能保持或提高。",
			actionLabel: "继续治理",
			target: "LIFECYCLE",
		};
	}
	if (readiness.state === "WARNING" || readiness.state === "FALLBACK") {
		return {
			title: "确认资产映射",
			description: readiness.reasons.join("；") || "资产映射证据需要确认。",
			actionLabel: "查看映射详情",
			target: "DETAIL",
			detailTab: "overview",
		};
	}
	if (!asset.assetTags?.length) {
		return {
			title: "完善业务数据标签",
			description: "当前资产尚未设置业务数据标签，补充标签后更容易被检索和复用。",
			actionLabel: "添加数据标签",
			target: "TAGS",
		};
	}
	return {
		title: "核验访问条件",
		description: "治理前置条件已满足，可继续核验访问权限或查看完整资产档案。",
		actionLabel: "查看权限申请",
		target: "DETAIL",
		detailTab: "access",
	};
}

type AssetGovernanceWorkbenchDrawerProps = {
	open: boolean;
	asset: AssetRow | null;
	classificationFact?: ClassificationFactView;
	onClose: () => void;
	onOpenLifecycle: (asset: AssetRow) => void;
	onChanged?: () => void;
};

export function AssetGovernanceWorkbenchDrawer({
	open,
	asset,
	classificationFact,
	onClose,
	onOpenLifecycle,
	onChanged,
}: AssetGovernanceWorkbenchDrawerProps) {
	const router = useRouter();
	const [activeTab, setActiveTab] = useState("overview");
	const readiness = useMemo(() => (asset ? resolveAssetReadiness(asset) : null), [asset]);
	const task = useMemo(
		() => (asset ? resolveAssetGovernanceTask(asset, classificationFact) : null),
		[asset, classificationFact],
	);

	if (!asset || !task || !readiness) return null;

	const openDetail = (tab = "overview") => {
		router.push(`/catalog/datasets/${encodeURIComponent(asset.id)}?tab=${encodeURIComponent(tab)}`);
	};
	const executePrimaryTask = () => {
		if (task.target === "TAGS") {
			setActiveTab("tags");
			return;
		}
		if (task.target === "LIFECYCLE") {
			onOpenLifecycle(asset);
			return;
		}
		openDetail(task.detailTab);
	};
	const handleClose = () => {
		onChanged?.();
		onClose();
	};
	const identityReady = Boolean(asset.assetType && asset.assetKey);
	const effectiveLevel = classificationFact?.effectiveLevel || asset.classification;

	return (
		<Drawer
			open={open}
			onClose={handleClose}
			afterOpenChange={(visible) => {
				if (visible) setActiveTab("overview");
			}}
			width="min(760px, 100vw)"
			title="资产治理工作台"
			destroyOnClose
			extra={<Button onClick={() => openDetail()}>完整资产档案</Button>}
		>
			<div className="space-y-4">
				<div>
					<div className="text-lg font-semibold text-slate-900">{asset.name}</div>
					<div className="mt-1 break-all font-mono text-xs text-slate-500">
						{asset.assetType || "UNKNOWN"} · {asset.assetKey || "资产键待补齐"}
					</div>
				</div>
				<Alert
					type={readiness.state === "BLOCKED" ? "warning" : readiness.state === "READY" ? "success" : "info"}
					showIcon
					message={`当前治理任务：${task.title}`}
					description={task.description}
					action={
						<Button type="primary" disabled={!asset.id} onClick={executePrimaryTask}>
							{task.actionLabel}
						</Button>
					}
				/>
				<Tabs
					activeKey={activeTab}
					onChange={setActiveTab}
					items={[
						{
							key: "overview",
							label: "治理总览",
							children: (
								<div className="space-y-4">
									<Descriptions bordered size="small" column={2}>
										<Descriptions.Item label="治理状态">
											<Tag color={readiness.color}>{readiness.label}</Tag>
										</Descriptions.Item>
										<Descriptions.Item label="资产类型">
											{resolveEnumLabel(ASSET_TYPE_DICT, asset.type, "未知类型")}
										</Descriptions.Item>
										<Descriptions.Item label="数仓分层">
											{LAYER_META[normalizeLayer(asset.warehouseLayer)].label}
										</Descriptions.Item>
										<Descriptions.Item label="主题域">{asset.domain || "未归域"}</Descriptions.Item>
										<Descriptions.Item label="有效密级">{classificationText(effectiveLevel)}</Descriptions.Item>
										<Descriptions.Item label="负责人">{asset.owner || asset.ownerDept || "待补齐"}</Descriptions.Item>
									</Descriptions>
									<div>
										<div className="mb-2 text-sm font-medium text-slate-700">业务数据标签</div>
										<AssetTagChips tags={asset.assetTags || []} variant="inline" />
									</div>
									<Space size={[8, 8]} wrap>
										<Button onClick={() => openDetail("governance")}>治理责任</Button>
										<Button onClick={() => onOpenLifecycle(asset)}>密级与生命周期</Button>
										<Button onClick={() => openDetail("quality-sla")}>质量与 SLA</Button>
										<Button onClick={() => openDetail("lineage-impact")}>血缘与影响</Button>
										<Button onClick={() => openDetail("access")}>权限申请</Button>
									</Space>
								</div>
							),
						},
						{
							key: "tags",
							label: "数据标签",
							children: identityReady ? (
								<GovernedAssetTagPanel
									assetType={String(asset.assetType)}
									assetKey={String(asset.assetKey)}
									onChanged={onChanged}
								/>
							) : (
								<Alert
									type="warning"
									showIcon
									message="资产身份不完整"
									description="缺少统一资产类型或资产键，无法加载和维护业务数据标签。"
								/>
							),
						},
					]}
				/>
			</div>
		</Drawer>
	);
}
