import { LockOutlined } from "@ant-design/icons";
import { Alert, Button, Card, Empty, Space, Tag, Tooltip } from "antd";
import type { ReactNode } from "react";
import { UNAVAILABLE_CAPABILITIES } from "./qualityRoutes";

export function QualityPageHeading({
	title,
	description,
	actions,
}: {
	title: string;
	description: string;
	actions?: ReactNode;
}) {
	return (
		<div className="dq-page-heading">
			<div>
				<h2>{title}</h2>
				<p>{description}</p>
			</div>
			{actions ? <Space wrap>{actions}</Space> : null}
		</div>
	);
}

export function QualityMetric({
	label,
	value,
	note,
	color = "#1677ff",
}: {
	label: string;
	value: ReactNode;
	note?: ReactNode;
	color?: string;
}) {
	return (
		<Card className="dq-metric-card" size="small" style={{ borderTopColor: color }}>
			<div className="dq-muted">{label}</div>
			<div className="dq-metric-card__value">{value}</div>
			{note ? (
				<div className="dq-muted" style={{ marginTop: 8 }}>
					{note}
				</div>
			) : null}
		</Card>
	);
}

export function QualityStatus({ status }: { status?: string | boolean }) {
	if (typeof status === "boolean") {
		return <Tag color={status ? "green" : "default"}>{status ? "启用" : "停用"}</Tag>;
	}
	const normalized = String(status || "-").toUpperCase();
	const color = ["PASSED", "SUCCESS", "SUCCEEDED", "COMPLETED", "PUBLISHED"].includes(normalized)
		? "green"
		: ["FAILED", "ERROR", "BLOCKED"].includes(normalized)
			? "red"
			: ["RUNNING", "QUEUED", "DRAFT"].includes(normalized)
				? "blue"
				: "default";
	return <Tag color={color}>{normalized}</Tag>;
}

export function UnavailableCapability({
	capability,
	title,
	compact = false,
}: {
	capability: (typeof UNAVAILABLE_CAPABILITIES)[number]["key"];
	title?: string;
	compact?: boolean;
}) {
	const item = UNAVAILABLE_CAPABILITIES.find((candidate) => candidate.key === capability);
	if (!item) return null;
	if (compact) {
		return (
			<Tooltip title={item.reason}>
				<Button disabled icon={<LockOutlined />}>
					{item.label}
				</Button>
			</Tooltip>
		);
	}
	return (
		<Alert
			type="info"
			showIcon
			message={title || item.label}
			description={item.reason}
			action={
				<Button disabled icon={<LockOutlined />}>
					{item.label}
				</Button>
			}
		/>
	);
}

export function QualityEmpty({ description }: { description: string }) {
	return (
		<Card>
			<Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={description} />
		</Card>
	);
}

export function ManagePermissionHint({ canManage }: { canManage: boolean }) {
	if (canManage) return null;
	return <Tag color="orange">当前账号只读</Tag>;
}
