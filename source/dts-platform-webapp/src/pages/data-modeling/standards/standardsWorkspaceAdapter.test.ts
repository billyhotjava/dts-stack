// @vitest-environment jsdom

import { beforeEach, describe, expect, it, vi } from "vitest";
import { updateReferenceCode } from "@/api/modelingStandardsApi";
import {
	adaptStandardsCatalog,
	type StandardsCatalogPayload,
	saveStandardsRow,
	standardsCapabilityFor,
} from "./standardsWorkspaceAdapter";

vi.mock("@/api/modelingStandardsApi", async (importOriginal) => {
	const actual = await importOriginal<typeof import("@/api/modelingStandardsApi")>();
	return { ...actual, updateReferenceCode: vi.fn() };
});

beforeEach(() => {
	vi.mocked(updateReferenceCode).mockReset();
});

describe("standards workspace adapter", () => {
	it("maps governed data standards without inventing demo records", () => {
		const payload: StandardsCatalogPayload = {
			content: [
				{
					id: "std-1",
					code: "CUSTOMER_ID",
					name: "客户标识",
					dataType: "VARCHAR",
					description: "客户稳定业务标识",
					currentVersion: "v3",
					status: "ACTIVE",
				},
			],
			total: 1,
		};

		expect(adaptStandardsCatalog("fields", payload)).toMatchObject({
			total: 1,
			rows: [
				{
					id: "std-1",
					code: "CUSTOMER_ID",
					name: "客户标识",
					dataType: "VARCHAR",
					definition: "客户稳定业务标识",
					version: "v3",
					state: "已生效",
				},
			],
		});
	});

	it("keeps reference-code business catalog and standard level independent across edits", async () => {
		const catalog = adaptStandardsCatalog("codes", {
			content: [
				{
					codeTypeId: "code-id",
					codeTypeCode: "ORDER_STATUS",
					codeTypeName: "订单状态",
					itemCount: 4,
					bizCatalog: "交易域",
					stdLevel: "企业级",
					version: "v2",
					status: 1,
				},
			],
		});
		expect(catalog).toMatchObject({
			rows: [
				{
					id: "code-id",
					code: "ORDER_STATUS",
					domain: "交易域",
					scope: "企业级",
					state: "已发布",
				},
			],
		});

		const row = catalog.rows[0];
		await saveStandardsRow(
			"codes",
			{
				code: row.code,
				name: "订单状态（修订）",
				domain: row.domain,
				scope: row.scope,
				version: row.version,
			},
			row,
		);
		expect(updateReferenceCode).toHaveBeenCalledWith(
			"code-id",
			expect.objectContaining({ bizCatalog: "交易域", stdLevel: "企业级" }),
		);
	});

	it("maps metadata-reference owners to their real identifiers", () => {
		expect(
			adaptStandardsCatalog("mappings", {
				content: [
					{
						id: "element-id",
						fieldNameEn: "order_status",
						fieldNameCn: "订单状态",
						dataType: "VARCHAR",
						codeSet: "ORDER_STATUS",
						version: 7,
					},
				],
			}),
		).toMatchObject({
			rows: [{ id: "element-id", code: "order_status", standard: "ORDER_STATUS", version: "v7" }],
		});
	});

	it("keeps unsupported root semantics explicit instead of disguising glossary terms", () => {
		expect(standardsCapabilityFor("roots")).toMatchObject({
			list: false,
			create: false,
			archive: false,
		});
		expect(standardsCapabilityFor("roots").disabledReason).toContain("独立词根契约");
	});
});
