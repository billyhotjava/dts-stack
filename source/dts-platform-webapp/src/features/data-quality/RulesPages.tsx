import { DeleteOutlined, EditOutlined, PlayCircleOutlined, PlusOutlined, ReloadOutlined } from "@ant-design/icons";
import { Button, Card, Descriptions, Input, Popconfirm, Space, Switch, Tag } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useNavigate, useParams, useSearchParams } from "react-router";
import { toast } from "sonner";
import {
	changeQualityRuleVersionStatus,
	deleteQualityRule,
	getRuleHistory,
	listQualityRules,
	listQualityRuleVersions,
	toggleQualityRule,
	triggerQualityDryRun,
	triggerQualityRun,
} from "@/api/platformApi";
import { CompactTable } from "@/components/table";
import { formatTime } from "@/utils/textUtils";
import { ManagePermissionHint, QualityEmpty, QualityPageHeading, QualityStatus } from "./QualityShared";
import { qualityPath } from "./qualityRoutes";
import { displayName, isExecutableQualityRule, type QualityRule, type QualityRun, toList } from "./qualityTypes";
import { useDefaultLakeDatasets } from "./useDefaultLakeDatasets";
import { useQualityMaintainerAccess } from "./useQualityAccess";

type RuleVersion = {
	id?: string;
	ruleId?: string;
	version?: number;
	status?: string;
	definition?: string;
	notes?: string;
	createdBy?: string;
	createdDate?: string;
};

