import { Alert, Button, Form, Space, Spin, Steps, Tag, Typography } from "antd";
import { useEffect, useMemo, useRef, useState } from "react";
import { toast } from "sonner";
import { PageHeader } from "@/components/page-header";
import { useRouter, useSearchParams } from "@/routes/hooks";
import { AccessPlanReviewStep } from "./AccessPlanReviewStep";
import styles from "./AccessPlanWizardPage.module.css";
import { ApiAccessStep } from "./ApiAccessStep";
import { ACCESS_KIND_LABELS, type AccessPlanFormValues, type AccessPlanStep } from "./accessPlan.types";
import { normalizeAccessKind, requireSafeApiResourcePath } from "./accessPlanPayload";
import { acquireSingleFlight, releaseSingleFlight } from "./accessSingleFlight";
import { DatabaseAccessStep } from "./DatabaseAccessStep";
import { FileAccessStep } from "./FileAccessStep";
import { LandingScheduleStep } from "./LandingScheduleStep";
import { validateFileTargetColumns } from "./shared/fileTargetSchemaMapping";
import { safeAccessPlanErrorMessage, useAccessPlanWizard } from "./useAccessPlanWizard";

const STEP_ITEMS = [
	{ title: "来源连接", description: "引用托管连接" },
	{ title: "资源定义", description: "确定接入范围" },
	{ title: "落地策略", description: "确认目标与调度" },
];

const initialValues: AccessPlanFormValues = {
	name: "",
	targetDataSourceId: "",
	tableSelectionMode: "all",
	selectedTables: [],
	syncMode: "full_refresh",
	scheduleType: "manual",
	scheduleIntervalMinutes: 60,
	airflowEnabled: true,
	runNow: false,
	apiMethod: "GET",
	apiPageSize: 100,
	fileClassification: "INTERNAL",
	fileStructureMode: "manual",
	fileLandingMode: "create_new",
	fileRecreateConfirmed: false,
	fileAutoId: true,
};

const parseEditId = (value: string | null) => {
	const parsed = Number(value);
	return Number.isInteger(parsed) && parsed > 0 ? parsed : undefined;
};

