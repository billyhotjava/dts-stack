import {
	Alert,
	Button,
	Card,
	Col,
	Collapse,
	Descriptions,
	Form,
	Input,
	Radio,
	Row,
	Select,
	Space,
	Tag,
	Typography,
} from "antd";
import type { ModelImplementationInputMode } from "../modelImplementationContract";
import type { CanonicalModelSpecView, ModelSpecField } from "../modelSpecV2Contract";

const { Text } = Typography;

const loadStrategyLabels = {
	FULL: "全量覆盖",
	INCREMENTAL: "增量装载",
	SNAPSHOT: "周期快照",
} as const;

const inputModeGuidance: Record<ModelImplementationInputMode, { title: string; description: string }> = {
	PHYSICAL_ASSET: {
		title: "使用已登记的数据表",
		description: "从建设计划的来源盘点中选择已确认的表、视图或数据集。适合项目、客户、组织等已有业务主数据的模型。",
	},
	UPSTREAM_MODEL: {
		title: "使用已有模型输出",
		description: "把另一个逻辑模型的已保存实现作为输入，并锁定具体版本。适合由明细模型继续加工汇总表或应用表。",
	},
	GENERATED: {
		title: "使用系统内置生成",
		description: "当前仅用于日期维度等可确定生成的数据。项目、客户、组织等业务维度不应使用此方式。",
	},
};

export function ModelImplementationGuide() {
	return (
		<Alert
			className="mb-4"
			type="info"
			showIcon
			message="把逻辑模型转换为可执行的数据产出"
			description={
				<div>
					<div className="mb-2">
						逻辑设计已经定义“要什么数据”；本页只需确认数据从哪里来、字段如何对应，以及最终怎样生成。
					</div>
					<Space size={[4, 4]} wrap>
						<Tag color="blue">1. 选择数据来源</Tag>
						<Tag color="blue">2. 对齐模型字段</Tag>
						<Tag color="blue">3. 确认产出方式</Tag>
					</Space>
				</div>
			}
		/>
	);
}

type ImplementationTargetSettings = {
	targetPhysicalName: string;
	loadStrategy: keyof typeof loadStrategyLabels;
	partitionFields: string[];
	retentionDays?: number;
};

export function ModelImplementationTargetSummary({
	model,
	settings,
}: {
	model: CanonicalModelSpecView;
	settings: ImplementationTargetSettings;
}) {
	return (
		<Card
			size="small"
			className="mb-4"
			title="目标表摘要"
			extra={<Text type="secondary">由当前数据实现生成</Text>}
			data-testid="model-implementation-target-summary"
		>
			<Descriptions size="small" column={{ xs: 1, sm: 2, md: 3 }}>
				<Descriptions.Item label="模型名称">{model.name}</Descriptions.Item>
				<Descriptions.Item label="数仓分层">
					<Tag color="blue">{model.layer}</Tag>
				</Descriptions.Item>
				<Descriptions.Item label="目标表名">{settings.targetPhysicalName || "尚未设置"}</Descriptions.Item>
				<Descriptions.Item label="装载策略">{loadStrategyLabels[settings.loadStrategy]}</Descriptions.Item>
				<Descriptions.Item label="分区字段">
					{settings.partitionFields.length ? settings.partitionFields.join("、") : "未设置"}
				</Descriptions.Item>
				<Descriptions.Item label="数据保留">
					{settings.retentionDays == null ? "未设置" : `${settings.retentionDays} 天`}
				</Descriptions.Item>
			</Descriptions>
		</Card>
	);
}

export function ModelImplementationInputModeHelp({ inputMode }: { inputMode: ModelImplementationInputMode }) {
	const guidance = inputModeGuidance[inputMode];
	return (
		<Alert
			className="mb-4"
			type={inputMode === "GENERATED" ? "warning" : "info"}
			showIcon
			message={guidance.title}
			description={guidance.description}
		/>
	);
}

type InputOption = {
	value: string;
	label: string;
	disabled?: boolean;
};

type UpstreamPinState = {
	id: string;
	label: string;
	pinned: boolean;
	requiresAdoption: boolean;
};

