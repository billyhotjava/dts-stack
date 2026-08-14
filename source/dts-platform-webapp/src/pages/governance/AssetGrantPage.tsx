import { useCallback, useEffect, useMemo, useState } from "react";
import { useSearchParams } from "react-router";
import { toast } from "sonner";
import { Button, Card, DatePicker, Form, Input, Modal, Radio, Select, Space, Tag } from "antd";
import { actionColumn, appendDetailAction, CompactTable, RecordDetailDrawer } from "@/components/table";
import type { ColumnsType } from "antd/es/table";
import { listAssetGrants, createAssetGrant, deleteAssetGrant } from "@/api/platformApi";

type AssetGrant = {
	id: number;
	assetType: string;
	assetId: string;
	granteeType: string;
	granteeId: string;
	permission: string;
	validFrom?: string;
	validTo?: string;
	grantedBy: string;
	grantReason?: string;
	createdDate?: string;
};

const PERMISSION_OPTIONS = [
	{ label: "READ", value: "READ" },
	{ label: "EDIT", value: "EDIT" },
	{ label: "MANAGE", value: "MANAGE" },
];

const GRANTEE_TYPE_OPTIONS = [
	{ label: "用户", value: "USER" },
	{ label: "角色", value: "ROLE" },
	{ label: "部门", value: "DEPT" },
];

const ASSET_TYPE_OPTIONS = [
	{ label: "TABLE", value: "TABLE" },
	{ label: "CARD", value: "CARD" },
	{ label: "DASHBOARD", value: "DASHBOARD" },
	{ label: "SCREEN", value: "SCREEN" },
	{ label: "MODEL", value: "MODEL" },
];

export default function AssetGrantPage() {
	const [searchParams] = useSearchParams();
	const initialAssetType = searchParams.get("assetType") || "TABLE";
	const initialAssetId = searchParams.get("assetId") || "";
	const [grants, setGrants] = useState<AssetGrant[]>([]);
	const [loading, setLoading] = useState(false);
	const [searchType, setSearchType] = useState(initialAssetType);
	const [searchId, setSearchId] = useState(initialAssetId);
	const [grantModal, setGrantModal] = useState(false);
	const [detailRow, setDetailRow] = useState<AssetGrant | null>(null);
	const [form] = Form.useForm();

	const loadGrants = useCallback(async () => {
		if (!searchId) return;
		setLoading(true);
		try {
			const resp = (await listAssetGrants({ assetType: searchType, assetId: searchId })) as AssetGrant[];
			setGrants(Array.isArray(resp) ? resp : []);
		} catch {
			/* global interceptor handles toast */
		} finally {
			setLoading(false);
		}
	}, [searchType, searchId]);

	useEffect(() => {
		if (!searchId) {
			setGrants([]);
			return;
		}
		void loadGrants();
	}, [loadGrants, searchId]);

	const handleCreate = async () => {
		try {
			const values = await form.validateFields();
			await createAssetGrant({
				assetType: searchType,
				assetId: searchId,
				granteeType: values.granteeType,
				granteeId: values.granteeId,
				permission: values.permission,
				validFrom: values.validRange?.[0]?.toISOString(),
				validTo: values.validRange?.[1]?.toISOString(),
				grantReason: values.grantReason,
			});
			toast.success("授权已创建");
			setGrantModal(false);
			form.resetFields();
			loadGrants();
		} catch (err: any) {
			if (err?.errorFields) return;
			/* global interceptor handles toast */
		}
	};

	const handleRevoke = async (id: number) => {
		try {
			await deleteAssetGrant(id);
			toast.success("授权已撤销");
			loadGrants();
		} catch {
			/* global interceptor handles toast */
		}
	};

	const baseColumns: ColumnsType<AssetGrant> = [
		{
			title: "被授权人类型",
			dataIndex: "granteeType",
			key: "granteeType",
			width: 100,
			render: (v: string) => <Tag color={v === "USER" ? "blue" : v === "ROLE" ? "green" : "orange"}>{v}</Tag>,
		},
		{ title: "被授权人", dataIndex: "granteeId", key: "granteeId" },
		{
			title: "权限",
			dataIndex: "permission",
			key: "permission",
			width: 100,
			render: (v: string) => <Tag color={v === "MANAGE" ? "red" : v === "EDIT" ? "orange" : "default"}>{v}</Tag>,
		},
		{
			title: "有效期至",
			dataIndex: "validTo",
			key: "validTo",
			width: 180,
			render: (v?: string) => (v ? new Date(v).toLocaleDateString() : "永久"),
		},
		{ title: "授权者", dataIndex: "grantedBy", key: "grantedBy", width: 120 },
		{ title: "原因", dataIndex: "grantReason", key: "grantReason", ellipsis: true },
		actionColumn<AssetGrant>(
			(record) => [
				{
					key: "revoke",
					label: "撤销",
					danger: true,
					confirm: "确定要撤销此授权吗？",
					onClick: () => handleRevoke(record.id),
				},
			],
			{ width: 160 },
		),
	];

	const columns = useMemo(
		() => appendDetailAction(baseColumns, (row) => setDetailRow(row)),
		// eslint-disable-next-line react-hooks/exhaustive-deps
		[],
	);

	return (
		<div className="space-y-4">
			<Card title="资产授权管理">
				<Space wrap className="mb-4">
					<Select options={ASSET_TYPE_OPTIONS} value={searchType} onChange={setSearchType} style={{ width: 140 }} />
					<Input.Search
						placeholder="输入资产 ID"
						style={{ width: 220 }}
						defaultValue={searchId}
						onSearch={(v) => {
							setSearchId(v);
						}}
					/>
					{searchId && (
						<Button
							type="primary"
							onClick={() => {
								form.resetFields();
								setGrantModal(true);
							}}
						>
							+ 新建授权
						</Button>
					)}
				</Space>

				{searchId && (
					<CompactTable<AssetGrant>
						rowKey="id"
						columns={columns}
						dataSource={grants}
						loading={loading}
						size="small"
						pagination={false}
					/>
				)}
			</Card>

			<Modal
				title="新建授权"
				open={grantModal}
				onCancel={() => setGrantModal(false)}
				onOk={handleCreate}
				destroyOnClose
			>
				<Form form={form} layout="vertical" className="pt-4">
					<div className="mb-2 text-gray-500">
						资产: {searchType}:{searchId}
					</div>
					<Form.Item name="granteeType" label="被授权人类型" rules={[{ required: true }]} initialValue="USER">
						<Radio.Group options={GRANTEE_TYPE_OPTIONS} />
					</Form.Item>
					<Form.Item name="granteeId" label="被授权人" rules={[{ required: true, message: "必填" }]}>
						<Input placeholder="用户名 / 角色名 / 部门代码" />
					</Form.Item>
					<Form.Item name="permission" label="权限" rules={[{ required: true }]} initialValue="READ">
						<Radio.Group options={PERMISSION_OPTIONS} />
					</Form.Item>
					<Form.Item name="validRange" label="有效期">
						<DatePicker.RangePicker style={{ width: "100%" }} />
					</Form.Item>
					<Form.Item name="grantReason" label="原因 / OA 单号">
						<Input.TextArea rows={2} placeholder="OA 审批单号或原因" />
					</Form.Item>
				</Form>
			</Modal>
			<RecordDetailDrawer<AssetGrant>
				open={detailRow !== null}
				onClose={() => setDetailRow(null)}
				record={detailRow}
				columns={baseColumns}
				title="授权详情"
			/>
		</div>
	);
}
