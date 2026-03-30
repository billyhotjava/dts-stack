import { useEffect, useMemo, useState } from "react";
import { Link } from "react-router";
import { analyticsApi, type DatabaseListItem, type CurrentUser } from "../api/analyticsApi";
import { PageContainer, PageHeader } from "../components/PageContainer/PageContainer";
import { EmptyState } from "../components/EmptyState";
import { ErrorNotice } from "../components/ErrorNotice";
import { Spin, Button, Card, Tag } from "antd";
import { getEffectiveLocale, t, type Locale } from "../i18n";
type LoadState<T> =
	| { state: "loading" }
	| { state: "loaded"; value: T }
	| { state: "error"; error: unknown };

// Icons
const PlusIcon = () => (
	<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
		<path d="M5 12h14" />
		<path d="M12 5v14" />
	</svg>
);

const DatabaseIcon = () => (
	<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
		<ellipse cx="12" cy="5" rx="9" ry="3" />
		<path d="M3 5v14a9 3 0 0 0 18 0V5" />
		<path d="M3 12a9 3 0 0 0 18 0" />
	</svg>
);

const TrashIcon = () => (
	<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
		<polyline points="3 6 5 6 21 6" />
		<path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2" />
	</svg>
);

export default function DataPage() {
	const locale: Locale = useMemo(() => getEffectiveLocale(), []);
	const [state, setState] = useState<LoadState<DatabaseListItem[]>>({ state: "loading" });
	const [currentUser, setCurrentUser] = useState<CurrentUser | null>(null);
	const [deleting, setDeleting] = useState<number | null>(null);
	const [confirmDeleteId, setConfirmDeleteId] = useState<number | null>(null);

	const reload = () => {
		setState({ state: "loading" });
		analyticsApi
			.listDatabases()
			.then((r) => {
				setState({ state: "loaded", value: r.data ?? [] });
			})
			.catch((e) => {
				setState({ state: "error", error: e });
			});
	};

	useEffect(() => {
		analyticsApi.getCurrentUser().then(setCurrentUser).catch(() => {});
	}, []);

	const isDataAdmin = currentUser?.is_data_admin || currentUser?.is_superuser || false;

	useEffect(() => {
		reload();
	}, []);

	async function handleDelete(id: number) {
		setDeleting(id);
		try {
			await analyticsApi.deleteDatabase(id);
			setConfirmDeleteId(null);
			reload();
		} catch (e) {
			console.error("Delete failed:", e);
			alert(String(e));
		} finally {
			setDeleting(null);
		}
	}

	return (
		<PageContainer>
			<PageHeader
				title={t(locale, "data.title")}
				actions={
					isDataAdmin ? (
						<Link to="/data/new">
							<Button type="primary" icon={<PlusIcon />}>
								{t(locale, "data.add")}
							</Button>
						</Link>
					) : (
						<Link to="/data/new?tab=other">
							<Button type="primary" icon={<PlusIcon />}>
								上传数据
							</Button>
						</Link>
					)
				}
			/>

			{state.state === "loading" && (
				<Card>
					<div className="loading-container" style={{ padding: "var(--spacing-xl)" }}>
						<Spin size="large" />
					</div>
				</Card>
			)}
			{state.state === "error" && <ErrorNotice locale={locale} error={state.error} />}
			{state.state === "loaded" && state.value.length === 0 && (
				<EmptyState
					title={t(locale, "data.empty")}
					action={
						<Link to="/data/new">
							<Button type="primary" icon={<PlusIcon />}>
								{t(locale, "data.add")}
							</Button>
						</Link>
					}
				/>
			)}
			{state.state === "loaded" && state.value.length > 0 && (
				<div className="grid grid-cols-3 gap-md">
					{state.value.map((db) => (
						<Card key={db.id} hoverable style={{ height: "100%" }}>
							<div style={{ display: "flex", alignItems: "flex-start", gap: "var(--spacing-md)" }}>
								<Link to={`/data/${db.id}`} style={{ textDecoration: "none", display: "flex", alignItems: "center", justifyContent: "center", width: 40, height: 40, borderRadius: "var(--radius-md)", background: "var(--color-bg-hover)", color: "var(--color-brand)", flexShrink: 0 }}>
									<DatabaseIcon />
								</Link>
								<div style={{ flex: 1, minWidth: 0 }}>
									<Link to={`/data/${db.id}`} style={{ textDecoration: "none" }}>
										<h3 style={{ margin: 0, fontSize: "var(--font-size-md)", fontWeight: "var(--font-weight-semibold)", color: "var(--color-text-primary)" }}>
											{db.name ?? `db:${db.id}`}
										</h3>
									</Link>
									<p className="text-secondary" style={{ margin: "var(--spacing-xs) 0 0", fontSize: "var(--font-size-sm)" }}>
										{t(locale, "common.id")}: {db.id}
									</p>
								</div>
								<div style={{ display: "flex", gap: "var(--spacing-xs)", alignItems: "center" }}>
									{db.is_system && (
										<Tag color="blue">内置</Tag>
									)}
									<Tag>
										{db.engine ?? "-"}
									</Tag>
								</div>
							</div>
							{!db.is_system && isDataAdmin && (
								<div style={{ display: "flex", gap: "var(--spacing-xs)", marginTop: "var(--spacing-md)", justifyContent: "flex-end" }}>
									<Button
										type="text"
										size="small"
										icon={<TrashIcon />}
										onClick={() => setConfirmDeleteId(db.id)}
										style={{ color: "var(--color-error)" }}
									>
										{t(locale, "data.delete")}
									</Button>
								</div>
							)}
						</Card>
					))}
				</div>
			)}

			{/* Delete Confirmation Dialog */}
			{confirmDeleteId !== null && (
				<div style={{
					position: "fixed",
					inset: 0,
					background: "rgba(0, 0, 0, 0.5)",
					display: "flex",
					alignItems: "center",
					justifyContent: "center",
					zIndex: 1000,
				}}>
					<Card style={{ maxWidth: 400, width: "90%" }}>
						<h3 style={{ margin: "0 0 var(--spacing-md)", fontSize: "var(--font-size-lg)", fontWeight: "var(--font-weight-semibold)" }}>
							{t(locale, "data.delete")}
						</h3>
						<p style={{ margin: "0 0 var(--spacing-lg)", color: "var(--color-text-secondary)" }}>
							{t(locale, "data.deleteConfirm")}
						</p>
						<div style={{ display: "flex", gap: "var(--spacing-sm)", justifyContent: "flex-end" }}>
							<Button
								type="default"
								onClick={() => setConfirmDeleteId(null)}
								disabled={deleting !== null}
							>
								{t(locale, "common.cancel")}
							</Button>
							<Button
								type="primary"
								loading={deleting === confirmDeleteId}
								disabled={deleting !== null}
								onClick={() => handleDelete(confirmDeleteId)}
								style={{ background: "var(--color-error)" }}
							>
								{t(locale, "data.delete")}
							</Button>
						</div>
					</Card>
				</div>
			)}
		</PageContainer>
	);
}
