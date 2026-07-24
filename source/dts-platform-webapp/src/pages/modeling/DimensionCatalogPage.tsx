import { Alert, Button, Card, Empty, Input, Modal, Space, Tag, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import { Plus, RefreshCw } from "lucide-react";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useNavigate } from "react-router";
import {
	confirmDimensionDefinition,
	getDimensionDefinition,
	listDimensionDefinitions,
	retireDimensionDefinition,
} from "@/api/dimensionDefinitionApi";
import { CompactTable } from "@/components/table";
import { useCatalogDomainOptions } from "@/hooks/useCatalogDomainOptions";
import { useSearchParams } from "@/routes/hooks";
import { useUserInfo, useUserRoles } from "@/store/userStore";
import { DimensionDefinitionCreateDrawer } from "./components/DimensionDefinitionCreateDrawer";
import {
	canRetireDimensionDefinition,
	dimensionCatalogEmptyText,
	dimensionDefinitionErrorMessage,
	dimensionDefinitionStatusLabel,
	formatDimensionDefinitionUpdatedAt,
	isDimensionDefinitionVersionConflict,
} from "./dimensionCatalogViewState";
import type { DimensionDefinitionView } from "./dimensionDefinitionContract";
import { hasWarehousePlanCreateAccess } from "./warehousePlanCreateFlow";

const { Text, Title } = Typography;

