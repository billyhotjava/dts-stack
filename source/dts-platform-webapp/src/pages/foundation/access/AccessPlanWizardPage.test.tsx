import { renderToStaticMarkup } from "react-dom/server";
import { MemoryRouter } from "react-router";
import { describe, expect, it, vi } from "vitest";

import SOURCE from "./AccessPlanWizardPage.tsx?raw";

const useWizard = vi.hoisted(() => vi.fn());

vi.mock("./useAccessPlanWizard", () => ({ useAccessPlanWizard: useWizard }));

import AccessPlanWizardPage from "./AccessPlanWizardPage";

const wizardState = {
	dataSources: [],
	sourceDataSources: [{ id: "api-1", name: "CRM API", type: "api" }],
	targetDataSources: [{ id: "target-1", name: "平台默认湖", type: "postgresql" }],
	defaultDestination: {
		available: true,
		writerTypeReady: true,
		writerConfigReady: true,
		destinationName: "平台默认湖",
		writerType: "postgresqlwriter",
	},
	loading: false,
	error: "",
	editError: "",
	existingTask: { id: 7, name: "订单 API" },
	fileUploadResult: null,
	fileAdmission: { ready: false, reason: "请先上传文件" },
	discoveredTables: [],
	discoveringTables: false,
	discoverError: "",
	apiPreview: null,
	apiPreviewing: false,
	uploadingFile: false,
	saving: false,
	discoverTables: vi.fn(),
	previewApi: vi.fn(),
	uploadFile: vi.fn(),
	setFileUploadResult: vi.fn(),
	submit: vi.fn(),
};

function renderPage(entry: string) {
	useWizard.mockReturnValue(wizardState);
	return renderToStaticMarkup(
		<MemoryRouter initialEntries={[entry]}>
			<AccessPlanWizardPage />
		</MemoryRouter>,
	);
}

describe("AccessPlanWizardPage", () => {
	it("locks the three-step wizard to the query kind and forwards editId", async () => {
		const text = renderPage("/foundation/data-sources/access/new?kind=api&editId=7");

		expect(text).toContain("编辑 API 接入计划");
		expect(text).toContain("来源连接");
		expect(text).toContain("资源定义");
		expect(text).toContain("落地策略");
		expect(text).not.toContain("切换为数据库");
		expect(useWizard).toHaveBeenCalledWith(expect.objectContaining({ kind: "api", editId: 7 }));
	});

	it("acquires a synchronous submit lock before validation and reports a saved active plan", () => {
		expect(SOURCE).toMatch(
			/if \(!acquireSingleFlight\(submitLockRef\)\) return;\s*try \{\s*await validateCurrentStep\(\)/,
		);
		expect(SOURCE).toContain("接入计划已更新并生效");
		expect(SOURCE).toContain("接入计划已保存并生效");
		expect(SOURCE).not.toContain("文件接入计划已保存，请完成文件预检");
		expect(SOURCE).not.toContain('kind === "file" ? (editId ? "保存修改" : "保存计划")');
		expect(SOURCE).not.toContain("待准入草稿");
		expect(SOURCE).toMatch(/finally \{\s*releaseSingleFlight\(submitLockRef\)/);
	});
});
