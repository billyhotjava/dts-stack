import { Alert, Button, Card, Select, Space } from "antd";
import { useRef, useState } from "react";
import { createMaskingRule, getDatasetFields, listMaskingRules } from "@/api/platformApi";
import { CompactTable } from "@/components/table";
import {
	detectSensitiveFields,
	type ExistingMaskingRule,
	type SensitiveFieldSuggestion,
} from "./sensitiveFieldDetection";

export function SensitiveFieldDetectionPanel({
	datasets,
	onComplete,
}: {
	datasets: { id: string; name: string }[];
	onComplete: () => Promise<void>;
}) {
	const [datasetId, setDatasetId] = useState<string>();
	const [suggestions, setSuggestions] = useState<SensitiveFieldSuggestion[]>([]);
	const [selected, setSelected] = useState<string[]>([]);
	const [busy, setBusy] = useState(false);
	const running = useRef(false);
	const [notice, setNotice] = useState("");
	const [error, setError] = useState("");
	const scan = async () => {
		if (!datasetId || running.current) return;
		running.current = true;
		setBusy(true);
		setError("");
		setNotice("");
		setSuggestions([]);
		setSelected([]);
		try {
			const [fields, existing] = await Promise.all([getDatasetFields(datasetId), listMaskingRules()]);
			const next = detectSensitiveFields(fields, existing, datasetId);
			setSuggestions(next);
			setNotice(`已检查 ${fields.length} 个字段，发现 ${next.length} 个尚未配置脱敏规则的候选字段。请核对后勾选保存。`);
		} catch {
			setError("识别失败，请确认数据集字段已同步且当前账号具有访问权限后重试。");
		} finally {
			running.current = false;
			setBusy(false);
		}
	};
	const apply = async () => {
		if (!datasetId || !selected.length || selected.length > 100 || running.current) return;
		running.current = true;
		setBusy(true);
		setError("");
		try {
			const existing: ExistingMaskingRule[] = await listMaskingRules();
			const covered = new Set(
				existing
					.filter((rule) => !rule.dataset?.id || rule.dataset.id === datasetId)
					.map((rule) => rule.column?.trim().toLowerCase()),
			);
			const done = new Set<string>();
			let saved = 0;
			const failed: string[] = [];
			for (const suggestion of suggestions.filter((item) => selected.includes(item.column))) {
				if (covered.has(suggestion.column.trim().toLowerCase())) {
					done.add(suggestion.column);
					continue;
				}
				try {
					await createMaskingRule({
						dataset: { id: datasetId },
						column: suggestion.column,
						function: suggestion.function,
						args: "",
					});
					done.add(suggestion.column);
					saved += 1;
				} catch {
					failed.push(suggestion.column);
				}
			}
			setSuggestions((items) => items.filter((item) => !done.has(item.column)));
			setSelected((items) => items.filter((item) => !done.has(item)));
			setNotice(`已新增 ${saved} 条规则；已有规则自动跳过。`);
			if (failed.length) setError(`以下字段保存失败，可重试：${failed.join("、")}`);
			await onComplete();
		} catch {
			setError("规则读取或刷新失败，请刷新页面核对已有规则后重试。");
		} finally {
			running.current = false;
			setBusy(false);
		}
	};
	return (
		<Card size="small" title="敏感字段自动识别" className="mb-3">
			<p className="mb-3">
				根据字段名称和注释识别证件、联系方式、银行账号、姓名和认证凭据。未匹配不代表不含敏感信息；请结合业务含义核对，现有规则不会被覆盖。
			</p>
			<Space wrap className="mb-3">
				<Select
					aria-label="识别数据集"
					placeholder="选择数据集"
					style={{ minWidth: 240 }}
					value={datasetId}
					disabled={busy}
					options={datasets.map((item) => ({ value: item.id, label: item.name }))}
					onChange={(id) => {
						setDatasetId(id);
						setSuggestions([]);
						setSelected([]);
						setNotice("");
						setError("");
					}}
				/>
				<Button disabled={!datasetId || busy} loading={busy} onClick={() => void scan()}>
					识别敏感字段
				</Button>
				<Button
					type="primary"
					disabled={busy || !selected.length || selected.length > 100}
					onClick={() => void apply()}
				>
					保存选中规则（{selected.length}）
				</Button>
			</Space>
			{notice ? <Alert type="info" showIcon message={notice} className="mb-3" /> : null}
			{error ? <Alert type="error" showIcon message={error} className="mb-3" /> : null}
			{selected.length > 100 ? <Alert type="warning" message="每次最多保存 100 条规则" /> : null}
			<CompactTable<SensitiveFieldSuggestion>
				rowKey="column"
				dataSource={suggestions}
				pagination={{ pageSize: 10 }}
				rowSelection={{
					selectedRowKeys: selected,
					onChange: (keys) => setSelected(keys.map(String)),
					getCheckboxProps: () => ({ disabled: busy }),
				}}
				columns={[
					{ title: "字段", dataIndex: "column" },
					{ title: "识别类别", dataIndex: "category" },
					{ title: "识别依据", dataIndex: "reason" },
					{
						title: "建议脱敏方式",
						dataIndex: "function",
						render: (value) => (value === "REDACT" ? "固定替换" : "部分隐藏"),
					},
				]}
			/>
		</Card>
	);
}