export function RuleListPage() {
	const navigate = useNavigate();
	const [searchParams] = useSearchParams();
	const linkedDatasetId = searchParams.get("datasetId") || "";
	const linkedKeyword = searchParams.get("keyword") || "";
	const canManage = useQualityMaintainerAccess();
	const { datasets, message: datasetMessage } = useDefaultLakeDatasets();
	const [rules, setRules] = useState<QualityRule[]>([]);
	const [loading, setLoading] = useState(true);
	const [keyword, setKeyword] = useState(linkedKeyword);
	const [actingId, setActingId] = useState("");

	const load = useCallback(async () => {
		setLoading(true);
		try {
			setRules(toList<QualityRule>(await listQualityRules()));
		} catch (error) {
			toast.error(error instanceof Error ? error.message : "质量规则加载失败");
		} finally {
			setLoading(false);
		}
	}, []);

	useEffect(() => {
		void load();
	}, [load]);

	useEffect(() => setKeyword(linkedKeyword), [linkedKeyword]);

	const datasetNames = useMemo(() => new Map(datasets.map((item) => [item.id, item.name])), [datasets]);
	const filtered = useMemo(() => {
		const query = keyword.trim().toLowerCase();
		return rules.filter((rule) => {
			if (linkedDatasetId && String(rule.datasetId || "") !== linkedDatasetId) return false;
			if (!query) return true;
			return [rule.name, rule.code, rule.type, rule.datasetId].some((value) =>
				String(value || "")
					.toLowerCase()
					.includes(query),
			);
		});
	}, [keyword, linkedDatasetId, rules]);

	const runAction = async (rule: QualityRule, action: "toggle" | "run" | "dry-run" | "delete") => {
		if (!rule.id) return;
		if (["run", "dry-run"].includes(action) && !isExecutableQualityRule(rule)) {
			toast.error("仅启用、当前版本已发布且存在数据集绑定的规则可以执行");
			return;
		}
		setActingId(rule.id);
		try {
			if (action === "toggle") await toggleQualityRule(rule.id, !rule.enabled);
			if (action === "run") await triggerQualityRun({ ruleId: rule.id });
			if (action === "dry-run") await triggerQualityDryRun({ ruleId: rule.id, datasetId: rule.datasetId });
			if (action === "delete") await deleteQualityRule(rule.id);
			toast.success(action === "run" ? "质量检测已提交" : action === "dry-run" ? "试跑完成" : "操作成功");
			if (["toggle", "delete"].includes(action)) await load();
			if (action === "run") navigate(qualityPath("run-records"));
		} catch (error) {
			toast.error(error instanceof Error ? error.message : "操作失败");
		} finally {
			setActingId("");
		}
	};

	const columns: ColumnsType<QualityRule> = [
		{
			title: "规则名称",
			dataIndex: "name",
			minWidth: 180,
			render: (value, row) => (
				<Button type="link" onClick={() => navigate(qualityPath("rule-detail", { ruleId: row.id }))}>
					{displayName(value)}
				</Button>
			),
		},
		{ title: "类型", dataIndex: "type", width: 120, render: (value) => <Tag>{displayName(value)}</Tag> },
		{
			title: "严重性",
			dataIndex: "severity",
			width: 100,
			render: (value) => (
				<Tag color={value === "CRITICAL" ? "red" : value === "HIGH" ? "orange" : "blue"}>{displayName(value)}</Tag>
			),
		},
		{
			title: "数据资产",
			dataIndex: "datasetId",
			minWidth: 160,
			ellipsis: true,
			render: (value) => datasetNames.get(String(value)) || displayName(value),
		},
		{
			title: "版本",
			width: 110,
			render: (_, row) => (
				<Space size={4}>
					<Tag color="blue">v{row.latestVersion?.version || "-"}</Tag>
					<QualityStatus status={row.latestVersion?.status} />
				</Space>
			),
		},
		{
			title: "启用",
			dataIndex: "enabled",
			width: 90,
			render: (value, row) => (
				<Switch
					size="small"
					checked={Boolean(value)}
					disabled={!canManage}
					loading={actingId === row.id}
					onChange={() => void runAction(row, "toggle")}
				/>
			),
		},
		{
			title: "操作",
			width: 280,
			fixed: "right",
			render: (_, row) => (
				<Space size={4}>
					<Button
						size="small"
						onClick={() => void runAction(row, "dry-run")}
						disabled={!canManage || !isExecutableQualityRule(row)}
						loading={actingId === row.id}
						title="仅启用、当前版本已发布且存在数据集绑定的规则可以试跑"
					>
						试跑
					</Button>
					<Button
						size="small"
						icon={<PlayCircleOutlined />}
						onClick={() => void runAction(row, "run")}
						disabled={!canManage || !isExecutableQualityRule(row)}
						title="仅启用、当前版本已发布且存在数据集绑定的规则可以执行"
					>
						执行
					</Button>
					<Button
						size="small"
						icon={<EditOutlined />}
						onClick={() => navigate(`${qualityPath("rule-detail", { ruleId: row.id })}/edit`)}
						disabled={!canManage}
					>
						编辑
					</Button>
					<Popconfirm title="确认删除该规则？" onConfirm={() => void runAction(row, "delete")} disabled={!canManage}>
						<Button size="small" danger icon={<DeleteOutlined />} disabled={!canManage} />
					</Popconfirm>
				</Space>
			),
		},
	];

	return (
		<div className="dq-page">
			<QualityPageHeading
				title="质量规则"
				description="维护规则启用状态与版本生命周期；只有已启用、当前版本已发布且存在数据集绑定的规则才能运行。"
				actions={[
					<ManagePermissionHint key="permission" canManage={canManage} />,
					<Button key="reload" icon={<ReloadOutlined />} onClick={() => void load()}>
						刷新
					</Button>,
					<Button
						key="new"
						type="primary"
						icon={<PlusOutlined />}
						disabled={!canManage || Boolean(datasetMessage)}
						onClick={() => navigate(qualityPath("rule-editor"))}
					>
						新建规则
					</Button>,
				]}
			/>
			<Card size="small">
				<Input.Search
					allowClear
					placeholder="搜索规则名称、编码、类型或数据资产"
					value={keyword}
					onChange={(event) => setKeyword(event.target.value)}
					style={{ maxWidth: 420 }}
				/>
			</Card>
			<CompactTable
				rowKey="id"
				columns={columns}
				dataSource={filtered}
				loading={loading}
				pagination={{ pageSize: 10 }}
			/>
		</div>
	);
}

