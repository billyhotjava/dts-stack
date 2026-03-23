import { useEffect, useMemo, useState } from "react";
import type { Locale } from "../../i18n";
import { getEffectiveLocale } from "../../i18n";
import {
	analyticsApi,
	type ProjectCockpitFilterQuery,
	type ProjectCockpitSettingsResponse,
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

type ContentProps = {
	settings: ProjectCockpitSettingsResponse | null;
	settingsLoading: boolean;
	settingsSaving: boolean;
	settingsError: unknown;
	onPublishPeriod: (periodStart: string, periodEnd: string) => Promise<void>;
};

function ProjectCockpitPageContent({
	settings,
	settingsLoading,
	settingsSaving,
	settingsError,
	onPublishPeriod,
}: ContentProps) {
	const locale: Locale = useMemo(() => getEffectiveLocale(), []);
	const { effectiveQueryState } = useProjectCockpitContext();
	const [summary, setSummary] = useState<ProjectCockpitSummaryResponse | null>(null);
	const [loading, setLoading] = useState(true);
	const [error, setError] = useState<unknown>(null);

	const filters = useMemo(
		() => toFilterQuery(effectiveQueryState),
		[
			effectiveQueryState.dateFrom,
			effectiveQueryState.dateTo,
			effectiveQueryState.deptId,
			effectiveQueryState.majorProjectId,
			effectiveQueryState.programId,
			effectiveQueryState.riskLevel,
		],
	);

	useEffect(() => {
		if (settingsLoading) {
			return;
		}
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
	}, [filters, settingsLoading]);

	return (
		<PageContainer maxWidth="full" padding="lg">
			<ProjectCockpitLayout
				locale={locale}
				settings={settings}
				settingsLoading={settingsLoading}
				settingsSaving={settingsSaving}
				settingsError={settingsError}
				onPublishPeriod={onPublishPeriod}
				summary={summary}
				summaryLoading={loading}
				summaryError={error}
			/>
		</PageContainer>
	);
}

export default function ProjectCockpitPage() {
	const [settings, setSettings] = useState<ProjectCockpitSettingsResponse | null>(null);
	const [settingsLoading, setSettingsLoading] = useState(true);
	const [settingsError, setSettingsError] = useState<unknown>(null);
	const [settingsSaving, setSettingsSaving] = useState(false);

	useEffect(() => {
		let cancelled = false;
		setSettingsLoading(true);
		setSettingsError(null);
		analyticsApi
			.getProjectCockpitSettings()
			.then((value) => {
				if (cancelled) return;
				setSettings(value);
			})
			.catch(() => {
				// Settings API may not exist yet (backend not deployed).
				// Degrade gracefully: treat as "no published period".
				if (cancelled) return;
				setSettings(null);
			})
			.finally(() => {
				if (cancelled) return;
				setSettingsLoading(false);
			});
		return () => {
			cancelled = true;
		};
	}, []);

	const handlePublishPeriod = async (periodStart: string, periodEnd: string) => {
		setSettingsSaving(true);
		setSettingsError(null);
		try {
			await analyticsApi.updateProjectCockpitSettings({ periodStart, periodEnd });
			const refreshed = await analyticsApi.getProjectCockpitSettings();
			setSettings(refreshed);
		} catch (requestError) {
			setSettingsError(requestError);
			throw requestError;
		} finally {
			setSettingsSaving(false);
		}
	};

	return (
		<ProjectCockpitProvider publishedPeriod={settings}>
			<ProjectCockpitPageContent
				settings={settings}
				settingsLoading={settingsLoading}
				settingsSaving={settingsSaving}
				settingsError={settingsError}
				onPublishPeriod={handlePublishPeriod}
			/>
		</ProjectCockpitProvider>
	);
}
