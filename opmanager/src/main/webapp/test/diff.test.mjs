import assert from "node:assert/strict";
import { execFileSync } from "node:child_process";
import { mkdirSync, rmSync } from "node:fs";
import { dirname, resolve } from "node:path";
import test from "node:test";
import { fileURLToPath, pathToFileURL } from "node:url";

const here = dirname(fileURLToPath(import.meta.url));
const webappRoot = resolve(here, "..");
const outDir = resolve(webappRoot, ".tmp-test/diff");

async function loadDiffModule() {
  rmSync(outDir, { force: true, recursive: true });
  mkdirSync(outDir, { recursive: true });
  execFileSync(
    resolve(webappRoot, "node_modules/.bin/tsc"),
    [
      "--target",
      "ES2020",
      "--module",
      "NodeNext",
      "--moduleResolution",
      "NodeNext",
      "--outDir",
      outDir,
      "--skipLibCheck",
      "--strict",
      "src/diff.ts"
    ],
    { cwd: webappRoot, stdio: "pipe" }
  );
  return import(pathToFileURL(resolve(outDir, "diff.js")).href);
}

test("aligns inserted package lines against an empty local side", async () => {
  const { buildSideBySideDiff, collectDiffHunks } = await loadDiffModule();

  const result = buildSideBySideDiff(["A", "C"], ["A", "B", "C"]);

  assert.deepEqual(
    result.rows.map(row => [row.kind, row.leftLineNumber, row.leftText, row.rightLineNumber, row.rightText]),
    [
      ["equal", 1, "A", 1, "A"],
      ["inserted", null, "", 2, "B"],
      ["equal", 2, "C", 3, "C"]
    ]
  );
  assert.deepEqual(collectDiffHunks(result.rows), [{ index: 0, start: 1, end: 1, label: "差异 1" }]);
});

test("pairs changed lines as a modified row", async () => {
  const { buildSideBySideDiff } = await loadDiffModule();

  const result = buildSideBySideDiff(["PORT=18095", "MODE=prod"], ["PORT=18096", "MODE=prod"]);

  assert.equal(result.rows[0].kind, "modified");
  assert.equal(result.rows[0].leftLineNumber, 1);
  assert.equal(result.rows[0].rightLineNumber, 1);
  assert.equal(result.changedRows, 1);
});

test("falls back without quadratic alignment for very large comparisons", async () => {
  const { buildSideBySideDiff } = await loadDiffModule();
  const localLines = Array.from({ length: 900 }, (_, index) => `L${index}`);
  const packageLines = Array.from({ length: 900 }, (_, index) => `R${index}`);

  const result = buildSideBySideDiff(localLines, packageLines, { maxMatrixCells: 1000 });

  assert.equal(result.strategy, "row-by-row");
  assert.equal(result.rows.length, 900);
  assert.equal(result.changedRows, 900);
});