export function RuleDetailPage() {
	const { ruleId = "" } = useParams();
	const navigate = useNavigate();
	const canManage = useQualityMaintainerAccess();
	const { datasets } = useDefaultLakeDatasets();
	const [rule, setRule] = useState<QualityRule>();
	const [versions, setVersions] = useState<RuleVersion[]>([]);
	const [history, setHistory] = useState<QualityRun[]>([]);
	const [loading, setLoading] = useState(true);
	const [loadError, setLoadError] = useState("");
	const loadSequence = useRef(0);
	const activeRuleId = useRef(ruleId);
	activeRuleId.current = ruleId;

	const load = useCallback(async () => {
		const requestedRuleId = ruleId;
		const sequence = ++loadSequence.current;
		setLoading(true);
		setLoadError("");
		setRule(undefined);
		setVersions([]);
		setHistory([]);
		try {
			const [ruleResponse, versionResponse, historyResponse] = await Promise.all([
				listQualityRules(),
				listQualityRuleVersions(requestedRuleId),
				getRuleHistory(requestedRuleId, 20),
			]);
			if (sequence !== loadSequence.current || activeRuleId.current !== requestedRuleId) return;
			const loadedRule = toList<QualityRule>(ruleResponse).find((item) => String(item.id) === requestedRuleId);
			if (!loadedRule) throw new Error("未找到该质量规则，可能已被删除");
			setRule(loadedRule);
			setVersions(toList<RuleVersion>(versionResponse));
			setHistory(toList<QualityRun>(historyResponse));
		} catch (error) {
			if (sequence !== loadSequence.current || activeRuleId.current !== requestedRuleId) return;
			const message = error instanceof Error ? error.message : "规则详情加载失败";
			setLoadError(message);
			toast.error(message);
		} finally {
			if (sequence === loadSequence.current && activeRuleId.current === requestedRuleId) setLoading(false);
		}
	}, [ruleId]);

	useEffect(() => {
		void load();
	}, [load]);

	const version = Number(rule?.latestVersion?.version);
	const loadedVersion = versions.find(
		(item) =>
			Number(item.version) === version &&
			(!item.ruleId || String(item.ruleId) === ruleId) &&
			(!item.id || !rule?.latestVersion?.id || String(item.id) === String(rule.latestVersion.id)),
	);
	const currentRuleMatches = Boolean(!loading && !loadError && rule && String(rule.id) === ruleId);
	const currentVersionMatches =
		currentRuleMatches && Number.isSafeInteger(version) && version > 0 && loadedVersion?.version === version;
	const currentVersionStatus = String(loadedVersion?.status || rule?.latestVersion?.status || "").toUpperCase();

	const changeStatus = async (status: "PUBLISHED" | "ARCHIVED") => {
		const transitionAllowed =
			(status === "PUBLISHED" && currentVersionStatus === "DRAFT") ||
			(status === "ARCHIVED" && ["DRAFT", "PUBLISHED"].includes(currentVersionStatus));
		if (!canManage || !rule || String(rule.id) !== ruleId || !currentVersionMatches || !transitionAllowed) {
			toast.error("规则或版本状态尚未安全加载，请刷新后重试");
			return;
		}
		const operationRuleId = String(rule.id);
		try {
			await changeQualityRuleVersionStatus(operationRuleId, version, { status });
			toast.success(status === "PUBLISHED" ? "版本已发布" : "版本已归档");
			if (activeRuleId.current === operationRuleId) await load();
		} catch (error) {
			toast.error(error instanceof Error ? error.message : "版本状态变更失败");
		}
	};

	if (!loading && loadError) return <QualityEmpty description={`规则详情加载失败：${loadError}`} />;
	if (!loading && !rule) return <QualityEmpty description="未找到该质量规则，可能已被删除。" />;
	const datasetName = datasets.find((item) => item.id === rule?.datasetId)?.name || rule?.datasetId;

	return (
		<div className="dq-page">
			<QualityPageHeading
				title={rule?.name || "规则详情"}
				description="查看规则当前版本、版本变更与最近执行结果。"
				actions={[
					<Button key="back" onClick={() => navigate(qualityPath("rule-list"))}>
						返回列表
					</Button>,
					<Button
						key="edit"
						type="primary"
						disabled={!canManage || !currentRuleMatches}
						onClick={() => navigate(`${qualityPath("rule-detail", { ruleId })}/edit`)}
					>
						编辑规则
					</Button>,
					<Button
						key="publish"
						disabled={!canManage || !currentVersionMatches || currentVersionStatus !== "DRAFT"}
						onClick={() => void changeStatus("PUBLISHED")}
					>
						发布当前版本
					</Button>,
					<Button
						key="archive"
						disabled={!canManage || !currentVersionMatches || !["DRAFT", "PUBLISHED"].includes(currentVersionStatus)}
						onClick={() => void changeStatus("ARCHIVED")}
					>
						归档
					</Button>,
				]}
			/>
			<Card loading={loading} title="基本信息">
				<Descriptions column={{ xs: 1, md: 2, xl: 3 }} size="small">
					<Descriptions.Item label="规则编码">{displayName(rule?.code)}</Descriptions.Item>
					<Descriptions.Item label="类型">{displayName(rule?.type)}</Descriptions.Item>
					<Descriptions.Item label="严重性">{displayName(rule?.severity)}</Descriptions.Item>
					<Descriptions.Item label="数据资产">{displayName(datasetName)}</Descriptions.Item>
					<Descriptions.Item label="执行器">{displayName(rule?.executor)}</Descriptions.Item>
					<Descriptions.Item label="规则分类">{displayName(rule?.category)}</Descriptions.Item>
					<Descriptions.Item label="当前版本">
						<Space>
							<Tag color="blue">v{rule?.latestVersion?.version || "-"}</Tag>
							<QualityStatus status={rule?.latestVersion?.status} />
						</Space>
					</Descriptions.Item>
				</Descriptions>
			</Card>
			<Card title="规则定义">
				<pre className="dq-code-block">
					{displayName(
						rule?.latestVersion?.definition || (rule?.definition ? JSON.stringify(rule.definition, null, 2) : ""),
					)}
				</pre>
			</Card>
			<Card title="版本历史">
				<CompactTable
					rowKey={(row) => String(row.id || row.version)}
					dataSource={versions}
					pagination={false}
					columns={[
						{ title: "版本", dataIndex: "version", width: 100, render: (value) => <Tag color="blue">v{value}</Tag> },
						{ title: "状态", dataIndex: "status", width: 130, render: (value) => <QualityStatus status={value} /> },
						{ title: "说明", dataIndex: "notes", ellipsis: true },
						{ title: "创建人", dataIndex: "createdBy", width: 130 },
						{ title: "创建时间", dataIndex: "createdDate", width: 180, render: formatTime },
					]}
				/>
			</Card>
			<Card title="最近执行">
				<CompactTable
					rowKey="runId"
					dataSource={history}
					pagination={false}
					columns={[
						{ title: "运行 ID", dataIndex: "runId", ellipsis: true },
						{ title: "时间", dataIndex: "time", width: 180, render: formatTime },
						{ title: "状态", dataIndex: "status", width: 110, render: (value) => <QualityStatus status={value} /> },
						{
							title: "通过率",
							dataIndex: "passRate",
							width: 100,
							render: (value, row) =>
								["QUEUED", "RUNNING"].includes(String(row.status || "").toUpperCase()) || value == null
									? "暂无统计"
									: `${value}%`,
						},
						{ title: "失败行数", dataIndex: "failingRows", width: 120 },
					]}
				/>
			</Card>
		</div>
	);
}
