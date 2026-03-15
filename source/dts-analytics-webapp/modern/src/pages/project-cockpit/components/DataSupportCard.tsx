import type { ReactNode } from "react";
import { Card, CardBody, CardHeader } from "../../../ui/Card/Card";

type Props = {
	title: string;
	subtitle?: string;
	children: ReactNode;
};

export function DataSupportCard({ title, subtitle, children }: Props) {
	return (
		<Card className="project-cockpit__panel-card">
			<CardHeader title={title} subtitle={subtitle} />
			<CardBody>{children}</CardBody>
		</Card>
	);
}
