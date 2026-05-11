import assert from "node:assert/strict";
import test from "node:test";
import { readFile } from "node:fs/promises";

const propertyPanelDir = new URL("./", import.meta.url);

const sectionFiles = [
	"PropertyPanel.tsx",
	"PositionSizeSection.tsx",
	"ComponentAppearanceSection.tsx",
	"FieldMappingSection.tsx",
	"AnimationConfigSection.tsx",
	"ComponentConfigSection.tsx",
	"ExplainConfigSection.tsx",
	"OtherConfigSection.tsx",
	"ScreenJumpPicker.tsx",
];

test("PropertyPanel section chrome uses icon components instead of text arrows", async () => {
	const sources = await Promise.all(sectionFiles.map((name) => readFile(new URL(name, propertyPanelDir), "utf8")));
	const combined = sources.join("\n");

	assert.match(combined, /SectionToggle/);
	assert.match(combined, /ChevronDown/);
	assert.equal(combined.includes("▸"), false);
	assert.equal(combined.includes("▾"), false);
	assert.equal(combined.includes("▲"), false);
	assert.equal(combined.includes("▼"), false);
});
