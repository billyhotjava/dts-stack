import { Alert, Button, Form, Select } from "antd";
import type { FormInstance } from "antd/es/form";
import { useEffect, useRef, useState } from "react";
import { getModelIngestionTarget } from "@/api/modelIngestionTargetApi";
import { listModelSpecs } from "@/api/modelSpecApi";
import type { ModelSpecView } from "@/features/modeling/contracts/modelSpecV2Contract";
import type { AccessPlanFormValues } from "./accessPlan.types";
function BindingValue(_props: { value?: unknown; onChange?: unknown }) { return null; }
export function ModelTargetBindingPicker({ form }: { form: FormInstance<AccessPlanFormValues> }) {
	const binding = Form.useWatch("modelTarget", form);
	const [models, setModels] = useState<ModelSpecView[]>([]);
	const [busy, setBusy] = useState(false), [failure, setFailure] = useState("");
	const request = useRef(0);
	const [reload, setReload] = useState(0);
	useEffect(() => {
		let live = true;
		void listModelSpecs({ modelType: "SOURCE" }).then(items => { if (live) setModels(items.filter(item => item.modelType === "SOURCE")); })
			.catch(() => { if (live) setFailure("模型列表读取失败，请重试"); });
		return () => { live = false; request.current++; };
	}, [reload]);
	const bind = async (id: string) => {
		const sequence = ++request.current; setBusy(true); setFailure("");
		try {
			const target = await getModelIngestionTarget(id, binding?.environment || "dev");
			if (sequence !== request.current) return;
			form.setFieldsValue({ modelTarget: target, targetDataSourceId: target.dataSourceId,
				fileTargetTable: `${target.schemaName}.${target.tableName}`, fileLandingMode: "create_new", fileRecreateConfirmed: false,
				tableSelectionMode: "manual", syncMode: "full_refresh" });
		} catch (error) { if (sequence === request.current) setFailure(error instanceof Error ? error.message : "目标不可绑定，请先物化模型"); }
		finally { if (sequence === request.current) setBusy(false); }
	};
	return <div>
		<Form.Item name="modelTarget" hidden><BindingValue /></Form.Item>
		<label>已有模型表（可选）</label>
		<Select aria-label="已有模型表" style={{ width: "100%" }} loading={busy} disabled={busy}
			placeholder="选择已物化贴源表" value={binding?.modelSpecId}
			options={models.map(model => ({ value: model.id, label: `${model.name} · r${model.revision}` }))}
			onChange={id => void bind(id)} />
		{failure ? <Alert type="error" message={failure} action={<Button onClick={() => { setFailure(""); setReload(n => n + 1); }}>重试列表</Button>} /> : null}
		{binding ? <Alert type="info" showIcon message={`绑定 ${binding.databaseName}.${binding.schemaName}.${binding.tableName}`}
			description={`采集结果仅追加到已有模型表，不清空、重建或补列。绑定版本 r${binding.modelRevision}；字段：${binding.columns.map(column => `${column.name} ${column.dataType}`).join("，")}。字段不匹配时执行会停止，请返回模型修订。`} /> : null}
	</div>;
}
