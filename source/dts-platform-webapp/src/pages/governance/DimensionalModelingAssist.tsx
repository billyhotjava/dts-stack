import { Button, Drawer, Tag } from "antd";
import type { ReactNode } from "react";
import { useEffect, useState } from "react";

export function DimensionalModelingAssist({
	candidateCount,
	openOnMount = false,
	children,
}: {
	candidateCount: number;
	openOnMount?: boolean;
	children: ReactNode;
}) {
	const [open, setOpen] = useState(openOnMount);

	useEffect(() => setOpen(openOnMount), [openOnMount]);

	return (
		<>
			<Button onClick={() => setOpen(true)} data-testid="dimensional-modeling-assist-entry">
				维度建模辅助 {candidateCount > 0 ? <Tag color="gold">待确认 {candidateCount}</Tag> : null}
			</Button>
			<Drawer title="维度建模辅助" width={720} open={open} onClose={() => setOpen(false)}>
				{children}
			</Drawer>
		</>
	);
}
