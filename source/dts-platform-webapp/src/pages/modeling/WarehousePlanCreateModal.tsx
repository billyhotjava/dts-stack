import { Alert, Button, Form, Input, Modal, Radio, Select, Typography, type FormInstance } from "antd";
import { Database, Layers3, Plus, Trash2 } from "lucide-react";
import type { CreateWarehousePlanInput, WarehousePlanOnboardingMode } from "@/api/warehousePlanApi";
import { type WarehousePlanCreateSession, validateWarehousePlanInitialSources } from "./warehousePlanCreateFlow";

const { Text } = Typography;

export type CreatePlanForm = Omit<
	CreateWarehousePlanInput,
	"idempotencyKey" | "onboardingMode" | "ownerId" | "ownerDepartmentId"
>;

type WarehousePlanCreateModalProps = {
	open: boolean;
	creating: boolean;
	onboardingMode: WarehousePlanOnboardingMode;
	createSession: WarehousePlanCreateSession;
	form: FormInstance<CreatePlanForm>;
	currentOwner: string;
	currentDepartment: string;
	onCancel: () => void;
	onSubmit: (values: CreatePlanForm) => void;
	onOnboardingModeChange: (mode: WarehousePlanOnboardingMode) => void;
	onReplaceConflictingIdempotencyKey: () => void;
};

export function WarehousePlanCreateModal({
	open,
	creating,
	onboardingMode,
	createSession,
	form,
	currentOwner,
	currentDepartment,
	onCancel,
	onSubmit,
	onOnboardingModeChange,
	onReplaceConflictingIdempotencyKey,
}: WarehousePlanCreateModalProps) {
	return (
		<Modal
			title="新建数据建设规划"
			open={open}
			confirmLoading={creating}
			okText="创建并继续"
			cancelText="取消"
			closable={!creating}
			maskClosable={!creating}
			keyboard={!creating}
			cancelButtonProps={{ disabled: creating }}
			onCancel={() => {
				if (!creating) onCancel();
			}}
			onOk={() => form.submit()}
			destroyOnClose
		>
			<div className="mb-5 grid grid-cols-2 gap-3">
				<label
					className={`cursor-pointer rounded-xl border p-4 ${onboardingMode === "BUSINESS_FIRST" ? "border-blue-500 bg-blue-50" : "border-slate-200"}`}
				>
					<Radio
						disabled={creating}
						checked={onboardingMode === "BUSINESS_FIRST"}
						onChange={() => onOnboardingModeChange("BUSINESS_FIRST")}
					/>
					<div className="mt-3 flex items-center gap-2 font-medium">
						<Layers3 size={17} />
						从业务目标开始
					</div>
					<div className="mt-1 text-xs leading-5 text-slate-500">先说明要解决的问题，再逐步确认业务分类和范围。</div>
				</label>
				<label
					className={`cursor-pointer rounded-xl border p-4 ${onboardingMode === "ASSET_FIRST" ? "border-blue-500 bg-blue-50" : "border-slate-200"}`}
				>
					<Radio
						disabled={creating}
						checked={onboardingMode === "ASSET_FIRST"}
						onChange={() => onOnboardingModeChange("ASSET_FIRST")}
					/>
					<div className="mt-3 flex items-center gap-2 font-medium">
						<Database size={17} />
						从现有数据开始
					</div>
					<div className="mt-1 text-xs leading-5 text-slate-500">先盘点现有表、文件和 dbt 产物。</div>
				</label>
			</div>
			{createSession.idempotencyConflict ? (
				<Alert
					className="mb-4"
					type="warning"
					showIcon
					message="这次创建内容与此前提交不一致"
					description="表单已保留。确认内容后，可作为新计划重新提交。"
					action={<Button onClick={onReplaceConflictingIdempotencyKey}>作为新计划重新提交</Button>}
				/>
			) : null}
			<Form<CreatePlanForm> form={form} layout="vertical" disabled={creating} onFinish={onSubmit}>
				<Form.Item
					name="name"
					label="规划名称"
					rules={[{ required: true, whitespace: true, max: 128, message: "请输入 1-128 个字符的规划名称" }]}
				>
					<Input maxLength={128} placeholder="例如：经营分析主题数仓" />
				</Form.Item>
				<Form.Item
					name="objective"
					label="建设目标"
					rules={
						onboardingMode === "BUSINESS_FIRST"
							? [{ required: true, whitespace: true, message: "请说明这次建设要解决的问题" }]
							: []
					}
				>
					<Input.TextArea
						rows={2}
						placeholder={onboardingMode === "BUSINESS_FIRST" ? "这次建设要解决什么业务问题" : "可稍后补充"}
					/>
				</Form.Item>
				<Form.Item name="scope" label="初始范围">
					<Input placeholder="涉及的业务范围、组织或数据边界" />
				</Form.Item>
				{onboardingMode === "ASSET_FIRST" ? (
					<div className="mb-4" data-testid="warehouse-plan-initial-sources">
						<Form.List
							name="initialSourceRefs"
							rules={[
								{
									validator: async (_, sources) => {
										const issue = validateWarehousePlanInitialSources(sources);
										if (issue) throw new Error(issue);
									},
								},
							]}
						>
							{(fields, { add, remove }, { errors }) => (
								<div className="space-y-2">
									<div className="flex items-center justify-between">
										<Text strong>现有数据来源</Text>
										<Button
											type="link"
											icon={<Plus size={14} />}
											onClick={() => add({ sourceType: "CATALOG_TABLE", sourceId: "" })}
										>
											添加来源
										</Button>
									</div>
									{fields.map((field) => (
										<div key={field.key} className="grid grid-cols-[150px_1fr_1fr_auto] gap-2">
											<Form.Item
												{...field}
												name={[field.name, "sourceType"]}
												rules={[{ required: true, message: "选择类型" }]}
												noStyle
											>
												<Select
													options={[
														{ value: "CATALOG_TABLE", label: "资产目录表" },
														{ value: "CONNECTION_TABLE", label: "连接中的表" },
														{ value: "EXCEL_FILE", label: "Excel 文件" },
														{ value: "DBT_NODE", label: "dbt 节点" },
													]}
												/>
											</Form.Item>
											<Form.Item
												{...field}
												name={[field.name, "sourceId"]}
												rules={[
													{ required: true, whitespace: true, message: "填写稳定来源标识" },
													{ max: 256, message: "来源标识不能超过 256 个字符" },
												]}
												noStyle
											>
												<Input maxLength={256} placeholder="来源标识" />
											</Form.Item>
											<Form.Item
												{...field}
												name={[field.name, "sourceVersion"]}
												rules={[{ max: 128, message: "来源版本不能超过 128 个字符" }]}
												noStyle
											>
												<Input maxLength={128} placeholder="版本（可选）" />
											</Form.Item>
											<Button aria-label="删除来源" icon={<Trash2 size={14} />} onClick={() => remove(field.name)} />
										</div>
									))}
									<Form.ErrorList errors={errors} />
								</div>
							)}
						</Form.List>
					</div>
				) : null}
				<div
					className="rounded-lg border border-slate-200 bg-slate-50 px-3 py-2"
					data-testid="warehouse-plan-current-owner"
				>
					<div className="text-xs text-slate-500">当前负责人由登录身份确定；创建后可在“建设规划”中按权限转派</div>
					<div className="mt-1 text-sm font-medium text-slate-900">
						{currentOwner} · {currentDepartment}
					</div>
				</div>
			</Form>
		</Modal>
	);
}
