// @ts-nocheck — migrated from analytics-webapp, pending unused-import cleanup
import { useEffect, useMemo, useState } from "react";
import { Link, useNavigate } from "react-router";
import { analyticsApi, type PlatformDataSourceItem, type CurrentUser } from "../api/analyticsApi";
import UploadedDataEditor from "../components/UploadedDataEditor";
import { PageContainer, PageHeader, Breadcrumb } from "../components/PageContainer/PageContainer";
import { EmptyState } from "../components/EmptyState";
import { ErrorNotice } from "../components/ErrorNotice";
import { Input, Spin, Button, Card, Tag } from "antd";
import { getEffectiveLocale, t, type Locale } from "../i18n";
type LoadState<T> =
	| { state: "loading" }
	| { state: "loaded"; value: T }
	| { state: "error"; error: unknown };

const DatabaseIcon = () => (
	<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
		<ellipse cx="12" cy="5" rx="9" ry="3" />
		<path d="M3 5v14a9 3 0 0 0 18 0V5" />
		<path d="M3 12a9 3 0 0 0 18 0" />
	</svg>
);

const PlusIcon = () => (
	<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
		<path d="M5 12h14" />
		<path d="M12 5v14" />
	</svg>
);

const normalizeType = (value?: string | null) => String(value || "").trim().toLowerCase();

const resolveEngine = (type?: string | null, jdbcUrl?: string | null) => {
	const normalized = normalizeType(type);
	if (normalized === "postgresql" || normalized === "postgres" || normalized === "pg") return "postgres";
	if (normalized === "mysql" || normalized === "mariadb") return "mysql";
	if (normalized === "oracle") return "oracle";
	if (normalized === "dm" || normalized === "dameng") return "dm";
	const url = String(jdbcUrl || "").toLowerCase();
	if (url.startsWith("jdbc:postgresql:")) return "postgres";
	if (url.startsWith("jdbc:mysql:")) return "mysql";
	if (url.startsWith("jdbc:oracle:")) return "oracle";
	if (url.startsWith("jdbc:dm:")) return "dm";
	return normalized || "jdbc";
};

