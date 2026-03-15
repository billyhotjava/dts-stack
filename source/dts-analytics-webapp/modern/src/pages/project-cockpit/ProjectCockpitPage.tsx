import { useEffect, useMemo, useState } from "react";
import type { Locale } from "../../i18n";
import { getEffectiveLocale } from "../../i18n";
import {
	analyticsApi,
	type ProjectCockpitFilterQuery,
	type ProjectCockpitSummaryResponse,
} from "../../api/analyticsApi";
import { PageContainer } from "../../components/PageContainer/PageContainer";
import { ProjectCockpitProvider, useProjectCockpitContext } from "./ProjectCockpitContext";
import { ProjectCockpitLayout } from "./ProjectCockpitLayout";

function toFilterQuery(state: {
	programId: string;
	majorProjectId: string;
	dateFrom: string;
	dateTo: string;
	deptId: string;
	riskLevel: string;
}): ProjectCockpitFilterQuery {
	return {
		programId: state.programId || undefined,
		majorProjectId: state.majorProjectId || undefined,
		dateFrom: state.dateFrom || undefined,
		dateTo: state.dateTo || undefined,
		deptId: state.deptId || undefined,
		riskLevel: state.riskLevel || undefined,
	};
}

function ProjectCockpitPageInner() {
	const locale: Locale = useMemo(() => getEffectiveLocale(), []);
	const { queryState } = useProjectCockpitContext();
	const [summary, setSummary] = useState<ProjectCockpitSummaryResponse | null>(null);
	const [loading, setLoading] = useState(true);
	const [error, setError] = useState<unknown>(null);

	const filters = useMemo(
		() => toFilterQuery(queryState),
		[
			queryState.dateFrom,
			queryState.dateTo,
			queryState.deptId,
			queryState.majorProjectId,
			queryState.programId,
			queryState.riskLevel,
		],
	);

	useEffect(() => {
		let cancelled = false;
		setLoading(true);
		setError(null);
		analyticsApi
			.getProjectCockpitSummary(filters)
			.then((value) => {
				if (cancelled) {
					return;
				}
				setSummary(value);
				setLoading(false);
			})
			.catch((requestError) => {
				if (cancelled) {
					return;
				}
				setError(requestError);
				setLoading(false);
			});
		return () => {
			cancelled = true;
		};
	}, [filters]);

	return (
		<PageContainer maxWidth="full" padding="lg">
			<ProjectCockpitLayout
				locale={locale}
				summary={summary}
				summaryLoading={loading}
				summaryError={error}
			/>
		</PageContainer>
	);
}

export default function ProjectCockpitPage() {
	return (
		<ProjectCockpitProvider>
			<ProjectCockpitPageInner />
		</ProjectCockpitProvider>
	);
}
