import { useEffect, useRef, useCallback, type ReactNode } from "react";
import { createPortal } from "react-dom";
import "./Drawer.css";

export type DrawerSize = "sm" | "md" | "lg" | "xl";

export interface DrawerProps {
	isOpen: boolean;
	onClose: () => void;
	title?: ReactNode;
	description?: string;
	size?: DrawerSize;
	footer?: ReactNode;
	children?: ReactNode;
}

export function Drawer({
	isOpen,
	onClose,
	title,
	description,
	size = "lg",
	footer,
	children,
}: DrawerProps) {
	const drawerRef = useRef<HTMLDivElement>(null);

	const handleKeyDown = useCallback(
		(event: globalThis.KeyboardEvent) => {
			if (event.key === "Escape") onClose();
		},
		[onClose],
	);

	useEffect(() => {
		if (isOpen) {
			document.addEventListener("keydown", handleKeyDown);
			document.body.style.overflow = "hidden";
			return () => {
				document.removeEventListener("keydown", handleKeyDown);
				document.body.style.overflow = "";
			};
		}
	}, [isOpen, handleKeyDown]);

	if (!isOpen) return null;

	return createPortal(
		<>
			<div className="mb-drawer__overlay" onClick={onClose} role="presentation" />
			<div
				ref={drawerRef}
				className={`mb-drawer mb-drawer--${size}`}
				role="dialog"
				aria-modal="true"
			>
				<div className="mb-drawer__header">
					<div className="mb-drawer__header-content">
						{title && <h3 className="mb-drawer__title">{title}</h3>}
						{description && <p className="mb-drawer__description">{description}</p>}
					</div>
					<button type="button" className="mb-drawer__close" onClick={onClose} aria-label="关闭">
						<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
							<path d="M18 6 6 18" />
							<path d="m6 6 12 12" />
						</svg>
					</button>
				</div>
				<div className="mb-drawer__body">{children}</div>
				{footer && <div className="mb-drawer__footer">{footer}</div>}
			</div>
		</>,
		document.body,
	);
}
