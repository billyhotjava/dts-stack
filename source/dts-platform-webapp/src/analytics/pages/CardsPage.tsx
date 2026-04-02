import { Link } from "react-router";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { analyticsApi, type CardListItem } from "../api/analyticsApi";
import { PageHeader } from "@/components/page-header";
import { EmptyState } from "../components/EmptyState";
import { ErrorNotice } from "../components/ErrorNotice";
import { Button, Card, Input, Modal, Select, Space, Spin, Table, Tag, message } from "antd";
import { PlusOutlined, EyeOutlined, EditOutlined, DeleteOutlined, UploadOutlined } from "@ant-design/icons";
import type { ColumnsType } from "antd/es/table";
import { getEffectiveLocale, t, type Locale } from "../i18n";

type LoadState<T> =
	| { state: "loading" }
	| { state: "loaded"; value: T }
	| { state: "error"; error: unknown };

function formatTime(raw?: string | null): string {
	if (!raw) return "-";
	try {
		const d = new Date(raw);
		return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")} ${String(d.getHours()).padStart(2, "0")}:${String(d.getMinutes()).padStart(2, "0")}`;
	} catch {
		return raw;
	}
}

const DISPLAY_LABELS: Record<string, string> = {
	table: "表格", line: "折线", bar: "柱状", pie: "饼图", area: "面积",
	scalar: "数字", row: "横柱", combo: "组合", funnel: "漏斗", scatter: "散点",
	number: "数字", gauge: "仪表", map: "地图", progress: "进度", waterfall: "瀑布",
};

// ── 批量导入 SQL 查询卡片 ──────────────────────────────────

function parseSqlCardMeta(filename: string, content: string): { name: string; description: string; screen: string } {
	// 从 SQL 注释头提取中文名称、用途和对应大屏
	const lines = content.split('\n');
	let name = filename.replace(/\.sql$/i, '').replace(/^card-/, '');
	let description = '';
	let screen = '';
	for (const line of lines) {
		const m = line.match(/^--\s*查询卡片[:：]\s*(.+)/);
		if (m) { name = m[1].trim(); continue; }
		const d = line.match(/^--\s*用途[:：]\s*(.+)/);
		if (d) { description = d[1].trim(); continue; }
		const s = line.match(/^--\s*对应大屏[:：]\s*(.+)/);
		if (s) { screen = s[1].trim(); continue; }
	}
	return { name, description, screen };
}

function extractPureSql(content: string): string {
	return content.split('\n').filter(line => !line.startsWith('--')).join('\n').trim();
}

