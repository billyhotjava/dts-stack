import assert from "node:assert/strict";
import test from "node:test";
import {
    resolveSourceColumnsKey,
    resolveSourceColumnsMeta,
    shouldPersistSourceColumns,
} from "./sourceColumns.ts";
import type { CardData } from "../../types";

test("resolveSourceColumnsMeta normalizes CardData column metadata", () => {
    const cardData: CardData = {
        cols: [
            { name: "amount", display_name: "金额", base_type: "type/Float" },
            { name: "owner", display_name: "", base_type: "type/Text" },
        ],
        rows: [],
    };

    assert.deepEqual(resolveSourceColumnsMeta(cardData), [
        { name: "amount", displayName: "金额", baseType: "type/Float" },
        { name: "owner", displayName: "owner", baseType: "type/Text" },
    ]);
});

test("shouldPersistSourceColumns compares stable column keys", () => {
    const next = [
        { name: "amount", displayName: "金额", baseType: "type/Float" },
        { name: "owner", displayName: "owner", baseType: "type/Text" },
    ];

    assert.equal(resolveSourceColumnsKey([{ name: " amount " }, { name: "owner" }]), "amount,owner");
    assert.equal(shouldPersistSourceColumns([{ name: "amount" }, { name: "owner" }], next), false);
    assert.equal(shouldPersistSourceColumns([{ name: "amount" }], next), true);
    assert.equal(shouldPersistSourceColumns([], []), false);
});
