import { describe, expect, it } from "vitest";
import { generateSelect, generateInsert, copyName } from "../sqlGenerators";

describe("generateSelect", () => {
  it("generates SELECT * with LIMIT 100 by default", () => {
    expect(generateSelect("public", "users", null)).toBe(
      "SELECT * FROM public.users LIMIT 100",
    );
  });

  it("uses provided columns when listed", () => {
    expect(generateSelect("public", "users", ["id", "name", "email"])).toBe(
      "SELECT id, name, email FROM public.users LIMIT 100",
    );
  });

  it("quotes identifiers containing spaces or special chars", () => {
    expect(generateSelect("public", "user list", null)).toBe(
      'SELECT * FROM public."user list" LIMIT 100',
    );
  });
});

describe("generateInsert", () => {
  it("generates INSERT template with named columns and ? placeholders", () => {
    expect(generateInsert("public", "users", ["id", "name", "email"])).toBe(
      "INSERT INTO public.users (id, name, email) VALUES (?, ?, ?)",
    );
  });

  it("returns empty string when columns is empty", () => {
    expect(generateInsert("public", "users", [])).toBe("");
  });
});

describe("copyName", () => {
  it("returns schema.table for table identifier", () => {
    expect(copyName("public", "users")).toBe("public.users");
  });
});
