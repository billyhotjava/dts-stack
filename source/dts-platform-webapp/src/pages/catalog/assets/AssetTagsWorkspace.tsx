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
			<TagManagementTab
				canManage={canManage}
				onViewAssets={(tag) => openLedger(tag, "view")}
				onAssociateAssets={(tag) => openLedger(tag, "associate")}
			/>
		</div>
	);
}
