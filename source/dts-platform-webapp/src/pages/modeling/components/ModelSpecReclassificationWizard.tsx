import { Alert, Button, Checkbox, Descriptions, Modal, Radio, Select, Space, Spin, Typography } from "antd";
import { useEffect, useMemo, useRef, useState } from "react";
import { listDimensionDefinitions } from "@/api/dimensionDefinitionApi";
import {
	previewModelSpecReclassification,
	reclassifyModelSpec,
	type ModelSpecReclassificationPreview,
} from "@/api/modelSpecApi";
import type { DimensionDefinitionView } from "../dimensionDefinitionContract";
import {
	canApplyModelReclassification,
	reclassificationFieldLabel,
	reclassificationReason,
} from "../modelSpecCorrection";
import type { CanonicalModelSpecView, ModelSpecType } from "../modelSpecV2Contract";
import { MODEL_TYPE_LABELS } from "../modelSpecWorkbench";

const { Text } = Typography;

const modelPurposeOptions: Array<{ value: ModelSpecType; title: string; description: string }> = [
	{ value: "DIMENSION", title: "稳定对象 · 维度表", description: "项目、客户、组织等用于分类和筛选的主数据" },
	{ value: "FACT", title: "业务事件 · 明细表", description: "付款、交易、变更等按事件或快照记录的明细" },
	{ value: "SUMMARY", title: "聚合结果 · 汇总表", description: "从明细或其他模型按固定粒度汇总的结果" },
	{ value: "APPLICATION", title: "消费输出 · 应用表", description: "面向报表、接口或具体业务场景的最终输出" },
];

type Props = {
	model: CanonicalModelSpecView;
	canMaintain: boolean;
	onApplied: (updated: CanonicalModelSpecView) => void | Promise<void>;
};

const correctionError = (error: unknown): string => {
	const response = (error as { response?: { status?: number; data?: { code?: string } } })?.response;
	if (response?.status === 412 || response?.status === 409) {
		return "模型版本或运行证据已变化，当前选择仍保留；请关闭向导并刷新模型后重试。";
	}
	if (response?.status === 422) return "仍有待确认的清理项或维度定义不符合当前模型，请检查预检结果。";
	if (response?.status === 403) return "当前账号没有调整模型类型的权限。";
	return "模型类型预检暂时失败，当前模型和历史版本均未被修改。";
};