export default function DatabaseNewPage() {
	const locale: Locale = useMemo(() => getEffectiveLocale(), []);
	const navigate = useNavigate();

	const [state, setState] = useState<LoadState<PlatformDataSourceItem[]>>({ state: "loading" });
	const [query, setQuery] = useState("");
	const [importingId, setImportingId] = useState<string | null>(null);
	const [okMessage, setOkMessage] = useState("");
	const [error, setError] = useState<unknown>(null);
	const [currentUser, setCurrentUser] = useState<CurrentUser | null>(null);
	const [dataLakeId, setDataLakeId] = useState<number | null>(null);

	const searchParams = new URLSearchParams(window.location.search);
	const [activeTab, setActiveTab] = useState<'platform' | 'other'>(
		searchParams.get('tab') === 'other' ? 'other' : 'platform'
	);
	const [databases, setDatabases] = useState<Array<{ id: number; name: string }>>([]);
	const [selectedDbId, setSelectedDbId] = useState<number | null>(null);

	const isDataAdmin = currentUser?.is_data_admin || currentUser?.is_superuser || false;

	const reload = () => {
		setState({ state: "loading" });
		setError(null);
		analyticsApi
			.listPlatformDataSources()
			.then((list) => {
				setState({ state: "loaded", value: Array.isArray(list) ? list : [] });
			})
			.catch((e) => {
				setState({ state: "error", error: e });
				setError(e);
			});
	};

	useEffect(() => {
		analyticsApi.getCurrentUser().then(setCurrentUser).catch(() => {});
	}, []);

	useEffect(() => {
		if (currentUser && !isDataAdmin) {
			setActiveTab('other');
		}
	}, [currentUser, isDataAdmin]);

	useEffect(() => {
		if (isDataAdmin) {
			reload();
		}
	}, [isDataAdmin]);

	useEffect(() => {
		analyticsApi.listDatabases().then((list: any) => {
			const dbs = (Array.isArray(list) ? list : list?.data || []);
			const dbList = dbs.map((d: any) => ({ id: d.id, name: d.name }));
			setDatabases(dbList);
			const lake = dbs.find((d: any) => d.is_system === true);
			if (lake) {
				setDataLakeId(lake.id);
				setSelectedDbId(lake.id);
			} else if (dbList.length > 0) {
				setSelectedDbId(dbList[0].id);
			}
		}).catch(() => {});
	}, []);

	const filtered = useMemo(() => {
		if (state.state !== "loaded") return [];
		const needle = query.trim().toLowerCase();
		if (!needle) return state.value;
		return state.value.filter((item) => {
			const name = String(item.name || "").toLowerCase();
			const type = String(item.type || "").toLowerCase();
			const url = String(item.jdbcUrl || "").toLowerCase();
			return name.includes(needle) || type.includes(needle) || url.includes(needle);
		});
	}, [state, query]);

	async function importSource(item: PlatformDataSourceItem) {
		setImportingId(item.id);
		setOkMessage("");
		setError(null);
		try {
			const engine = resolveEngine(item.type, item.jdbcUrl);
			const response = await analyticsApi.createDatabase({
				name: item.name || `${engine}-db`,
				engine,
				details: { platformDataSourceId: item.id },
			});
			const createdId = (response as any)?.id;
			if (!createdId) {
				setOkMessage(t(locale, "data.created"));
				return;
			}
			await analyticsApi.syncDatabaseSchema(createdId);
			setOkMessage(t(locale, "data.synced"));
			navigate(`/analytics/data/${encodeURIComponent(String(createdId))}`, { replace: true });
		} catch (e) {
			setError(e);
		} finally {
			setImportingId(null);
		}
	}

	return (
		<PageContainer>
			<PageHeader
				title={t(locale, "data.add")}
				breadcrumbs={
					<Breadcrumb items={[
						{ label: t(locale, "data.title"), href: "/analytics/data" },
						{ label: t(locale, "data.add") }
					]} />
				}
			/>

			<Card>
				<div style={{ display: 'flex', borderBottom: '1px solid var(--color-border)', marginBottom: 'var(--spacing-md)' }}>
					{isDataAdmin && (
						<button type="button" onClick={() => setActiveTab('platform')}
							style={{
								padding: 'var(--spacing-sm) var(--spacing-md)',
								border: 'none',
								background: 'none',
								cursor: 'pointer',
								fontSize: 'var(--font-size-md)',
								fontWeight: activeTab === 'platform' ? 'var(--font-weight-semibold)' : 'normal',
								color: activeTab === 'platform' ? 'var(--color-brand)' : 'var(--color-text-secondary)',
								borderBottom: activeTab === 'platform' ? '2px solid var(--color-brand)' : '2px solid transparent',
							}}
						>
							{t(locale, 'data.tabPlatform')}
						</button>
					)}
					<button
						type="button"
						onClick={() => setActiveTab('other')}
						style={{
							padding: 'var(--spacing-sm) var(--spacing-md)',
							border: 'none',
							background: 'none',
							cursor: 'pointer',
							fontSize: 'var(--font-size-md)',
							fontWeight: activeTab === 'other' ? 'var(--font-weight-semibold)' : 'normal',
							color: activeTab === 'other' ? 'var(--color-brand)' : 'var(--color-text-secondary)',
							borderBottom: activeTab === 'other' ? '2px solid var(--color-brand)' : '2px solid transparent',
						}}
					>
						{t(locale, 'data.tabOther')}
					</button>
				</div>
					{error ? <ErrorNotice locale={locale} error={error} /> : null}
					{okMessage && (
						<div style={{
							display: "flex",
							alignItems: "center",
							gap: "var(--spacing-sm)",
							marginBottom: "var(--spacing-md)",
							padding: "var(--spacing-sm)",
							background: "var(--color-success-bg)",
							borderRadius: "var(--radius-sm)",
							color: "var(--color-success)",
						}}>
							<PlusIcon />
							{okMessage}
						</div>
					)}

					{activeTab === 'platform' && (
						<>
							<div style={{ display: "flex", gap: "var(--spacing-sm)", marginBottom: "var(--spacing-md)" }}>
								<Input.Search
									value={query}
									onChange={(e) => setQuery(e.target.value)}
									placeholder={t(locale, "search.placeholder")}
									allowClear
									onSearch={() => {}}
								/>
								<Button type="default" onClick={reload}>
									{t(locale, "common.refresh")}
								</Button>
							</div>

							{state.state === "loading" && (
								<div className="loading-container" style={{ padding: "var(--spacing-xl)" }}>
									<Spin size="large" />
								</div>
							)}

							{state.state === "loaded" && filtered.length === 0 && (
								<EmptyState
									title={t(locale, "data.platformEmpty")}
									description={t(locale, "data.platformEmptyDesc")}
								/>
							)}

							{state.state === "loaded" && filtered.length > 0 && (
								<div className="grid grid-cols-3 gap-md">
									{filtered.map((item) => (
										<Card key={item.id} hoverable style={{ height: "100%" }}>
												<div style={{ display: "flex", alignItems: "flex-start", gap: "var(--spacing-md)" }}>
													<div style={{
														display: "flex",
														alignItems: "center",
														justifyContent: "center",
														width: 40,
														height: 40,
														borderRadius: "var(--radius-md)",
														background: "var(--color-bg-hover)",
														color: "var(--color-brand)",
														flexShrink: 0,
													}}>
														<DatabaseIcon />
													</div>
													<div style={{ flex: 1, minWidth: 0 }}>
														<h3 style={{ margin: 0, fontSize: "var(--font-size-md)", fontWeight: "var(--font-weight-semibold)" }}>
															{item.name || item.id}
														</h3>
														<p className="text-secondary" style={{ margin: "var(--spacing-xs) 0 0", fontSize: "var(--font-size-sm)" }}>
															{t(locale, "common.id")}: {item.id}
														</p>
														{item.description ? (
															<p className="text-secondary" style={{ margin: "var(--spacing-xs) 0 0", fontSize: "var(--font-size-sm)" }}>
																{item.description}
															</p>
														) : null}
													</div>
													<div style={{ display: "flex", flexDirection: "column", alignItems: "flex-end", gap: "var(--spacing-xs)" }}>
														<Tag>
															{item.type || "JDBC"}
														</Tag>
														{item.status ? (
															<Tag color={item.status === "active" ? "success" : undefined}>
																{item.status}
															</Tag>
														) : null}
													</div>
												</div>
												<div style={{ display: "flex", justifyContent: "flex-end", marginTop: "var(--spacing-md)" }}>
													<Button
														type="primary"
														icon={<PlusIcon />}
														loading={importingId === item.id}
														onClick={() => importSource(item)}
													>
														{t(locale, "data.import")}
													</Button>
												</div>
										</Card>
									))}
								</div>
							)}
						</>
					)}

					{activeTab === 'other' && (
						<div>
							<p style={{ color: 'var(--color-text-secondary)', fontSize: 'var(--font-size-sm)', marginBottom: 'var(--spacing-md)' }}>
								{t(locale, 'data.uploadDesc')}
							</p>

							{dataLakeId ? (
								<UploadedDataEditor
									databaseId={dataLakeId}
									onComplete={(result: { tableName: string; schema: string; rowCount: number }) => {
										setOkMessage(`导入成功: ${result.tableName} (${result.rowCount} 行)`);
										if (dataLakeId) {
											analyticsApi.syncDatabaseSchema(dataLakeId).then(() => {
												navigate(`/analytics/data/${dataLakeId}`, { replace: true });
											}).catch(() => {
												navigate(`/analytics/data/${dataLakeId}`, { replace: true });
											});
										}
									}}
								/>
							) : (
								<EmptyState
									title="数据湖未就绪"
									description="内置数据湖尚未初始化，请联系管理员。"
								/>
							)}
						</div>
					)}
				<div style={{ display: "flex", justifyContent: "space-between", padding: "var(--spacing-md)", borderTop: "1px solid var(--color-border)" }}>
					<Link to="/analytics/data">
						<Button type="text">
							{t(locale, "common.open")} {t(locale, "data.title")}
						</Button>
					</Link>
				</div>
			</Card>
		</PageContainer>
	);
}
