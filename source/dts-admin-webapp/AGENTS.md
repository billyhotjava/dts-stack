# Repository Guidelines

## Project Structure & Module Organization
- Source lives in `src/`. `src/main.tsx` boots the Vite admin shell and wires routes from `src/routes` to views under `src/pages`.
- Feature code: `src/admin`. Shared primitives: `src/components`, `src/hooks`, `src/store`, `src/utils`.
- Tests are colocated beside subjects as `*.test.ts[x]`.
- Theme tokens & Tailwind helpers: `src/theme`.
- Assets (bundled): `src/assets`. Static files: `public/`. Production builds emit to `dist/`.

## Build, Test, and Development Commands
- Install deps: `pnpm install` (Node 20; sets up Lefthook).
- Run locally: `pnpm dev` (Vite + HMR).
- Type-check & build: `pnpm build` (runs `tsc --noEmit`, then bundles to `dist/`).
- Preview compiled app: `pnpm preview`.
- Lint/format preflight: `pnpm exec lefthook run pre-commit`.

## Coding Style & Naming Conventions
- Formatting is enforced by Biome; avoid manual reflows—commit hooks will fix style.
- Naming: React components `PascalCase` (e.g., `UserTable.tsx`); hooks/utilities `camelCase` (e.g., `useUserFilters.ts`); exported constants `SCREAMING_SNAKE_CASE`.
- Prefer imports via module boundaries (e.g., `import { Button } from "src/components"`), not deep feature paths.
- Extract shared Tailwind recipes into theme utilities rather than duplicating class strings.

## Testing Guidelines
- No dedicated runner yet; use `pnpm build` to surface TypeScript regressions.
- Colocate tests with implementations; prefer exercising real API clients or light-weight stubs under `src/tests` when backend contracts are unavailable.
- Until automation lands, document manual verification (browsers, flags) in PR descriptions.

## Commit & Pull Request Guidelines
- Use Conventional Commits (`feat:`, `fix:`, `chore:`), written in the imperative.
- PRs must link issues, describe changes, call out risk & mitigation, list commands run (e.g., `pnpm build`), and include screenshots/GIFs for visual changes.
- Ensure Lefthook passes locally; keep commits focused to speed up review.

## Security & Configuration Tips
- Centralize defaults in `src/global-config.ts` rather than scattering env constants.
- Adjust Tailwind tokens in `tailwind.config.ts`; formatting defaults live in `biome.json`.