function BatchImportCardsModal({
	open, onClose, onSuccess,
}: {
	open: boolean; onClose: () => void; onSuccess: () => void;
}) {
	const [databases, setDatabases] = useState<Array<{ id: number; name: string }>>([]);
	const [selectedDb, setSelectedDb] = useState<number | null>(null);
	const [namePrefix, setNamePrefix] = useState('');
	const [files, setFiles] = useState<Array<{ name: string; cardName: string; description: string; screen: string; sql: string }>>([]);
	const [importing, setImporting] = useState(false);
	const [results, setResults] = useState<Array<{ name: string; status: string }> | null>(null);
	const fileInputRef = useRef<HTMLInputElement>(null);

	useEffect(() => {
		if (!open) return;
		analyticsApi.listDatabases().then((resp: any) => {
			const dbs = (resp?.data || resp || []).map((d: any) => ({ id: d.id, name: d.name }));
			setDatabases(dbs);
			if (dbs.length === 1) setSelectedDb(dbs[0].id);
		}).catch(() => {});
	}, [open]);

	const handleFilesSelected = async (fileList: FileList | null) => {
		if (!fileList) return;
		const parsed: typeof files = [];
		for (const file of Array.from(fileList)) {
			if (!file.name.endsWith('.sql')) continue;
			const content = await file.text();
			const meta = parseSqlCardMeta(file.name, content);
			parsed.push({ name: file.name, cardName: meta.name, description: meta.description, screen: meta.screen, sql: extractPureSql(content) });
		}
		parsed.sort((a, b) => a.name.localeCompare(b.name));
		setFiles(parsed);
	};

	const handleImport = async () => {
		if (!selectedDb || files.length === 0) return;
		setImporting(true);
		const results: Array<{ name: string; status: string }> = [];
		for (const file of files) {
			try {
				const fullName = namePrefix ? `[${namePrefix}] ${file.cardName}` : file.cardName;
				await analyticsApi.createCard({
					name: fullName,
					description: file.description || null,
					dataset_query: {
						database: selectedDb,
						type: "native",
						native: { query: file.sql },
					},
					display: "table",
					visualization_settings: {},
				});
				results.push({ name: file.cardName, status: 'OK' });
			} catch (err: any) {
				results.push({ name: file.cardName, status: err?.message || '失败' });
			}
		}
		setResults(results);
		setImporting(false);
		const ok = results.filter(r => r.status === 'OK').length;
		message.success(`批量导入完成: ${ok}/${results.length} 成功`);
		onSuccess();
	};

	const reset = () => {
		setFiles([]);
		setResults(null);
		setSelectedDb(databases.length === 1 ? databases[0].id : null);
		setNamePrefix('');
	};

	return (
		<Modal
			open={open}
			title="批量导入查询卡片"
			width={700}
			onCancel={() => { reset(); onClose(); }}
			footer={results ? (
				<Button type="primary" onClick={() => { reset(); onClose(); }}>关闭</Button>
			) : (
				<Space>
					<Button onClick={() => { reset(); onClose(); }}>取消</Button>
					<Button type="primary" onClick={handleImport} loading={importing} disabled={!selectedDb || files.length === 0}>
						导入 {files.length > 0 ? `(${files.length} 个)` : ''}
					</Button>
				</Space>
			)}
		>
			{results ? (
				<Table
					size="small"
					dataSource={results}
					rowKey="name"
					pagination={false}
					columns={[
						{ title: '卡片名称', dataIndex: 'name', key: 'name' },
						{ title: '状态', dataIndex: 'status', key: 'status', width: 100,
							render: (s: string) => <Tag color={s === 'OK' ? 'green' : 'red'}>{s}</Tag> },
					]}
				/>
			) : (
				<Space direction="vertical" className="w-full" size={16}>
					<div>
						<div className="text-sm font-medium mb-2">数据源</div>
						<Select
							className="w-full"
							placeholder="选择数据源（查询卡片将从此数据源查询）"
							value={selectedDb}
							onChange={setSelectedDb}
							options={databases.map(d => ({ label: d.name, value: d.id }))}
						/>
					</div>
					<div>
						<div className="text-sm font-medium mb-2">名称前缀（用于分组）</div>
						<Input
							placeholder="如: GPMC项管、专利数仓（卡片名称显示为 [前缀] 卡片名）"
							value={namePrefix}
							onChange={(e) => setNamePrefix(e.target.value)}
							allowClear
						/>
					</div>
					<div>
						<div className="text-sm font-medium mb-2">SQL 文件</div>
						<input
							ref={fileInputRef}
							type="file"
							multiple
							accept=".sql"
							style={{ display: 'none' }}
							onChange={(e) => handleFilesSelected(e.target.files)}
						/>
						<Button icon={<UploadOutlined />} onClick={() => fileInputRef.current?.click()}>
							选择 SQL 文件（可多选）
						</Button>
					</div>
					{files.length > 0 && (
						<Table
							size="small"
							dataSource={files}
							rowKey="name"
							pagination={false}
							scroll={{ y: 300 }}
							columns={[
								{ title: '文件名', dataIndex: 'name', key: 'name', width: 200, ellipsis: true },
								{ title: '卡片名称', dataIndex: 'cardName', key: 'cardName', width: 180, ellipsis: true },
								{ title: '对应大屏', dataIndex: 'screen', key: 'screen', width: 120, ellipsis: true },
								{ title: '用途', dataIndex: 'description', key: 'description', ellipsis: true },
							]}
						/>
					)}
				</Space>
			)}
		</Modal>
	);
}

// ── CardsPage ──────────────────────────────────────────────

