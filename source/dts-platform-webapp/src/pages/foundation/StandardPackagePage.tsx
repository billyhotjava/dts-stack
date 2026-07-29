import { ArrowLeftOutlined, DownloadOutlined, InboxOutlined } from "@ant-design/icons";
import { Button, Card, Popconfirm, Result, Space, Steps, Tabs, Tag, Typography, Upload } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useCallback, useEffect, useMemo, useState } from "react";
import { useNavigate, useSearchParams } from "react-router";
import { toast } from "sonner";
import {
	applyStandardPackageImport,
	downloadDataStandardPackageTemplate,
	installBuiltinStandardPackage,
	listBuiltinStandardPackages,
	listStandardPackageRuns,
	previewStandardPackageImport,
	rollbackStandardPackageRun,
} from "@/api/platformApi";
import { EmptyState } from "@/components/empty-state";
import { PageHeader } from "@/components/page-header";
import { CompactTable } from "@/components/table";
import { useGovernanceManageAccess } from "@/hooks/useModuleManageAccess";
import { buildStandardPackageReturnRoute, getStandardPackageSourceMeta } from "../governance/standardOwnerNavigation";

const { Text } = Typography;

type PreviewError = { row?: number; message?: string };

type FileReport = {
	file: string;
	present?: boolean;
	total?: number;
	toCreate?: number;
	toUpdate?: number;
	errorCount?: number;
	errors?: PreviewError[];
};

type PreviewResult = {
	runId?: string;
	packageName?: string;
	files?: FileReport[];
	totalErrors?: number;
	blocking?: boolean;
};

type ApplyResult = {
	runId?: string;
	status?: string;
	totalCreated?: number;
	totalUpdated?: number;
	created?: Record<string, number>;
	updated?: Record<string, number>;
};

type RunRow = {
	id: string;
	packageName?: string;
	source?: string;
	status?: string;
	summary?: string;
	createdBy?: string;
	createdDate?: string;
};

type RunsPayload = {
	content?: RunRow[];
	total?: number;
};

type BuiltinPackage = {
	code: string;
	name?: string;
	standardNo?: string;
	category?: string;
	description?: string;
	installed?: boolean;
	entryCounts?: Record<string, number>;
};

const FILE_LABELS: Record<string, string> = {
	"01-business-terms.csv": "业务术语",
	"02-data-elements.csv": "数据元",
	"03-reference-code-directories.csv": "码表目录",
	"04-reference-code-items.csv": "码表码值",
	"05-reference-code-mappings.csv": "码表映射",
	"06-measurement-units.csv": "计量单位",
};

const SOURCE_LABELS: Record<string, string> = {
	UPLOAD: "上传",
	BUILTIN: "内置包",
	LEGACY_SINGLE: "数据元单文件",
};

const STATUS_META: Record<string, { color: string; label: string }> = {
	PREVIEWED: { color: "processing", label: "已预检" },
	APPLIED: { color: "success", label: "已应用" },
	ROLLED_BACK: { color: "default", label: "已回滚" },
	FAILED: { color: "error", label: "失败" },
};

const ENTITY_LABELS: Record<string, string> = {
	TERM: "业务术语",
	ELEMENT: "数据元",
	CODE_DIRECTORY: "码表目录",
	CODE_VALUE: "码表码值",
	CODE_MAPPING: "码表映射",
	MEASUREMENT_UNIT: "计量单位",
};

function downloadBlob(blob: Blob, filename: string) {
	const url = URL.createObjectURL(blob);
	const link = document.createElement("a");
	link.href = url;
	link.download = filename;
	document.body.appendChild(link);
	link.click();
	link.remove();
	URL.revokeObjectURL(url);
}

