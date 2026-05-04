import { useEffect, useMemo, useState } from "react";
import { toast } from "sonner";
import {
	Alert,
	Button,
	Card,
	Descriptions,
	Form,
	Input,
	Modal,
	Select,
	Space,
	Tabs,
	Tag,
	Typography,
} from "antd";
import { CompactTable } from "@/components/table";
import type { ColumnsType } from "antd/es/table";
import { DeleteOutlined, EditOutlined, PlusOutlined, SaveOutlined } from "@ant-design/icons";
import { PageHeader } from "@/components/page-header";
import {
	getClassificationMapping,
	getClassificationMaskingLinkage,
	replaceClassificationMapping,
	validateClassificationMapping,
	listMaskingRules,
	createMaskingRule,
	updateMaskingRule,
	deleteMaskingRule,
	listDatasets,
	getDatasetSecurityMapping,
	upsertDatasetSecurityMapping,
} from "@/api/platformApi";

const { Text } = Typography;
const SECURITY_LINKAGE_VERSION_KEY = "catalog.security.linkage.version";
const SECURITY_LINKAGE_EVENT = "catalog-security-linkage-updated";

const MASKING_FUNCTIONS = [
	{ label: "HASH", value: "HASH" },
	{ label: "PARTIAL", value: "PARTIAL" },
	{ label: "NULL", value: "NULL" },
	{ label: "REDACT", value: "REDACT" },
];

type ClassificationRow = { id?: string; source?: string; sourceLevel?: string; platformLevel?: string };

type MaskingRule = {
	id?: string;
	dataset?: { id?: string; name?: string } | null;
	column?: string;
	function?: string;
	args?: string;
};

type MappingValidationIssue = {
	row?: number;
	code?: string;
	message?: string;
	suggestion?: string;
	datasetName?: string;
	classification?: string;
};

type MappingValidationResult = {
	valid?: boolean;
	normalizedCount?: number;
	conflicts?: MappingValidationIssue[];
	warnings?: MappingValidationIssue[];
};

type DatasetLinkage = {
	datasetId?: string;
	datasetName?: string;
	classification?: string;
	requiresMasking?: boolean;
	maskingRuleCount?: number;
	conflict?: boolean;
	effectiveRules?: Array<{ id?: string; column?: string; function?: string; args?: string }>;
	suggestions?: string[];
};

