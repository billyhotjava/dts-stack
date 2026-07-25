import { useEffect, useState } from "react";
import { getAssetTagCapability } from "@/api/catalogTagsApi";
import { AssetTagPanel } from "./AssetTagPanel";

type GovernedAssetTagPanelProps = {
	assetType: string;
	assetKey: string;
};

export function GovernedAssetTagPanel({ assetType, assetKey }: GovernedAssetTagPanelProps) {
	const currentIdentity = `${assetType}\u0000${assetKey}`;
	const [grantedIdentity, setGrantedIdentity] = useState<string | null>(null);

	useEffect(() => {
		let active = true;
		setGrantedIdentity(null);
		getAssetTagCapability({ assetType, assetKey })
			.then((capability) => {
				if (active) setGrantedIdentity(capability?.canTag === true ? currentIdentity : null);
			})
			.catch(() => {
				if (active) setGrantedIdentity(null);
			});
		return () => {
			active = false;
		};
	}, [assetType, assetKey, currentIdentity]);

	return <AssetTagPanel assetType={assetType} assetKey={assetKey} canEdit={grantedIdentity === currentIdentity} />;
}
