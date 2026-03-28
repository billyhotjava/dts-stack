import type { ReactNode } from "react";
import { Card } from "antd";

type Props = {
	title: string;
	subtitle?: string;
	children: ReactNode;
};

export function DataSupportCard({ title, subtitle, children }: Props) {
	const cardTitle = subtitle ? (
		<span>
			{title}
			<span style={{ fontSize: "0.85em", fontWeight: "normal", color: "var(--color-text-secondary)", marginLeft: 8 }}>{subtitle}</span>
		</span>
	) : title;
	return (
		<Card className="project-cockpit__panel-card" title={cardTitle}>
			{children}
		</Card>
	);
}
