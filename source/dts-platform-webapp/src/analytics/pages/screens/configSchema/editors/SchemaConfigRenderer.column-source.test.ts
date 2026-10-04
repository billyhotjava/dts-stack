import assert from "node:assert/strict";
import test from "node:test";

import { resolveColumnStyleSourceColumns } from "./columnStyleSourceColumns";

test("resolveColumnStyleSourceColumns prefers dynamic source columns", () => {
  const columns = resolveColumnStyleSourceColumns({
    _sourceColumns: [
      { name: "project_no", displayName: "项目编号", baseType: "text" },
      { name: "issue_name", displayName: "问题名称", baseType: "text" },
    ],
    header: ["旧项目编号"],
    data: [["PJ-001"]],
  });

  assert.deepEqual(columns, [
    { name: "project_no", displayName: "项目编号", baseType: "text" },
    { name: "issue_name", displayName: "问题名称", baseType: "text" },
  ]);
});

test("resolveColumnStyleSourceColumns derives fields from static table headers", () => {
  const columns = resolveColumnStyleSourceColumns({
    header: ["项目编号", "问题名称", "发生时间"],
    data: [["PJ-001", "焊缝缺陷", "2026-05-20"]],
  });

  assert.deepEqual(columns, [
    { name: "0", displayName: "项目编号", baseType: "text" },
    { name: "1", displayName: "问题名称", baseType: "text" },
    { name: "2", displayName: "发生时间", baseType: "date" },
  ]);
});

test("resolveColumnStyleSourceColumns derives unnamed static columns from row width", () => {
  const columns = resolveColumnStyleSourceColumns({
    data: [
      ["PJ-001", 12],
      ["PJ-002", 18, "超期"],
    ],
  });

  assert.deepEqual(columns, [
    { name: "0", displayName: "列1", baseType: "text" },
    { name: "1", displayName: "列2", baseType: "number" },
    { name: "2", displayName: "列3", baseType: "text" },
  ]);
});
