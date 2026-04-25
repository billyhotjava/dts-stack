import { Alert, Button, Col, Row, Space } from "antd";
import { useCallback, useEffect, useMemo, useState } from "react";
import workbenchService, { type LeaderOverviewResponse } from "@/api/services/workbenchService";
import { auditLog } from "@/utils/audit";
import { useWorkbenchRole } from "./hooks/useWorkbenchRole";
import {
	WorkbenchFilterBar,
	initialFilterState,
	type WorkbenchFilterState,
} from "./components/WorkbenchFilterBar";
import { KpiRow } from "./components/KpiRow";
import { DomainMatrix } from "./components/DomainMatrix";
import { TopReportsBlock } from "./components/TopReportsBlock";
import { CoreAssetsBlock } from "./components/CoreAssetsBlock";
import { ScreenStrip } from "./components/ScreenStrip";

/**
 * Sprint-15 F5/T04 — Leader-overview workbench page shell.
 *
 * Assembles the sticky filter bar (F3) on top, a KPI row (F4/T01),
 * optional business-domain heat strip (F4/T03, INST_LEADER only),
 * and the TOP reports / core assets columns (F5 wave A) below a
 * compact published-screen strip.
 *
 * Fetches `/workbench/leader-overview` on mount and on any filter
 * change, debounced by 150 ms. Errors surface as a retryable Alert.
 */

const DEBOUNCE_MS = 150;

export function LeaderOverviewPage() {
	const roleInfo = useWorkbenchRole();
	const [filter, setFilter] = useState<WorkbenchFilterState>(() => initialFilterState(roleInfo));
	const [data, setData] = useState<LeaderOverviewResponse | null>(null);
	const [loading, setLoading] = useState<boolean>(true);
	const [error, setError] = useState<Error | null>(null);

	// Page-enter audit (once per role/dept identity).
	useEffect(() => {
		auditLog("WORKBENCH_OVERVIEW_VIEW", {
			role: roleInfo.role,
			deptCode: roleInfo.deptCode,
		});
	}, [roleInfo.role, roleInfo.deptCode]);

	// Re-seed filter when the effective role or department changes (e.g. login swap).
	useEffect(() => {
		setFilter(initialFilterState(roleInfo));
	}, [roleInfo.role, roleInfo.deptCode]);

	const fetchData = useCallback(async (f: WorkbenchFilterState, signal: AbortSignal): Promise<void> => {
		if (signal.aborted) return;
		setLoading(true);
		setError(null);
		try {
			const resp = await workbenchService.leaderOverview({
				scope: f.scope,
				deptCode: f.deptCode,
				bizDomain: f.bizDomain,
				timeRange: f.timeRange,
			});
			if (signal.aborted) return;
			setData(resp);
		} catch (ex: unknown) {
			if (signal.aborted) return;
			setError(ex instanceof Error ? ex : new Error(String(ex)));
		} finally {
			if (!signal.aborted) setLoading(false);
		}
	}, []);

	// Serialize filter so rapid changes collapse into a single trailing fetch.
	const filterKey = useMemo(() => JSON.stringify(filter), [filter]);

	useEffect(() => {
		const controller = new AbortController();
		const id = setTimeout(() => {
			void fetchData(filter, controller.signal);
		}, DEBOUNCE_MS);
		return () => {
			clearTimeout(id);
			controller.abort();
		};
		// `filter` is structurally identified by filterKey; avoid thrashing on identity-only changes.
		// eslint-disable-next-line react-hooks/exhaustive-deps
	}, [filterKey, fetchData]);

	const handleRetry = useCallback((): void => {
		const controller = new AbortController();
		void fetchData(filter, controller.signal);
	}, [fetchData, filter]);

	const handleDomainSelect = useCallback((domain: string | null): void => {
		setFilter((prev) => ({ ...prev, bizDomain: domain }));
	}, []);

	const showMatrix =
		roleInfo.isInstLeader &&
		filter.bizDomainAvailable &&
		(data?.domainMatrix?.length ?? 0) > 0;

	return (
		<div data-testid="platform-workbench-page">
			<WorkbenchFilterBar value={filter} onChange={setFilter} />

			<div style={{ padding: 16 }}>
				<Space direction="vertical" size={16} style={{ width: "100%" }}>
					<ScreenStrip />

					{error && (
						<Alert
							type="error"
							showIcon
							message="暂时拿不到数据"
							description="请稍后重试。如问题持续，请联系管理员。"
							action={
								<Button size="small" onClick={handleRetry} data-testid="leader-overview-retry">
									重试
								</Button>
							}
						/>
					)}

					<KpiRow
						role={roleInfo.role}
						filter={filter}
						kpis={data?.kpis ?? null}
						loading={loading}
						error={Boolean(error)}
					/>

					{roleInfo.isInstLeader && (
						<DomainMatrix
							visible={showMatrix}
							cells={data?.domainMatrix ?? []}
							activeDomain={filter.bizDomain}
							onSelect={handleDomainSelect}
						/>
					)}

					<Row gutter={16}>
						<Col xs={24} md={16}>
							<TopReportsBlock
								role={roleInfo.role}
								items={data?.topReports ?? []}
								loading={loading}
							/>
						</Col>
						<Col xs={24} md={8}>
							<CoreAssetsBlock
								role={roleInfo.role}
								items={data?.topAssets ?? []}
								loading={loading}
							/>
						</Col>
					</Row>
				</Space>
			</div>
		</div>
	);
}

export default LeaderOverviewPage;
