import { Button, Empty, message, Skeleton, Space, Typography } from "antd";
import { RefreshCw, RotateCcw, SlidersHorizontal } from "lucide-react";
import { useCallback, useEffect, useMemo, useState } from "react";
import { useSearchParams } from "react-router";
import workbenchService, {
	type WorkbenchComponentDescriptor,
	type WorkbenchPreferenceItem,
	type WorkbenchPreferencesResponse,
} from "@/api/services/workbenchService";
import { GLOBAL_CONFIG } from "@/global-config";
import { useUserInfo } from "@/store/userStore";
import WorkbenchCustomizeDrawer from "./components/WorkbenchCustomizeDrawer";
import DataManagementWorkbenchPage from "./DataManagementWorkbenchPage";
import { LeaderOverviewPage } from "./LeaderOverviewPage";
import {
	getWorkbenchComponent,
	isLeaderOverviewComponentKey,
	registryOrderOf,
	renderWorkbenchComponent,
	toWorkbenchComponentDescriptors,
	WORKBENCH_COMPONENT_REGISTRY,
} from "./workbenchComponentRegistry";
import {
	readLocalWorkbenchPreferenceItems,
	resetLocalWorkbenchPreferenceItems,
	resolveWorkbenchPreferenceOwner,
	saveLocalWorkbenchPreferenceItems,
} from "./workbenchLocalPreferences";
import { normalizeWorkbenchPreferenceItems } from "./workbenchPersonalizationModel";

const CUSTOMIZE_QUERY = "customize=1";
const CUSTOMIZE_QUERY_INDEX = CUSTOMIZE_QUERY.indexOf("=");
const CUSTOMIZE_QUERY_KEY = CUSTOMIZE_QUERY.slice(0, CUSTOMIZE_QUERY_INDEX);
const CUSTOMIZE_QUERY_VALUE = CUSTOMIZE_QUERY.slice(CUSTOMIZE_QUERY_INDEX + 1);

function normalizeAvailableComponents(
	components: WorkbenchComponentDescriptor[] | undefined,
): WorkbenchComponentDescriptor[] {
	const source = components?.length
		? components
		: WORKBENCH_COMPONENT_REGISTRY.map((component) => ({
				key: component.key,
				title: component.title,
				description: component.description,
				enabled: true,
				disabledReason: null,
			}));
	return source
		.filter((component) => Boolean(getWorkbenchComponent(component.key)))
		.map((component) => {
			const local = getWorkbenchComponent(component.key);
			return {
				key: component.key,
				title: component.title || local?.title || component.key,
				description: component.description || local?.description || null,
				enabled: component.enabled ?? true,
				disabledReason: component.disabledReason ?? null,
			};
		})
		.sort((a, b) => registryOrderOf(a.key) - registryOrderOf(b.key));
}

function normalizeSavedItems(items: WorkbenchPreferenceItem[]): WorkbenchPreferenceItem[] {
	return items.map((item, index) => ({
		key: item.key,
		visible: item.visible,
		order: (index + 1) * 10,
	}));
}

function buildLocalPreferenceResponse(items?: WorkbenchPreferenceItem[] | null): WorkbenchPreferencesResponse {
	const availableComponents = toWorkbenchComponentDescriptors();
	return {
		version: 0,
		availableComponents,
		items: normalizeWorkbenchPreferenceItems(items ?? undefined, availableComponents),
	};
}

