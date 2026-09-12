import { useEffect, useMemo, useState } from "react";
import { type CompactColumns, CompactTable } from "@/components/table";
import { Button, Modal, RequestState, Status } from "./PrototypePrimitives";
import {
	applySuggestedStandardMappings,
	previewSuggestedStandardMappings,
	type StandardMappingSuggestion,
} from "./services/standardsProjectionService";

export function StandardMappingSuggestionModal({
	onClose,
	onComplete,
}: {
	onClose: () => void;
	onComplete: (successCount: number, failureCount: number) => void | Promise<void>;
}) {
	const [suggestions, setSuggestions] = useState<StandardMappingSuggestion[]>([]);
	const [selectedIds, setSelectedIds] = useState<string[]>([]);
	const [loading, setLoading] = useState(true);
	const [saving, setSaving] = useState(false);
	const [error, setError] = useState("");

	useEffect(() => {
		let active = true;
		void previewSuggestedStandardMappings()
			.then((next) => {
				if (!active) return;
				setSuggestions(next);
				setSelectedIds(next.map((item) => item.id));
			})
			.catch((cause) => {
				if (active) setError(cause instanceof Error ? cause.message : "推荐标准映射读取失败");
			})
			.finally(() => {
				if (active) setLoading(false);
			});
		return () => {
			active = false;
		};
	}, []);

	const selected = useMemo(() => {
		const keys = new Set(selectedIds);
		return suggestions.filter((item) => keys.has(item.id));
	}, [selectedIds, suggestions]);
	const modelCount = new Set(selected.map((item) => item.modelId)).size;
	const publishedModelCount = new Set(selected.filter((item) => item.createsDraft).map((item) => item.modelId)).size;

	const columns = useMemo<CompactColumns<StandardMappingSuggestion>>(
		() => [
			{ title: "所属模型", dataIndex: "modelName" },
			{
				title: "当前状态",
				key: "status",
				render: (_, row) => (
					<Status tone={row.createsDraft ? "warning" : "success"}>
						{row.createsDraft ? "将创建新草稿" : "更新当前草稿"}
					</Status>
				),
			},
			{
				title: "模型字段",
				key: "field",
				render: (_, row) => `${row.fieldLabel}（${row.fieldName}）`,
			},
			{ title: "数据类型", dataIndex: "fieldDataType" },
			{
				title: "推荐数据元",
				key: "standard",
				render: (_, row) => `${row.standardName}（${row.standardCode}，v${row.standardVersion}）`,
			},
		],
		[],
	);

	const apply = async () => {
		if (!selected.length) return;
		setSaving(true);
		setError("");
		try {
			const results = await applySuggestedStandardMappings(selected);
			const successCount = results.filter((item) => item.success).length;
			const failureCount = results.length - successCount;
			await onComplete(successCount, failureCount);
		} catch (cause) {
			setError(cause instanceof Error ? cause.message : "推荐标准映射保存失败");
		} finally {
			setSaving(false);
		}
	};

	return (
		<Modal
			footer={
				<>
					<Button disabled={saving} onClick={onClose}>
						取消
					</Button>
					<Button disabled={saving || loading || !selected.length} primary onClick={() => void apply()}>
						{saving ? "补全中…" : `确认补全（${selected.length}）`}
					</Button>
				</>
			}
			onClose={onClose}
			title="推荐标准映射"
			wide
		>
			<div className="dmx-capability-note">
				仅匹配字段编码唯一且数据类型兼容的数据元。已选择 {selected.length} 个字段，涉及 {modelCount} 个模型；其中
				{publishedModelCount} 个已发布模型将生成新的草稿修订，原发布版本保持不变。
			</div>
			{loading ? (
				<RequestState description="正在比对模型字段与数据元标准。" kind="loading" title="正在生成推荐" />
			) : error ? (
				<RequestState description={error} kind="error" title="推荐处理失败" />
			) : suggestions.length ? (
				<div className="dmx-table-scroll">
					<CompactTable<StandardMappingSuggestion>
						columns={columns}
						dataSource={suggestions}
						pagination={{ pageSize: 10 }}
						rowKey="id"
						rowSelection={{
							selectedRowKeys: selectedIds,
							onChange: (keys) => setSelectedIds(keys.map(String)),
						}}
					/>
				</div>
			) : (
				<RequestState
					description="当前模型字段没有唯一且类型兼容、可匹配的数据元。"
					kind="empty"
					title="暂无可补全映射"
				/>
			)}
		</Modal>
	);
}
