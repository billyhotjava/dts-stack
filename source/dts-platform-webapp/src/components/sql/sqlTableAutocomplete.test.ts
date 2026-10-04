import { describe, expect, it } from "vitest";
import { filterTableSuggestions, resolveTableAutocompleteContext } from "./sqlTableAutocomplete";

describe("SQL table autocomplete", () => {
	it.each(["select * from ", "SELECT * FROM pub", "select * join public.orders", "update ods."])(
		"recognizes a table context after %s",
		(sql) => {
			expect(resolveTableAutocompleteContext(sql, sql.length)).not.toBeNull();
		},
	);

	it("does not suggest tables outside a table expression", () => {
		expect(resolveTableAutocompleteContext("select customer_name", 20)).toBeNull();
	});

	it("filters current datasource tables by table or qualified name", () => {
		const tables = [
			{ schema: "public", name: "orders", type: "TABLE" },
			{ schema: "ods", name: "order_items", type: "TABLE" },
			{ schema: "public", name: "customers", type: "TABLE" },
		];
		expect(filterTableSuggestions(tables, "public.o").map((item) => item.name)).toEqual(["orders"]);
		expect(filterTableSuggestions(tables, "order").map((item) => item.name)).toEqual(["orders", "order_items"]);
	});
});
