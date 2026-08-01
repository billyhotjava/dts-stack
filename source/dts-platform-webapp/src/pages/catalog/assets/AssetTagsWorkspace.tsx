import { Alert } from "antd";
import type { CatalogTagDto } from "@/api/catalogTagsApi";
import { TagManagementTab } from "@/components/catalog/tags/TagManagementTab";
import { useCatalogTagGovernanceAccess } from "@/hooks/useModuleManageAccess";
import { useRouter } from "@/routes/hooks";

export function AssetTagsWorkspace() {
	const router = useRouter();
	const canManage = useCatalogTagGovernanceAccess();

	const openLedger = (tag: CatalogTagDto, mode: "view" | "associate") => {
		const params = new URLSearchParams();
		if (mode === "view") params.append("tagIds", tag.id);
		else {
			params.set("manageTag", tag.id);
			params.set("manageTagName", tag.name);
		}
		router.push(`/catalog/assets/ledger?${params.toString()}`);
	};

	return (
		<div className="space-y-4">
			<Alert
				type="info"
				showIcon
				message="业务数据标签用于资产发现与检索"
				description="标签与分类分级、数据密级相互独立。可查看标签关联资产，或从标签发起资产关联。"
			/>
			<TagManagementTab
				canManage={canManage}
				onViewAssets={(tag) => openLedger(tag, "view")}
				onAssociateAssets={(tag) => openLedger(tag, "associate")}
			/>
		</div>
	);
}
