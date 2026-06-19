import type { CSSProperties, ReactNode } from "react";

interface SurfaceProps {
	children: ReactNode;
	/** 内边距档位 */
	pad?: "none" | "sm" | "md" | "lg";
	/** 是否带发丝边框（Swiss 默认靠 hairline 而非阴影分层） */
	bordered?: boolean;
	raised?: boolean;
	style?: CSSProperties;
	className?: string;
}

const PAD: Record<NonNullable<SurfaceProps["pad"]>, number> = {
	none: 0,
	sm: 12,
	md: 16,
	lg: 24,
};

/** 基础表面容器。Swiss 风：白底 + 1px 发丝线 + 极小圆角。 */
export function Surface({ children, pad = "md", bordered = true, raised, style, className }: SurfaceProps) {
	return (
		<div
			className={className}
			style={{
				background: "var(--surface)",
				border: bordered ? "1px solid var(--hairline)" : "none",
				borderRadius: "var(--radius-md)",
				padding: PAD[pad],
				boxShadow: raised ? "0 1px 2px rgba(16, 24, 40, 0.04), 0 1px 3px rgba(16, 24, 40, 0.06)" : "none",
				...style,
			}}
		>
			{children}
		</div>
	);
}