export function ModelImplementationSourceSection({
	inputMode,
	inputModeOptions,
	inputOptions,
	inputIds,
	sourceLoading,
	sourceError,
	sourcePermissionDenied,
	pendingPinDriftCount,
	upstreamPinStates,
	adoptableCurrentInputCount,
	onAdoptCurrentPins,
	onReloadSources,
	onManageSources,
}: {
	inputMode: ModelImplementationInputMode;
	inputModeOptions: InputOption[];
	inputOptions: InputOption[];
	inputIds: string[];
	sourceLoading: boolean;
	sourceError: string;
	sourcePermissionDenied: boolean;
	pendingPinDriftCount: number;
	upstreamPinStates: UpstreamPinState[];
	adoptableCurrentInputCount: number;
	onAdoptCurrentPins: () => void;
	onReloadSources: () => void;
	onManageSources?: () => void;
}) {
	return (
		<Card size="small" className="mb-4" title="1. 选择数据来源">
			<Form.Item name="inputMode" label="数据来源方式" rules={[{ required: true }]}>
				<Radio.Group options={inputModeOptions} />
			</Form.Item>
			<ModelImplementationInputModeHelp inputMode={inputMode} />
			<Form.Item
				name="inputIds"
				label={
					inputMode === "PHYSICAL_ASSET"
						? "选择已登记的数据表"
						: inputMode === "UPSTREAM_MODEL"
							? "选择已有模型输出"
							: "选择系统生成规则"
				}
				rules={[
					{
						required: true,
						type: "array",
						min: 1,
						message: "请至少选择一个数据来源",
					},
				]}
			>
				<Select
					mode="multiple"
					allowClear
					showSearch
					optionFilterProp="label"
					options={inputOptions}
					loading={inputMode === "PHYSICAL_ASSET" && sourceLoading}
					placeholder={
						inputMode === "PHYSICAL_ASSET"
							? "选择来源盘点中已确认的表、视图或数据集"
							: inputMode === "UPSTREAM_MODEL"
								? "选择一个或多个已保存的模型输出"
								: "选择适用于当前模型的内置生成规则"
					}
				/>
			</Form.Item>
			{inputMode === "PHYSICAL_ASSET" && pendingPinDriftCount > 0 ? (
				<Alert
					className="mb-4"
					type="warning"
					showIcon
					data-testid="model-implementation-pin-drift"
					message={`检测到 ${pendingPinDriftCount} 个输入已有新版本，当前仍保留已保存的精确版本`}
					description="版本不会随普通保存静默升级；确认采用当前盘点版本后，下一次保存才会写入新 pin。"
					action={
						<Button size="small" onClick={onAdoptCurrentPins}>
							采用当前版本
						</Button>
					}
				/>
			) : null}
			{inputMode === "UPSTREAM_MODEL" && upstreamPinStates.length > 0 ? (
				<Alert
					className="mb-4"
					type={
						upstreamPinStates.some((input) => input.requiresAdoption) || pendingPinDriftCount > 0 ? "warning" : "info"
					}
					showIcon
					data-testid="model-implementation-upstream-pin-state"
					message={
						<Space size={8} wrap>
							<span>上游实现引用</span>
							<Tag color="success">已固定 {upstreamPinStates.filter((input) => input.pinned).length}</Tag>
							<Tag color="gold">待固定 {upstreamPinStates.filter((input) => !input.pinned).length}</Tag>
						</Space>
					}
					description={
						<div>
							<Space size={4} wrap>
								{upstreamPinStates.map((input) => (
									<Tag key={input.id} color={input.pinned ? "success" : "gold"}>
										{input.pinned ? "已固定" : "待固定"} · {input.label}
									</Tag>
								))}
							</Space>
							<div className="mt-2">
								{upstreamPinStates.some((input) => input.requiresAdoption)
									? "已有引用缺少完整 implementation pin；必须明确采用当前实现后才能保存。"
									: pendingPinDriftCount > 0
										? "检测到上游 ModelSpec 已有新 revision；普通保存仍保留原六元 pin，只有明确升级才会重新固定。"
										: upstreamPinStates.some((input) => !input.pinned)
											? "新选或已明确升级的引用将在保存时由服务端固定当前 implementation。"
											: "普通保存会原样保留 modelSpec 与 implementation 六元 pin，不会静默升级。"}
							</div>
						</div>
					}
					action={
						adoptableCurrentInputCount > 0 ? (
							<Button size="small" onClick={onAdoptCurrentPins}>
								采用当前实现 / 升级引用
							</Button>
						) : undefined
					}
				/>
			) : null}
			{inputIds.length > 1 ? (
				<Form.List name="joins">
					{(fields) => (
						<Card
							size="small"
							className="mb-4"
							title="多来源关联条件"
							extra={<Text type="secondary">系统别名按顺序固定为 src_0、src_1…，不接受自由 SQL</Text>}
						>
							{fields.map((field, index) => {
								const inputNumber = index + 2;
								const rightAlias = `src_${index + 1}`;
								return (
									<Row key={field.key} gutter={8}>
										<Col xs={24} md={4}>
											<Form.Item label={`输入 ${inputNumber}`}>
												<Input value={rightAlias} disabled />
											</Form.Item>
										</Col>
										<Col xs={24} md={5}>
											<Form.Item name={[field.name, "type"]} label="关联方式" rules={[{ required: true }]}>
												<Select
													options={[
														{ value: "INNER", label: "INNER" },
														{ value: "LEFT", label: "LEFT" },
														{ value: "RIGHT", label: "RIGHT" },
														{ value: "FULL", label: "FULL" },
													]}
												/>
											</Form.Item>
										</Col>
										<Col xs={24} md={7}>
											<Form.Item
												name={[field.name, "leftField"]}
												label="左侧关联字段"
												rules={[
													{ required: true, whitespace: true },
													{
														validator: async (_rule, value) => {
															const match = /^src_(\d+)\.[A-Za-z_][A-Za-z0-9_]*$/.exec(String(value || ""));
															if (!match || Number(match[1]) >= index + 1) {
																throw new Error(`仅可引用 src_0 至 src_${index} 的安全字段名`);
															}
														},
													},
												]}
											>
												<Input placeholder={`src_${index}.business_key`} />
											</Form.Item>
										</Col>
										<Col xs={24} md={8}>
											<Form.Item
												name={[field.name, "rightField"]}
												label="当前输入关联字段"
												rules={[
													{ required: true, whitespace: true },
													{
														pattern: new RegExp(`^${rightAlias}\\.[A-Za-z_][A-Za-z0-9_]*$`),
														message: `必须使用 ${rightAlias}.field`,
													},
												]}
											>
												<Input placeholder={`${rightAlias}.business_key`} />
											</Form.Item>
										</Col>
									</Row>
								);
							})}
						</Card>
					)}
				</Form.List>
			) : null}
			{inputMode === "PHYSICAL_ASSET" && sourceError ? (
				<Alert
					className="mb-4"
					type="warning"
					showIcon
					message={sourceError}
					action={
						<Space wrap>
							<Button size="small" onClick={onReloadSources} loading={sourceLoading}>
								重试
							</Button>
							{!sourcePermissionDenied && onManageSources ? (
								<Button size="small" type="primary" onClick={onManageSources}>
									管理规划来源
								</Button>
							) : null}
						</Space>
					}
				/>
			) : null}
		</Card>
	);
}

