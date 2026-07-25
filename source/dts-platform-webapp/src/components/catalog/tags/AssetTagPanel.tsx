import { Alert, Card, Empty, Select, Spin } from "antd";
import { useEffect, useMemo, useRef, useState } from "react";
import { toast } from "sonner";
import {
	type CatalogTagCategoryDto,
	type CatalogTagDto,
	listAllEnabledCatalogTags,
	listAssetTags,
	listTagCategories,
	tagAsset,
	untagAsset,
} from "@/api/catalogTagsApi";
import { AssetTagChips, type DisplayCatalogTag } from "./AssetTagChips";

type AssetTagPanelProps = {
	assetType: string;
	assetKey: string;
	canEdit?: boolean;
};

function errorMessage(error: unknown, fallback: string): string {
	return error instanceof Error && error.message ? error.message : fallback;
}

function flattenCategories(categories: CatalogTagCategoryDto[]): CatalogTagCategoryDto[] {
	const result: CatalogTagCategoryDto[] = [];
	for (const category of categories) {
		result.push(category);
		result.push(...flattenCategories(category.children || []));
	}
	return result;
}

export function AssetTagPanel({ assetType, assetKey, canEdit = false }: AssetTagPanelProps) {
	const [selected, setSelected] = useState<DisplayCatalogTag[]>([]);
	const [catalog, setCatalog] = useState<DisplayCatalogTag[]>([]);
	const [loading, setLoading] = useState(true);
	const [busy, setBusy] = useState(false);
	const [selectedReady, setSelectedReady] = useState(false);
	const [catalogReady, setCatalogReady] = useState(false);
	const [loadError, setLoadError] = useState("");
	const [mutationError, setMutationError] = useState("");
	const requestSequence = useRef(0);
	const currentIdentity = `${assetType}\u0000${assetKey}`;
	const identityRef = useRef(currentIdentity);
	identityRef.current = currentIdentity;

	useEffect(() => {
		const sequence = ++requestSequence.current;
		setSelected([]);
		setCatalog([]);
		setSelectedReady(false);
		setCatalogReady(false);
		setLoadError("");
		setMutationError("");
		setBusy(false);
		setLoading(true);
		Promise.allSettled([listAssetTags({ assetType, assetKey }), listTagCategories(), listAllEnabledCatalogTags()])
			.then(([currentTagsResult, categoriesResult, availableTagsResult]) => {
				if (sequence !== requestSequence.current) return;
				const categories = categoriesResult.status === "fulfilled" ? categoriesResult.value || [] : [];
				const categoryNameById = new Map(flattenCategories(categories).map((category) => [category.id, category.name]));
				const decorate = (tag: CatalogTagDto): DisplayCatalogTag => ({
					...tag,
					categoryName: categoryNameById.get(tag.categoryId),
				});
				const errors: string[] = [];
				if (currentTagsResult.status === "fulfilled") {
					setSelected((currentTagsResult.value || []).map(decorate));
					setSelectedReady(true);
				} else {
					errors.push(errorMessage(currentTagsResult.reason, "当前资产的业务数据标签暂时无法加载"));
				}
				if (availableTagsResult.status === "fulfilled") {
					setCatalog((availableTagsResult.value || []).filter((tag) => tag.enabled).map(decorate));
					setCatalogReady(true);
				} else {
					errors.push(errorMessage(availableTagsResult.reason, "可选业务数据标签目录暂时无法加载"));
				}
				if (categoriesResult.status === "rejected") {
					errors.push(errorMessage(categoriesResult.reason, "业务数据标签分类暂时无法加载"));
				}
				setLoadError(errors.join("；"));
			})
			.finally(() => {
				if (sequence === requestSequence.current) setLoading(false);
			});
		return () => {
			if (sequence === requestSequence.current) {
				requestSequence.current++;
			}
		};
	}, [assetType, assetKey]);

	const selectedIds = useMemo(() => new Set(selected.map((tag) => tag.id)), [selected]);
	const candidateOptions = useMemo(() => {
		const grouped = new Map<string, DisplayCatalogTag[]>();
		for (const tag of catalog) {
			if (selectedIds.has(tag.id)) continue;
			const group = tag.categoryName || "其他业务标签";
			const rows = grouped.get(group) || [];
			rows.push(tag);
			grouped.set(group, rows);
		}
		return Array.from(grouped.entries()).map(([label, rows]) => ({
			label,
			options: rows
				.slice()
				.sort((left, right) => left.name.localeCompare(right.name, "zh-CN"))
				.map((tag) => ({ value: tag.id, label: tag.name })),
		}));
	}, [catalog, selectedIds]);

	const addTag = async (tagId: string) => {
		const tag = catalog.find((item) => item.id === tagId);
		if (!tag || selectedIds.has(tag.id) || busy) return;
		const identity = currentIdentity;
		setMutationError("");
		setBusy(true);
		setSelected((current) => [...current, tag]);
		try {
			await tagAsset({ assetType, assetKey, tagIds: [tag.id] });
		} catch (error: unknown) {
			if (identity === identityRef.current) {
				setSelected((current) => current.filter((item) => item.id !== tag.id));
				const message = errorMessage(error, "添加业务数据标签失败");
				setMutationError(message);
				toast.error(message);
			}
		} finally {
			if (identity === identityRef.current) setBusy(false);
		}
	};

	const removeTag = async (tag: DisplayCatalogTag) => {
		if (busy) return;
		const identity = currentIdentity;
		const originalIndex = selected.findIndex((item) => item.id === tag.id);
		setMutationError("");
		setBusy(true);
		setSelected((current) => current.filter((item) => item.id !== tag.id));
		try {
			await untagAsset({ assetType, assetKey, tagIds: [tag.id] });
		} catch (error: unknown) {
			if (identity === identityRef.current) {
				setSelected((current) => {
					if (current.some((item) => item.id === tag.id)) return current;
					const restored = current.slice();
					restored.splice(Math.max(0, Math.min(originalIndex, restored.length)), 0, tag);
					return restored;
				});
				const message = errorMessage(error, "移除业务数据标签失败");
				setMutationError(message);
				toast.error(message);
			}
		} finally {
			if (identity === identityRef.current) setBusy(false);
		}
	};

	return (
		<Card title="业务数据标签" size="small" className="border-slate-200">
			<Spin spinning={loading}>
				{loadError ? (
					<Alert type="warning" showIcon message="业务数据标签加载不完整" description={loadError} className="mb-3" />
				) : null}
				{mutationError ? (
					<Alert
						type="warning"
						showIcon
						message="业务数据标签操作未完成"
						description={mutationError}
						className="mb-3"
					/>
				) : null}
				{selected.length ? (
					<AssetTagChips
						tags={selected}
						variant="panel"
						removable={canEdit}
						disabled={busy}
						onRemove={(tag) => void removeTag(tag)}
					/>
				) : !loading && selectedReady ? (
					<Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="当前资产尚未设置业务数据标签" />
				) : null}
				<div className="mt-3 border-t border-slate-100 pt-3">
					<Select<string>
						aria-label="添加业务数据标签"
						value={undefined}
						options={candidateOptions}
						onChange={(tagId) => void addTag(tagId)}
						disabled={!canEdit || loading || busy || !selectedReady || !catalogReady}
						placeholder={
							!canEdit
								? "当前账号无该资产标签维护权限"
								: !selectedReady || !catalogReady
									? "业务数据标签目录暂时不可用"
									: "选择要添加的业务数据标签"
						}
						showSearch
						optionFilterProp="label"
						className="w-full"
						notFoundContent="暂无其他可用业务标签"
					/>
					{!canEdit ? (
						<div className="mt-1 text-xs text-slate-500">您可以查看业务数据标签，但不能修改当前资产。</div>
					) : null}
				</div>
			</Spin>
		</Card>
	);
}
