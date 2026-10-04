# Repository Guidelines

## Project Structure & Module Organization
- Vite + React + TypeScript. Entry `src/main.tsx`; routes from `src/routes`; pages in `src/pages`.
- Admin features in `src/admin`. Shared UI/logic in `src/components`, `src/hooks`, `src/store` (Zustand), and `src/utils`.
- Design tokens in `src/theme` and `tailwind.config.ts`.
- Processed assets in `src/assets`; static files in `public/`.
- Centralize runtime defaults in `src/global-config.ts` (avoid scattered env fallbacks).

## Build, Test, and Development Commands
- `pnpm install` — install dependencies (Node 20.x required).
- `pnpm dev` — start Vite dev server with hot reload.
- `pnpm build` — run `tsc --noEmit` and create a production bundle in `dist/`.
- `pnpm preview` — serve the built bundle locally for QA.
- `pnpm exec lefthook run pre-commit` — reproduce formatting and lint checks.

## Coding Style & Naming Conventions
- Formatting: Biome via lefthook. Do not hand‑format or bypass hooks.
- Names: PascalCase components (`UserCard.tsx`), camelCase utilities/hooks (`useUserPermissions`), SCREAMING_SNAKE_CASE constants.
- Styling: Tailwind utilities; extract repeated patterns into helpers or theme tokens.
- Keep modules focused, typed, and colocated with usage when practical.

## Testing Guidelines
- Treat `pnpm build` as the baseline regression gate.
- Add colocated specs as `*.test.ts(x)` where logic warrants automation.
- Prefer exercising backend APIs directly; if stubbing is unavoidable, place lightweight fixtures next to consumers.
- Document manual verification steps in PRs until coverage expands.

## Commit & Pull Request Guidelines
- Use Conventional Commits (`feat:`, `fix:`, `chore:`) with imperative, user‑focused summaries.
- PRs should link an issue, outline risk, list verification commands (`pnpm build`, `pnpm preview`), and include screenshots/GIFs for UI changes.
- Ensure lefthook passes before review and limit scope to the touched feature.

## Security & Configuration Tips
- Keep secrets and API endpoints in environment files; access via `import.meta.env`.
- Update shared defaults exclusively in `src/global-config.ts` to align environments.
- Toggle the experimental SQL workbench UI via `VITE_ENABLE_SQL_WORKBENCH=true`; the flag gates the Monaco editor experience and new `/api/sql/*` endpoints.
