import { Form } from "antd";
import { renderToStaticMarkup } from "react-dom/server";
import { MemoryRouter } from "react-router";
import { describe, expect, it, vi } from "vitest";
import { AccessPlanReviewStep } from "./AccessPlanReviewStep";
import { ApiAccessStep } from "./ApiAccessStep";
import { DatabaseAccessStep } from "./DatabaseAccessStep";
import { FileAccessStep } from "./FileAccessStep";
import { LandingScheduleStep } from "./LandingScheduleStep";

const source = {
	id: "source-1",
	name: "CRM 生产库",
	type: "mysql",
	jdbcUrl: "jdbc:mysql://secret-host:3306/crm",
	username: "root",
	status: "ACTIVE",
};

const target = {
	id: "target-1",
	name: "平台默认湖",
	type: "postgresql",
	jdbcUrl: "jdbc:postgresql://private-host:5432/lake",
	username: "lake_admin",
	recommended: true,
};

function renderInForm(node: (form: ReturnType<typeof Form.useForm>[0]) => React.ReactNode) {
	function Harness() {
		const [form] = Form.useForm();
		return <Form form={form}>{node(form)}</Form>;
	}
	return renderToStaticMarkup(
		<MemoryRouter>
			<Harness />
		</MemoryRouter>,
	);
}

describe("access plan steps", () => {
	it("keeps database credentials out of the resource step", () => {
		const html = renderInForm((form) => (
			<DatabaseAccessStep
				form={form}
				dataSources={[source]}
				discoveredTables={[{ schema: "crm", name: "customer", type: "TABLE" }]}
				discovering={false}
				discoverError=""
				onDiscover={vi.fn()}
				onDiscoveryInputChange={vi.fn()}
			/>
		));

		expect(html).toContain("发现源表");
		expect(html).toContain("crm.customer");
		expect(html).not.toContain("secret-host");
		expect(html).not.toContain("root");
	});

	it("renders typed API paging controls without a raw JSON editor", () => {
		const html = renderInForm((form) => (
			<ApiAccessStep
				form={form}
				dataSources={[{ ...source, type: "api", connectorCategory: "API" }]}
				preview={null}
				previewing={false}
				onPreview={vi.fn()}
				onPreviewInputChange={vi.fn()}
			/>
		));

		expect(html).toContain("资源路径");
		expect(html).toContain("分页参数");
		expect(html).toContain("增量游标");
		expect(html).not.toContain("JSON");
	});

	it("shows file classification before the managed upload action", () => {
		const html = renderInForm((form) => (
			<FileAccessStep
				form={form}
				fileUploadResult={null}
				onFileUploadResultChange={vi.fn()}
				uploading={false}
				userClassificationRank={2}
				onUpload={vi.fn()}
			/>
		));

		expect(html.indexOf("文件密级")).toBeLessThan(html.indexOf("选择并上传文件"));
		expect(html).toContain("先声明文件密级");
		expect(html).toContain("支持 CSV/XLSX，最大 50MB");
		expect(html).not.toContain("当前用户密级未下发");
	});

	it("fails closed when the current user classification rank is unavailable", () => {
		const html = renderInForm((form) => (
			<FileAccessStep
				form={form}
				fileUploadResult={null}
				onFileUploadResultChange={vi.fn()}
				uploading={false}
				onUpload={vi.fn()}
			/>
		));

		expect(html).toContain("当前用户密级未下发，已禁止文件上传");
	});

	it("shows only the platform destination summary and scheduling policy", () => {
		const html = renderInForm((form) => (
			<LandingScheduleStep
				form={form}
				kind="database"
				targetDataSources={[target]}
				defaultDestination={{
					available: true,
					writerTypeReady: true,
					writerConfigReady: true,
					destinationName: "平台默认湖",
					writerType: "postgresqlwriter",
				}}
			/>
		));

		expect(html).toContain("平台默认湖");
		expect(html).toContain("postgresqlwriter");
		expect(html).toContain("保存后配置立即生效");
		expect(html).not.toContain("保存后立即运行");
		expect(html).not.toContain("private-host");
		expect(html).not.toContain("lake_admin");
	});

	it("reviews real task settings without fabricated approval or revision state", () => {
		const html = renderToStaticMarkup(
			<AccessPlanReviewStep
				kind="api"
				values={{
					name: "订单 API",
					sourceDataSourceId: "source-1",
					targetDataSourceId: "target-1",
					tableSelectionMode: "manual",
					selectedTables: [],
					syncMode: "full_refresh",
					scheduleType: "manual",
					airflowEnabled: true,
					runNow: false,
					apiMethod: "GET",
					apiResourcePath: "/orders",
					fileClassification: "INTERNAL",
					fileAutoId: true,
				}}
				sourceName="CRM API"
				targetName="平台默认湖"
				fileUploadResult={null}
				apiPreview={null}
			/>,
		);

		expect(html).toContain("订单 API");
		expect(html).toContain("/orders");
		expect(html).toContain("不会立即执行");
		expect(html).toContain("保存后立即生效，可按调度策略运行");
		expect(html).not.toContain("密级准入");
		expect(html).not.toContain("立即运行");
		expect(html).not.toContain("审批");
		expect(html).not.toContain("Revision");
		expect(html).not.toContain("质量通过");
	});

	it("presents file quality detection as optional and activates the saved plan immediately", () => {
		const html = renderToStaticMarkup(
			<AccessPlanReviewStep
				kind="file"
				values={{
					name: "预算文件入湖",
					targetDataSourceId: "target-1",
					tableSelectionMode: "manual",
					selectedTables: [],
					syncMode: "full_refresh",
					scheduleType: "manual",
					airflowEnabled: true,
					runNow: false,
					apiMethod: "GET",
					fileClassification: "INTERNAL",
					fileAutoId: true,
				}}
				targetName="平台默认湖"
				fileUploadResult={{
					fileId: "file-1",
					fileType: "csv",
					originalName: "budget.csv",
					columns: [{ name: "amount", type: "string" }],
					classification: "INTERNAL",
				}}
				apiPreview={null}
			/>,
		);

		expect(html).toContain("保存后立即生效");
		expect(html).toContain("质量检测为可选项");
		expect(html).not.toContain("预检通过即自动生效");
	});
});