export function ModelSpecReclassificationWizard({ model, canMaintain, onApplied }: Props) {
	const [open, setOpen] = useState(false);
	const [targetType, setTargetType] = useState<ModelSpecType | null>(null);
	const [definitionRef, setDefinitionRef] = useState<string>();
	const [definitions, setDefinitions] = useState<DimensionDefinitionView[]>([]);
	const [loadingDefinitions, setLoadingDefinitions] = useState(false);
	const [preview, setPreview] = useState<ModelSpecReclassificationPreview | null>(null);
	const [acceptedClearFields, setAcceptedClearFields] = useState<string[]>([]);
	const [loading, setLoading] = useState(false);
	const [error, setError] = useState("");
	const requestRef = useRef(0);
	const selectedDefinition = useMemo(
		() => definitions.find((definition) => `${definition.id}:${definition.revision}` === definitionRef),
		[definitionRef, definitions],
	);
	const canApply = canApplyModelReclassification(preview, acceptedClearFields);

	useEffect(() => {
		if (!open || targetType !== "DIMENSION") return;
		const requestId = ++requestRef.current;
		setLoadingDefinitions(true);
		void listDimensionDefinitions({ domainId: model.domainId, status: "CURRENT" })
			.then((result) => {
				if (requestId === requestRef.current) setDefinitions(Array.isArray(result) ? result : []);
			})
			.catch(() => {
				if (requestId === requestRef.current) {
					setDefinitions([]);
					setError("现行业务维度加载失败，请稍后重试；模型尚未发生变化。");
				}
			})
			.finally(() => {
				if (requestId === requestRef.current) setLoadingDefinitions(false);
			});
	}, [model.domainId, open, targetType]);

	useEffect(
		() => () => {
			requestRef.current += 1;
		},
		[],
	);

	const reset = () => {
		requestRef.current += 1;
		setOpen(false);
		setTargetType(null);
		setDefinitionRef(undefined);
		setDefinitions([]);
		setPreview(null);
		setAcceptedClearFields([]);
		setLoading(false);
		setLoadingDefinitions(false);
		setError("");
	};

	const request = () => {
		if (!targetType) return null;
		return {
			targetType,
			dimensionDefinitionRef:
				targetType === "DIMENSION" && selectedDefinition
					? { dimensionDefinitionId: selectedDefinition.id, revision: selectedDefinition.revision }
					: null,
		};
	};

	const runPreview = async () => {
		const command = request();
		if (!command) {
			setError("请先选择调整后的业务目的。");
			return;
		}
		if (targetType === "DIMENSION" && !selectedDefinition) {
			setError("改为维度表前，请选择当前业务分类下的现行业务维度。");
			return;
		}
		const requestId = ++requestRef.current;
		setLoading(true);
		setError("");
		setPreview(null);
		setAcceptedClearFields([]);
		try {
			const result = await previewModelSpecReclassification(model.id, command);
			if (requestId === requestRef.current) setPreview(result);
		} catch (failure) {
			if (requestId === requestRef.current) setError(correctionError(failure));
		} finally {
			if (requestId === requestRef.current) setLoading(false);
		}
	};

	const apply = async () => {
		const command = request();
		if (!command || !preview || !canApply) return;
		const requestId = ++requestRef.current;
		setLoading(true);
		setError("");
		try {
			const updated = await reclassifyModelSpec(
				{ id: model.id, revision: preview.currentRevision, checksum: preview.checksum },
				{
					...command,
					acceptedClearFields,
					idempotencyKey: `model-reclassify:${model.id}:${preview.currentRevision}:${command.targetType}`,
				},
			);
			if (requestId !== requestRef.current) return;
			await onApplied(updated);
			reset();
		} catch (failure) {
			if (requestId === requestRef.current) setError(correctionError(failure));
		} finally {
			if (requestId === requestRef.current) setLoading(false);
		}
	};

	return (
		<>
			<Button disabled={!canMaintain || model.status !== "DRAFT"} onClick={() => setOpen(true)}>
				调整模型类型
			</Button>
			<Modal
				open={open}
				width={760}
				title={`调整模型类型 · 当前为${MODEL_TYPE_LABELS[model.modelType]}`}
				onCancel={reset}
				footer={
					<Space wrap>
						<Button onClick={reset}>取消</Button>
						<Button loading={loading} onClick={() => void runPreview()}>
							预检影响
						</Button>
						<Button type="primary" disabled={!canApply} loading={loading} onClick={() => void apply()}>
							确认并保留 r{model.revision}
						</Button>
					</Space>
				}
			>
				<Alert
					className="mb-4"
					type="info"
					showIcon
					message={`系统会保留当前 r${model.revision}，成功后追加一个新版本；不会覆盖历史记录。`}
				/>
				<Radio.Group
					value={targetType}
					className="w-full"
					onChange={(event) => {
						setTargetType(event.target.value);
						setDefinitionRef(undefined);
						setPreview(null);
						setAcceptedClearFields([]);
						setError("");
					}}
				>
					<Space direction="vertical" className="w-full">
						{modelPurposeOptions.map((option) => (
							<Radio key={option.value} value={option.value} disabled={option.value === model.modelType}>
								<strong>{option.title}</strong>
								<Text type="secondary"> · {option.description}</Text>
							</Radio>
						))}
					</Space>
				</Radio.Group>
				{targetType === "DIMENSION" ? (
					<div className="mt-4">
						<Text strong>现行业务维度定义</Text>
						<Select
							className="mt-2 w-full"
							value={definitionRef}
							loading={loadingDefinitions}
							placeholder="选择已确认的业务维度"
							options={definitions.map((definition) => ({
								value: `${definition.id}:${definition.revision}`,
								label: `${definition.name}（${definition.systemCode}）· r${definition.revision}`,
							}))}
							onChange={(value) => {
								setDefinitionRef(value);
								setPreview(null);
								setAcceptedClearFields([]);
							}}
						/>
					</div>
				) : null}
				{loading ? (
					<div className="my-4 text-center">
						<Spin />
					</div>
				) : null}
				{error ? <Alert className="mt-4" type="error" showIcon message={error} /> : null}
				{preview ? (
					<Space direction="vertical" className="mt-4 w-full">
						<Descriptions size="small" bordered column={2}>
							<Descriptions.Item label="调整前">{MODEL_TYPE_LABELS[preview.fromType]}</Descriptions.Item>
							<Descriptions.Item label="调整后">
								{MODEL_TYPE_LABELS[preview.toType]} · {preview.targetLayer}
							</Descriptions.Item>
							<Descriptions.Item label="保留内容" span={2}>
								{preview.retainedFields.map(reclassificationFieldLabel).join("、") || "无"}
							</Descriptions.Item>
							<Descriptions.Item label="后续必填" span={2}>
								{preview.requiredFields.map(reclassificationFieldLabel).join("、") || "无"}
							</Descriptions.Item>
						</Descriptions>
						{preview.reasonCodes.length > 0 ? (
							<Alert
								type="warning"
								showIcon
								message="当前不能安全调整"
								description={preview.reasonCodes.map(reclassificationReason).join("；")}
							/>
						) : null}
						{preview.clearFields.length > 0 ? (
							<div>
								<Text strong>必须逐项确认将清理的旧类型内容</Text>
								<Checkbox.Group
									className="mt-2 flex flex-col gap-2"
									value={acceptedClearFields}
									options={preview.clearFields.map((field) => ({
										value: field,
										label: reclassificationFieldLabel(field),
									}))}
									onChange={(values) => setAcceptedClearFields(values as string[])}
								/>
							</div>
						) : null}
						{preview.toType === "DIMENSION" ? (
							<Alert
								type="info"
								showIcon
								message="字段角色将按维度语义重置"
								description="原时间或度量字段会改为属性字段；模型保存后仍需在逻辑设计中确认项目编码、项目名称、粒度键和维度变化保留方式。"
							/>
						) : null}
					</Space>
				) : null}
			</Modal>
		</>
	);
}