type CastOption = { value: string; label: string };

export function ModelImplementationFieldMappings({
	inputCount,
	modelFields,
	readOnly,
	castOptions,
	onInitialize,
}: {
	inputCount: number;
	modelFields: ModelSpecField[];
	readOnly: boolean;
	castOptions: CastOption[];
	onInitialize: () => void;
}) {
	return (
		<Form.List name="fieldMappings">
			{(fields, { add, remove }) => (
				<Card
					size="small"
					className="mb-4"
					title={
						<Space size={8} wrap>
							<span>2. 对齐模型字段</span>
							<Tag>
								已配置 {fields.length} / 模型字段 {modelFields.length}
							</Tag>
						</Space>
					}
					extra={
						!readOnly ? (
							<Space size={8} wrap>
								<Button size="small" onClick={onInitialize}>
									按模型字段初始化
								</Button>
								<Button size="small" onClick={() => add({ sourceField: "", targetField: "" })}>
									添加一行
								</Button>
							</Space>
						) : null
					}
					data-testid="model-implementation-field-mappings"
				>
					<Text type="secondary">
						将来源字段对应到逻辑设计中的模型字段，用于生成字段级血缘和类型转换。名称相同时可先自动初始化，再调整差异项。
					</Text>
					{fields.length === 0 ? (
						<Alert
							className="mt-3"
							type="info"
							showIcon
							message="尚未配置字段对应关系"
							description="可按模型字段初始化同名映射，也可以只添加需要改名或转换类型的字段。"
						/>
					) : (
						<div className="mt-3">
							{fields.map((field) => (
								<Row key={field.key} gutter={8}>
									<Col xs={24} md={8}>
										<Form.Item
											name={[field.name, "sourceField"]}
											label="来源字段"
											rules={[
												{
													pattern: /^(?:src_\d+\.)?[A-Za-z_][A-Za-z0-9_]*$/,
													message: "请输入字段名或安全限定名 src_N.field，不要填写 SQL 表达式",
												},
												{
													validator: async (_rule, value) => {
														const match = /^src_(\d+)\./.exec(String(value || ""));
														if (match && Number(match[1]) >= inputCount) {
															throw new Error("字段别名超出当前输入范围");
														}
													},
												},
											]}
										>
											<Input placeholder={inputCount > 1 ? "src_0.customer_id" : "customer_id"} />
										</Form.Item>
									</Col>
									<Col xs={24} md={8}>
										<Form.Item
											name={[field.name, "targetField"]}
											label="模型字段"
											rules={[
												{
													pattern: /^[A-Za-z_][A-Za-z0-9_]*$/,
													message: "请输入字段名，不要填写 SQL 表达式",
												},
											]}
										>
											<Select
												showSearch
												allowClear
												optionFilterProp="label"
												placeholder="选择逻辑模型字段"
												options={modelFields.map((modelField) => ({
													value: modelField.name,
													label: `${modelField.name} · ${modelField.dataType}`,
												}))}
											/>
										</Form.Item>
									</Col>
									<Col xs={20} md={6}>
										<Form.Item name={[field.name, "castType"]} label="类型转换">
											<Select allowClear placeholder="保持原类型" options={castOptions} />
										</Form.Item>
									</Col>
									<Col xs={4} md={2}>
										{!readOnly ? (
											<Button type="link" danger className="mt-8" onClick={() => remove(field.name)}>
												移除
											</Button>
										) : null}
									</Col>
								</Row>
							))}
						</div>
					)}
				</Card>
			)}
		</Form.List>
	);
}