export default function WorkbenchPage() {
	const [searchParams, setSearchParams] = useSearchParams();
	const [preferences, setPreferences] = useState<WorkbenchPreferencesResponse | null>(null);
	const [loading, setLoading] = useState<boolean>(true);
	const [saving, setSaving] = useState<boolean>(false);
	const [drawerOpen, setDrawerOpen] = useState<boolean>(
		() => searchParams.get(CUSTOMIZE_QUERY_KEY) === CUSTOMIZE_QUERY_VALUE,
	);
	const activeSection = searchParams.get("section");
	const productId = searchParams.get("productId");
	const userInfo = useUserInfo();
	const preferenceOwner = useMemo(() => resolveWorkbenchPreferenceOwner(userInfo), [userInfo]);

	const loadPreferences = useCallback(async (): Promise<void> => {
		setLoading(true);
		if (!GLOBAL_CONFIG.enableWorkbenchPreferenceApi) {
			setPreferences(buildLocalPreferenceResponse(readLocalWorkbenchPreferenceItems(preferenceOwner)));
			setLoading(false);
			return;
		}
		try {
			const next = await workbenchService.preferences();
			setPreferences(next);
		} catch (ex: unknown) {
			console.warn("[workbench] personal preferences are unavailable; using local defaults", ex);
			setPreferences(buildLocalPreferenceResponse(readLocalWorkbenchPreferenceItems(preferenceOwner)));
		} finally {
			setLoading(false);
		}
	}, [preferenceOwner]);

	useEffect(() => {
		void loadPreferences();
	}, [loadPreferences]);

	useEffect(() => {
		if (searchParams.get(CUSTOMIZE_QUERY_KEY) === CUSTOMIZE_QUERY_VALUE) {
			setDrawerOpen(true);
		}
	}, [searchParams]);

	const openCustomize = useCallback((): void => {
		const next = new URLSearchParams(searchParams);
		next.set(CUSTOMIZE_QUERY_KEY, CUSTOMIZE_QUERY_VALUE);
		setSearchParams(next, { replace: true });
		setDrawerOpen(true);
	}, [searchParams, setSearchParams]);

	const closeCustomize = useCallback((): void => {
		const next = new URLSearchParams(searchParams);
		next.delete(CUSTOMIZE_QUERY_KEY);
		setSearchParams(next, { replace: true });
		setDrawerOpen(false);
	}, [searchParams, setSearchParams]);

	const availableComponents = useMemo(
		() => normalizeAvailableComponents(preferences?.availableComponents),
		[preferences?.availableComponents],
	);
	const preferenceItems = useMemo(
		() => normalizeWorkbenchPreferenceItems(preferences?.items, availableComponents),
		[availableComponents, preferences?.items],
	);
	const visibleComponentKeys = useMemo(
		() => new Set(preferenceItems.filter((item) => item.visible).map((item) => item.key)),
		[preferenceItems],
	);
	const visibleEntries = useMemo(
		() => preferenceItems.filter((item) => item.visible && !isLeaderOverviewComponentKey(item.key)),
		[preferenceItems],
	);
	const hasLeaderOverviewComponents = preferenceItems.some(
		(item) => item.visible && isLeaderOverviewComponentKey(item.key),
	);
	const isEmptyWorkbench = visibleComponentKeys.size === 0;
	const showDataManagementSection = activeSection === "data-management" || activeSection === "consumption";

	const handleSave = useCallback(
		async (items: WorkbenchPreferenceItem[]): Promise<void> => {
			const nextItems = normalizeSavedItems(items);
			setSaving(true);
			try {
				if (!GLOBAL_CONFIG.enableWorkbenchPreferenceApi) {
					saveLocalWorkbenchPreferenceItems(preferenceOwner, nextItems);
					setPreferences(buildLocalPreferenceResponse(nextItems));
					message.success("工作台已更新");
					closeCustomize();
					return;
				}
				const next = await workbenchService.savePreferences({
					items: nextItems,
				});
				setPreferences(next);
				saveLocalWorkbenchPreferenceItems(preferenceOwner, next.items);
				message.success("工作台已更新");
				closeCustomize();
			} catch (ex: unknown) {
				console.warn("[workbench] failed to save personal preferences", ex);
				saveLocalWorkbenchPreferenceItems(preferenceOwner, nextItems);
				setPreferences(buildLocalPreferenceResponse(nextItems));
				message.success("工作台已更新");
				closeCustomize();
			} finally {
				setSaving(false);
			}
		},
		[closeCustomize, preferenceOwner],
	);

	const handleReset = useCallback(async (): Promise<void> => {
		setSaving(true);
		try {
			if (!GLOBAL_CONFIG.enableWorkbenchPreferenceApi) {
				resetLocalWorkbenchPreferenceItems(preferenceOwner);
				setPreferences(buildLocalPreferenceResponse(null));
				message.success("已恢复默认工作台");
				return;
			}
			const next = await workbenchService.resetPreferences();
			setPreferences(next);
			resetLocalWorkbenchPreferenceItems(preferenceOwner);
			message.success("已恢复默认工作台");
		} catch (ex: unknown) {
			console.warn("[workbench] failed to reset personal preferences", ex);
			resetLocalWorkbenchPreferenceItems(preferenceOwner);
			setPreferences(buildLocalPreferenceResponse(null));
			message.success("已恢复默认工作台");
		} finally {
			setSaving(false);
		}
	}, [preferenceOwner]);

	return (
		<div data-testid="platform-workbench-home">
			<div style={{ padding: "16px 16px 0" }}>
				<Space direction="vertical" size={12} style={{ width: "100%" }}>
					<Space wrap style={{ width: "100%", justifyContent: "space-between" }}>
						<Typography.Title level={3} style={{ margin: 0 }}>
							工作台
						</Typography.Title>
						<Space wrap>
							<Button icon={<SlidersHorizontal size={16} aria-hidden="true" />} type="primary" onClick={openCustomize}>
								自定义工作台
							</Button>
							<Button
								icon={<RefreshCw size={16} aria-hidden="true" />}
								loading={loading}
								onClick={() => void loadPreferences()}
							>
								刷新
							</Button>
							<Button
								icon={<RotateCcw size={16} aria-hidden="true" />}
								loading={saving}
								onClick={() => void handleReset()}
							>
								恢复默认
							</Button>
						</Space>
					</Space>
				</Space>
			</div>

			{loading && !preferences && (
				<div style={{ padding: 16 }}>
					<Skeleton active paragraph={{ rows: 3 }} />
				</div>
			)}

			{showDataManagementSection && (
				<div style={{ padding: 16 }}>
					<DataManagementWorkbenchPage
						embedded
						focus={activeSection === "consumption" ? "consumption" : "data-management"}
						productId={productId}
					/>
				</div>
			)}

			{!isEmptyWorkbench && hasLeaderOverviewComponents && (
				<LeaderOverviewPage visibleComponentKeys={visibleComponentKeys} />
			)}

			{!isEmptyWorkbench && visibleEntries.length > 0 && (
				<div
					style={{
						padding: "0 16px 16px",
						display: "grid",
						gridTemplateColumns: "repeat(auto-fit, minmax(220px, 1fr))",
						gap: 12,
					}}
				>
					{visibleEntries.map((item) => (
						<div key={item.key}>{renderWorkbenchComponent(item.key)}</div>
					))}
				</div>
			)}

			{isEmptyWorkbench && (
				<div style={{ padding: 32 }}>
					<Empty description="当前工作台未选择任何组件">
						<Button type="primary" icon={<SlidersHorizontal size={16} aria-hidden="true" />} onClick={openCustomize}>
							自定义工作台
						</Button>
					</Empty>
				</div>
			)}

			<WorkbenchCustomizeDrawer
				open={drawerOpen}
				availableComponents={availableComponents}
				items={preferenceItems}
				saving={saving}
				onClose={closeCustomize}
				onSave={handleSave}
				onReset={handleReset}
			/>
		</div>
	);
}
