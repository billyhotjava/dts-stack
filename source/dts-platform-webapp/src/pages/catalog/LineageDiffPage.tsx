import { useEffect, useMemo, useState } from "react";
import { Alert, Button, Card, Input, Select, Space, Statistic, Table } from "antd";
import { toast } from "sonner";
import { getCatalogLineageDiff } from "@/api/platformApi";
import {
	edgeColumns,
	EmptyAction,
	type LineageDiffResult,
	type LineageDirection,
	LineageSectionNav,
	loadDatasetOptions,
	formatTs,
	toIsoInstant,
} from "./lineageShared";

export default function LineageDiffPage() {
	const [datasets, setDatasets] = useState<Array<{ id: string; name: string }>>([]);
	const [selectedId, setSelectedId] = useState<string>();
	const [direction, setDirection] = useState<LineageDirection>("BOTH");
	const [depth, setDepth] = useState(3);
	const [projectName, setProjectName] = useState("");
	const [diffFrom, setDiffFrom] = useState("");
	const [diffTo, setDiffTo] = useState("");
	const [diffLoading, setDiffLoading] = useState(false);
	const [diffResult, setDiffResult] = useState<LineageDiffResult | null>(null);
	const datasetOptions = useMemo(() => datasets.map((item) => ({ label: item.name, value: item.id })), [datasets]);

	const loadDatasets = async () => {
		try {
			const options = await loadDatasetOptions();
			setDatasets(options);
			if (!selectedId && options.length) setSelectedId(options[0].id);
		} catch {
			// global interceptor handles toast
		}
	};

	useEffect(() => {
		void loadDatasets();
	}, []);

	const handleLoadDiff = async () => {
		if (!selectedId) return;
		const from = toIsoInstant(diffFrom);
		const to = toIsoInstant(diffTo);
		if (!from || !to) {
			toast.error("请选择有效的对比起止时间");
			return;
		}
		setDiffLoading(true);
		try {
			const resp: any = await getCatalogLineageDiff(selectedId, {
				from,
				to,
				direction,
				depth,
				projectName: projectName.trim() || undefined,
			});
			setDiffResult(resp || null);
		} catch {
			setDiffResult(null);
		} finally {
			setDiffLoading(false);
		}
	};

	return (
		<div className="space-y-4">
			<Card title="血缘与影响分析 / 快照对比">
				<div className="mb-3"><LineageSectionNav section="diff" /></div>
				<Space wrap>
					<Select placeholder="选择数据集" style={{ minWidth: 320 }} value={selectedId} options={datasetOptions} onChange={setSelectedId} showSearch optionFilterProp="label" />
					<Select
						style={{ width: 150 }}
						value={direction}
						options={[
							{ label: "双向", value: "BOTH" },
							{ label: "仅上游", value: "UPSTREAM" },
							{ label: "仅下游", value: "DOWNSTREAM" },
						]}
						onChange={setDirection}
					/>
					<Select style={{ width: 140 }} value={depth} options={[1, 2, 3, 5].map((value) => ({ label: `深度 ${value}`, value }))} onChange={setDepth} />
					<Input style={{ width: 220 }} placeholder="项目名过滤（可选）" value={projectName} onChange={(e) => setProjectName(e.target.value)} allowClear />
				</Space>
			</Card>
			{!selectedId ? <Alert type="info" message="请选择一个数据集进行快照对比。" showIcon action={<EmptyAction onReload={loadDatasets} />} /> : null}
			{selectedId ? (
				<>
					<Card title="对比条件">
						<Space wrap>
							<Input style={{ width: 220 }} type="datetime-local" value={diffFrom} onChange={(e) => setDiffFrom(e.target.value)} />
							<Input style={{ width: 220 }} type="datetime-local" value={diffTo} onChange={(e) => setDiffTo(e.target.value)} />
							<Button type="primary" loading={diffLoading} onClick={handleLoadDiff}>对比</Button>
						</Space>
					</Card>
					<Card title="差异概览" loading={diffLoading}>
						<Space size={24} wrap>
							<Statistic title="新增关系" value={Number(diffResult?.addedCount || 0)} />
							<Statistic title="移除关系" value={Number(diffResult?.removedCount || 0)} />
							<Statistic title="不变关系" value={Number(diffResult?.unchangedCount || 0)} />
							<Statistic title="起始快照" value={diffResult?.from ? formatTs(diffResult.from) : "-"} />
							<Statistic title="目标快照" value={diffResult?.to ? formatTs(diffResult.to) : "-"} />
						</Space>
					</Card>
					<Card title="新增关系">
						<Table rowKey={(row, idx) => row.id || `added-${idx}`} columns={edgeColumns} dataSource={diffResult?.addedEdges || []} loading={diffLoading} scroll={{ x: 1200 }} pagination={{ pageSize: 8 }} />
					</Card>
					<Card title="移除关系">
						<Table rowKey={(row, idx) => row.id || `removed-${idx}`} columns={edgeColumns} dataSource={diffResult?.removedEdges || []} loading={diffLoading} scroll={{ x: 1200 }} pagination={{ pageSize: 8 }} />
					</Card>
				</>
			) : null}
		</div>
	);
}
