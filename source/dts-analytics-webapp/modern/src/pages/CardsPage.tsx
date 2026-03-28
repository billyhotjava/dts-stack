import { Link } from "react-router";
import { useCallback, useEffect, useMemo, useState, type JSX } from "react";
import { analyticsApi, type CardListItem } from "../api/analyticsApi";
import { PageContainer, PageHeader, EmptyState } from "../components/PageContainer/PageContainer";
import { Button, Card, Checkbox, Dropdown, Input, Modal, Skeleton, Tag, message } from "antd";
import { EllipsisOutlined, DeleteOutlined } from "@ant-design/icons";
import { CardGrid } from "../components/DashboardGrid/DashboardGrid";
import { ErrorNotice } from "../components/ErrorNotice";
import { getEffectiveLocale, t, type Locale } from "../i18n";
import "./page.css";

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

const QuestionIcon = () => (
	<svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
		<rect width="18" height="18" x="3" y="3" rx="2" />
		<path d="M3 9h18" />
		<path d="M9 21V9" />
	</svg>
);

const GridIcon = () => (
	<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
		<rect width="7" height="7" x="3" y="3" rx="1" />
		<rect width="7" height="7" x="14" y="3" rx="1" />
		<rect width="7" height="7" x="14" y="14" rx="1" />
		<rect width="7" height="7" x="3" y="14" rx="1" />
	</svg>
);

const ListIcon = () => (
	<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
		<line x1="8" x2="21" y1="6" y2="6" />
		<line x1="8" x2="21" y1="12" y2="12" />
		<line x1="8" x2="21" y1="18" y2="18" />
		<line x1="3" x2="3.01" y1="6" y2="6" />
		<line x1="3" x2="3.01" y1="12" y2="12" />
		<line x1="3" x2="3.01" y1="18" y2="18" />
	</svg>
);

const displayTypeIcons: Record<string, () => JSX.Element> = {
	table: () => (
		<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
			<rect width="18" height="18" x="3" y="3" rx="2" />
			<path d="M3 9h18" />
			<path d="M3 15h18" />
			<path d="M9 3v18" />
		</svg>
	),
	bar: () => (
		<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
			<line x1="12" x2="12" y1="20" y2="10" />
			<line x1="18" x2="18" y1="20" y2="4" />
			<line x1="6" x2="6" y1="20" y2="14" />
		</svg>
	),
	line: () => (
		<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
			<path d="M3 3v18h18" />
			<path d="m19 9-5 5-4-4-3 3" />
		</svg>
	),
	pie: () => (
		<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
			<path d="M21.21 15.89A10 10 0 1 1 8 2.83" />
			<path d="M22 12A10 10 0 0 0 12 2v10z" />
		</svg>
	),
	scalar: () => (
		<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
			<path d="M12 2v20" />
			<path d="M2 12h20" />
		</svg>
	),
};

function getDisplayTypeIcon(display?: string) {
	const IconComponent = displayTypeIcons[display || ""] || displayTypeIcons.table;
	return <IconComponent />;
}

