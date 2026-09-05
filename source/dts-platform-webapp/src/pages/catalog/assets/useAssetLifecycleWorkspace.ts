import { useCallback, useEffect, useRef, useState } from "react";
import {
	type AssetGovernanceWorkspace,
	type GovernanceIssueView,
	getCatalogAssetGovernanceWorkspace,
	getCatalogGovernanceIssues,
	getCatalogLifecycleMetrics,
	type LifecycleMetrics,
} from "@/api/platformApi";

export function useAssetLifecycleWorkspace(open: boolean, datasetId?: string, subjectKey?: string) {
	const [activeTab, setActiveTab] = useState("classification");
	const [workspace, setWorkspace] = useState<AssetGovernanceWorkspace | null>(null);
	const [metrics, setMetrics] = useState<LifecycleMetrics | null>(null);
	const [issues, setIssues] = useState<GovernanceIssueView[]>([]);
	const [loading, setLoading] = useState(false);
	const epoch = useRef(0);
	const load = useCallback(async () => {
		if (!open) return;
		const current = ++epoch.current;
		setLoading(true);
		const [detail, stats, notices] = await Promise.allSettled([
			datasetId && subjectKey ? getCatalogAssetGovernanceWorkspace(datasetId, subjectKey) : Promise.resolve(null),
			activeTab === "metrics" ? getCatalogLifecycleMetrics({ days: 30 }) : Promise.resolve(null),
			activeTab === "issues" ? getCatalogGovernanceIssues(100) : Promise.resolve([]),
		]);
		if (current !== epoch.current) return;
		setWorkspace(detail.status === "fulfilled" ? detail.value : null);
		setMetrics(stats.status === "fulfilled" ? stats.value : null);
		setIssues(notices.status === "fulfilled" && Array.isArray(notices.value) ? notices.value : []);
		setLoading(false);
	}, [open, datasetId, subjectKey, activeTab]);
	useEffect(() => {
		if (!open) setActiveTab("classification");
		void load();
		return () => {
			epoch.current += 1;
		};
	}, [load, open]);
	return { workspace, metrics, issues, loading, load, activeTab, setActiveTab };
}
