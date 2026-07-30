import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const read = (path: string) => readFileSync(new URL(path, import.meta.url), "utf8");

test("model detail exposes guarded reclassification and legacy implementation migration", () => {
	const page = [
		read("./ModelSpecDetailPage.tsx"),
		read("./components/ModelSpecDetailHeader.tsx"),
		read("./components/ModelSpecDetailNotices.tsx"),
	].join("\n");
	const api = read("../../api/modelSpecApi.ts");
	const reclassification = read("./components/ModelSpecReclassificationWizard.tsx");
	const migration = read("./components/ModelSpecImplementationMigrationPanel.tsx");

	assert.match(page, /ModelSpecReclassificationWizard/);
	assert.match(page, /ModelSpecImplementationMigrationPanel/);
	assert.match(reclassification, /调整模型类型/);
	assert.match(reclassification, /逐项确认将清理的旧类型内容/);
	assert.match(migration, /已有数据实现优先，不会自动覆盖/);
	assert.match(api, /reclassify-preview/);
	assert.match(api, /implementation-migrations\/dry-run/);
	assert.match(api, /implementation-migrations\/apply/);
	assert.match(api, /implementation-migrations\/rollback/);
	assert.match(api, /"If-Match": toModelSpecEtag\(expected\)/);
});
