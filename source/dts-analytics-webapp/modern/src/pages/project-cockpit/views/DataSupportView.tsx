import { toFilters, toTable } from "../utils/viewHelpers";
import { useEffect, useMemo, useState } from "react";
import type { Locale } from "../../../i18n";
import {
	analyticsApi,
	type ProjectCockpitDataSupportResponse,
} from "../../../api/analyticsApi";
import { ErrorNotice } from "../../../components/ErrorNotice";
import { DataTable } from "../../../components/DataTable";
import { Spin } from "antd";
import { DataSupportCard } from "../components";
import { useProjectCockpitContext } from "../ProjectCockpitContext";
import { buildDataSupportSnapshot } from "./dataSupportView.helpers";



export default function DataSupportView({ locale }: { locale: Locale }) {
	const { effectiveQueryState } = useProjectCockpitContext();
	const [data, setData] = useState<ProjectCockpitDataSupportResponse | null>(null);
	const [loading, setLoading] = useState(true);
	const [error, setError] = useState<unknown>(null);

	const filters = useMemo(
		() => toFilters(effectiveQueryState),
		[
			effectiveQueryState.dateFrom,
			effectiveQueryState.dateTo,
			effectiveQueryState.deptId,
			effectiveQueryState.majorProjectId,
			effectiveQueryState.riskLevel,
		],
	);

	useEffect(() => {
		let cancelled = false;
		setLoading(true);
		setError(null);
		analyticsApi
			.getProjectCockpitDataSupport(filters)
			.then((value) => {
				if (cancelled) return;
				setData(value);
				setLoading(false);
			})
			.catch((requestError) => {
				if (cancelled) return;
				setError(requestError);
				setLoading(false);
			});
		return () => {
			cancelled = true;
		};
	}, [filters]);

	const snapshot = buildDataSupportSnapshot(
		data?.lastUpdatedAt ?? "",
		data?.batch ?? null,
		data?.quality ?? null,
		(data?.coverage ?? []) as Array<Record<string, unknown>>,
		(data?.missingChecklist ?? []) as Array<Record<string, unknown>>,
		(data?.dataSources ?? []) as Array<Record<string, unknown>>,
	);

	const glossaryTable = useMemo(
		() =>
			toTable((data?.glossary ?? []) as Array<Record<string, unknown>>, [
				{ key: "indicator", label: "指标" },
				{ key: "description", label: "口径说明" },
			]),
		[data?.glossary],
	);

	return (
		<div className="project-cockpit__view">
			<div className="project-cockpit__support-metrics">
				<div className="project-cockpit__support-metric">
					<span className="project-cockpit__meta-label">最新更新时间</span>
					<strong>{snapshot.lastUpdatedAt || "--"}</strong>
				</div>
				<div className="project-cockpit__support-metric">
					<span className="project-cockpit__meta-label">最新批次</span>
					<strong>{snapshot.batchId || "--"}</strong>
				</div>
				<div className="project-cockpit__support-metric">
					<span className="project-cockpit__meta-label">批次状态</span>
					<strong>{snapshot.batchStatus || "--"}</strong>
				</div>
				<div className="project-cockpit__support-metric">
					<span className="project-cockpit__meta-label">问题记录数</span>
					<strong>{snapshot.issueCount}</strong>
				</div>
				<div className="project-cockpit__support-metric">
					<span className="project-cockpit__meta-label">未映射子项目</span>
					<strong>{snapshot.unmappedSubprojectCount}</strong>
				</div>
				<div className="project-cockpit__support-metric">
					<span className="project-cockpit__meta-label">未分类延期原因</span>
					<strong>{snapshot.unknownDelayReasonCount}</strong>
				</div>
			</div>

			<div className="project-cockpit__support-grid">
				<DataSupportCard title="批次与覆盖情况">
					{loading ? (
						<div className="project-cockpit__loading-card"><Spin size="large" /></div>
					) : (
						<div className="project-cockpit__checklist-list">
							<div className="project-cockpit__checklist-item">
								<strong>来源文件</strong>
								<span className="project-cockpit__milestone-meta">
									{String(data?.batch?.sourceFileName ?? "--")}
								</span>
							</div>
							<div className="project-cockpit__checklist-item">
								<strong>上传时间</strong>
								<span className="project-cockpit__milestone-meta">
									{String(data?.batch?.uploadedAt ?? "--")}
								</span>
							</div>
							<div className="project-cockpit__checklist-item">
								<strong>建模刷新时间</strong>
								<span className="project-cockpit__milestone-meta">
									{String(data?.batch?.refreshedAt ?? "--")}
								</span>
							</div>
							{(data?.coverage ?? []).map((item, index) => (
								<div key={`${item.label}-${index}`} className="project-cockpit__checklist-item">
									<strong>{String(item.label ?? "--")}</strong>
									<span className="project-cockpit__milestone-meta">{String(item.value ?? "--")}</span>
								</div>
							))}
						</div>
					)}
				</DataSupportCard>

				<DataSupportCard title="待补与修复入口">
					<div className="project-cockpit__checklist-list">
						{(data?.missingChecklist ?? []).map((item, index) => (
							<div key={`${item.id}-${index}`} className="project-cockpit__checklist-item">
								<strong>{String(item.title ?? "--")}</strong>
								<span className="project-cockpit__milestone-meta">{String(item.status ?? "--")}</span>
								<span className="project-cockpit__milestone-meta">{String(item.detail ?? "--")}</span>
							</div>
						))}
					</div>
				</DataSupportCard>
			</div>

			<DataSupportCard title="指标口径说明">
				<DataTable cols={glossaryTable.cols} rows={glossaryTable.rows} pageSize={8} />
			</DataSupportCard>

			<DataSupportCard title="正式数据来源">
				<div className="project-cockpit__source-list">
					{(data?.dataSources ?? []).map((item, index) => (
						<div key={`${item.name}-${index}`} className="project-cockpit__source-item">
							<strong>{String(item.name ?? "--")}</strong>
							<span>{String(item.description ?? "--")}</span>
						</div>
					))}
				</div>
			</DataSupportCard>

			{data?.dataState?.message ? (
				<DataSupportCard title="当前数据状态">
					<div className="project-cockpit__checklist-list">
						<div className="project-cockpit__checklist-item">
							<strong>状态说明</strong>
							<span className="project-cockpit__milestone-meta">
								{String(data.dataState.message)}
							</span>
						</div>
						<div className="project-cockpit__checklist-item">
							<strong>最新数据源</strong>
							<span className="project-cockpit__milestone-meta">{snapshot.latestSourceName || "--"}</span>
						</div>
						<div className="project-cockpit__checklist-item">
							<strong>待补清单数</strong>
							<span className="project-cockpit__milestone-meta">{snapshot.pendingChecklistCount}</span>
						</div>
					</div>
				</DataSupportCard>
			) : null}

			{error ? <ErrorNotice locale={locale} error={error} /> : null}
		</div>
	);
}
