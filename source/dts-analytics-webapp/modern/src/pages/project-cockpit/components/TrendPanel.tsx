import type { ReactNode } from "react";
import { Card, CardBody, CardHeader } from "../../../ui/Card/Card";

type Props = {
	title: string;
	subtitle?: string;
	action?: ReactNode;
	children: ReactNode;
};

export function TrendPanel({ title, subtitle, action, children }: Props) {
	return (
		<Card className="project-cockpit__panel-card">
			<CardHeader title={title} subtitle={subtitle} action={action} />
			<CardBody>{children}</CardBody>
		</Card>
	);
}
