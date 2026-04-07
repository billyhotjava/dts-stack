/**
 * Custom Monaco language definition for dbt SQL files.
 *
 * Extends standard SQL tokenization with Jinja2 template syntax used by dbt:
 *   - {{ expression }}   — variable interpolation / macros
 *   - {% statement %}    — block tags (if, for, set, macro, …)
 *   - {# comment #}      — template comments
 *
 * Also recognises dbt-specific macros: ref(), source(), config(), var(), etc.
 */
import type { languages, editor } from "monaco-editor";

export const DBT_SQL_LANGUAGE_ID = "dbt-sql";

// ── Language registration metadata ────────────────────────────

export const languageDefinition: languages.ILanguageExtensionPoint = {
	id: DBT_SQL_LANGUAGE_ID,
	extensions: [".sql"],
	aliases: ["dbt SQL", "dbt", "Jinja SQL"],
};

// ── Monarch tokenizer ─────────────────────────────────────────

export const monarchTokensProvider: languages.IMonarchLanguage = {
	defaultToken: "",
	ignoreCase: true,
	tokenPostfix: ".dbt-sql",

	brackets: [
		{ open: "(", close: ")", token: "delimiter.parenthesis" },
		{ open: "[", close: "]", token: "delimiter.square" },
		{ open: "{{", close: "}}", token: "delimiter.jinja.expression" },
		{ open: "{%", close: "%}", token: "delimiter.jinja.block" },
		{ open: "{#", close: "#}", token: "delimiter.jinja.comment" },
	],

	// dbt / Jinja built-in macros & functions
	dbtMacros: [
		"ref",
		"source",
		"config",
		"var",
		"env_var",
		"log",
		"return",
		"exceptions",
		"adapter",
		"run_query",
		"statement",
		"this",
		"target",
		"project_name",
		"schema",
		"database",
		"is_incremental",
		"generate_schema_name",
		"generate_database_name",
	],

	// Jinja keywords (inside {% %} blocks)
	jinjaKeywords: [
		"if",
		"elif",
		"else",
		"endif",
		"for",
		"endfor",
		"in",
		"not",
		"and",
		"or",
		"is",
		"block",
		"endblock",
		"extends",
		"include",
		"import",
		"from",
		"macro",
		"endmacro",
		"call",
		"endcall",
		"filter",
		"endfilter",
		"set",
		"endset",
		"raw",
		"endraw",
		"with",
		"endwith",
		"do",
		"true",
		"false",
		"none",
		"True",
		"False",
		"None",
	],

	// SQL keywords
	keywords: [
		"SELECT",
		"FROM",
		"WHERE",
		"AND",
		"OR",
		"NOT",
		"IN",
		"IS",
		"NULL",
		"AS",
		"ON",
		"JOIN",
		"LEFT",
		"RIGHT",
		"INNER",
		"OUTER",
		"FULL",
		"CROSS",
		"UNION",
		"ALL",
		"INSERT",
		"INTO",
		"VALUES",
		"UPDATE",
		"SET",
		"DELETE",
		"CREATE",
		"ALTER",
		"DROP",
		"TABLE",
		"VIEW",
		"INDEX",
		"DATABASE",
		"SCHEMA",
		"IF",
		"EXISTS",
		"REPLACE",
		"TEMPORARY",
		"TEMP",
		"WITH",
		"RECURSIVE",
		"GROUP",
		"BY",
		"ORDER",
		"ASC",
		"DESC",
		"HAVING",
		"LIMIT",
		"OFFSET",
		"DISTINCT",
		"CASE",
		"WHEN",
		"THEN",
		"ELSE",
		"END",
		"CAST",
		"BETWEEN",
		"LIKE",
		"ILIKE",
		"ESCAPE",
		"OVER",
		"PARTITION",
		"ROWS",
		"RANGE",
		"UNBOUNDED",
		"PRECEDING",
		"FOLLOWING",
		"CURRENT",
		"ROW",
		"WINDOW",
		"EXCEPT",
		"INTERSECT",
		"LATERAL",
		"NATURAL",
		"USING",
		"MATERIALIZED",
		"GRANT",
		"REVOKE",
		"PRIMARY",
		"KEY",
		"FOREIGN",
		"REFERENCES",
		"CONSTRAINT",
		"UNIQUE",
		"CHECK",
		"DEFAULT",
		"CASCADE",
		"RESTRICT",
		"TRUNCATE",
		"COMMENT",
		"EXPLAIN",
		"ANALYZE",
		"BEGIN",
		"COMMIT",
		"ROLLBACK",
		"TRANSACTION",
		"COALESCE",
		"NULLIF",
		"TRUE",
		"FALSE",
		"ANY",
		"SOME",
		"ARRAY",
		"UNNEST",
		"PIVOT",
		"UNPIVOT",
		"QUALIFY",
		"FETCH",
		"NEXT",
		"FIRST",
		"ONLY",
	],

	// SQL types
	typeKeywords: [
		"INT",
		"INTEGER",
		"BIGINT",
		"SMALLINT",
		"TINYINT",
		"FLOAT",
		"DOUBLE",
		"DECIMAL",
		"NUMERIC",
		"REAL",
		"VARCHAR",
		"CHAR",
		"CHARACTER",
		"NCHAR",
		"NVARCHAR",
		"TEXT",
		"STRING",
		"CLOB",
		"BLOB",
		"BINARY",
		"VARBINARY",
		"BOOLEAN",
		"BOOL",
		"DATE",
		"TIME",
		"TIMESTAMP",
		"DATETIME",
		"INTERVAL",
		"JSON",
		"JSONB",
		"UUID",
		"ARRAY",
		"MAP",
		"STRUCT",
		"ROW",
		"VARIANT",
		"OBJECT",
		"NUMBER",
		"BYTES",
	],

	// SQL operators
	operators: [
		"=",
		">",
		"<",
		"!",
		"~",
		"?",
		":",
		"==",
		"<=",
		">=",
		"!=",
		"<>",
		"&&",
		"||",
		"+",
		"-",
		"*",
		"/",
		"%",
		"&",
		"|",
		"^",
		">>",
		"<<",
		"=>",
		"->",
		"->>",
		"::!",
		"::",
	],

	// The main tokenizer
	tokenizer: {
		root: [
			// ── Jinja comment {# ... #} ──
			[/\{#/, "comment.jinja", "@jinjaComment"],

			// ── Jinja expression {{ ... }} ──
			[/\{\{-?/, "delimiter.jinja.expression", "@jinjaExpression"],

			// ── Jinja block {% ... %} ──
			[/\{%-?/, "delimiter.jinja.block", "@jinjaBlock"],

			// ── SQL comments ──
			[/--.*$/, "comment.sql"],
			[/\/\*/, "comment.sql", "@sqlBlockComment"],

			// ── SQL strings ──
			[/'/, "string.sql", "@sqlString"],

			// ── SQL numbers ──
			[/\d+(\.\d+)?([eE][-+]?\d+)?/, "number.sql"],

			// ── SQL identifiers / keywords ──
			[
				/[a-zA-Z_]\w*/,
				{
					cases: {
						"@keywords": "keyword.sql",
						"@typeKeywords": "type.sql",
						"@default": "identifier.sql",
					},
				},
			],

			// ── SQL operators ──
			[/[<>=!~?&|+\-*/^%]+/, "operator.sql"],

			// ── Delimiters ──
			[/[;,.]/, "delimiter.sql"],
			[/[()]/, "delimiter.parenthesis"],
			[/[\[\]]/, "delimiter.square"],

			// ── Whitespace ──
			{ include: "@whitespace" },
		],

		// ── Jinja comment state ──
		jinjaComment: [
			[/[^#}]+/, "comment.jinja"],
			[/#\}/, "comment.jinja", "@pop"],
			[/./, "comment.jinja"],
		],

		// ── Jinja expression state {{ ... }} ──
		jinjaExpression: [
			[/-?\}\}/, "delimiter.jinja.expression", "@pop"],
			{ include: "@jinjaCommon" },
		],

		// ── Jinja block state {% ... %} ──
		jinjaBlock: [
			[/-?%\}/, "delimiter.jinja.block", "@pop"],
			{ include: "@jinjaCommon" },
		],

		// ── Common Jinja tokens ──
		jinjaCommon: [
			// Nested strings
			[/"/, "string.jinja", "@jinjaDoubleString"],
			[/'/, "string.jinja", "@jinjaSingleString"],
			// Numbers
			[/\d+(\.\d+)?/, "number.jinja"],
			// dbt macros — e.g. ref(…), source(…)
			[
				/[a-zA-Z_]\w*/,
				{
					cases: {
						"@dbtMacros": "variable.dbt",
						"@jinjaKeywords": "keyword.jinja",
						"@default": "variable.jinja",
					},
				},
			],
			// Operators & punctuation
			[/[=<>!~+\-*/%|]+/, "operator.jinja"],
			[/[(),.\[\]:]/, "delimiter.jinja"],
			// Whitespace
			{ include: "@whitespace" },
		],

		jinjaDoubleString: [
			[/[^"\\]+/, "string.jinja"],
			[/\\./, "string.escape.jinja"],
			[/"/, "string.jinja", "@pop"],
		],

		jinjaSingleString: [
			[/[^'\\]+/, "string.jinja"],
			[/\\./, "string.escape.jinja"],
			[/'/, "string.jinja", "@pop"],
		],

		sqlBlockComment: [
			[/[^/*]+/, "comment.sql"],
			[/\*\//, "comment.sql", "@pop"],
			[/./, "comment.sql"],
		],

		sqlString: [
			[/[^']+/, "string.sql"],
			[/''/, "string.sql"],
			[/'/, "string.sql", "@pop"],
		],

		whitespace: [[/\s+/, "white"]],
	},
};

// ── Theme rules ───────────────────────────────────────────────

export const dbtThemeRules: editor.ITokenThemeRule[] = [
	// Jinja delimiters — distinctive orange
	{ token: "delimiter.jinja.expression", foreground: "E06C20", fontStyle: "bold" },
	{ token: "delimiter.jinja.block", foreground: "AF5F00", fontStyle: "bold" },
	// Jinja comment
	{ token: "comment.jinja", foreground: "8A8A8A", fontStyle: "italic" },
	// Jinja keywords (if/for/set/macro …)
	{ token: "keyword.jinja", foreground: "AF5F00", fontStyle: "bold" },
	// dbt macros — ref(), source(), config() …
	{ token: "variable.dbt", foreground: "D1580C", fontStyle: "bold" },
	// Jinja variables
	{ token: "variable.jinja", foreground: "C75E1A" },
	// Jinja strings
	{ token: "string.jinja", foreground: "22863A" },
	{ token: "string.escape.jinja", foreground: "1B7A2F" },
	// Jinja operators / delimiters
	{ token: "operator.jinja", foreground: "995522" },
	{ token: "delimiter.jinja", foreground: "995522" },
	// Jinja numbers
	{ token: "number.jinja", foreground: "1976D2" },
	// SQL tokens
	{ token: "keyword.sql", foreground: "0550AE", fontStyle: "bold" },
	{ token: "type.sql", foreground: "8250DF" },
	{ token: "string.sql", foreground: "22863A" },
	{ token: "number.sql", foreground: "1976D2" },
	{ token: "comment.sql", foreground: "8A8A8A", fontStyle: "italic" },
	{ token: "operator.sql", foreground: "555555" },
	{ token: "identifier.sql", foreground: "24292F" },
	{ token: "delimiter.sql", foreground: "777777" },
];

// ── Registration helper ───────────────────────────────────────

let registered = false;

/**
 * Call this inside `beforeMount` of @monaco-editor/react to register the
 * dbt-sql language and theme before the editor initialises.
 */
export function registerDbtLanguage(monaco: typeof import("monaco-editor")) {
	if (registered) return;
	registered = true;

	monaco.languages.register(languageDefinition);
	monaco.languages.setMonarchTokensProvider(DBT_SQL_LANGUAGE_ID, monarchTokensProvider);

	// Extend the default VS theme with dbt-specific rules
	monaco.editor.defineTheme("dbt-light", {
		base: "vs",
		inherit: true,
		rules: dbtThemeRules,
		colors: {},
	});

	// Auto-close Jinja delimiters
	monaco.languages.setLanguageConfiguration(DBT_SQL_LANGUAGE_ID, {
		comments: {
			lineComment: "--",
			blockComment: ["{#", "#}"],
		},
		brackets: [
			["(", ")"],
			["[", "]"],
			["{{", "}}"],
			["{%", "%}"],
			["{#", "#}"],
		],
		autoClosingPairs: [
			{ open: "(", close: ")" },
			{ open: "[", close: "]" },
			{ open: "'", close: "'", notIn: ["string"] },
			{ open: '"', close: '"', notIn: ["string"] },
			{ open: "{{", close: "}}" },
			{ open: "{%", close: "%}" },
			{ open: "{#", close: "#}" },
		],
		surroundingPairs: [
			{ open: "(", close: ")" },
			{ open: "[", close: "]" },
			{ open: "'", close: "'" },
			{ open: '"', close: '"' },
			{ open: "{{", close: "}}" },
			{ open: "{%", close: "%}" },
		],
	});
}
