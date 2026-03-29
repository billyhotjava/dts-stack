import { useEffect, useMemo, useState } from "react";
import { Link } from "react-router";
import { analyticsApi, type CurrentUser, type DashboardListItem, type CardListItem, type ScreenListItem } from "../api/analyticsApi";
import { ErrorNotice } from "../components/ErrorNotice";
import { PageContainer } from "../components/PageContainer/PageContainer";
import { Spin, Button, Card, Table, Tag } from "antd";
import { PlusOutlined } from "@ant-design/icons";
import type { ColumnsType } from "antd/es/table";
import { getEffectiveLocale, t, type Locale } from "../i18n";
type LoadState<T> =
	| { state: "loading" }
	| { state: "loaded"; value: T }
	| { state: "error"; error: unknown };

// Icons
const DashboardIcon = () => (
	<svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
		<rect width="7" height="9" x="3" y="3" rx="1" />
		<rect width="7" height="5" x="14" y="3" rx="1" />
		<rect width="7" height="9" x="14" y="12" rx="1" />
		<rect width="7" height="5" x="3" y="16" rx="1" />
	</svg>
);

const QuestionIcon = () => (
	<svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
		<rect width="18" height="18" x="3" y="3" rx="2" />
		<path d="M3 9h18" />
		<path d="M9 21V9" />
	</svg>
);

const DatabaseIcon = () => (
	<svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
		<ellipse cx="12" cy="5" rx="9" ry="3" />
		<path d="M3 5v14a9 3 0 0 0 18 0V5" />
		<path d="M3 12a9 3 0 0 0 18 0" />
	</svg>
);

const ScreenIcon = () => (
	<svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
		<rect x="3" y="4" width="18" height="12" rx="2" />
		<path d="M8 20h8" />
		<path d="M12 16v4" />
	</svg>
);

const ProjectCockpitIcon = () => (
	<svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
		<path d="M4 5h16v4H4z" />
		<path d="M4 11h7v8H4z" />
		<path d="M13 11h7v3h-7z" />
		<path d="M13 17h7v2h-7z" />
	</svg>
);

const PlusIcon = () => (
	<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
		<path d="M5 12h14" />
		<path d="M12 5v14" />
	</svg>
);

const ArrowRightIcon = () => (
	<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
		<path d="M5 12h14" />
		<path d="m12 5 7 7-7 7" />
	</svg>
);

