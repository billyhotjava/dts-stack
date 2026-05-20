import assert from "node:assert/strict";
import test from "node:test";
import { readFile } from "node:fs/promises";

const propertyPanelPath = new URL("./PropertyPanel.tsx", import.meta.url);
const appearanceSectionPath = new URL("./ComponentAppearanceSection.tsx", import.meta.url);

test("PropertyPanel delegates component appearance config to the extracted module", async () => {
	const [propertyPanelSource, appearanceSource] = await Promise.all([
		readFile(propertyPanelPath, "utf8"),
		readFile(appearanceSectionPath, "utf8"),
	]);

	assert.match(propertyPanelSource, /from '\.\/ComponentAppearanceSection'/);
	assert.equal(propertyPanelSource.includes("componentBgOpacity"), false);
	assert.equal(propertyPanelSource.includes("componentBorderRadius"), false);

	assert.match(appearanceSource, /export function renderComponentAppearanceConfig/);
	assert.match(appearanceSource, /componentBgOpacity/);
	assert.match(appearanceSource, /componentBorderRadius/);
	assert.match(appearanceSource, /componentPadding/);
	assert.match(appearanceSource, /placeholder="transparent"/);
	assert.equal(appearanceSource.includes("onChange={(e) => handleConfigChange('componentBgColor'"), false);
});
