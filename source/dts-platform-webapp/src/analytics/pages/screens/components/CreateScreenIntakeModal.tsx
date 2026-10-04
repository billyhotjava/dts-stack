import { useEffect, useMemo, useState } from "react";
import { Modal, Input, Select } from "antd";

/**
 * 新建大屏入口对话框（Sprint-24 F3/T01-T02）
 *
 * 在 handleCreate / handleCreateV2 等创建流程前面拦一道，强制收集：
 *   - 大屏名称（带默认值，可改）
 *   - 大屏密级（必填，无默认）
 *
 * 提交后通过 onSubmit 透传给 caller，让 caller 决定走 v2 直接创建还是
 * 跳到模板选择 / 设计器。配合后端 normalizeRequiredClassification（F3/T03）
 * 形成前后端双重防线，从源头消除 classification=null 的大屏。
 *
 * 设计取舍：
 * - **不给 classification 默认值**：避免用户盲点确认。INTERNAL 听上去是
 *   "安全的中间值"，但盲选会导致大量真 SECRET 数据被误标 INTERNAL。
 * - **保留 placeholder「请选择密级」红字**：视觉强调必选。
 * - **submit 按钮 disabled 直到密级选完**：行为校验，前端不让漏。
 */
export interface CreateScreenIntakePayload {
	name: string;
	classification: "PUBLIC" | "INTERNAL" | "SECRET" | "CONFIDENTIAL";
	domainId?: string;
	file?: File;
}

export interface CreateScreenIntakeModalProps {
	open: boolean;
	mode?: "create" | "import";
	defaultName?: string;
	defaultDomainId?: string;
	domainOptions?: Array<{ label: string; value: string }>;
	/** 弹窗标题，默认「新建大屏」；导入流程可改成「导入大屏」。 */
	title?: string;
	/** 标题下方说明文案；用于解释当前 intake 的来源（例：JSON 缺密级）。 */
	description?: string;
	/** 提交标签，默认「下一步」；handleCreateV2 这种直接创建可改成「创建」 */
	okText?: string;
	onCancel: () => void;
	onSubmit: (payload: CreateScreenIntakePayload) => void | Promise<void>;
}

const LEVEL_OPTIONS: Array<{ label: string; value: CreateScreenIntakePayload["classification"] }> = [
	{ label: "公开 (PUBLIC) — 全体登录用户可见", value: "PUBLIC" },
	{ label: "内部 (INTERNAL) — 内部员工可见", value: "INTERNAL" },
	{ label: "秘密 (SECRET) — 持秘密及以上人员密级可见", value: "SECRET" },
	{ label: "机密 (CONFIDENTIAL) — 持机密人员密级可见", value: "CONFIDENTIAL" },
];

const UNASSIGNED_DOMAIN_KEY = "__UNASSIGNED__";

export function CreateScreenIntakeModal({
	open,
	mode = "create",
	defaultName = "",
	defaultDomainId,
	domainOptions = [],
	title = "新建大屏",
	description,
	okText = "下一步",
	onCancel,
	onSubmit,
}: CreateScreenIntakeModalProps) {
	const [name, setName] = useState(defaultName);
	const [classification, setClassification] = useState<CreateScreenIntakePayload["classification"] | undefined>(
		undefined,
	);
	const [domainId, setDomainId] = useState<string | undefined>(defaultDomainId);
	const [file, setFile] = useState<File | undefined>(undefined);
	const [submitting, setSubmitting] = useState(false);

	// 每次打开时复位 — 上次的密级选择不在新建间持续，避免盲点确认。
	useEffect(() => {
		if (open) {
			setName(defaultName);
			setClassification(undefined);
			setDomainId(defaultDomainId);
			setFile(undefined);
			setSubmitting(false);
		}
	}, [open, defaultName, defaultDomainId]);

	const trimmedName = useMemo(() => name.trim(), [name]);
	const domainSelectOptions = useMemo(
		() => [{ label: "未归类", value: UNASSIGNED_DOMAIN_KEY }, ...domainOptions],
		[domainOptions],
	);
	const okDisabled = !trimmedName || !classification || (mode === "import" && !file) || submitting;

	const handleOk = async () => {
		if (okDisabled || !classification) return;
		setSubmitting(true);
		try {
			await onSubmit({
				name: trimmedName,
				classification,
				domainId,
				...(file ? { file } : {}),
			});
		} finally {
			setSubmitting(false);
		}
	};

	return (
		<Modal
			open={open}
			title={title}
			width={460}
			onCancel={onCancel}
			onOk={handleOk}
			okText={okText}
			cancelText="取消"
			okButtonProps={{ disabled: okDisabled, loading: submitting }}
			destroyOnClose
		>
			<div style={{ display: "flex", flexDirection: "column", gap: 14, paddingTop: 4 }}>
				{description && (
					<div style={{ fontSize: 13, color: "var(--color-text-secondary, #6b7280)", lineHeight: 1.5 }}>
						{description}
					</div>
				)}
				<div>
					<div style={{ fontSize: 13, fontWeight: 500, marginBottom: 6 }}>
						大屏名称 <span style={{ color: "#ff4d4f" }}>*</span>
					</div>
					<Input
						value={name}
						onChange={(e) => setName(e.target.value)}
						placeholder="例如：项目综合看板"
						maxLength={120}
					/>
				</div>
				<div>
					<div style={{ fontSize: 13, fontWeight: 500, marginBottom: 6 }}>数据域</div>
					<Select
						value={domainId || UNASSIGNED_DOMAIN_KEY}
						onChange={(v) => setDomainId(v === UNASSIGNED_DOMAIN_KEY ? undefined : String(v))}
						placeholder="请选择数据域"
						style={{ width: "100%" }}
						options={domainSelectOptions}
					/>
					<div style={{ fontSize: 12, color: "var(--color-text-tertiary, #6b7280)", marginTop: 6 }}>
						数据域来自主题域管理；不选择时归入未归类。
					</div>
				</div>
				{mode === "import" && (
					<div>
						<div style={{ fontSize: 13, fontWeight: 500, marginBottom: 6 }}>
							导入文件 <span style={{ color: "#ff4d4f" }}>*</span>
						</div>
						<Input
							type="file"
							accept="application/json, application/zip, .json, .zip"
							onChange={(e) => setFile(e.target.files?.[0])}
							status={file ? undefined : "warning"}
						/>
						<div style={{ fontSize: 12, color: "var(--color-text-tertiary, #6b7280)", marginTop: 6 }}>
							{file ? `已选择：${file.name}` : "请选择大屏包 (.zip) 或旧版 JSON 文件"}
						</div>
					</div>
				)}
				<div>
					<div style={{ fontSize: 13, fontWeight: 500, marginBottom: 6 }}>
						大屏密级 <span style={{ color: "#ff4d4f" }}>*</span>
					</div>
					<Select
						value={classification}
						onChange={(v) => setClassification(v as CreateScreenIntakePayload["classification"])}
						placeholder="请选择密级"
						style={{ width: "100%" }}
						options={LEVEL_OPTIONS}
						status={classification ? undefined : "warning"}
					/>
					<div style={{ fontSize: 12, color: "var(--color-text-tertiary, #6b7280)", marginTop: 6 }}>
						密级必填：决定哪些人员可访问本大屏；可在编辑器属性面板随时调整。
					</div>
				</div>
			</div>
		</Modal>
	);
}