export function ModelImplementationTechnicalDetails({
	model,
	projectKey,
	dbtUniqueId,
}: {
	model: CanonicalModelSpecView;
	projectKey: string;
	dbtUniqueId: string;
}) {
	return (
		<Collapse
			className="mt-4"
			size="small"
			items={[
				{
					key: "technical",
					label: "高级信息：系统技术标识与预处理",
					children: (
						<Space direction="vertical" size={12} className="w-full">
							{model.implementationMode === "DESIGNER_GENERATED" ? (
								<div data-testid="model-spec-system-preprocessing">
									<Space wrap>
										<Tag color="blue">系统管理</Tag>
										<Tag>ephemeral</Tag>
										<Tag>无物理表</Tag>
									</Space>
									<div className="mt-2">
										<Text type="secondary">普通模式会自动生成临时 STG，仅用于编译和血缘，不登记为物理资产。</Text>
									</div>
								</div>
							) : null}
							<Row gutter={[12, 8]}>
								<Col xs={24} md={12}>
									<Text type="secondary">实现项目标识</Text>
									<br />
									<Text code copyable>
										{projectKey}
									</Text>
								</Col>
								<Col xs={24} md={12}>
									<Text type="secondary">目标节点标识</Text>
									<br />
									<Text code copyable>
										{dbtUniqueId}
									</Text>
								</Col>
							</Row>
						</Space>
					),
				},
			]}
		/>
	);
}
