import {
	Alert,
	Button,
	Card,
	Form,
	Input,
	Select,
	Switch,
	Typography,
} from "antd";
import type { IngestionFormContext } from "./types";
import { normalizeText } from "@/utils/textUtils";

const { Text } = Typography;

const jsonValidator = (label: string) => (_: any, value: string) => {
	if (!normalizeText(value)) return Promise.resolve();
	try {
		JSON.parse(value);
		return Promise.resolve();
	} catch {
		return Promise.reject(new Error(`${label} JSON 格式错误`));
	}
};

export type PreviewState = {
	config: Record<string, any> | null;
	error: string;
};

export type SqlModel = {
	id?: string;
	name?: string;
	alias?: string;
};

export type ReviewStepProps = Pick<IngestionFormContext, "form" | "isFileFlow"> & {
	previewState: PreviewState;
	sqlModels: SqlModel[];
	loadingSqlModels: boolean;
	onNavigateToModeling?: () => void;
};

export function ReviewStep({
	form: _form,
	isFileFlow,
	previewState,
	sqlModels,
	loadingSqlModels,
	onNavigateToModeling,
}: ReviewStepProps) {
	if (isFileFlow) {
		return (
			<>
				<Form.Item name="jobConfig" label="作业参数 (JSON，可选)" rules={[{ validator: jsonValidator("作业参数") }]}>
					<Input.TextArea rows={4} placeholder='{"setting":{"speed":{"channel":3}}}' />
				</Form.Item>
				<Card type="inner" title="Airflow 触发">
					<Form.Item name="airflowEnabled" label="启用 Airflow" valuePropName="checked">
						<Switch />
					</Form.Item>
					<Form.Item name="runNow" label="立即触发" valuePropName="checked">
						<Switch />
					</Form.Item>
					<Text type="secondary">
						若未勾选立即触发，仅保存作业配置，后续可在 Airflow 中手动运行。
					</Text>
				</Card>
			</>
		);
	}

	return (
		<>
			<Card
				type="inner"
				title="dbt 模型联动"
				extra={
					onNavigateToModeling ? (
						<Button size="small" onClick={onNavigateToModeling}>
							进入建模
						</Button>
					) : null
				}
				className="mb-4"
			>
				<Form.Item name="dbtModels" label="选择模型（可选）">
					<Select
						mode="multiple"
						allowClear
						loading={loadingSqlModels}
						placeholder={loadingSqlModels ? "模型加载中..." : "选择需要联动的模型"}
						options={sqlModels.map((model) => ({
							label: model.alias ? `${model.name} (${model.alias})` : model.name,
							value: model.name,
						}))}
						showSearch
						optionFilterProp="label"
					/>
				</Form.Item>
				<Form.Item name="dbtModelSelector" label="模型选择器（可选）">
					<Input placeholder="例如：model:ods_xxx model:dwd_xxx" />
				</Form.Item>
				<Form.Item name="dbtDagSelector" label="DAG 族选择器（可选）">
					<Input placeholder="例如：tab:erp" />
				</Form.Item>
				<Text type="secondary">
					若未填写模型选择器，将根据选中的模型生成 model:xxx 选择器；DAG 族建议使用 tab:源系统。
				</Text>
			</Card>
			<Form.Item name="jobConfig" label="作业参数 (JSON，可选)" rules={[{ validator: jsonValidator("作业参数") }]}>
				<Input.TextArea rows={4} placeholder='{"setting":{"speed":{"channel":3}}}' />
			</Form.Item>
			<Card type="inner" title="作业预览">
				{previewState.error ? (
					<Alert type="warning" message={previewState.error} showIcon />
				) : (
					<pre className="bg-muted p-4 rounded overflow-auto">
						{JSON.stringify(previewState.config, null, 2)}
					</pre>
				)}
			</Card>
			<Card type="inner" title="Airflow 触发">
				<Form.Item name="airflowEnabled" label="启用 Airflow" valuePropName="checked">
					<Switch />
				</Form.Item>
				<Form.Item name="runNow" label="立即触发" valuePropName="checked">
					<Switch />
				</Form.Item>
				<Text type="secondary">
					若未勾选立即触发，仅保存作业配置，后续可在 Airflow 中手动运行。
				</Text>
			</Card>
		</>
	);
}