export default function CardsPage() {
	const locale: Locale = useMemo(() => getEffectiveLocale(), []);
	const [state, setState] = useState<LoadState<CardListItem[]>>({ state: "loading" });
	const [searchQuery, setSearchQuery] = useState("");
	const [batchImportOpen, setBatchImportOpen] = useState(false);

	const loadCards = useCallback(() => {
		setState({ state: "loading" });
		analyticsApi.listCards("question")
			.then((value) => setState({ state: "loaded", value: Array.isArray(value) ? value.filter((c) => !c.archived) : [] }))
			.catch((e) => setState({ state: "error", error: e }));
	}, []);

	useEffect(() => { loadCards(); }, [loadCards]);

	const filteredCards = useMemo(() => {
		if (state.state !== "loaded") return [];
		const kw = searchQuery.trim().toLowerCase();
		if (!kw) return state.value;
		return state.value.filter((c) =>
			(c.name ?? "").toLowerCase().includes(kw) || (c.description ?? "").toLowerCase().includes(kw)
		);
	}, [state, searchQuery]);

	const [selectedRowKeys, setSelectedRowKeys] = useState<React.Key[]>([]);
	const [batchDeleting, setBatchDeleting] = useState(false);

	const handleBatchDelete = () => {
		if (selectedRowKeys.length === 0) return;
		Modal.confirm({
			title: `批量删除 ${selectedRowKeys.length} 张卡片`,
			content: "确定将选中的卡片移至废纸篓？可在废纸篓中恢复。",
			okText: "确定",
			cancelText: "取消",
			okButtonProps: { danger: true },
			onOk: async () => {
				setBatchDeleting(true);
				let ok = 0;
				let fail = 0;
				for (const id of selectedRowKeys) {
					try {
						await analyticsApi.deleteCard(Number(id));
						ok++;
					} catch {
						fail++;
					}
				}
				setBatchDeleting(false);
				setSelectedRowKeys([]);
				if (fail === 0) {
					message.success(`已将 ${ok} 张卡片移至废纸篓`);
				} else {
					message.warning(`完成：成功 ${ok}，失败 ${fail}`);
				}
				loadCards();
			},
		});
	};

	const handleDelete = (id: number, name: string) => {
		Modal.confirm({
			title: "移至废纸篓",
			content: `确定将「${name}」移至废纸篓？可在废纸篓中恢复。`,
			okText: "确定",
			cancelText: "取消",
			okButtonProps: { danger: true },
			onOk: async () => {
				await analyticsApi.deleteCard(id);
				message.success("已移至废纸篓");
				loadCards();
			},
		});
	};

	const columns: ColumnsType<CardListItem> = [
		{
			title: t(locale, "common.name"),
			dataIndex: "name",
			key: "name",
			ellipsis: true,
			render: (name: string, record) => (
				<Link to={`/bi/questions/${record.id}`} className="text-brand hover:underline font-medium">
					{name || t(locale, "common.untitled")}
				</Link>
			),
		},
		{
			title: t(locale, "common.description"),
			dataIndex: "description",
			key: "description",
			ellipsis: true,
			render: (desc: string | null) => <span className="text-text-secondary">{desc || "-"}</span>,
		},
		{
			title: t(locale, "common.type"),
			dataIndex: "display",
			key: "display",
			width: 90,
			render: (display: string) => <Tag>{DISPLAY_LABELS[display] ?? display ?? "-"}</Tag>,
		},
		{
			title: t(locale, "common.updatedAt"),
			dataIndex: "updated_at",
			key: "updated_at",
			width: 155,
			render: (v: string) => <span className="text-text-muted text-xs">{formatTime(v)}</span>,
			sorter: (a, b) => (a.updated_at ?? "").localeCompare(b.updated_at ?? ""),
			defaultSortOrder: "descend",
		},
		{
			title: t(locale, "common.actions"),
			key: "actions",
			width: 150,
			render: (_, record) => (
				<Space size={4}>
					<Link to={`/bi/questions/${record.id}`}>
						<Button type="link" size="small" icon={<EyeOutlined />}>查看</Button>
					</Link>
					<Link to={`/bi/questions/${record.id}/edit`}>
						<Button type="link" size="small" icon={<EditOutlined />}>编辑</Button>
					</Link>
					<Button
						type="link"
						size="small"
						danger
						icon={<DeleteOutlined />}
						onClick={() => handleDelete(record.id, record.name || "")}
					>
						删除
					</Button>
				</Space>
			),
		},
	];

	return (
		<div className="space-y-4">
			<PageHeader
				title={t(locale, "questions.title")}
				actions={
					<Space>
						<Button icon={<UploadOutlined />} onClick={() => setBatchImportOpen(true)}>
							批量导入 SQL
						</Button>
						<Link to="/bi/questions/new">
							<Button type="primary" icon={<PlusOutlined />}>
								{t(locale, "questions.new")}
							</Button>
						</Link>
					</Space>
				}
			/>
			<BatchImportCardsModal
				open={batchImportOpen}
				onClose={() => setBatchImportOpen(false)}
				onSuccess={loadCards}
			/>

			{state.state === "loading" && (
				<div className="flex justify-center py-12"><Spin size="large" /></div>
			)}
			{state.state === "error" && <ErrorNotice locale={locale} error={state.error} />}
			{state.state === "loaded" && (
				<Card
					title="卡片清单"
					extra={<Tag color="blue">{filteredCards.length} 张卡片</Tag>}
				>
					<div className="mb-4 flex items-center gap-3 flex-wrap">
						<Input.Search
							placeholder={t(locale, "common.search")}
							value={searchQuery}
							onChange={(e) => setSearchQuery(e.target.value)}
							allowClear
							style={{ width: 300 }}
						/>
						{selectedRowKeys.length > 0 && (
							<Space size="small">
								<span className="text-xs text-text-secondary">已选 {selectedRowKeys.length} 项</span>
								<Button size="small" onClick={() => setSelectedRowKeys([])}>取消选择</Button>
								<Button size="small" danger icon={<DeleteOutlined />} loading={batchDeleting} onClick={handleBatchDelete}>
									批量删除
								</Button>
							</Space>
						)}
					</div>
					{filteredCards.length === 0 ? (
						<EmptyState title={searchQuery ? t(locale, "common.noResults") : t(locale, "common.empty")} />
					) : (
						<Table<CardListItem>
							columns={columns}
							dataSource={filteredCards}
							rowKey={(r) => r.id}
							rowSelection={{
								selectedRowKeys,
								onChange: (keys) => setSelectedRowKeys(keys),
							}}
							pagination={{
								pageSize: 20,
								showSizeChanger: true,
								showQuickJumper: true,
								showTotal: (total) => `共 ${total} 条`,
							}}
						/>
					)}
				</Card>
			)}
		</div>
	);
}
