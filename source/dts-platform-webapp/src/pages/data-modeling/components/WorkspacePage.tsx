import type { ReactNode } from "react";
import type { DemoRow, TableColumn } from "../types";

export function WorkspacePage({
	title,
	description,
	eyebrow,
	actions,
	children,
}: {
	title: string;
	description: string;
	eyebrow?: string;
	actions?: ReactNode;
	children: ReactNode;
}) {
	return (
		<main className="dm-page" data-testid="data-modeling-page">
			<header className="dm-page__header">
				<div>
					{eyebrow ? <div className="dm-page__eyebrow">{eyebrow}</div> : null}
					<h1>{title}</h1>
					<p>{description}</p>
				</div>
				{actions ? <div className="dm-page__actions">{actions}</div> : null}
			</header>
			{children}
		</main>
	);
}

export function Panel({
	title,
	subtitle,
	actions,
	children,
	className = "",
}: {
	title?: string;
	subtitle?: string;
	actions?: ReactNode;
	children: ReactNode;
	className?: string;
}) {
	return (
		<section className={`dm-panel ${className}`.trim()}>
			{title || actions ? (
				<div className="dm-panel__header">
					<div>
						{title ? <h2>{title}</h2> : null}
						{subtitle ? <p>{subtitle}</p> : null}
					</div>
					{actions ? <div className="dm-panel__actions">{actions}</div> : null}
				</div>
			) : null}
			<div className="dm-panel__body">{children}</div>
		</section>
	);
}

export function ActionButton({
	children,
	kind = "default",
	disabled = false,
	onClick,
	title,
}: {
	children: ReactNode;
	kind?: "default" | "primary" | "quiet" | "danger";
	disabled?: boolean;
	onClick?: () => void;
	title?: string;
}) {
	return (
		<button
			className={`dm-button dm-button--${kind}`}
			disabled={disabled}
			onClick={onClick}
			title={title}
			type="button"
		>
			{children}
		</button>
	);
}

export function StatusTag({
	children,
	tone = "neutral",
}: {
	children: ReactNode;
	tone?: "neutral" | "success" | "warning" | "info" | "danger";
}) {
	return <span className={`dm-tag dm-tag--${tone}`}>{children}</span>;
}

export function DataTable({
	columns,
	rows,
	rowKey,
	emptyText = "暂无数据",
}: {
	columns: TableColumn[];
	rows: DemoRow[];
	rowKey: string;
	emptyText?: string;
}) {
	return (
		<div className="dm-table-wrap">
			<table className="dm-table">
				<thead>
					<tr>
						{columns.map((column) => (
							<th key={column.key} style={column.width ? { width: column.width } : undefined}>
								{column.title}
							</th>
						))}
					</tr>
				</thead>
				<tbody>
					{rows.length === 0 ? (
						<tr>
							<td className="dm-table__empty" colSpan={columns.length}>
								{emptyText}
							</td>
						</tr>
					) : (
						rows.map((row, index) => (
							<tr key={String(row[rowKey] ?? index)}>
								{columns.map((column) => (
									<td key={column.key}>{row[column.key] ?? "—"}</td>
								))}
							</tr>
						))
					)}
				</tbody>
			</table>
		</div>
	);
}

export function SearchToolbar({
	placeholder = "搜索名称或编码",
	children,
}: {
	placeholder?: string;
	children?: ReactNode;
}) {
	return (
		<div className="dm-toolbar">
			<input aria-label={placeholder} className="dm-input dm-toolbar__search" placeholder={placeholder} type="search" />
			{children}
		</div>
	);
}

export function EmptyState({ title, description }: { title: string; description: string }) {
	return (
		<div className="dm-empty">
			<div className="dm-empty__mark">DTS</div>
			<strong>{title}</strong>
			<p>{description}</p>
		</div>
	);
}
