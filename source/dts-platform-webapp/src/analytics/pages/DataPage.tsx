import { useCallback, useEffect, useMemo, useState } from "react";
import { Link } from "react-router";
import {
	analyticsApi,
	type PlatformDataSourceItem,
	type DatabaseListItem,
	type CurrentUser,
	type MyUploadItem,
} from "../api/analyticsApi";
import { PageSection } from "../components/PageContainer/PageContainer";
import { PageHeader } from "@/components/page-header";
import { Table, Tag, Button, Input, Modal, message, Space, Tooltip } from "antd";
import type { ColumnsType } from "antd/es/table";
import UploadedDataEditor from "../components/UploadedDataEditor";

/* ------------------------------------------------------------------ */
/*  Types                                                              */
/* ------------------------------------------------------------------ */

/** Platform data source row enriched with matched analytics DB id */
type DataLakeRow = PlatformDataSourceItem & {
	analyticsDbId?: number;
};

/* ------------------------------------------------------------------ */
/*  Component                                                          */
/* ------------------------------------------------------------------ */

export default function DataPage() {
	// --- user / permissions ---
	const [currentUser, setCurrentUser] = useState<CurrentUser | null>(null);
	const isDataAdmin = currentUser?.is_data_admin || currentUser?.is_superuser || false;

	// --- data lake list ---
	const [platformSources, setPlatformSources] = useState<PlatformDataSourceItem[]>([]);
	const [databases, setDatabases] = useState<DatabaseListItem[]>([]);
	const [lakeLoading, setLakeLoading] = useState(true);
	const [lakeSearch, setLakeSearch] = useState("");

	// --- my uploads ---
	const [dataLakeId, setDataLakeId] = useState<number | null>(null);
	const [uploads, setUploads] = useState<MyUploadItem[]>([]);
	const [uploadsLoading, setUploadsLoading] = useState(true);
	const [uploadsSearch, setUploadsSearch] = useState("");

	// --- upload modal ---
	const [uploadModalOpen, setUploadModalOpen] = useState(false);

	// --- sync loading per source ---
	const [syncingId, setSyncingId] = useState<string | null>(null);

	/* ---------- loaders ---------- */

	const loadLakeData = useCallback(() => {
		setLakeLoading(true);
		Promise.all([
			analyticsApi.listPlatformDataSources(),
			analyticsApi.listDatabases(),
		])
			.then(([sources, dbResp]) => {
				setPlatformSources(sources ?? []);
				const dbs = dbResp.data ?? [];
				setDatabases(dbs);
				// find the is_system database for uploads
				const systemDb = dbs.find((d) => d.is_system);
				if (systemDb) setDataLakeId(systemDb.id);
			})
			.catch((e) => {
				console.error("Failed to load data lake sources:", e);
				message.error("加载数据源列表失败");
			})
			.finally(() => setLakeLoading(false));
	}, []);

	const loadUploads = useCallback(() => {
		if (dataLakeId == null) {
			setUploadsLoading(false);
			return;
		}
		setUploadsLoading(true);
		analyticsApi
			.listMyUploads(dataLakeId)
			.then((items) => setUploads(items ?? []))
			.catch((e) => {
				console.error("Failed to load uploads:", e);
				message.error("加载上传列表失败");
			})
			.finally(() => setUploadsLoading(false));
	}, [dataLakeId]);

	useEffect(() => {
		analyticsApi.getCurrentUser().then(setCurrentUser).catch(() => {});
		loadLakeData();
	}, [loadLakeData]);

	useEffect(() => {
		loadUploads();
	}, [loadUploads]);

	/* ---------- derived data ---------- */

	/** Map platform source name -> analytics database id */
	const dbByName = useMemo(() => {
		const map = new Map<string, DatabaseListItem>();
		for (const db of databases) {
			if (db.name) map.set(db.name, db);
		}
		return map;
	}, [databases]);

	const lakeRows: DataLakeRow[] = useMemo(() => {
		const keyword = lakeSearch.trim().toLowerCase();
		return platformSources
			.map((src) => ({
				...src,
				analyticsDbId: src.name ? dbByName.get(src.name)?.id : undefined,
			}))
			.filter((row) => {
				if (!keyword) return true;
				return (
					(row.name ?? "").toLowerCase().includes(keyword) ||
					(row.type ?? "").toLowerCase().includes(keyword) ||
					(row.jdbcUrl ?? "").toLowerCase().includes(keyword)
				);
			});
	}, [platformSources, dbByName, lakeSearch]);

	const filteredUploads = useMemo(() => {
		const keyword = uploadsSearch.trim().toLowerCase();
		if (!keyword) return uploads;
		return uploads.filter(
			(u) =>
				u.name.toLowerCase().includes(keyword) ||
				(u.display_name ?? "").toLowerCase().includes(keyword) ||
				(u.schema ?? "").toLowerCase().includes(keyword),
		);
	}, [uploads, uploadsSearch]);

	/* ---------- actions ---------- */

	const handleSync = async (row: DataLakeRow) => {
		if (!row.analyticsDbId) {
			message.warning("该数据源尚未在分析平台中注册，无法同步");
			return;
		}
		setSyncingId(row.id);
		try {
			await analyticsApi.syncDatabaseSchema(row.analyticsDbId);
			message.success(`已触发「${row.name}」的元数据同步`);
		} catch (e) {
			console.error("Sync failed:", e);
		} finally {
			setSyncingId(null);
		}
	};

	const handleDeleteUpload = (record: MyUploadItem) => {
		if (dataLakeId == null) return;
		Modal.confirm({
			title: "确认删除",
			content: `确定要删除上传表「${record.display_name || record.name}」吗？此操作不可恢复。`,
			okText: "删除",
			okType: "danger",
			cancelText: "取消",
			onOk: async () => {
				try {
					await analyticsApi.deleteUploadTable(dataLakeId, record.name);
					message.success("删除成功");
					loadUploads();
				} catch (e) {
					console.error("Delete upload failed:", e);
				}
			},
		});
	};

	const handleUploadComplete = async (result: { tableName: string; schema: string; rowCount: number }) => {
		setUploadModalOpen(false);
		message.success(`上传成功：${result.tableName}（${result.rowCount} 行）`);
		loadUploads();
		// also sync schema so the table is queryable
		if (dataLakeId != null) {
			try {
				await analyticsApi.syncDatabaseSchema(dataLakeId);
			} catch {
				// silent — sync is best-effort after upload
			}
		}
	};

	/* ---------- table columns ---------- */

	const lakeColumns: ColumnsType<DataLakeRow> = useMemo(
		() => [
			{
				title: "名称",
				dataIndex: "name",
				key: "name",
				ellipsis: true,
				render: (name: string | undefined) => name ?? "-",
			},
			{
				title: "类型",
				dataIndex: "type",
				key: "type",
				width: 120,
				render: (type: string | undefined) =>
					type ? <Tag>{type}</Tag> : "-",
			},
			{
				title: "连接地址",
				dataIndex: "jdbcUrl",
				key: "jdbcUrl",
				ellipsis: true,
				render: (url: string | undefined) => (
					<Tooltip title={url}>
						<span className="text-text-secondary text-[length:var(--font-size-sm)]">
							{url ?? "-"}
						</span>
					</Tooltip>
				),
			},
			{
				title: "状态",
				dataIndex: "status",
				key: "status",
				width: 100,
				render: (status: string | undefined | null) => {
					if (!status) return "-";
					const color = status === "ACTIVE" || status === "active" ? "green" : "default";
					return <Tag color={color}>{status}</Tag>;
				},
			},
			{
				title: "操作",
				key: "actions",
				width: 180,
				render: (_: unknown, row: DataLakeRow) => (
					<Space size="small">
						{isDataAdmin && (
							<Button
								type="link"
								size="small"
								loading={syncingId === row.id}
								disabled={!row.analyticsDbId}
								onClick={() => handleSync(row)}
							>
								同步元数据
							</Button>
						)}
						{row.analyticsDbId ? (
							<Link to={`/bi/data/${row.analyticsDbId}`}>
								<Button type="link" size="small">
									查看表
								</Button>
							</Link>
						) : (
							<Button type="link" size="small" disabled>
								查看表
							</Button>
						)}
					</Space>
				),
			},
		],
		[isDataAdmin, syncingId],
	);

	const uploadColumns: ColumnsType<MyUploadItem> = useMemo(
		() => [
			{
				title: "表名",
				dataIndex: "name",
				key: "name",
				ellipsis: true,
			},
			{
				title: "显示名",
				dataIndex: "display_name",
				key: "display_name",
				ellipsis: true,
				render: (v: string | undefined) => v ?? "-",
			},
			{
				title: "Schema",
				dataIndex: "schema",
				key: "schema",
				width: 140,
				render: (v: string | undefined) => v ?? "-",
			},
			{
				title: "上传时间",
				dataIndex: "created_at",
				key: "created_at",
				width: 180,
				render: (v: string | undefined) => (v ? new Date(v).toLocaleString("zh-CN") : "-"),
			},
			{
				title: "操作",
				key: "actions",
				width: 140,
				render: (_: unknown, record: MyUploadItem) => (
					<Space size="small">
						{dataLakeId != null && (
							<Link to={`/bi/data/${dataLakeId}`}>
								<Button type="link" size="small">
									查看
								</Button>
							</Link>
						)}
						<Button
							type="link"
							size="small"
							danger
							onClick={() => handleDeleteUpload(record)}
						>
							删除
						</Button>
					</Space>
				),
			},
		],
		[dataLakeId],
	);

	/* ---------- render ---------- */

	return (
		<div className="space-y-4">
			<PageHeader title="数据管理" />

			{/* --- Data Lake List --- */}
			<PageSection
				title="数据湖列表"
				description="平台已注册的数据源列表"
				actions={
					<Input.Search
						placeholder="搜索数据源..."
						allowClear
						style={{ width: 240 }}
						value={lakeSearch}
						onChange={(e) => setLakeSearch(e.target.value)}
					/>
				}
			>
				<Table<DataLakeRow>
					rowKey="id"
					columns={lakeColumns}
					dataSource={lakeRows}
					loading={lakeLoading}
					pagination={false}
					size="middle"
					locale={{ emptyText: "暂无数据源" }}
				/>
			</PageSection>

			{/* --- My Uploads --- */}
			<PageSection
				title="我的上传"
				description="您上传的 CSV / Excel 数据表"
				actions={
					<Space>
						<Input.Search
							placeholder="搜索上传表..."
							allowClear
							style={{ width: 240 }}
							value={uploadsSearch}
							onChange={(e) => setUploadsSearch(e.target.value)}
						/>
						<Button
							type="primary"
							disabled={dataLakeId == null}
							onClick={() => setUploadModalOpen(true)}
						>
							上传 Excel/CSV
						</Button>
					</Space>
				}
			>
				<Table<MyUploadItem>
					rowKey="id"
					columns={uploadColumns}
					dataSource={filteredUploads}
					loading={uploadsLoading}
					pagination={false}
					size="middle"
					locale={{ emptyText: "暂无上传数据" }}
				/>
			</PageSection>

			{/* --- Upload Modal --- */}
			<Modal
				title="上传数据"
				open={uploadModalOpen}
				onCancel={() => setUploadModalOpen(false)}
				width={720}
				destroyOnClose
				footer={null}
			>
				{dataLakeId != null && (
					<UploadedDataEditor
						databaseId={dataLakeId}
						onComplete={handleUploadComplete}
					/>
				)}
			</Modal>
		</div>
	);
}
