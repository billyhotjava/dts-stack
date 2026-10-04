import { useCallback, useEffect, useMemo, useState } from "react";
import { Card, DatePicker, Input, Select, Space, Tag } from "antd";
import { CompactTable, RecordDetailDrawer, appendDetailAction } from "@/components/table";
import type { ColumnsType } from "antd/es/table";
import { listPermissionAudit } from "@/api/platformApi";
import { assetTypeLabel, auditActionLabel, permissionLabel } from "@/utils/customerDisplayLabels";

type AuditEntry = {
	id: number;
	action: string;
	assetType?: string;
	assetId?: string;
	targetUser?: string;
	permission?: string;
	operator: string;
	oaReference?: string;
	detail?: string;
	createdDate?: string;
};

const ACTION_OPTIONS = [
	{ label: "权限校验通过", value: "CHECK_ALLOW" },
	{ label: "权限校验拒绝", value: "CHECK_DENY" },
	{ label: "授权", value: "GRANT" },
	{ label: "撤销授权", value: "REVOKE" },
	{ label: "变更所有者", value: "CHANGE_OWNERSHIP" },
];

const ACTION_COLORS: Record<string, string> = {
	CHECK_ALLOW: "green",
	CHECK_DENY: "volcano",
	GRANT: "green",
	REVOKE: "red",
	CHANGE_OWNERSHIP: "blue",
};

export default function PermissionAuditPage() {
	const [items, setItems] = useState<AuditEntry[]>([]);
	const [loading, setLoading] = useState(false);
	const [total, setTotal] = useState(0);
	const [page, setPage] = useState(0);
	const [action, setAction] = useState<string | undefined>();
	const [operator, setOperator] = useState("");
	const [targetUser, setTargetUser] = useState("");
	const [oaReference, setOaReference] = useState("");
	const [dateRange, setDateRange] = useState<[any, any] | null>(null);
	const [detailRow, setDetailRow] = useState<AuditEntry | null>(null);

	const loadData = useCallback(async () => {
		setLoading(true);
		try {
			const resp = await listPermissionAudit({
				action, operator: operator || undefined,
				targetUser: targetUser || undefined,
				oaReference: oaReference || undefined,
				dateFrom: dateRange?.[0]?.toISOString(),
				dateTo: dateRange?.[1]?.toISOString(),
				page, size: 20,
			}) as any;
			const content = resp?.content ?? resp?.data ?? resp ?? [];
			setItems(Array.isArray(content) ? content : []);
			setTotal(resp?.totalElements ?? content.length);
		} catch {
			/* global interceptor handles toast */
		} finally {
			setLoading(false);
		}
	}, [action, operator, targetUser, oaReference, dateRange, page]);

	useEffect(() => { loadData(); }, [loadData]);

	const baseColumns: ColumnsType<AuditEntry> = [
		{ title: "时间", dataIndex: "createdDate", key: "time", width: 170,
			render: (v?: string) => v ? new Date(v).toLocaleString() : "-" },
		{ title: "操作", dataIndex: "action", key: "actionType", width: 140,
			render: (v: string) => <Tag color={ACTION_COLORS[v] || "default"}>{auditActionLabel(v)}</Tag> },
		{ title: "资产", dataIndex: "assetId", key: "asset", width: 200,
			render: (_: any, r: AuditEntry) => r.assetType ? `${assetTypeLabel(r.assetType)}：${r.assetId}` : "-" },
		{ title: "目标用户", dataIndex: "targetUser", key: "targetUser", width: 120 },
		{ title: "权限", dataIndex: "permission", key: "permission", width: 100, render: (v?: string) => v ? permissionLabel(v) : "-" },
		{ title: "操作者", dataIndex: "operator", key: "operator", width: 120 },
		{ title: "OA 单号", dataIndex: "oaReference", key: "oaReference", width: 120 },
	];

	const columns = useMemo(
		() => appendDetailAction(baseColumns, (row) => setDetailRow(row)),
		// eslint-disable-next-line react-hooks/exhaustive-deps
		[],
	);

	return (
		<Card title="权限审计日志">
			<Space wrap className="mb-4">
				<Select allowClear placeholder="操作" options={ACTION_OPTIONS}
					style={{ width: 160 }} value={action} onChange={setAction} />
				<Input placeholder="操作者" allowClear style={{ width: 140 }}
					value={operator} onChange={(e) => setOperator(e.target.value)} onPressEnter={() => loadData()} />
				<Input placeholder="目标用户" allowClear style={{ width: 140 }}
					value={targetUser} onChange={(e) => setTargetUser(e.target.value)} onPressEnter={() => loadData()} />
				<Input placeholder="OA 单号" allowClear style={{ width: 140 }}
					value={oaReference} onChange={(e) => setOaReference(e.target.value)} onPressEnter={() => loadData()} />
				<DatePicker.RangePicker onChange={(dates) => setDateRange(dates as any)} />
			</Space>
			<CompactTable<AuditEntry>
				rowKey="id" columns={columns} dataSource={items} loading={loading}
				size="small"
				pagination={{
					current: page + 1, pageSize: 10, total,
					onChange: (p) => setPage(p - 1),
				}}
			/>
			<RecordDetailDrawer<AuditEntry>
				open={detailRow !== null}
				onClose={() => setDetailRow(null)}
				record={detailRow}
				columns={baseColumns}
				title="审计详情"
			/>
		</Card>
	);
}