export default function StandardPackagePage() {
	const canManage = useGovernanceManageAccess();
	const navigate = useNavigate();
	const [searchParams] = useSearchParams();
	const fromSource = searchParams.get("from");
	const sourceMeta = useMemo(() => getStandardPackageSourceMeta(fromSource), [fromSource]);
	const sourceReturnRoute = useMemo(() => buildStandardPackageReturnRoute(searchParams), [searchParams]);
	const appliedSourceReturnRoute = useMemo(
		() => buildStandardPackageReturnRoute(searchParams, { applied: true }),
		[searchParams],
	);
	const [activeTab, setActiveTab] = useState("wizard");

	// wizard state
	const [step, setStep] = useState(0);
	const [file, setFile] = useState<File | null>(null);
	const [previewing, setPreviewing] = useState(false);
	const [preview, setPreview] = useState<PreviewResult | null>(null);
	const [applying, setApplying] = useState(false);
	const [applyResult, setApplyResult] = useState<ApplyResult | null>(null);
	const [templateDownloading, setTemplateDownloading] = useState(false);

	// builtin state
	const [builtins, setBuiltins] = useState<BuiltinPackage[]>([]);
	const [builtinLoading, setBuiltinLoading] = useState(false);
	const [installingCode, setInstallingCode] = useState<string | null>(null);

	// history state
	const [runs, setRuns] = useState<RunRow[]>([]);
	const [runsTotal, setRunsTotal] = useState(0);
	const [runsLoading, setRunsLoading] = useState(false);
	const [pageNum, setPageNum] = useState(1);
	const [pageSize, setPageSize] = useState(10);

	const loadRuns = useCallback(async () => {
		setRunsLoading(true);
		try {
			const resp = (await listStandardPackageRuns({ page: pageNum - 1, size: pageSize })) as RunsPayload;
			setRuns(Array.isArray(resp?.content) ? resp.content : []);
			setRunsTotal(Number(resp?.total) || 0);
		} catch (err: any) {
			toast.error(err?.message || "加载导入历史失败");
		} finally {
			setRunsLoading(false);
		}
	}, [pageNum, pageSize]);

	const loadBuiltins = useCallback(async () => {
		setBuiltinLoading(true);
		try {
			const resp = (await listBuiltinStandardPackages()) as BuiltinPackage[];
			setBuiltins(Array.isArray(resp) ? resp : []);
		} catch (err: any) {
			toast.error(err?.message || "加载内置标准包失败");
		} finally {
			setBuiltinLoading(false);
		}
	}, []);

	useEffect(() => {
		if (activeTab === "history") {
			void loadRuns();
		}
		if (activeTab === "builtin") {
			void loadBuiltins();
		}
	}, [activeTab, loadRuns, loadBuiltins]);

	const installBuiltin = async (pkg: BuiltinPackage) => {
		setInstallingCode(pkg.code);
		try {
			const resp = (await installBuiltinStandardPackage(pkg.code)) as {
				applied?: boolean;
				totalCreated?: number;
				totalUpdated?: number;
			};
			if (resp?.applied === false) {
				toast.error(`内置包 ${pkg.name || pkg.code} 校验未通过，请联系产品方修复资源`);
			} else {
				toast.success(
					`已安装 ${pkg.name || pkg.code}：新增 ${resp?.totalCreated ?? 0}，更新 ${resp?.totalUpdated ?? 0}`,
				);
			}
			void loadBuiltins();
		} catch (err: any) {
			toast.error(err?.message || "安装内置标准包失败");
		} finally {
			setInstallingCode(null);
		}
	};

	const downloadTemplate = async () => {
		setTemplateDownloading(true);
		try {
			const blob = await downloadDataStandardPackageTemplate();
			downloadBlob(blob, "data-standard-package-template.zip");
			toast.success("标准包模板已下载");
		} catch (err: any) {
			toast.error(err?.message || "下载标准包模板失败");
		} finally {
			setTemplateDownloading(false);
		}
	};

	const startPreview = async () => {
		if (!file) {
			toast.error("请先选择标准包 zip 文件");
			return;
		}
		setPreviewing(true);
		try {
			const formData = new FormData();
			formData.append("file", file);
			const resp = (await previewStandardPackageImport(formData)) as PreviewResult;
			setPreview(resp);
			setStep(1);
		} catch (err: any) {
			toast.error(err?.message || "标准包校验失败");
		} finally {
			setPreviewing(false);
		}
	};

	const confirmApply = async () => {
		if (!preview?.runId) return;
		setApplying(true);
		try {
			const resp = (await applyStandardPackageImport(preview.runId)) as ApplyResult;
			setApplyResult(resp);
			setStep(2);
		} catch (err: any) {
			toast.error(err?.message || "标准包应用失败");
		} finally {
			setApplying(false);
		}
	};

	const resetWizard = () => {
		setStep(0);
		setFile(null);
		setPreview(null);
		setApplyResult(null);
	};

	const downloadErrorReport = () => {
		if (!preview?.files) return;
		const lines = ["file,row,message"];
		for (const report of preview.files) {
			for (const error of report.errors || []) {
				const message = String(error.message || "").replaceAll('"', '""');
				lines.push(`${report.file},${error.row ?? ""},"${message}"`);
			}
		}
		downloadBlob(new Blob([`﻿${lines.join("\n")}`], { type: "text/csv;charset=utf-8" }), "standard-package-errors.csv");
	};

	const rollbackRun = async (runId: string) => {
		try {
			await rollbackStandardPackageRun(runId);
			toast.success("已回滚该次导入");
			void loadRuns();
		} catch (err: any) {
			toast.error(err?.message || "回滚失败");
		}
	};

	const errorColumns: ColumnsType<PreviewError> = [
		{ title: "行号", dataIndex: "row", width: 80 },
		{ title: "原因", dataIndex: "message" },
	];

	const previewTabs = useMemo(() => {
		if (!preview?.files) return [];
		return preview.files.map((report) => {
			const errorCount = report.errorCount || 0;
			return {
				key: report.file,
				label: (
					<Space size={4}>
						<span>{FILE_LABELS[report.file] || report.file}</span>
						{report.present === false ? (
							<Tag>未提供</Tag>
						) : errorCount > 0 ? (
							<Tag color="error">{errorCount} 错误</Tag>
						) : (
							<Tag color="success">通过</Tag>
						)}
					</Space>
				),
				children:
					report.present === false ? (
						<EmptyState title="包内未提供该文件" description="标准包允许按需提供部分 CSV，未提供的对象类型将跳过。" />
					) : (
						<Space direction="vertical" className="w-full" size={12}>
							<Space size={16}>
								<Text>共 {report.total ?? 0} 行</Text>
								<Text type="success">新增 {report.toCreate ?? 0}</Text>
								<Text type="warning">更新 {report.toUpdate ?? 0}</Text>
								<Text type={errorCount > 0 ? "danger" : "secondary"}>错误 {errorCount}</Text>
							</Space>
							{errorCount > 0 ? (
								<CompactTable<PreviewError>
									rowKey={(record, index) => `${record.row}-${index}`}
									columns={errorColumns}
									dataSource={report.errors || []}
									pagination={{ defaultPageSize: 10, showSizeChanger: true }}
								/>
							) : null}
						</Space>
					),
			};
		});
	}, [preview]);

	const runColumns: ColumnsType<RunRow> = [
		{ title: "包名", dataIndex: "packageName" },
		{
			title: "来源",
			dataIndex: "source",
			width: 120,
			render: (value: string) => SOURCE_LABELS[value] || value || "-",
		},
		{
			title: "状态",
			dataIndex: "status",
			width: 100,
			render: (value: string) => {
				const meta = STATUS_META[value];
				return meta ? <Tag color={meta.color}>{meta.label}</Tag> : <Tag>{value || "-"}</Tag>;
			},
		},
		{ title: "摘要", dataIndex: "summary" },
		{ title: "操作人", dataIndex: "createdBy", width: 110 },
		{
			title: "时间",
			dataIndex: "createdDate",
			width: 170,
			render: (value: string) => (value ? new Date(value).toLocaleString() : "-"),
		},
		{
			title: "操作",
			dataIndex: "action",
			width: 100,
			render: (_: unknown, record) =>
				record.status === "APPLIED" ? (
					<Popconfirm
						title="回滚该次导入？"
						description="将删除本次新增并还原被更新的记录。"
						onConfirm={() => rollbackRun(record.id)}
						okText="回滚"
						cancelText="取消"
					>
						<Button type="link" size="small" danger disabled={!canManage}>
							回滚
						</Button>
					</Popconfirm>
				) : null,
		},
	];

	const applySummary = useMemo(() => {
		if (!applyResult) return [];
		const rows: { type: string; created: number; updated: number }[] = [];
		const types = new Set([...Object.keys(applyResult.created || {}), ...Object.keys(applyResult.updated || {})]);
		for (const type of types) {
			rows.push({
				type: ENTITY_LABELS[type] || type,
				created: applyResult.created?.[type] || 0,
				updated: applyResult.updated?.[type] || 0,
			});
		}
		return rows;
	}, [applyResult]);

	const wizard = (
		<Space direction="vertical" className="w-full" size={16}>
			<Steps current={step} items={[{ title: "上传标准包" }, { title: "校验报告" }, { title: "应用结果" }]} />
			{step === 0 ? (
				<Card>
					<Space direction="vertical" className="w-full" size={16}>
						<Upload.Dragger
							accept=".zip"
							maxCount={1}
							beforeUpload={(selected) => {
								setFile(selected);
								return false;
							}}
							onRemove={() => setFile(null)}
							fileList={file ? [{ uid: "-1", name: file.name }] : []}
							data-testid="standard-package-wizard-upload"
						>
							<p className="ant-upload-drag-icon">
								<InboxOutlined />
							</p>
							<p className="ant-upload-text">点击或拖拽标准包 zip 到此处</p>
							<p className="ant-upload-hint">
								包内包含业务术语、数据元、码表目录/码值/映射和计量单位共 6 个 CSV，可按需提供部分文件
							</p>
						</Upload.Dragger>
						<Space>
							<Button
								icon={<DownloadOutlined />}
								onClick={downloadTemplate}
								loading={templateDownloading}
								data-testid="standard-package-template-download"
							>
								下载标准包模板
							</Button>
							<Button type="primary" onClick={startPreview} loading={previewing} disabled={!canManage || !file}>
								开始校验
							</Button>
						</Space>
					</Space>
				</Card>
			) : null}
			{step === 1 && preview ? (
				<Card>
					<Space direction="vertical" className="w-full" size={16}>
						<Space size={16} wrap>
							<Text strong>{preview.packageName}</Text>
							{preview.blocking ? (
								<Tag color="error">存在 {preview.totalErrors} 处错误，修复后重新上传</Tag>
							) : (
								<Tag color="success">校验通过</Tag>
							)}
						</Space>
						<Tabs items={previewTabs} />
						<Space>
							<Button onClick={resetWizard}>重新上传</Button>
							{preview.blocking ? (
								<Button onClick={downloadErrorReport} data-testid="standard-package-error-report-download">
									下载错误报告 CSV
								</Button>
							) : (
								<Button type="primary" onClick={confirmApply} loading={applying} disabled={!canManage}>
									确认应用
								</Button>
							)}
						</Space>
					</Space>
				</Card>
			) : null}
			{step === 2 && applyResult ? (
				<Card>
					<Result
						status="success"
						title="标准包已应用"
						subTitle={`新增 ${applyResult.totalCreated ?? 0} 条，更新 ${applyResult.totalUpdated ?? 0} 条`}
						extra={[
							<Button key="again" onClick={resetWizard}>
								再导入一个
							</Button>,
							...(sourceMeta && appliedSourceReturnRoute
								? [
										<Button key="return-source" onClick={() => navigate(appliedSourceReturnRoute)}>
											{sourceMeta.label}
										</Button>,
									]
								: []),
							<Button key="history" type="primary" onClick={() => setActiveTab("history")}>
								查看导入历史
							</Button>,
						]}
					/>
					{applySummary.length > 0 ? (
						<CompactTable
							rowKey="type"
							columns={[
								{ title: "对象类型", dataIndex: "type" },
								{ title: "新增", dataIndex: "created", width: 100 },
								{ title: "更新", dataIndex: "updated", width: 100 },
							]}
							dataSource={applySummary}
							pagination={false}
						/>
					) : null}
				</Card>
			) : null}
		</Space>
	);

	const builtinPanel = (
		<Card loading={builtinLoading}>
			{builtins.length === 0 && !builtinLoading ? (
				<EmptyState title="暂无内置标准包" description="产品内置的国标包会显示在这里。" />
			) : (
				<div className="grid grid-cols-1 gap-4 md:grid-cols-2">
					{builtins.map((pkg) => {
						const totalEntries = Object.values(pkg.entryCounts || {}).reduce((sum, count) => sum + count, 0);
						return (
							<Card key={pkg.code} size="small" data-testid={`builtin-package-${pkg.code}`}>
								<Space direction="vertical" className="w-full" size={8}>
									<Space size={8} wrap>
										<Text strong>{pkg.name}</Text>
										<Tag>{pkg.standardNo}</Tag>
										<Tag color="blue">{pkg.category}</Tag>
										{pkg.installed ? <Tag color="success">已安装</Tag> : null}
									</Space>
									<Text type="secondary">{pkg.description}</Text>
									<Space size={16}>
										<Text type="secondary">共 {totalEntries} 条</Text>
										<Button
											size="small"
											type={pkg.installed ? "default" : "primary"}
											loading={installingCode === pkg.code}
											disabled={!canManage || (installingCode !== null && installingCode !== pkg.code)}
											onClick={() => installBuiltin(pkg)}
										>
											{pkg.installed ? "重新安装" : "安装"}
										</Button>
									</Space>
								</Space>
							</Card>
						);
					})}
				</div>
			)}
		</Card>
	);

	const history = (
		<Card>
			<CompactTable<RunRow>
				rowKey="id"
				loading={runsLoading}
				columns={runColumns}
				dataSource={runs}
				locale={{
					emptyText: <EmptyState title="暂无导入记录" description="通过导入向导上传标准包后，历史会出现在这里。" />,
				}}
				pagination={{
					current: pageNum,
					pageSize,
					total: runsTotal,
					showSizeChanger: true,
					onChange: (nextPage, nextSize) => {
						if (nextSize !== pageSize) {
							setPageNum(1);
							setPageSize(nextSize);
						} else {
							setPageNum(nextPage);
						}
					},
				}}
			/>
		</Card>
	);

	return (
		<div className="flex flex-col gap-4 p-4">
			<PageHeader
				title="标准包导入"
				actions={
					sourceMeta && sourceReturnRoute ? (
						<Button
							icon={<ArrowLeftOutlined />}
							onClick={() => navigate(sourceReturnRoute)}
							data-testid={`standard-package-return-${sourceMeta.source}`}
						>
							{sourceMeta.label}
						</Button>
					) : null
				}
			/>
			<div className="rounded-md border border-border bg-muted/20 px-4 py-3 text-sm text-muted-foreground">
				下载模板 → 填写业务术语/数据元/公共码表/计量单位 → 上传校验 → 确认应用，支持整包回滚。
			</div>
			<Tabs
				activeKey={activeTab}
				onChange={setActiveTab}
				items={[
					{ key: "wizard", label: "导入向导", children: wizard },
					{ key: "builtin", label: "内置标准包", children: builtinPanel },
					{ key: "history", label: "导入历史", children: history },
				]}
			/>
		</div>
	);
}
