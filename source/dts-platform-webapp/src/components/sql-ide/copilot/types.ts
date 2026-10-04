/**
 * Copilot contract — F6/T27.
 *
 * Providers implement this interface to plug AI into the SQL IDE.
 * Current in-tree implementations: none (placeholder only).
 * Future candidates: ClaudeMessagesProvider, ManagedAgentProvider.
 *
 * See copilot/README.md for the integration guide.
 */

export type CopilotMode =
  | "nl2sql"       // Natural-language question → SQL
  | "explain"     // Explain what this SQL does in plain language
  | "optimize"   // Suggest optimizations for this SQL
  | "fix";       // Given an error message + SQL, suggest a fix

export interface CopilotRequest {
  mode: CopilotMode;
  sql?: string;           // current SQL (for explain/optimize/fix)
  naturalQuery?: string;  // user question (for nl2sql)
  schemaContext?: string; // compact schema description: "schema.table(col1 type, col2 type, ...)"
  errorMessage?: string;  // for fix mode
  datasourceId: string | null;
  engine: string;
}

export interface CopilotResponse {
  sql?: string;            // generated/optimized SQL (for nl2sql/optimize/fix)
  explanation?: string;    // prose explanation (for explain/optimize/fix)
  suggestions?: string[];  // additional hints
  confidence: number;      // 0..1
  modelVersion: string;    // e.g. "claude-sonnet-4-6"
}

export interface CopilotProvider {
  readonly name: string;
  readonly supportedModes: CopilotMode[];

  chat(req: CopilotRequest): Promise<CopilotResponse>;

  /** Optional streaming for providers that support it. */
  stream?(req: CopilotRequest): AsyncIterable<Partial<CopilotResponse>>;
}

/**
 * The CopilotProvider registry for dependency injection.
 * When AI lands, a concrete provider instance registers here and
 * CopilotSlot reads from it.
 */
export interface CopilotProviderRegistry {
  register(provider: CopilotProvider): void;
  unregister(name: string): void;
  default(): CopilotProvider | null;
}
