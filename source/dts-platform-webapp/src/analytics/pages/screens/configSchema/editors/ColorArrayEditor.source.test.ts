import assert from "node:assert/strict";
import test from "node:test";
import { readFile } from "node:fs/promises";

const colorArrayEditorPath = new URL("./ColorArrayEditor.tsx", import.meta.url);
const schemaConfigRendererPath = new URL("./SchemaConfigRenderer.tsx", import.meta.url);

test("ColorArrayEditor keeps color rows inside narrow property panels", async () => {
  const source = await readFile(colorArrayEditorPath, "utf8");

  assert.match(source, /gridTemplateColumns: '52px minmax\(0, 1fr\)'/);
  assert.match(source, /gridTemplateColumns: '28px minmax\(0, 1fr\) 24px'/);
  assert.match(source, /style=\{\{ width: '100%', minWidth: 0 \}\}/);
  assert.match(source, /style=\{\{ width: 24, minWidth: 24, padding: 0 \}\}/);
  assert.match(source, /key=\{`color-\$\{idx\}`\}/);
  assert.equal(source.includes("key={`${idx}-${color}`}"), false);
  assert.equal(source.includes("style={{ width: 100 }}"), false);
});

test("SchemaConfigRenderer allows nested editors to shrink", async () => {
  const source = await readFile(schemaConfigRendererPath, "utf8");

  assert.match(source, /style=\{\{ flex: 1, minWidth: 0 \}\}/);
});

test("SchemaConfigRenderer gives image URL fields full property-panel width", async () => {
  const source = await readFile(schemaConfigRendererPath, "utf8");

  assert.match(source, /fullWidth\?: boolean/);
  assert.match(source, /fullWidth=\{field\.type === 'image-url'\}/);
  assert.match(source, /style=\{\{ width: '100%', minWidth: 0 \}\}/);
  assert.match(source, /display: 'grid', gap: 6, marginBottom: 10/);
});
