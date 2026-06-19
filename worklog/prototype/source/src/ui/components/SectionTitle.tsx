import type { ReactNode } from "react";

interface SectionTitleProps {
	/** 小号全大写的眉标（Swiss 标志性元素） */
	kicker?: string;
	title: ReactNode;
	desc?: ReactNode;
	extra?: ReactNode;
}

/** 区块标题：眉标 + 主标题 + 描述 + 右侧操作位。 */
export function SectionTitle({ kicker, title, desc, extra }: SectionTitleProps) {
	return (
		<div style={{ display: "flex", alignItems: "flex-end", justifyContent: "space-between", gap: 16, marginBottom: 16 }}>
			<div>
				{kicker ? (
					<div
						style={{
							fontSize: 11,
							fontWeight: 600,
							letterSpacing: "0.08em",
							textTransform: "uppercase",
							color: "var(--ink-subtle)",
							marginBottom: 4,
						}}
					>
						{kicker}
					</div>
				) : null}
				<div style={{ fontSize: "var(--text-lg)", fontWeight: 650, color: "var(--ink)", lineHeight: 1.25 }}>{title}</div>
				{desc ? <div style={{ fontSize: "var(--text-sm)", color: "var(--ink-muted)", marginTop: 4 }}>{desc}</div> : null}
			</div>
			{extra ? <div style={{ flex: "0 0 auto" }}>{extra}</div> : null}
		</div>
	);
}