export default function AccessPlanWizardPage() {
	const searchParams = useSearchParams();
	const router = useRouter();
	const kind = useMemo(() => normalizeAccessKind(searchParams.get("kind")), [searchParams]);
	const editId = useMemo(() => parseEditId(searchParams.get("editId")), [searchParams]);
	const [currentStep, setCurrentStep] = useState<AccessPlanStep>(0);
	const [form] = Form.useForm<AccessPlanFormValues>();
	const submitLockRef = useRef(false);
	const wizard = useAccessPlanWizard({ kind, editId, form });
	const watchedValues = Form.useWatch([], form);
	const values = { ...initialValues, ...form.getFieldsValue(true), ...(watchedValues || {}) } as AccessPlanFormValues;
	const sourceName = wizard.sourceDataSources.find((item) => item.id === values.sourceDataSourceId)?.name;
	const targetName = wizard.targetDataSources.find((item) => item.id === values.targetDataSourceId)?.name;
	const listPath = kind === "file" ? "/foundation/data-sources/files" : `/foundation/data-sources/${kind}`;

	// biome-ignore lint/correctness/useExhaustiveDependencies: changing the route edit target or access kind must restart the wizard at its first step.
	useEffect(() => {
		setCurrentStep(0);
	}, [editId, kind]);

	const validateCurrentStep = async () => {
		if (wizard.uploadingFile) throw new Error("文件仍在上传解析，请等待完成后继续");
		if (currentStep === 0) {
			const fields: Array<keyof AccessPlanFormValues> =
				kind === "file" ? ["name", "fileClassification"] : ["name", "sourceDataSourceId"];
			await form.validateFields(fields);
			if (kind === "file" && !wizard.fileAdmission.ready) throw new Error(wizard.fileAdmission.reason);
			return;
		}
		if (currentStep === 1) {
			if (kind === "database" && values.tableSelectionMode === "manual" && !values.selectedTables.length) {
				throw new Error("请选择需要接入的源表");
			}
			if (kind === "api") {
				await form.validateFields(["apiMethod", "apiResourcePath"]);
				requireSafeApiResourcePath(values.apiResourcePath);
			}
			if (kind === "file") {
				await form.validateFields([
					"targetDataSourceId",
					"fileStructureMode",
					"fileReferenceTable",
					"fileLandingMode",
					"fileTargetTable",
					"fileRecreateConfirmed",
				]);
				const issue = validateFileTargetColumns(wizard.fileUploadResult?.columns || [])[0];
				if (issue) throw new Error(issue.message);
				if (values.fileLandingMode === "recreate_existing" && !values.fileRecreateConfirmed) {
					throw new Error("请确认全量重建原表");
				}
			}
			return;
		}
		const fields: Array<keyof AccessPlanFormValues> = ["targetDataSourceId", "scheduleType"];
		if (values.scheduleType === "cron") fields.push("scheduleCron");
		if (kind !== "file" && values.syncMode === "incremental") fields.push("incrementalColumn");
		await form.validateFields(fields);
	};

	const next = async () => {
		try {
			await validateCurrentStep();
			setCurrentStep((previous) => Math.min(2, previous + 1) as AccessPlanStep);
		} catch (error: unknown) {
			toast.error(safeAccessPlanErrorMessage(error, "请完成当前步骤必填项"));
		}
	};

	const submit = async () => {
		if (!acquireSingleFlight(submitLockRef)) return;
		try {
			await validateCurrentStep();
			const result = await wizard.submit();
			toast.success(result.updated ? "接入计划已更新并生效" : "接入计划已保存并生效");
			if (result.taskId) router.push(`/foundation/data-sources/access/${result.taskId}`);
			else router.push(listPath);
		} catch (error: unknown) {
			toast.error(safeAccessPlanErrorMessage(error, "接入任务保存失败"));
		} finally {
			releaseSingleFlight(submitLockRef);
		}
	};

	const sourceStep =
		kind === "database" ? (
			<DatabaseAccessStep
				form={form}
				phase="source"
				dataSources={wizard.sourceDataSources}
				discoveredTables={wizard.discoveredTables}
				discovering={wizard.discoveringTables}
				discoverError={wizard.discoverError}
				onDiscoveryInputChange={wizard.resetDatabaseDiscovery}
				onDiscover={() =>
					void wizard
						.discoverTables()
						.catch((error: unknown) => toast.error(safeAccessPlanErrorMessage(error, "源表发现失败")))
				}
			/>
		) : kind === "api" ? (
			<ApiAccessStep
				form={form}
				phase="source"
				dataSources={wizard.sourceDataSources}
				preview={wizard.apiPreview}
				previewing={wizard.apiPreviewing}
				onPreviewInputChange={wizard.resetApiPreview}
				onPreview={() =>
					void wizard
						.previewApi()
						.catch((error: unknown) => toast.error(safeAccessPlanErrorMessage(error, "API 预览失败")))
				}
			/>
		) : (
			<FileAccessStep
				form={form}
				phase="source"
				fileUploadResult={wizard.fileUploadResult}
				onFileUploadResultChange={wizard.setFileUploadResult}
				uploading={wizard.uploadingFile}
				userClassificationRank={wizard.userClassificationRank}
				onUpload={async (file) => {
					try {
						await wizard.uploadFile(file);
						toast.success("文件已加密上传并完成解析");
					} catch (error: unknown) {
						toast.error(safeAccessPlanErrorMessage(error, "文件上传失败"));
						throw error;
					}
				}}
			/>
		);

	const resourceStep =
		kind === "database" ? (
			<DatabaseAccessStep
				form={form}
				phase="resource"
				dataSources={wizard.sourceDataSources}
				discoveredTables={wizard.discoveredTables}
				discovering={wizard.discoveringTables}
				discoverError={wizard.discoverError}
				onDiscoveryInputChange={wizard.resetDatabaseDiscovery}
				onDiscover={() =>
					void wizard
						.discoverTables()
						.catch((error: unknown) => toast.error(safeAccessPlanErrorMessage(error, "源表发现失败")))
				}
			/>
		) : kind === "api" ? (
			<ApiAccessStep
				form={form}
				phase="resource"
				dataSources={wizard.sourceDataSources}
				preview={wizard.apiPreview}
				previewing={wizard.apiPreviewing}
				onPreviewInputChange={wizard.resetApiPreview}
				onPreview={() =>
					void wizard
						.previewApi()
						.catch((error: unknown) => toast.error(safeAccessPlanErrorMessage(error, "API 预览失败")))
				}
			/>
		) : (
			<FileAccessStep
				form={form}
				phase="resource"
				fileUploadResult={wizard.fileUploadResult}
				onFileUploadResultChange={wizard.setFileUploadResult}
				uploading={wizard.uploadingFile}
				userClassificationRank={wizard.userClassificationRank}
				onUpload={async (file) => {
					await wizard.uploadFile(file);
				}}
				targetDataSources={wizard.targetDataSources}
				defaultDestination={wizard.defaultDestination}
			/>
		);

	if (wizard.loading) {
		return (
			<div className={styles.centerState}>
				<Spin tip={editId ? "正在加载接入任务" : "正在加载接入配置"} />
			</div>
		);
	}

	return (
		<div className={styles.page}>
			<div className={styles.shell}>
				<PageHeader
					title={`${editId ? "编辑" : "新建"} ${ACCESS_KIND_LABELS[kind]}计划`}
					actions={<Button onClick={() => router.push(listPath)}>返回列表</Button>}
				/>
				<div className={styles.contextBar}>
					<div className={styles.contextMeta}>
						<Tag color="blue">{ACCESS_KIND_LABELS[kind]}</Tag>
						<Typography.Text>
							{editId ? `任务 #${editId} · 保留原任务配置后更新` : "入口类型已固定，按三步完成配置"}
						</Typography.Text>
					</div>
					<Typography.Text type="secondary">连接凭据由平台托管</Typography.Text>
				</div>
				{wizard.error ? (
					<Alert className="mb-4" type="error" showIcon message="接入配置加载失败" description={wizard.error} />
				) : null}
				{wizard.editError ? (
					<Alert className="mb-4" type="error" showIcon message="无法编辑当前任务" description={wizard.editError} />
				) : null}
				<div className={styles.stepsPanel}>
					<Steps current={currentStep} items={STEP_ITEMS} responsive />
				</div>
				<Form form={form} layout="vertical" initialValues={initialValues} disabled={Boolean(wizard.editError)}>
					<div className={styles.formPanel}>
						{currentStep === 0 ? sourceStep : null}
						{currentStep === 1 ? resourceStep : null}
						{currentStep === 2 ? (
							<Space direction="vertical" size={28} className="w-full">
								<LandingScheduleStep
									form={form}
									kind={kind}
									editing={editId !== undefined}
									targetDataSources={wizard.targetDataSources}
									defaultDestination={wizard.defaultDestination}
								/>
								<AccessPlanReviewStep
									kind={kind}
									editing={editId !== undefined}
									values={values}
									sourceName={sourceName}
									targetName={targetName}
									fileUploadResult={wizard.fileUploadResult}
									apiPreview={wizard.apiPreview}
								/>
							</Space>
						) : null}
					</div>
					<div className={styles.footer}>
						<Typography.Text type="secondary">步骤 {currentStep + 1} / 3</Typography.Text>
						<Space>
							<Button
								disabled={currentStep === 0}
								onClick={() => setCurrentStep((previous) => Math.max(0, previous - 1) as AccessPlanStep)}
							>
								上一步
							</Button>
							{currentStep < 2 ? (
								<Button type="primary" onClick={() => void next()}>
									下一步
								</Button>
							) : (
								<Button type="primary" loading={wizard.saving} onClick={() => void submit()}>
									{editId ? "保存修改并生效" : "保存并生效"}
								</Button>
							)}
						</Space>
					</div>
				</Form>
			</div>
		</div>
	);
}
