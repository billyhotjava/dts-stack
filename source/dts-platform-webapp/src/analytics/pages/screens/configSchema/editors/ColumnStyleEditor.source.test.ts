import assert from "node:assert/strict";
import test from "node:test";
import { readFile } from "node:fs/promises";

const columnStyleEditorPath = new URL("./ColumnStyleEditor.tsx", import.meta.url);

test("ColumnStyleEditor keeps legacy source/alias columns editable in schema mode", async () => {
  const source = await readFile(columnStyleEditorPath, "utf8");

  assert.match(source, /return col\.key \|\| col\.source/);
  assert.match(source, /return col\.label \|\| col\.alias/);
  assert.match(source, /source: key/);
  assert.match(source, /alias: label \|\| undefined/);
});

test("ColumnStyleEditor exposes expected table column capabilities", async () => {
  const source = await readFile(columnStyleEditorPath, "utf8");

  assert.match(source, /label="宽度\(px\)"/);
  assert.match(source, /widthUnit: 'px'/);
  assert.match(source, /checked=\{col\.sortable !== false\}/);
  assert.match(source, /label="自动换行"/);
  assert.match(source, /label="格式化"/);
});
