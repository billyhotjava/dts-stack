import { Navigate, useLocation } from "react-router";

const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

// ADR-85-09：旧 /catalog/asset-detail 路由退役（410 语义），
// 有可解析的资产 id 时收敛到统一详情页 /catalog/datasets/{id}，否则收敛到资产台账。
export default function LegacyAssetDetailRedirect() {
	const location = useLocation();
	const params = new URLSearchParams(location.search);
	const rawId = params.get("assetId") || params.get("id") || "";
	const id = rawId.trim();
	if (id && UUID_PATTERN.test(id)) {
		return <Navigate to={`/catalog/datasets/${encodeURIComponent(id)}`} replace />;
	}
	return <Navigate to="/catalog/assets/ledger" replace />;
}
