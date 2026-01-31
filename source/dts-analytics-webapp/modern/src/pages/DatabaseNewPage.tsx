import { useEffect, useMemo, useState } from "react";
import { Link, useNavigate } from "react-router";
import { analyticsApi, type PlatformDataSourceItem } from "../api/analyticsApi";
import { PageContainer, PageHeader, Breadcrumb } from "../components/PageContainer/PageContainer";
import { EmptyState } from "../components/EmptyState";
import { ErrorNotice } from "../components/ErrorNotice";
import { Card, CardHeader, CardBody, CardFooter } from "../ui/Card/Card";
import { Button } from "../ui/Button/Button";
import { SearchInput } from "../ui/Input/Input";
import { Badge } from "../ui/Badge/Badge";
import { Spinner } from "../ui/Loading/Spinner";
import { getEffectiveLocale, t, type Locale } from "../i18n";
import "./page.css";

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
		reload();
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
			navigate(`/data/${encodeURIComponent(String(createdId))}`, { replace: true });
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
				subtitle={t(locale, "data.platformHint")}
				breadcrumbs={
					<Breadcrumb items={[
						{ label: t(locale, "data.title"), href: "/data" },
						{ label: t(locale, "data.add") }
					]} />
				}
			/>

			<Card>
				<CardHeader title={t(locale, "data.platformSources")} icon={<DatabaseIcon />} />
				<CardBody>
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

					<div style={{ display: "flex", gap: "var(--spacing-sm)", marginBottom: "var(--spacing-md)" }}>
						<SearchInput
							label={t(locale, "common.search")}
							value={query}
							onChange={(e) => setQuery(e.target.value)}
							placeholder={t(locale, "search.placeholder")}
							onClear={() => setQuery("")}
						/>
						<Button variant="secondary" onClick={reload}>
							{t(locale, "common.refresh")}
						</Button>
					</div>

					{state.state === "loading" && (
						<div className="loading-container" style={{ padding: "var(--spacing-xl)" }}>
							<Spinner size="lg" />
						</div>
					)}

					{state.state === "loaded" && filtered.length === 0 && (
						<EmptyState
							title={t(locale, "data.platformEmpty")}
							description={t(locale, "data.platformEmptyDesc")}
						/>
					)}

					{state.state === "loaded" && filtered.length > 0 && (
						<div className="grid3">
							{filtered.map((item) => (
								<Card key={item.id} variant="hoverable" style={{ height: "100%" }}>
									<CardBody>
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
												<p className="text-muted" style={{ margin: "var(--spacing-xs) 0 0", fontSize: "var(--font-size-sm)" }}>
													{t(locale, "common.id")}: {item.id}
												</p>
												{item.description ? (
													<p className="text-muted" style={{ margin: "var(--spacing-xs) 0 0", fontSize: "var(--font-size-sm)" }}>
														{item.description}
													</p>
												) : null}
											</div>
											<div style={{ display: "flex", flexDirection: "column", alignItems: "flex-end", gap: "var(--spacing-xs)" }}>
												<Badge variant="default" size="sm">
													{item.type || "JDBC"}
												</Badge>
												{item.status ? (
													<Badge variant={item.status === "active" ? "success" : "default"} size="sm">
														{item.status}
													</Badge>
												) : null}
											</div>
										</div>
										<div style={{ display: "flex", justifyContent: "flex-end", marginTop: "var(--spacing-md)" }}>
											<Button
												variant="primary"
												icon={<PlusIcon />}
												loading={importingId === item.id}
												onClick={() => importSource(item)}
											>
												{t(locale, "data.import")}
											</Button>
										</div>
									</CardBody>
								</Card>
							))}
						</div>
					)}
				</CardBody>
				<CardFooter align="between">
					<Link to="/data">
						<Button variant="tertiary">
							{t(locale, "common.open")} {t(locale, "data.title")}
						</Button>
					</Link>
				</CardFooter>
			</Card>
		</PageContainer>
	);
}