export default function CardsPage() {
	const locale: Locale = useMemo(() => getEffectiveLocale(), []);
	const [state, setState] = useState<LoadState<CardListItem[]>>({ state: "loading" });
	const [viewMode, setViewMode] = useState<"grid" | "list">("grid");
	const [searchQuery, setSearchQuery] = useState("");
	const [selectedIds, setSelectedIds] = useState<Set<number>>(new Set());

	const loadCards = useCallback(() => {
		analyticsApi
			.listCards()
			.then((value) => {
				setState({ state: "loaded", value });
			})
			.catch((e) => {
				setState({ state: "error", error: e });
			});
	}, []);

	useEffect(() => {
		loadCards();
	}, [loadCards]);

	const filteredCards = useMemo(() => {
		if (state.state !== "loaded") return [];
		if (!searchQuery.trim()) return state.value;
		const query = searchQuery.toLowerCase();
		return state.value.filter((c) =>
			(c.name || "").toLowerCase().includes(query) ||
			(c.description || "").toLowerCase().includes(query)
		);
	}, [state, searchQuery]);

	/* ---- Delete handlers ---- */
	const handleDelete = (id: number, name: string) => {
		Modal.confirm({
			title: "移至回收站",
			content: `确定将「${name}」移至回收站？`,
			okText: "确定",
			cancelText: "取消",
			okButtonProps: { danger: true },
			onOk: async () => {
				await analyticsApi.deleteCard(id);
				message.success("已移至回收站");
				setSelectedIds((prev) => { const n = new Set(prev); n.delete(id); return n; });
				loadCards();
			},
		});
	};

	const handleBatchDelete = () => {
		if (selectedIds.size === 0) return;
		Modal.confirm({
			title: "批量移至回收站",
			content: `确定将 ${selectedIds.size} 项移至回收站？`,
			okText: "确定",
			cancelText: "取消",
			okButtonProps: { danger: true },
			onOk: async () => {
				const ids = Array.from(selectedIds);
				const results = await Promise.allSettled(ids.map((id) => analyticsApi.deleteCard(id)));
				const failed = results.filter((r) => r.status === "rejected").length;
				if (failed > 0) message.warning(`${ids.length - failed} 项已移至回收站，${failed} 项失败`);
				else message.success(`${ids.length} 项已移至回收站`);
				setSelectedIds(new Set());
				loadCards();
			},
		});
	};

	/* ---- Selection helpers ---- */
	const toggleSelect = (id: number) => {
		setSelectedIds((prev) => {
			const next = new Set(prev);
			if (next.has(id)) next.delete(id); else next.add(id);
			return next;
		});
	};
	const toggleSelectAll = () => {
		if (selectedIds.size === filteredCards.length) setSelectedIds(new Set());
		else setSelectedIds(new Set(filteredCards.map((c) => c.id)));
	};

	return (
		<PageContainer>
			<PageHeader
				title={t(locale, "questions.title")}
				actions={
					<Link to="/questions/new">
						<Button type="primary" icon={<PlusIcon />}>
							{t(locale, "questions.new")}
						</Button>
					</Link>
				}
			/>

			{/* Batch Action Bar */}
			{selectedIds.size > 0 && (
				<div style={{ display: "flex", alignItems: "center", gap: 12, padding: "8px 12px", background: "#f0f5ff", borderRadius: 6, marginBottom: 8 }}>
					<Checkbox
						checked={selectedIds.size === filteredCards.length}
						indeterminate={selectedIds.size > 0 && selectedIds.size < filteredCards.length}
						onChange={toggleSelectAll}
					/>
					<span>已选 {selectedIds.size} 项</span>
					<Button size="small" danger icon={<DeleteOutlined />} onClick={handleBatchDelete}>移至回收站</Button>
					<Button size="small" type="text" onClick={() => setSelectedIds(new Set())}>取消选择</Button>
				</div>
			)}

			{/* Filter Bar */}
			<div className="filterBar">
				<div style={{ flex: 1, maxWidth: 320 }}>
					<Input.Search
						placeholder={t(locale, "common.search")}
						value={searchQuery}
						onChange={(e) => setSearchQuery(e.target.value)}
						allowClear
						onSearch={() => {}}
					/>
				</div>
				<div style={{ marginLeft: "auto", display: "flex", gap: "var(--spacing-xs)" }}>
					<Button
						type={viewMode === "grid" ? "primary" : "default"}
						size="small"
						icon={<GridIcon />}
						onClick={() => setViewMode("grid")}
						aria-label={t(locale, "common.viewAll")} // Grid view
					/>
					<Button
						type={viewMode === "list" ? "primary" : "default"}
						size="small"
						icon={<ListIcon />}
						onClick={() => setViewMode("list")}
						aria-label={t(locale, "common.viewAll")} // List view
					/>
				</div>
			</div>

			{/* Loading State */}
			{state.state === "loading" && (
				<CardGrid columns={3} gap="md">
					{[1, 2, 3, 4, 5, 6].map((i) => (
						<Card key={i}><Skeleton active paragraph={{ rows: 2 }} /></Card>
					))}
				</CardGrid>
			)}

			{/* Error State */}
			{state.state === "error" && <ErrorNotice locale={locale} error={state.error} />}

			{/* Empty State */}
			{state.state === "loaded" && state.value.length === 0 && (
				<EmptyState
					icon={<QuestionIcon />}
					title={t(locale, "common.empty")}
					description={t(locale, "questions.emptyDesc")}
					action={
						<Link to="/questions/new">
							<Button type="primary" icon={<PlusIcon />}>
								{t(locale, "questions.new")}
							</Button>
						</Link>
					}
				/>
			)}

			{/* No Results */}
			{state.state === "loaded" && state.value.length > 0 && filteredCards.length === 0 && (
				<EmptyState
					title={t(locale, "common.noResults")}
					description={t(locale, "common.noResultsDesc")}
					action={
						<Button type="default" onClick={() => setSearchQuery("")}>
							{t(locale, "common.clearSearch")}
						</Button>
					}
				/>
			)}

			{/* Grid View */}
			{state.state === "loaded" && filteredCards.length > 0 && viewMode === "grid" && (
				<CardGrid columns={3} gap="md">
					{filteredCards.map((c) => (
						<div key={c.id} style={{ position: "relative" }}>
							<div
								className="question-card__checkbox"
								style={{ position: "absolute", top: 12, left: 12, zIndex: 1 }}
								onClick={(e) => e.stopPropagation()}
							>
								<Checkbox
									checked={selectedIds.has(c.id)}
									onChange={() => toggleSelect(c.id)}
								/>
							</div>
							<div
								className="question-card__actions"
								style={{ position: "absolute", top: 12, right: 12, zIndex: 1 }}
								onClick={(e) => e.preventDefault()}
							>
								<Dropdown
									menu={{
										items: [
											{
												key: "delete",
												icon: <DeleteOutlined />,
												label: "移至回收站",
												danger: true,
												onClick: () => handleDelete(c.id, c.name || t(locale, "common.untitled")),
											},
										],
									}}
									trigger={["click"]}
								>
									<Button type="text" size="small" icon={<EllipsisOutlined />} onClick={(e) => e.stopPropagation()} />
								</Dropdown>
							</div>
							<Link to={`/questions/${c.id}`} style={{ textDecoration: "none" }}>
								<Card hoverable>
									<div className="question-card">
										<div className="question-card__icon">
											{getDisplayTypeIcon(c.display)}
										</div>
										<div className="question-card__content">
											<h3 className="question-card__title">{c.name || t(locale, "common.untitled")}</h3>
											{c.display && (
												<Tag>
													{c.display}
												</Tag>
											)}
										</div>
									</div>
								</Card>
							</Link>
						</div>
					))}
				</CardGrid>
			)}

			{/* List View */}
			{state.state === "loaded" && filteredCards.length > 0 && viewMode === "list" && (
				<Card styles={{ body: { padding: 0 } }}>
					<table className="table">
						<thead>
							<tr>
								<th style={{ width: 40 }}>
									<Checkbox
										checked={selectedIds.size === filteredCards.length}
										indeterminate={selectedIds.size > 0 && selectedIds.size < filteredCards.length}
										onChange={toggleSelectAll}
									/>
								</th>
								<th>{t(locale, "common.name")}</th>
								<th>{t(locale, "common.type")}</th>
								<th style={{ width: 80 }}>{t(locale, "common.id")}</th>
								<th style={{ width: 48 }} />
							</tr>
						</thead>
						<tbody>
							{filteredCards.map((c) => (
								<tr key={String(c.id)}>
									<td>
										<Checkbox
											checked={selectedIds.has(c.id)}
											onChange={() => toggleSelect(c.id)}
										/>
									</td>
									<td>
										<Link to={`/questions/${c.id}`} className="link">
											{c.name || t(locale, "common.untitled")}
										</Link>
									</td>
									<td>
										{c.display && (
											<Tag>
												{c.display}
											</Tag>
										)}
									</td>
									<td className="muted">{c.id}</td>
									<td>
										<Dropdown
											menu={{
												items: [
													{
														key: "delete",
														icon: <DeleteOutlined />,
														label: "移至回收站",
														danger: true,
														onClick: () => handleDelete(c.id, c.name || t(locale, "common.untitled")),
													},
												],
											}}
											trigger={["click"]}
										>
											<Button type="text" size="small" icon={<EllipsisOutlined />} />
										</Dropdown>
									</td>
								</tr>
							))}
						</tbody>
					</table>
				</Card>
			)}

			<style>{`
				.question-card {
					display: flex;
					align-items: flex-start;
					gap: var(--spacing-md);
				}

				.question-card__icon {
					display: flex;
					align-items: center;
					justify-content: center;
					width: 40px;
					height: 40px;
					border-radius: var(--radius-md);
					background: var(--color-bg-hover);
					color: var(--color-brand);
					flex-shrink: 0;
				}

				.question-card__content {
					flex: 1;
					min-width: 0;
					display: flex;
					flex-direction: column;
					gap: var(--spacing-xs);
				}

				.question-card__title {
					margin: 0;
					font-size: var(--font-size-md);
					font-weight: var(--font-weight-semibold);
					color: var(--color-text-primary);
					overflow: hidden;
					text-overflow: ellipsis;
					white-space: nowrap;
				}
			`}</style>
		</PageContainer>
	);
}
