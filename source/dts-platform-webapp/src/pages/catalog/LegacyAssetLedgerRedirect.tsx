import { Navigate, useLocation } from "react-router";

// ADR-86（UI 收敛）：资产台账并入数据搜索页的表格视图。
// /catalog/assets/ledger 旧深链（概览矩阵下钻、缺口下钻、书签）一律收敛到
// /catalog/search?view=table，原有筛选参数（domain/layer/governance/unclassified/stale/tagIds 等）原样透传。
export default function LegacyAssetLedgerRedirect() {
	const location = useLocation();
	const params = new URLSearchParams(location.search);
	params.set("view", "table");
	const queryString = params.toString();
	return <Navigate to={`/catalog/search${queryString ? `?${queryString}` : ""}`} replace />;
}
