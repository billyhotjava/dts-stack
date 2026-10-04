import { Alert } from "antd";
import { useEffect, useState } from "react";
import { getAssetTagCapability } from "@/api/catalogTagsApi";
import { AssetTagPanel } from "./AssetTagPanel";

type GovernedAssetTagPanelProps = {
	assetType: string;
	assetKey: string;
	onChanged?: () => void;
};

export function GovernedAssetTagPanel({ assetType, assetKey, onChanged }: GovernedAssetTagPanelProps) {
	const currentIdentity = `${assetType}\u0000${assetKey}`;
	const [grantedIdentity, setGrantedIdentity] = useState<string | null>(null);
	const [capabilityError, setCapabilityError] = useState("");

	useEffect(() => {
		let active = true;
		setGrantedIdentity(null);
		setCapabilityError("");
		getAssetTagCapability({ assetType, assetKey })
			.then((capability) => {
				if (active) setGrantedIdentity(capability?.canTag === true ? currentIdentity : null);
			})
			.catch((error: unknown) => {
				if (active) {
					setGrantedIdentity(null);
					setCapabilityError(
						error instanceof Error && error.message ? error.message : "资产不存在、无权访问或标签治理能力暂时不可用",
					);
				}
			});
		return () => {
			active = false;
		};
	}, [assetType, assetKey, currentIdentity]);

	return (
		<div className="space-y-3">
			{capabilityError ? (
				<Alert type="warning" showIcon message="业务数据标签治理能力不可用" description={capabilityError} />
			) : null}
			<AssetTagPanel
				assetType={assetType}
				assetKey={assetKey}
				canEdit={grantedIdentity === currentIdentity}
				onChanged={onChanged}
			/>
		</div>
	);
}
