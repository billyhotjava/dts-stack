import { AlertTriangle, CheckCircle2, LoaderCircle, LockKeyhole, X } from "lucide-react";
import { type ReactNode, useCallback, useEffect, useRef, useState } from "react";

export function PageHeader({
	title,
	description,
	actions,
	trail,
}: {
	title: string;
	description: string;
	actions?: ReactNode;
	trail?: string;
}) {
	return (
		<header className="dmx-page-header">
			<div>
				{trail ? <div className="dmx-page-trail">{trail}</div> : null}
				<h1>{title}</h1>
				<p>{description}</p>
			</div>
			{actions ? <div className="dmx-page-actions">{actions}</div> : null}
		</header>
	);
}

export function Button({
	children,
	primary = false,
	danger = false,
	onClick,
	disabled = false,
	title,
	className = "",
}: {
	children: ReactNode;
	primary?: boolean;
	danger?: boolean;
	onClick?: () => void;
	disabled?: boolean;
	title?: string;
	className?: string;
}) {
	const tone = primary ? " dmx-button--primary" : danger ? " dmx-button--danger" : "";
	return (
		<button
			className={`dmx-button${tone}${className ? ` ${className}` : ""}`}
			disabled={disabled}
			onClick={onClick}
			title={title}
			type="button"
		>
			{children}
		</button>
	);
}

export function Modal({
	title,
	children,
	onClose,
	wide = false,
	footer,
}: {
	title: string;
	children: ReactNode;
	onClose: () => void;
	wide?: boolean;
	footer?: ReactNode;
}) {
	return (
		<div className="dmx-modal-backdrop">
			<section aria-modal="true" className={`dmx-modal${wide ? " dmx-modal--wide" : ""}`} role="dialog">
				<header className="dmx-modal__header">
					<h2>{title}</h2>
					<button aria-label="关闭" onClick={onClose} type="button">
						<X size={18} />
					</button>
				</header>
				<div className="dmx-modal__body">{children}</div>
				{footer ? <footer className="dmx-modal__footer">{footer}</footer> : null}
			</section>
		</div>
	);
}

export function Drawer({
	title,
	children,
	onClose,
	footer,
}: {
	title: string;
	children: ReactNode;
	onClose: () => void;
	footer?: ReactNode;
}) {
	return (
		<div className="dmx-drawer-backdrop" onClick={onClose}>
			<section aria-modal="true" className="dmx-drawer" onClick={(event) => event.stopPropagation()} role="dialog">
				<header className="dmx-drawer__header">
					<h2>{title}</h2>
					<button aria-label="关闭" onClick={onClose} type="button">
						<X size={18} />
					</button>
				</header>
				<div className="dmx-drawer__body">{children}</div>
				{footer ? <footer className="dmx-drawer__footer">{footer}</footer> : null}
			</section>
		</div>
	);
}

export function Toast({ message }: { message: string }) {
	if (!message) return null;
	return (
		<output className="dmx-toast">
			<CheckCircle2 size={17} />
			{message}
		</output>
	);
}

export function Status({ children, tone = "neutral" }: { children: ReactNode; tone?: string }) {
	return <span className={`dmx-status dmx-status--${tone}`}>{children}</span>;
}

export function EmptyState({ title, description }: { title: string; description: string }) {
	return (
		<div className="dmx-empty">
			<div className="dmx-empty__icon">▤</div>
			<strong>{title}</strong>
			<p>{description}</p>
		</div>
	);
}

export function RequestState({
	kind,
	title,
	description,
	onRetry,
}: {
	kind: "loading" | "empty" | "error" | "permission";
	title: string;
	description: string;
	onRetry?: () => void;
}) {
	const Icon = kind === "loading" ? LoaderCircle : kind === "permission" ? LockKeyhole : AlertTriangle;
	return (
		<div className={`dmx-request-state dmx-request-state--${kind}`} role={kind === "error" ? "alert" : "status"}>
			<Icon className={kind === "loading" ? "spin" : ""} size={24} />
			<div>
				<strong>{title}</strong>
				<p>{description}</p>
			</div>
			{onRetry ? <Button onClick={onRetry}>重新加载</Button> : null}
		</div>
	);
}

export function useTransientMessage() {
	const [message, setMessage] = useState("");
	const timer = useRef<number | undefined>(undefined);
	const show = useCallback((next: string) => {
		window.clearTimeout(timer.current);
		setMessage(next);
		timer.current = window.setTimeout(() => setMessage(""), 2200);
	}, []);
	useEffect(() => () => window.clearTimeout(timer.current), []);
	return { message, show };
}
