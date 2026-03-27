import { Alert, Button, Checkbox, Form, Input, Modal, Select, Space, Switch } from "antd";
import type { ProjectSpace } from "../sqlModeling.types";
import { useRouter } from "@/routes/hooks";

export type OdsGenerateModalProps = {
	open: boolean;
	onClose: () => void;
	onSubmit: () => void;
	submitting: boolean;
	spaces: ProjectSpace[];
	sourcesLoading: boolean;
	odsSourceOptions: { label: string; value: string }[];
	odsSourceFilterOptions: { label: string; value: string }[];
	form: ReturnType<typeof Form.useForm>[0];
};

export default function OdsGenerateModal({
	open,
	onClose,
	onSubmit,
	submitting,
	spaces,
	sourcesLoading,
	odsSourceOptions,
	odsSourceFilterOptions,
	form,
}: OdsGenerateModalProps) {
	const router = useRouter();

	return (
		<Modal
			open={open}
			title="从 ODS 一键生成 DWD / DWS / ADS"
			onCancel={onClose}
			footer={
				<Space>
					<Button onClick={onClose}>取消</Button>
					<Button type="primary" onClick={onSubmit} loading={submitting}>
						开始生成
					</Button>
				</Space>
			}
		>
			<Form layout="vertical" form={form} disabled={submitting}>
				<div className="grid gap-4 md:grid-cols-2">
					<Form.Item name="planId" label="项目空间" rules={[{ required: true, message: "请选择项目空间" }]}>
						<Select
							placeholder="选择项目空间"
							options={spaces.map((space) => ({ label: space.name || "未命名", value: space.id }))}
						/>
					</Form.Item>
					<Form.Item name="sourceDataSourceId" label="来源数据源（可选）">
						<Select
							allowClear
							placeholder={sourcesLoading ? "加载可用来源..." : "按 ODS 映射自动识别"}
							options={odsSourceFilterOptions}
						/>
					</Form.Item>
				</div>
				<Form.Item
					name="mappingIds"
					label="选择 ODS 表"
					rules={[{ required: true, message: "请至少选择一个 ODS 表" }]}
				>
					<Select
						mode="multiple"
						showSearch
						optionFilterProp="label"
						placeholder={sourcesLoading ? "ODS 列表加载中..." : "选择一个或多个 ODS 表"}
						options={odsSourceOptions}
					/>
				</Form.Item>
				{!sourcesLoading && odsSourceOptions.length === 0 && (
					<Alert
						type="warning"
						showIcon
						message="未发现 ODS 映射"
						description={
							<div>
								请先在数据集成中完成 ODS 接入，然后回到本页刷新后选择映射。
								<Button type="link" size="small" onClick={() => router.push("/foundation/data-sources")}>
									去 ODS 接入
								</Button>
							</div>
						}
						className="mb-4"
					/>
				)}
				<div className="grid gap-4 md:grid-cols-2">
					<Form.Item name="schemaName" label="目标 Schema">
						<Input placeholder="可选，留空使用默认 schema" />
					</Form.Item>
					<Form.Item name="materialized" label="物化方式">
						<Select
							allowClear
							placeholder="默认 table"
							options={[
								{ label: "table", value: "table" },
								{ label: "view", value: "view" },
								{ label: "incremental", value: "incremental" },
							]}
						/>
					</Form.Item>
				</div>
				<div className="grid gap-4 md:grid-cols-2">
					<Form.Item name="status" label="状态">
						<Select
							allowClear
							placeholder="默认 DRAFT"
							options={[
								{ label: "草稿", value: "DRAFT" },
								{ label: "就绪", value: "READY" },
								{ label: "已发布", value: "PUBLISHED" },
							]}
						/>
					</Form.Item>
					<Form.Item name="enabled" label="启用" valuePropName="checked">
						<Switch />
					</Form.Item>
				</div>
				<div className="grid gap-4 md:grid-cols-2">
					<Form.Item name="tags" label="额外标签 (逗号分隔)">
						<Input placeholder="可选，如 finance,patent" />
					</Form.Item>
					<Form.Item name="ownerDept" label="归属部门">
						<Input placeholder="可选" />
					</Form.Item>
				</div>
				<Form.Item label="生成分层">
					<Space size={24}>
						<Form.Item name="createDwd" valuePropName="checked" noStyle>
							<Checkbox>DWD</Checkbox>
						</Form.Item>
						<Form.Item name="createDws" valuePropName="checked" noStyle>
							<Checkbox>DWS</Checkbox>
						</Form.Item>
						<Form.Item name="createAds" valuePropName="checked" noStyle>
							<Checkbox>ADS</Checkbox>
						</Form.Item>
					</Space>
				</Form.Item>
				<Form.Item name="overwriteExisting" valuePropName="checked">
					<Checkbox>已存在模型时覆盖更新</Checkbox>
				</Form.Item>
				<div className="rounded border border-border bg-muted/40 px-3 py-2 text-xs text-muted-foreground">
					<div className="font-medium text-foreground">生成说明</div>
					<ol className="mt-1 list-decimal pl-4">
						<li>先选择项目空间与 ODS 映射，至少选择 1 张 ODS 表。</li>
						<li>默认按映射自动生成 DWD / DWS / ADS 三层模型。</li>
						<li>建议先勾选 DWD，再按需勾选 DWS、ADS。</li>
					</ol>
				</div>
			</Form>
		</Modal>
	);
}