export default function Page() {
	const [classificationRows, setClassificationRows] = useState<ClassificationRow[]>([]);
	const [classificationDirty, setClassificationDirty] = useState(false);
	const [maskingRules, setMaskingRules] = useState<MaskingRule[]>([]);
	const [datasets, setDatasets] = useState<{ id: string; name: string }[]>([]);
	const [loading, setLoading] = useState(false);
	const [mappingModalOpen, setMappingModalOpen] = useState(false);
	const [editingMapping, setEditingMapping] = useState<ClassificationRow | null>(null);
	const [mappingForm] = Form.useForm<ClassificationRow>();
	const [maskingModalOpen, setMaskingModalOpen] = useState(false);
	const [editingMasking, setEditingMasking] = useState<MaskingRule | null>(null);
	const [maskingForm] = Form.useForm<MaskingRule & { datasetId?: string }>();
	const [selectedDataset, setSelectedDataset] = useState<string | undefined>();
	const [, setSecurityMapping] = useState<any>(null);
	const [securityForm] = Form.useForm();
	const [mappingValidation, setMappingValidation] = useState<MappingValidationResult | null>(null);
	const [validatingMapping, setValidatingMapping] = useState(false);
	const [datasetLinkage, setDatasetLinkage] = useState<DatasetLinkage | null>(null);

	const datasetOptions = useMemo(
		() => datasets.map((item) => ({ label: item.name, value: item.id })),
		[datasets],
	);

	const notifySecurityLinkageChanged = () => {
		const version = String(Date.now());
		localStorage.setItem(SECURITY_LINKAGE_VERSION_KEY, version);
		window.dispatchEvent(new CustomEvent(SECURITY_LINKAGE_EVENT, { detail: { version } }));
	};

	const loadMappings = async () => {
		try {
			const list = await getClassificationMapping();
			setClassificationRows(Array.isArray(list) ? (list as ClassificationRow[]) : []);
			setClassificationDirty(false);
			setMappingValidation(null);
		} catch (error: any) {
			toast.error(error?.message || "分类映射加载失败");
		}
	};

	const loadMaskingRules = async () => {
		try {
			const list = await listMaskingRules();
			setMaskingRules(Array.isArray(list) ? (list as MaskingRule[]) : []);
		} catch (error: any) {
			toast.error(error?.message || "脱敏规则加载失败");
		}
	};

	const loadDatasets = async () => {
		try {
			const resp: any = await listDatasets({ page: 0, size: 200 });
			const list = Array.isArray(resp?.content) ? resp.content : [];
			setDatasets(list.map((item: any) => ({ id: String(item.id), name: item.name || item.id })));
		} catch (error: any) {
			toast.error(error?.message || "数据集加载失败");
		}
	};

	const loadSecurityMapping = async (datasetId?: string) => {
		if (!datasetId) {
			setSecurityMapping(null);
			securityForm.resetFields();
			setDatasetLinkage(null);
			return;
		}
		try {
			const result = await getDatasetSecurityMapping(datasetId);
			setSecurityMapping(result || null);
			securityForm.setFieldsValue({
				dataLevelField: (result as any)?.dataLevelField || "",
				deptField: (result as any)?.deptField || "",
			});
		} catch (error: any) {
			toast.error(error?.message || "安全字段加载失败");
		}
	};

	const loadDatasetLinkage = async (datasetId?: string) => {
		if (!datasetId) {
			setDatasetLinkage(null);
			return;
		}
		try {
			const linkage = await getClassificationMaskingLinkage(datasetId);
			setDatasetLinkage((linkage || null) as DatasetLinkage | null);
		} catch (error: any) {
			toast.error(error?.message || "密级与脱敏联动信息加载失败");
			setDatasetLinkage(null);
		}
	};

	const runMappingValidation = async (silent = false) => {
		setValidatingMapping(true);
		try {
			const result = (await validateClassificationMapping(classificationRows)) as MappingValidationResult;
			setMappingValidation(result || null);
			const conflicts = Array.isArray(result?.conflicts) ? result.conflicts : [];
			if (!silent) {
				if (conflicts.length) {
					toast.error(`发现 ${conflicts.length} 个冲突，请先修复`);
				} else {
					toast.success("未发现阻断冲突");
				}
			}
			return result || null;
		} catch (error: any) {
			if (!silent) {
				toast.error(error?.message || "冲突预检失败");
			}
			return null;
		} finally {
			setValidatingMapping(false);
		}
	};

	useEffect(() => {
		setLoading(true);
		Promise.all([loadMappings(), loadMaskingRules(), loadDatasets()])
			.catch(() => {})
			.finally(() => setLoading(false));
	}, []);

	useEffect(() => {
		void loadSecurityMapping(selectedDataset);
		void loadDatasetLinkage(selectedDataset);
	}, [selectedDataset]);

	const openMappingModal = (row?: ClassificationRow) => {
		setEditingMapping(row || null);
		mappingForm.setFieldsValue({
			source: row?.source || "",
			sourceLevel: row?.sourceLevel || "",
			platformLevel: row?.platformLevel || "",
		});
		setMappingModalOpen(true);
	};

	const saveMappingRow = async () => {
		try {
			const values = await mappingForm.validateFields();
			if (editingMapping) {
				setClassificationRows((prev) =>
					prev.map((row) => (row === editingMapping ? { ...row, ...values } : row)),
				);
			} else {
				setClassificationRows((prev) => [...prev, { ...values }]);
			}
			setClassificationDirty(true);
			setMappingValidation(null);
			setMappingModalOpen(false);
		} catch (error: any) {
			if (error?.errorFields) return;
			toast.error(error?.message || "保存失败");
		}
	};

	const deleteMappingRow = (row: ClassificationRow) => {
		setClassificationRows((prev) => prev.filter((item) => item !== row));
		setClassificationDirty(true);
		setMappingValidation(null);
	};

	const saveMappingAll = async () => {
		try {
			const validation = await runMappingValidation(true);
			const conflicts = Array.isArray(validation?.conflicts) ? validation?.conflicts : [];
			if (conflicts.length > 0) {
				toast.error("分类映射存在冲突，请先修复后再保存");
				return;
			}
			await replaceClassificationMapping(classificationRows);
			toast.success("分类映射已保存");
			setClassificationDirty(false);
			await loadMappings();
			notifySecurityLinkageChanged();
			await loadDatasetLinkage(selectedDataset);
		} catch (error: any) {
			toast.error(error?.message || "保存失败");
		}
	};

	const openMaskingModal = (row?: MaskingRule) => {
		setEditingMasking(row || null);
		maskingForm.setFieldsValue({
			datasetId: row?.dataset?.id || undefined,
			column: row?.column || "",
			function: row?.function || "",
			args: row?.args || "",
		});
		setMaskingModalOpen(true);
	};

	const saveMasking = async () => {
		try {
			const values = await maskingForm.validateFields();
			const payload: any = {
				dataset: values.datasetId ? { id: values.datasetId } : null,
				column: values.column,
				function: values.function,
				args: values.args,
			};
			if (editingMasking?.id) {
				await updateMaskingRule(editingMasking.id, payload);
				toast.success("脱敏规则已更新");
			} else {
				await createMaskingRule(payload);
				toast.success("脱敏规则已新增");
			}
			setMaskingModalOpen(false);
			await loadMaskingRules();
			notifySecurityLinkageChanged();
			await loadDatasetLinkage(selectedDataset);
		} catch (error: any) {
			if (error?.errorFields) return;
			toast.error(error?.message || "保存失败");
		}
	};

	const removeMasking = async (id?: string) => {
		if (!id) return;
		try {
			await deleteMaskingRule(id);
			toast.success("已删除脱敏规则");
			await loadMaskingRules();
			notifySecurityLinkageChanged();
			await loadDatasetLinkage(selectedDataset);
		} catch (error: any) {
			toast.error(error?.message || "删除失败");
		}
	};

	const saveSecurityMapping = async () => {
		if (!selectedDataset) {
			toast.warning("请先选择数据集");
			return;
		}
		try {
			const values = await securityForm.validateFields();
			await upsertDatasetSecurityMapping(selectedDataset, values);
			toast.success("安全字段已保存");
			await loadSecurityMapping(selectedDataset);
			notifySecurityLinkageChanged();
			await loadDatasetLinkage(selectedDataset);
		} catch (error: any) {
			if (error?.errorFields) return;
			toast.error(error?.message || "保存失败");
		}
	};

	const classificationColumns: ColumnsType<ClassificationRow> = [
		{ title: "来源系统", dataIndex: "source", render: (v) => v || "-" },
		{ title: "来源级别", dataIndex: "sourceLevel", render: (v) => v || "-" },
		{ title: "平台级别", dataIndex: "platformLevel", render: (v) => <Tag>{v || "-"}</Tag> },
		{
			title: "操作",
			render: (_, record) => (
				<Space>
					<Button size="small" icon={<EditOutlined />} onClick={() => openMappingModal(record)}>
						编辑
					</Button>
					<Button size="small" danger icon={<DeleteOutlined />} onClick={() => deleteMappingRow(record)}>
						删除
					</Button>
				</Space>
			),
		},
	];

	const maskingColumns: ColumnsType<MaskingRule> = [
		{ title: "数据集", dataIndex: ["dataset", "id"], render: (value) => datasets.find((d) => d.id === value)?.name || value || "-" },
		{ title: "字段", dataIndex: "column", render: (v) => v || "-" },
		{ title: "函数", dataIndex: "function", render: (v) => <Tag>{v || "-"}</Tag> },
		{ title: "参数", dataIndex: "args", render: (v) => v || "-" },
		{
			title: "操作",
			render: (_, record) => (
				<Space>
					<Button size="small" icon={<EditOutlined />} onClick={() => openMaskingModal(record)}>
						编辑
					</Button>
					<Button size="small" danger icon={<DeleteOutlined />} onClick={() => removeMasking(record.id)}>
						删除
					</Button>
				</Space>
			),
		},
	];

	return (
		<div className="space-y-6">
			<PageHeader title="数据治理中心 / 分级分类" />
			<Card>
				<Tabs
					items={[
						{
							key: "classification",
							label: "分类映射",
								children: (
									<>
										<Space className="mb-3">
											<Button icon={<PlusOutlined />} type="primary" onClick={() => openMappingModal()}>
												新增映射
											</Button>
											<Button loading={validatingMapping} onClick={() => void runMappingValidation()}>
												冲突预检
											</Button>
											<Button
												type="default"
												disabled={!classificationDirty}
												icon={<SaveOutlined />}
												onClick={saveMappingAll}
										>
											保存映射
											</Button>
										</Space>
										{mappingValidation ? (
											<Space direction="vertical" className="mb-3 w-full">
												<Alert
													type={Array.isArray(mappingValidation.conflicts) && mappingValidation.conflicts.length > 0 ? "error" : "success"}
													showIcon
													message={
														Array.isArray(mappingValidation.conflicts) && mappingValidation.conflicts.length > 0
															? `发现 ${mappingValidation.conflicts.length} 个冲突`
															: "映射校验通过"
													}
													description={`标准化后映射条数：${Number(mappingValidation.normalizedCount || 0)}`}
												/>
												{Array.isArray(mappingValidation.conflicts) && mappingValidation.conflicts.length > 0 ? (
													<div className="rounded border border-red-200 bg-red-50 p-3 text-xs text-red-700">
														{mappingValidation.conflicts.slice(0, 6).map((item, idx) => (
															<div key={`conflict-${idx}`}>
																{item.message || "映射冲突"}{item.suggestion ? `；建议：${item.suggestion}` : ""}
															</div>
														))}
													</div>
												) : null}
												{Array.isArray(mappingValidation.warnings) && mappingValidation.warnings.length > 0 ? (
													<div className="rounded border border-amber-200 bg-amber-50 p-3 text-xs text-amber-700">
														{mappingValidation.warnings.slice(0, 6).map((item, idx) => (
															<div key={`warning-${idx}`}>
																{item.message || "校验提示"}{item.suggestion ? `；建议：${item.suggestion}` : ""}
															</div>
														))}
													</div>
												) : null}
											</Space>
										) : null}
										<CompactTable
											rowKey={(record, idx) => record.id || `new-${idx}`}
											columns={classificationColumns}
											dataSource={classificationRows}
										loading={loading}
									/>
								</>
							),
						},
						{
							key: "masking",
							label: "脱敏规则",
							children: (
								<>
									<Space className="mb-3">
										<Button icon={<PlusOutlined />} type="primary" onClick={() => openMaskingModal()}>
											新增规则
										</Button>
									</Space>
									<CompactTable
										rowKey={(record) => record.id || record.column || "mask"}
										columns={maskingColumns}
										dataSource={maskingRules}
										loading={loading}
									/>
								</>
							),
						},
						{
							key: "datasetSecurity",
							label: "数据集安全字段",
							children: (
								<>
									<Space className="mb-3" align="center">
										<Text>选择数据集：</Text>
										<Select
											options={datasetOptions}
											value={selectedDataset}
											onChange={setSelectedDataset}
											style={{ minWidth: 240 }}
											allowClear
										/>
										<Button type="primary" onClick={saveSecurityMapping} disabled={!selectedDataset}>
											保存
										</Button>
									</Space>
										<Form form={securityForm} layout="vertical">
											<Form.Item label="密级字段" name="dataLevelField">
												<Input placeholder="例如 data_level" />
											</Form.Item>
											<Form.Item label="部门字段" name="deptField">
												<Input placeholder="例如 owner_dept" />
											</Form.Item>
										</Form>
										{selectedDataset && datasetLinkage ? (
											<Space direction="vertical" className="w-full">
												<Descriptions bordered size="small" column={1} title="密级与脱敏联动">
													<Descriptions.Item label="当前密级">{datasetLinkage.classification || "-"}</Descriptions.Item>
													<Descriptions.Item label="生效脱敏策略">
														{Number(datasetLinkage.maskingRuleCount || 0)} 条
													</Descriptions.Item>
													<Descriptions.Item label="规则明细">
														{Array.isArray(datasetLinkage.effectiveRules) && datasetLinkage.effectiveRules.length > 0
															? datasetLinkage.effectiveRules
																	.slice(0, 5)
																	.map((rule) => `${rule.column || "-"} -> ${rule.function || "-"}`)
																	.join("；")
															: "未配置"}
													</Descriptions.Item>
												</Descriptions>
												{datasetLinkage.conflict ? (
													<Alert
														type="warning"
														showIcon
														message="当前密级与脱敏策略不一致"
														description={(datasetLinkage.suggestions || []).join("；") || "请补齐脱敏规则后重试。"}
													/>
												) : null}
											</Space>
										) : null}
									</>
								),
							},
					]}
				/>
			</Card>

			<Modal
				open={mappingModalOpen}
				title={editingMapping ? "编辑映射" : "新增映射"}
				onCancel={() => setMappingModalOpen(false)}
				onOk={saveMappingRow}
				okText="保存"
				destroyOnClose
			>
				<Form form={mappingForm} layout="vertical">
					<Form.Item label="来源系统" name="source" rules={[{ required: true, message: "请输入来源系统" }]}>
						<Input placeholder="例如 OM" />
					</Form.Item>
					<Form.Item label="来源级别" name="sourceLevel" rules={[{ required: true, message: "请输入来源级别" }]}>
						<Input placeholder="例如 P0" />
					</Form.Item>
					<Form.Item label="平台级别" name="platformLevel" rules={[{ required: true, message: "请输入平台级别" }]}>
						<Input placeholder="例如 PUBLIC" />
					</Form.Item>
				</Form>
			</Modal>

			<Modal
				open={maskingModalOpen}
				title={editingMasking ? "编辑脱敏规则" : "新增脱敏规则"}
				onCancel={() => setMaskingModalOpen(false)}
				onOk={saveMasking}
				okText="保存"
				destroyOnClose
			>
				<Form form={maskingForm} layout="vertical">
					<Form.Item label="数据集" name="datasetId">
						<Select options={datasetOptions} allowClear />
					</Form.Item>
					<Form.Item label="字段" name="column" rules={[{ required: true, message: "请输入字段名" }]}>
						<Input placeholder="例如 mobile_no" />
					</Form.Item>
					<Form.Item label="脱敏函数" name="function" rules={[{ required: true, message: "请选择函数" }]}>
						<Select options={MASKING_FUNCTIONS} />
					</Form.Item>
					<Form.Item label="参数" name="args">
						<Input placeholder="可选" />
					</Form.Item>
				</Form>
			</Modal>
		</div>
	);
}