export default function HomePage() {
	const locale: Locale = useMemo(() => getEffectiveLocale(), []);
	const [user, setUser] = useState<LoadState<CurrentUser>>({ state: "loading" });
	const [health, setHealth] = useState<LoadState<string>>({ state: "loading" });
	const [dashboards, setDashboards] = useState<LoadState<DashboardListItem[]>>({ state: "loading" });
	const [questions, setQuestions] = useState<LoadState<CardListItem[]>>({ state: "loading" });
	const [screens, setScreens] = useState<LoadState<ScreenListItem[]>>({ state: "loading" });

	useEffect(() => {
		let cancelled = false;

		analyticsApi
			.getCurrentUser()
			.then((value) => {
				if (cancelled) return;
				setUser({ state: "loaded", value });
			})
			.catch((e) => {
				if (cancelled) return;
				setUser({ state: "error", error: e });
			});

		analyticsApi
			.getHealth()
			.then((v) => {
				if (cancelled) return;
				setHealth({ state: "loaded", value: v?.status ?? "unknown" });
			})
			.catch((e) => {
				if (cancelled) return;
				setHealth({ state: "error", error: e });
			});

		analyticsApi
			.listDashboards()
			.then((value) => {
				if (cancelled) return;
				setDashboards({ state: "loaded", value: value.slice(0, 5) });
			})
			.catch((e) => {
				if (cancelled) return;
				setDashboards({ state: "error", error: e });
			});

		analyticsApi
			.listCards()
			.then((value) => {
				if (cancelled) return;
				setQuestions({ state: "loaded", value: value.slice(0, 5) });
			})
			.catch((e) => {
				if (cancelled) return;
				setQuestions({ state: "error", error: e });
			});

		analyticsApi
			.listScreens()
			.then((value) => {
				if (cancelled) return;
				const sorted = value
					.sort((a, b) => {
						const ta = new Date(a.updatedAt || 0).getTime();
						const tb = new Date(b.updatedAt || 0).getTime();
						return tb - ta;
					})
					.slice(0, 10);
				setScreens({ state: "loaded", value: sorted });
			})
			.catch((e) => {
				if (cancelled) return;
				setScreens({ state: "error", error: e });
			});

		return () => {
			cancelled = true;
		};
	}, []);

	const userName = (() => {
		if (user.state !== "loaded") return "";
		const value = user.value;
		return value.common_name || [value.first_name, value.last_name].filter(Boolean).join(" ") || value.email || "";
	})();

	const healthStatus = health.state === "loaded" ? health.value : "loading";

	const fmtTime = (raw?: string | null) => {
		if (!raw) return "-";
		try {
			const d = new Date(raw);
			return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")} ${String(d.getHours()).padStart(2, "0")}:${String(d.getMinutes()).padStart(2, "0")}`;
		} catch { return raw; }
	};

	const screenColumns: ColumnsType<ScreenListItem> = [
		{
			title: t(locale, "common.name"),
			dataIndex: "name",
			key: "name",
			ellipsis: true,
			render: (name: string, record) => (
				<Link to={`/screens/${record.id}/edit`} style={{ color: "var(--color-brand)", fontWeight: 500 }}>
					{name || t(locale, "common.untitled")}
				</Link>
			),
		},
		{
			title: t(locale, "common.description"),
			dataIndex: "description",
			key: "description",
			ellipsis: true,
			render: (desc: string | null) => <span style={{ color: "var(--color-text-secondary)" }}>{desc || "-"}</span>,
		},
		{
			title: "尺寸",
			key: "size",
			width: 110,
			render: (_, record) => <span style={{ color: "var(--color-text-secondary)", fontSize: 12 }}>{record.width ?? "-"} x {record.height ?? "-"}</span>,
		},
		{
			title: "状态",
			key: "status",
			width: 90,
			render: (_, record) => Number(record.publishedVersionNo || 0) > 0
				? <Tag color="green">v{record.publishedVersionNo}</Tag>
				: <Tag>草稿</Tag>,
		},
		{
			title: t(locale, "common.updatedAt"),
			dataIndex: "updatedAt",
			key: "updatedAt",
			width: 150,
			render: (v: string) => <span style={{ color: "var(--color-text-secondary)", fontSize: 12 }}>{fmtTime(v)}</span>,
		},
		{
			title: "",
			key: "actions",
			width: 120,
			render: (_, record) => (
				<div style={{ display: 'flex', gap: 8 }}>
					<Link to={`/screens/${record.id}/edit`}>
						<Button type="link" size="small">编辑</Button>
					</Link>
					<a
						href={`/analytics/screens/${encodeURIComponent(String(record.id))}/preview`}
						target="_blank"
						rel="noreferrer"
					>
						<Button type="link" size="small">预览</Button>
					</a>
				</div>
			),
		},
	];

	return (
		<PageContainer>
			{/* Error Notices */}
			{user.state === "error" && <ErrorNotice locale={locale} error={user.error} />}
			{health.state === "error" && <ErrorNotice locale={locale} error={health.error} />}

			{/* My Screens — full-width Table */}
			<Card
				title={t(locale, "home.myScreens")}
				extra={
					<div style={{ display: 'flex', gap: 8 }}>
						<Link to="/screens">
							<Button type="text" size="small" icon={<ArrowRightIcon />} iconPosition="end">
								{t(locale, "common.viewAll")}
							</Button>
						</Link>
						<Link to="/screens/new">
							<Button type="primary" size="small" icon={<PlusOutlined />}>
								{t(locale, "home.newScreen")}
							</Button>
						</Link>
					</div>
				}
				styles={{ body: { padding: 0 } }}
				style={{ marginBottom: 'var(--spacing-lg)' }}
			>
				{screens.state === "loading" && (
					<div className="loading-state"><Spin /></div>
				)}
				{screens.state === "error" && (
					<div className="error-state">{t(locale, "error")}</div>
				)}
				{screens.state === "loaded" && (
					<Table<ScreenListItem>
						columns={screenColumns}
						dataSource={screens.value}
						rowKey={(r) => String(r.id)}
						size="small"
						pagination={false}
						locale={{ emptyText: t(locale, "home.noScreens") }}
					/>
				)}
			</Card>

			{/* Recent Dashboards & Questions — 2 columns */}
			<div className="grid grid-cols-2 gap-md">
				{/* Recent Dashboards */}
				<Card
					title={t(locale, "home.recentDashboards")}
					extra={
						<Link to="/dashboards">
							<Button type="text" size="small" icon={<ArrowRightIcon />} iconPosition="end">
								{t(locale, "common.viewAll")}
							</Button>
						</Link>
					}
				>
						{dashboards.state === "loading" && (
							<div className="loading-state"><Spin /></div>
						)}
						{dashboards.state === "error" && (
							<div className="error-state">{t(locale, "error")}</div>
						)}
						{dashboards.state === "loaded" && dashboards.value.length === 0 && (
							<div className="empty-state-small">
								<p>{t(locale, "common.empty")}</p>
								<Link to="/dashboards/new">
									<Button type="primary" size="small" icon={<PlusOutlined />}>
										{t(locale, "dashboards.new")}
									</Button>
								</Link>
							</div>
						)}
						{dashboards.state === "loaded" && dashboards.value.length > 0 && (
							<ul className="item-list">
								{dashboards.value.map((d) => (
									<li key={d.id}>
										<Link to={`/dashboards/${d.id}`} className="item-list__link">
											<DashboardIcon />
											<span>{d.name || t(locale, "common.untitled")}</span>
										</Link>
									</li>
								))}
							</ul>
						)}
				</Card>

				{/* Recent Questions */}
				<Card
					title={t(locale, "home.recentQuestions")}
					extra={
						<Link to="/questions">
							<Button type="text" size="small" icon={<ArrowRightIcon />} iconPosition="end">
								{t(locale, "common.viewAll")}
							</Button>
						</Link>
					}
				>
						{questions.state === "loading" && (
							<div className="loading-state"><Spin /></div>
						)}
						{questions.state === "error" && (
							<div className="error-state">{t(locale, "error")}</div>
						)}
						{questions.state === "loaded" && questions.value.length === 0 && (
							<div className="empty-state-small">
								<p>{t(locale, "common.empty")}</p>
								<Link to="/questions/new">
									<Button type="primary" size="small" icon={<PlusOutlined />}>
										{t(locale, "questions.new")}
									</Button>
								</Link>
							</div>
						)}
						{questions.state === "loaded" && questions.value.length > 0 && (
							<ul className="item-list">
								{questions.value.map((q) => (
									<li key={q.id}>
										<Link to={`/questions/${q.id}`} className="item-list__link">
											<QuestionIcon />
											<span>{q.name || t(locale, "common.untitled")}</span>
										</Link>
									</li>
								))}
							</ul>
						)}
				</Card>
			</div>

			{/* Quick Actions — bottom section */}
			<div style={{ marginTop: "var(--spacing-xl)" }}>
				<h2 className="text-lg font-semibold text-primary mb-md">{t(locale, "home.quickActions")}</h2>
				<div className="grid grid-cols-3 gap-md">
					<Link to="/screens/new" className="quick-action-card">
						<div className="quick-action-card__icon">
							<ScreenIcon />
						</div>
						<div className="quick-action-card__content">
							<h3>自建大屏</h3>
							<p>创建自定义数据大屏，可视化展示关键指标。</p>
						</div>
					</Link>
					<Link to="/dashboards/new" className="quick-action-card">
						<div className="quick-action-card__icon">
							<DashboardIcon />
						</div>
						<div className="quick-action-card__content">
							<h3>自建分析看板</h3>
							<p>创建分析看板，组合多个图表进行数据分析。</p>
						</div>
					</Link>
				</div>
			</div>

			<style>{`
				.welcome-banner {
					display: flex;
					align-items: center;
					justify-content: space-between;
					padding: var(--spacing-lg) var(--spacing-xl);
					background: linear-gradient(135deg, var(--color-brand) 0%, var(--color-brand-dark) 100%);
					border-radius: var(--radius-lg);
					color: var(--color-text-inverse);
					margin-bottom: var(--spacing-xl);
					min-height: 64px;
				}

				.welcome-banner__title {
					margin: 0;
					font-size: var(--font-size-xl);
					font-weight: var(--font-weight-bold);
					line-height: 1;
				}

				.quick-action-card {
					display: flex;
					align-items: flex-start;
					gap: var(--spacing-md);
					padding: var(--spacing-lg);
					background: var(--color-bg-primary);
					border: 1px solid var(--color-border);
					border-radius: var(--radius-md);
					text-decoration: none;
					color: inherit;
					transition: box-shadow var(--transition-fast), border-color var(--transition-fast);
				}

				.quick-action-card:hover {
					border-color: var(--color-brand);
					box-shadow: var(--shadow-md);
				}

				.quick-action-card__icon {
					display: flex;
					align-items: center;
					justify-content: center;
					width: 48px;
					height: 48px;
					border-radius: var(--radius-md);
					background: var(--color-bg-hover);
					color: var(--color-brand);
					flex-shrink: 0;
				}

				.quick-action-card__content h3 {
					margin: 0;
					font-size: var(--font-size-md);
					font-weight: var(--font-weight-semibold);
					color: var(--color-text-primary);
				}

				.quick-action-card__content p {
					margin: var(--spacing-xs) 0 0;
					font-size: var(--font-size-sm);
					color: var(--color-text-secondary);
				}

				.loading-state {
					display: flex;
					justify-content: center;
					padding: var(--spacing-lg);
				}

				.error-state {
					padding: var(--spacing-md);
					text-align: center;
					color: var(--color-error);
				}

				.empty-state-small {
					display: flex;
					flex-direction: column;
					align-items: center;
					gap: var(--spacing-md);
					padding: var(--spacing-lg);
					text-align: center;
					color: var(--color-text-secondary);
				}

				.empty-state-small p {
					margin: 0;
				}

				.item-list {
					list-style: none;
					margin: 0;
					padding: 0;
				}

				.item-list li {
					border-bottom: 1px solid var(--color-border);
				}

				.item-list li:last-child {
					border-bottom: none;
				}

				.item-list__link {
					display: flex;
					align-items: center;
					gap: var(--spacing-sm);
					padding: var(--spacing-sm) var(--spacing-xs);
					color: var(--color-text-primary);
					text-decoration: none;
					transition: background-color var(--transition-fast);
					flex: 1 1 auto;
					min-width: 0;
				}

				.item-list__link:hover {
					background: var(--color-bg-hover);
				}

				.item-list__link svg {
					width: 16px;
					height: 16px;
					color: var(--color-text-tertiary);
					flex-shrink: 0;
				}

				.item-list__link span {
					min-width: 0;
					overflow: hidden;
					text-overflow: ellipsis;
					white-space: nowrap;
				}

				.item-list__row {
					display: flex;
					align-items: center;
					justify-content: space-between;
					gap: var(--spacing-sm);
				}

				.item-list__meta {
					padding-right: var(--spacing-xs);
					font-size: var(--font-size-xs);
					color: var(--color-text-tertiary);
					white-space: nowrap;
					flex: 0 0 auto;
				}
			`}</style>
		</PageContainer>
	);
}