export default function DimensionCatalogPage() {
	const navigate = useNavigate();
	const searchParams = useSearchParams();
	const userRoles = useUserRoles();
	const userInfo = useUserInfo();
	const canEdit = hasWarehousePlanCreateAccess(userRoles);
	const domainId = searchParams.get("domainId")?.trim() || "";
	const [dimensions, setDimensions] = useState<DimensionDefinitionView[]>([]);
	const [loading, setLoading] = useState(true);
	const [loadError, setLoadError] = useState("");
	const [actionError, setActionError] = useState("");
	const [actionNeedsRefresh, setActionNeedsRefresh] = useState(false);
	const [search, setSearch] = useState("");
	const [createOpen, setCreateOpen] = useState(false);
	const [editing, setEditing] = useState<DimensionDefinitionView | null>(null);
	const loadRequestRef = useRef(0);
	const actionRequestRef = useRef(0);
	const activeDomainRef = useRef(domainId);
	activeDomainRef.current = domainId;
	const { labelByKey, options: domainOptions, loading: domainsLoading, error: domainsError } = useCatalogDomainOptions();

	const load = useCallback(async () => {
		const requestId = ++loadRequestRef.current;
		setLoading(true);
		setLoadError("");
		try {
			const result = await listDimensionDefinitions(domainId ? { domainId } : undefined);
			if (requestId !== loadRequestRef.current) return;
			setDimensions(Array.isArray(result) ? result : []);
		} catch {
			if (requestId !== loadRequestRef.current) return;
			setDimensions([]);
			setLoadError("维度目录加载失败，请稍后重试");
		} finally {
			if (requestId === loadRequestRef.current) setLoading(false);
		}
	}, [domainId]);

	useEffect(() => {
		setCreateOpen(false);
		setEditing(null);
		setActionError("");
		setActionNeedsRefresh(false);
		setDimensions([]);
		void load();
		return () => {
			loadRequestRef.current += 1;
			actionRequestRef.current += 1;
		};
	}, [load]);

	const refreshAfterActionConflict = () => {
		actionRequestRef.current += 1;
		setActionError("");
		setActionNeedsRefresh(false);
		void load();
	};

	const visibleDimensions = useMemo(() => {
		const keyword = search.trim().toLowerCase();
		if (!keyword) return dimensions;
		return dimensions.filter((definition) =>
			[definition.name, definition.systemCode, definition.definition]
				.filter(Boolean)
				.some((value) => value.toLowerCase().includes(keyword)),
		);
	}, [dimensions, search]);
	const emptyText = dimensionCatalogEmptyText({
		totalCount: dimensions.length,
		visibleCount: visibleDimensions.length,
		search,
		canEdit,
	});

	const enterDimensionTableCreation = (definition: DimensionDefinitionView) => {
		const params = new URLSearchParams({
			modelType: "DIMENSION",
			create: "lightweight",
			dimensionDefinitionId: definition.id,
			dimensionDefinitionRevision: String(definition.revision),
		});
		navigate(`/modeling/models?${params.toString()}`);
	};

	const showReferences = (definition: DimensionDefinitionView) => {
		Modal.info({
			title: `${definition.name}的引用`,
			content: `当前有 ${definition.usageCount} 个逻辑模型引用此维度定义。`,
			okText: "知道了",
		});
	};

	const retire = (definition: DimensionDefinitionView) => {
		Modal.confirm({
			title: "退役维度",
			content: `确认退役“${definition.name}”吗？已创建的维度表不会被删除。`,
			okText: "确认退役",
			okButtonProps: { danger: true },
			cancelText: "取消",
			onOk: async () => {
				const requestId = ++actionRequestRef.current;
				const actionDomainId = domainId;
				setActionError("");
				setActionNeedsRefresh(false);
				try {
					await retireDimensionDefinition(definition);
					if (requestId !== actionRequestRef.current || actionDomainId !== activeDomainRef.current) return;
					await load();
				} catch (error) {
					if (requestId !== actionRequestRef.current || actionDomainId !== activeDomainRef.current) return;
					setActionError(dimensionDefinitionErrorMessage(error));
					setActionNeedsRefresh(isDimensionDefinitionVersionConflict(error));
				}
			},
		});
	};

	const confirmCurrent = (definition: DimensionDefinitionView) => {
		Modal.confirm({
			title: "设为现行维度",
			content: `确认将“${definition.name}”设为现行版本吗？设为现行后，逻辑模型才能引用该版本。`,
			okText: "确认设为现行",
			cancelText: "取消",
			onOk: async () => {
				const requestId = ++actionRequestRef.current;
				const actionDomainId = domainId;
				setActionError("");
				setActionNeedsRefresh(false);
				try {
					await confirmDimensionDefinition(definition);
					if (requestId !== actionRequestRef.current || actionDomainId !== activeDomainRef.current) return;
					await load();
				} catch (error) {
					if (requestId !== actionRequestRef.current || actionDomainId !== activeDomainRef.current) return;
					setActionError(dimensionDefinitionErrorMessage(error));
					setActionNeedsRefresh(isDimensionDefinitionVersionConflict(error));
				}
			},
		});
	};

	const columns: ColumnsType<DimensionDefinitionView> = [
		{
			title: "维度名称",
			dataIndex: "name",
			width: 220,
			render: (value: string) => <div className="font-medium text-gray-900">{value}</div>,
		},
		{
			title: "系统编码",
			dataIndex: "systemCode",
			width: 180,
			render: (value: string) => <Text code>{value}</Text>,
		},
		{
			title: "业务分类",
			dataIndex: "domainId",
			width: 210,
			render: (value: string) => labelByKey[value] || "已绑定业务分类",
		},
		{
			title: "状态",
			dataIndex: "status",
			width: 100,
			render: (value: DimensionDefinitionView["status"]) => (
				<Tag color={value === "CURRENT" ? "processing" : value === "RETIRED" ? "default" : "gold"}>
					{dimensionDefinitionStatusLabel(value)}
				</Tag>
			),
		},
		{
			title: "逻辑模型引用数",
			dataIndex: "usageCount",
			align: "right",
			width: 150,
			render: (value: number) => value,
		},
		{
			title: "更新时间",
			dataIndex: "updatedAt",
			width: 180,
			render: (value: string) => formatDimensionDefinitionUpdatedAt(value),
		},
		{
			title: "操作",
			key: "actions",
			width: 330,
			fixed: "right",
			render: (_value, definition) => (
				<Space size={0} wrap>
					{definition.status === "DRAFT" ? (
						<Button type="link" size="small" disabled={!canEdit} onClick={() => confirmCurrent(definition)}>
							设为现行
						</Button>
					) : null}
					<Button
						type="link"
						size="small"
						disabled={!canEdit || definition.status !== "CURRENT"}
						title={definition.status === "CURRENT" ? undefined : "仅现行维度可创建维度表"}
						onClick={() => enterDimensionTableCreation(definition)}
					>
						创建维度表
					</Button>
					<Button type="link" size="small" onClick={() => showReferences(definition)}>
						引用
					</Button>
					<Button
						type="link"
						size="small"
						disabled={!canEdit || definition.status === "RETIRED"}
						onClick={() => setEditing(definition)}
					>
						编辑
					</Button>
					<Button
						type="link"
						danger
						size="small"
						disabled={!canRetireDimensionDefinition(definition.status, canEdit)}
						title={definition.status === "CURRENT" ? undefined : "仅现行维度可退役"}
						onClick={() => retire(definition)}
					>
						退役
					</Button>
				</Space>
			),
		},
	];

	return (
		<div className="p-4" data-testid="dimension-catalog-page">
			<div className="mb-4 flex flex-wrap items-start justify-between gap-3">
				<div>
					<Title level={3} className="!mb-1">
						维度目录
					</Title>
					<Text type="secondary">统一维护可复用的业务维度定义，并按版本提供给逻辑模型引用。</Text>
				</div>
				<Button type="primary" disabled={!canEdit} onClick={() => setCreateOpen(true)}>
					<Plus size={16} />
					登记维度
				</Button>
			</div>

			{!canEdit ? (
				<Alert className="mb-3" type="info" showIcon message="当前账号为只读浏览；维护维度需要相应权限" />
			) : null}
			{actionError ? (
				<Alert
					className="mb-3"
					type="error"
					showIcon
					message={actionError}
					action={actionNeedsRefresh ? <Button size="small" onClick={refreshAfterActionConflict}>刷新目录</Button> : undefined}
					closable
					onClose={() => {
						setActionError("");
						setActionNeedsRefresh(false);
					}}
				/>
			) : null}
			{loadError ? (
				<Alert
					className="mb-3"
					type="error"
					showIcon
					message={loadError}
					action={
						<Button size="small" onClick={() => void load()}>
							<RefreshCw size={14} />
							重试
						</Button>
					}
				/>
			) : (
				<Card>
					<div className="mb-3 flex justify-end">
						<Input.Search
							allowClear
							className="max-w-xs"
							placeholder="搜索维度名称、系统编码或业务定义"
							value={search}
							onChange={(event) => setSearch(event.target.value)}
						/>
					</div>
					<CompactTable<DimensionDefinitionView>
						rowKey="id"
						loading={loading}
						columns={columns}
						dataSource={visibleDimensions}
						scroll={{ x: 1370 }}
						locale={{ emptyText: <Empty description={emptyText} /> }}
					/>
				</Card>
			)}

			<DimensionDefinitionCreateDrawer
				open={createOpen || Boolean(editing)}
				definition={editing}
				canEdit={canEdit}
				initialDomainId={domainId || undefined}
				initialOwnerId={String(userInfo.username || userInfo.id || "")}
				domainOptions={domainOptions.map((option) => ({ value: option.key, label: option.label }))}
				domainsLoading={domainsLoading}
				domainLoadError={domainsError ? "业务分类加载失败，当前表单会保留" : ""}
				onClose={() => {
					setCreateOpen(false);
					setEditing(null);
				}}
				onSaved={async () => {
					const savedDomainId = domainId;
					if (savedDomainId !== activeDomainRef.current) return;
					setCreateOpen(false);
					setEditing(null);
					await load();
				}}
				onReloadLatest={(id) => getDimensionDefinition(id)}
			/>
		</div>
	);
}
