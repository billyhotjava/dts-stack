# Fix Analytics Hash Routing & Hardcoded URLs Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix all broken navigation (window.open / window.location) and shared URL generation in the analytics module that bypass hash routing, causing redirects to /workbench instead of the intended page.

**Architecture:** The platform-webapp uses `createHashRouter` (VITE_APP_ROUTER_HISTORY=hash). All client-side routes must include `/#/` prefix when used outside React Router (i.e. in window.open, window.location, or clipboard URLs). We'll create a single utility module `resolveAnalyticsUrl.ts` with two helpers, then update all 15+ call sites to use them.

**Tech Stack:** React 19 + React Router 7 + TypeScript + Vite

---

## File Structure

| Action | File | Responsibility |
|--------|------|----------------|
| Create | `src/analytics/helpers/resolveAnalyticsUrl.ts` | URL resolution for window.open & shared links |
| Create | `src/analytics/helpers/resolveAnalyticsUrl.test.ts` | Unit tests |
| Modify | `src/analytics/pages/screens/components/ScreenHeader.tsx` | Fix 6 hardcoded URLs |
| Modify | `src/analytics/pages/screens/ScreensPage.tsx` | Fix 4 hardcoded URLs |
| Modify | `src/analytics/pages/screens/ScreenExportPage.tsx` | Fix 1 hardcoded URL |
| Modify | `src/analytics/pages/screens/renderers/InteractionLayer.tsx` | Fix 1 hardcoded URL |
| Modify | `src/analytics/pages/screens/components/ScreenSharePanel.tsx` | Fix 1 hardcoded URL |
| Modify | `src/analytics/pages/screens/components/ScreenSharePolicyPanel.tsx` | Fix 1 hardcoded URL |
| Modify | `src/analytics/pages/screens/components/TemplateGallery.tsx` | Fix 1 hardcoded URL |
| Modify | `src/analytics/pages/DashboardDetailPage.tsx` | Fix 2 hardcoded URLs |
| Modify | `src/analytics/pages/CardDetailPage.tsx` | Fix 2 hardcoded URLs |
| Modify | `src/analytics/pages/ExploreSessionsPage.tsx` | Fix 1 URL (API — verify) |
| Modify | `src/analytics/api/analyticsApi.ts` | Fix 2 hardcoded login redirects |
| Modify | `src/pages/workbench/index.tsx` | Fix 1 hardcoded URL |

---

### Task 1: Create URL resolution utility

**Files:**
- Create: `src/analytics/helpers/resolveAnalyticsUrl.ts`
- Create: `src/analytics/helpers/resolveAnalyticsUrl.test.ts`

The project already has `resolveAppHref()` in `src/routes/constants.ts` which handles hash/browser routing for route paths. We need two additional helpers:

1. `resolveRouteHref(routePath)` — returns a full URL (with origin) for a client-side route, correctly prefixed with `/#/` in hash mode. Used for shared/copied links.
2. `resolveRouteForOpen(routePath)` — returns a URL suitable for `window.open()` / `window.location.href` — same as above but used directly by navigation code.

Both delegate to `resolveAppHref` from `@/routes/constants` so routing mode is determined in one place.

- [ ] **Step 1: Write the failing test**

Create `src/analytics/helpers/resolveAnalyticsUrl.test.ts`:

```ts
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';

// We need to mock GLOBAL_CONFIG to test both hash and browser modes
vi.mock('@/global-config', () => ({
    GLOBAL_CONFIG: {
        routerHistory: 'hash',
        publicPath: '/',
    },
}));

// After mock is set up, import the module under test
const { resolveRouteHref, resolveRouteForOpen } = await import('./resolveAnalyticsUrl');

describe('resolveAnalyticsUrl', () => {
    const originalLocation = window.location;

    beforeEach(() => {
        Object.defineProperty(window, 'location', {
            value: { ...originalLocation, origin: 'https://example.com' },
            writable: true,
        });
    });

    afterEach(() => {
        Object.defineProperty(window, 'location', {
            value: originalLocation,
            writable: true,
        });
    });

    describe('resolveRouteHref (full URL for clipboard/sharing)', () => {
        it('should produce hash-prefixed full URL', () => {
            const result = resolveRouteHref('/bi/screens/42/preview');
            expect(result).toBe('https://example.com/#/bi/screens/42/preview');
        });

        it('should handle query parameters', () => {
            const result = resolveRouteHref('/bi/screens/42/preview?device=mobile');
            expect(result).toBe('https://example.com/#/bi/screens/42/preview?device=mobile');
        });

        it('should handle paths without leading slash', () => {
            const result = resolveRouteHref('bi/public/screen/abc');
            expect(result).toBe('https://example.com/#/bi/public/screen/abc');
        });
    });

    describe('resolveRouteForOpen (for window.open / location.href)', () => {
        it('should produce hash-prefixed URL for window.open', () => {
            const result = resolveRouteForOpen('/bi/screens/42/preview');
            expect(result).toBe('/#/bi/screens/42/preview');
        });

        it('should handle query parameters', () => {
            const result = resolveRouteForOpen('/bi/screens/42/preview?device=mobile');
            expect(result).toBe('/#/bi/screens/42/preview?device=mobile');
        });
    });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd source/dts-platform-webapp && npx vitest run src/analytics/helpers/resolveAnalyticsUrl.test.ts`
Expected: FAIL with module not found

- [ ] **Step 3: Write minimal implementation**

Create `src/analytics/helpers/resolveAnalyticsUrl.ts`:

```ts
import { resolveAppHref } from '@/routes/constants';

/**
 * Build a full absolute URL (with origin) for a client-side route.
 * Correctly handles hash vs browser routing mode.
 *
 * Use for: shared links, clipboard URLs, embed codes.
 *
 * @example resolveRouteHref('/bi/screens/42/preview')
 *   hash mode  → "https://host/#/bi/screens/42/preview"
 *   browser    → "https://host/bi/screens/42/preview"
 */
export const resolveRouteHref = (routePath: string): string => {
    const href = resolveAppHref(routePath);
    return `${window.location.origin}${href}`;
};

/**
 * Build a URL suitable for window.open() or window.location.href assignment.
 * Same as resolveAppHref but exported under a name that makes intent clear.
 *
 * Use for: window.open(), window.location.href, window.location.assign().
 *
 * @example resolveRouteForOpen('/bi/screens/42/preview')
 *   hash mode  → "/#/bi/screens/42/preview"
 *   browser    → "/bi/screens/42/preview"
 */
export const resolveRouteForOpen = (routePath: string): string => {
    return resolveAppHref(routePath);
};
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd source/dts-platform-webapp && npx vitest run src/analytics/helpers/resolveAnalyticsUrl.test.ts`
Expected: PASS (all 5 tests)

- [ ] **Step 5: Commit**

```bash
git add source/dts-platform-webapp/src/analytics/helpers/resolveAnalyticsUrl.ts \
       source/dts-platform-webapp/src/analytics/helpers/resolveAnalyticsUrl.test.ts
git commit -m "feat(analytics): add resolveAnalyticsUrl helpers for hash-safe navigation"
```

---

### Task 2: Fix ScreenHeader.tsx (6 hardcoded URLs)

**Files:**
- Modify: `src/analytics/pages/screens/components/ScreenHeader.tsx`

This file has the most broken URLs. All 6 must use the new helpers.

- [ ] **Step 1: Add import at top of file**

Add near existing imports:
```ts
import { resolveRouteForOpen, resolveRouteHref } from '../../../helpers/resolveAnalyticsUrl';
```

- [ ] **Step 2: Fix preview (line ~1112)**

Change:
```ts
window.open(`/bi/screens/${id}/preview${suffix}`, '_blank', 'noopener,noreferrer');
```
To:
```ts
window.open(resolveRouteForOpen(`/bi/screens/${id}/preview${suffix}`), '_blank', 'noopener,noreferrer');
```

- [ ] **Step 3: Fix "save as" navigation (line ~747)**

Change:
```ts
navigate(`/bi/screens/${result.id}/edit`, { replace: true });
```

NOTE: `navigate()` goes through React Router, which already handles hash routing. **No change needed here.** Skip this one.

- [ ] **Step 4: Fix publish preview URL (line ~968)**

Change:
```ts
const previewUrl = `${window.location.origin}/bi/screens/${encodeURIComponent(String(screenId))}/preview`;
```
To:
```ts
const previewUrl = resolveRouteHref(`/bi/screens/${encodeURIComponent(String(screenId))}/preview`);
```

- [ ] **Step 5: Fix publish public URL (line ~973)**

Change:
```ts
publicUrl = `${window.location.origin}/bi/public/screen/${policy.uuid}`;
```
To:
```ts
publicUrl = resolveRouteHref(`/bi/public/screen/${policy.uuid}`);
```

- [ ] **Step 6: Fix hydrated notice preview URL (line ~1015)**

Change:
```ts
previewUrl: `${window.location.origin}/bi/screens/${encodeURIComponent(String(id))}/preview`,
```
To:
```ts
previewUrl: resolveRouteHref(`/bi/screens/${encodeURIComponent(String(id))}/preview`),
```

- [ ] **Step 7: Fix JSON import redirect (line ~1537)**

Change:
```ts
window.location.href = `/bi/screens/${String(created.id)}/edit`;
```
To:
```ts
window.location.href = resolveRouteForOpen(`/bi/screens/${String(created.id)}/edit`);
```

- [ ] **Step 8: Fix share base URL (line ~1558)**

Change:
```ts
const baseUrl = `${window.location.origin}/bi/public/screen/${uuid}`;
```
To:
```ts
const baseUrl = resolveRouteHref(`/bi/public/screen/${uuid}`);
```

- [ ] **Step 9: Fix export URL (line ~1296)**

The export URL `/bi/screens/${id}/export?...` is used with `window.open` — this is a **route**, not an API endpoint. Fix:

Change:
```ts
const url = `/bi/screens/${id}/export?${params.toString()}`;
```
To:
```ts
const url = resolveRouteForOpen(`/bi/screens/${id}/export?${params.toString()}`);
```

- [ ] **Step 10: Commit**

```bash
git add source/dts-platform-webapp/src/analytics/pages/screens/components/ScreenHeader.tsx
git commit -m "fix(analytics): fix ScreenHeader hash routing for preview/share/export URLs"
```

---

### Task 3: Fix ScreensPage.tsx (3 hardcoded URLs)

**Files:**
- Modify: `src/analytics/pages/screens/ScreensPage.tsx`

- [ ] **Step 1: Add import**

```ts
import { resolveRouteForOpen, resolveRouteHref } from '../../helpers/resolveAnalyticsUrl';
```

- [ ] **Step 2: Fix handlePreview (line ~351)**

Change:
```ts
window.open(`/bi/screens/${id}/preview`, '_blank', 'noopener,noreferrer');
```
To:
```ts
window.open(resolveRouteForOpen(`/bi/screens/${id}/preview`), '_blank', 'noopener,noreferrer');
```

- [ ] **Step 3: Fix getPreviewUrl (line ~355)**

Change:
```ts
(id: string | number) => `${window.location.origin}/bi/screens/${encodeURIComponent(String(id))}/preview`,
```
To:
```ts
(id: string | number) => resolveRouteHref(`/bi/screens/${encodeURIComponent(String(id))}/preview`),
```

- [ ] **Step 4: Fix handleShare URL (line ~370)**

Change:
```ts
const url = `${window.location.origin}/bi/public/screen/${uuid}`;
```
To:
```ts
const url = resolveRouteHref(`/bi/public/screen/${uuid}`);
```

- [ ] **Step 5: Commit**

```bash
git add source/dts-platform-webapp/src/analytics/pages/screens/ScreensPage.tsx
git commit -m "fix(analytics): fix ScreensPage hash routing for preview/share URLs"
```

---

### Task 4: Fix ScreenExportPage.tsx (1 URL)

**Files:**
- Modify: `src/analytics/pages/screens/ScreenExportPage.tsx`

- [ ] **Step 1: Add import**

```ts
import { resolveRouteForOpen } from '../../helpers/resolveAnalyticsUrl';
```

- [ ] **Step 2: Fix buildPreviewFallbackUrl (line ~271)**

Change:
```ts
return `/bi/screens/${id}/preview${suffix ? `?${suffix}` : ''}`;
```
To:
```ts
return resolveRouteForOpen(`/bi/screens/${id}/preview${suffix ? `?${suffix}` : ''}`);
```

- [ ] **Step 3: Commit**

```bash
git add source/dts-platform-webapp/src/analytics/pages/screens/ScreenExportPage.tsx
git commit -m "fix(analytics): fix ScreenExportPage hash routing for preview fallback URL"
```

---

### Task 5: Fix InteractionLayer.tsx (1 URL)

**Files:**
- Modify: `src/analytics/pages/screens/renderers/InteractionLayer.tsx`

- [ ] **Step 1: Add import**

```ts
import { resolveRouteForOpen } from '../../../helpers/resolveAnalyticsUrl';
```

- [ ] **Step 2: Fix screen reference jump target (line ~59)**

Change:
```ts
return `/bi/screens/${encodeURIComponent(String(exact.id))}/preview`;
```
To:
```ts
return resolveRouteForOpen(`/bi/screens/${encodeURIComponent(String(exact.id))}/preview`);
```

- [ ] **Step 3: Commit**

```bash
git add source/dts-platform-webapp/src/analytics/pages/screens/renderers/InteractionLayer.tsx
git commit -m "fix(analytics): fix InteractionLayer hash routing for screen jump target"
```

---

### Task 6: Fix ScreenSharePanel.tsx & ScreenSharePolicyPanel.tsx (2 URLs)

**Files:**
- Modify: `src/analytics/pages/screens/components/ScreenSharePanel.tsx`
- Modify: `src/analytics/pages/screens/components/ScreenSharePolicyPanel.tsx`

- [ ] **Step 1: Fix ScreenSharePanel.tsx (line ~212)**

Add import:
```ts
import { resolveRouteHref } from '../../../helpers/resolveAnalyticsUrl';
```

Change:
```ts
const url = `${window.location.origin}/bi/public/screen/${uuid}`;
```
To:
```ts
const url = resolveRouteHref(`/bi/public/screen/${uuid}`);
```

- [ ] **Step 2: Fix ScreenSharePolicyPanel.tsx (line ~50)**

Add import:
```ts
import { resolveRouteHref } from '../../../helpers/resolveAnalyticsUrl';
```

Change:
```ts
return `${window.location.origin}/bi/public/screen/${uuid}`;
```
To:
```ts
return resolveRouteHref(`/bi/public/screen/${uuid}`);
```

- [ ] **Step 3: Commit**

```bash
git add source/dts-platform-webapp/src/analytics/pages/screens/components/ScreenSharePanel.tsx \
       source/dts-platform-webapp/src/analytics/pages/screens/components/ScreenSharePolicyPanel.tsx
git commit -m "fix(analytics): fix screen share panels hash routing for public URLs"
```

---

### Task 7: Fix TemplateGallery.tsx (1 URL)

**Files:**
- Modify: `src/analytics/pages/screens/components/TemplateGallery.tsx`

- [ ] **Step 1: Add import and fix (line ~484)**

Add import:
```ts
import { resolveRouteForOpen } from '../../../helpers/resolveAnalyticsUrl';
```

Change:
```ts
window.location.href = `/bi/screens/${String(created.id)}/edit`;
```
To:
```ts
window.location.href = resolveRouteForOpen(`/bi/screens/${String(created.id)}/edit`);
```

- [ ] **Step 2: Commit**

```bash
git add source/dts-platform-webapp/src/analytics/pages/screens/components/TemplateGallery.tsx
git commit -m "fix(analytics): fix TemplateGallery hash routing for post-creation redirect"
```

---

### Task 8: Fix DashboardDetailPage.tsx & CardDetailPage.tsx (4 URLs)

**Files:**
- Modify: `src/analytics/pages/DashboardDetailPage.tsx`
- Modify: `src/analytics/pages/CardDetailPage.tsx`

- [ ] **Step 1: Fix DashboardDetailPage.tsx (lines ~255, ~271)**

Add import:
```ts
import { resolveRouteHref } from '../../helpers/resolveAnalyticsUrl';
```

Change both occurrences of:
```ts
`${window.location.origin}/bi/public/dashboard/${encodeURIComponent(shareUuid)}`
```
To:
```ts
resolveRouteHref(`/bi/public/dashboard/${encodeURIComponent(shareUuid)}`)
```

- [ ] **Step 2: Fix CardDetailPage.tsx (lines ~157, ~173)**

Add import:
```ts
import { resolveRouteHref } from '../../helpers/resolveAnalyticsUrl';
```

Change both occurrences of:
```ts
`${window.location.origin}/bi/public/card/${encodeURIComponent(shareUuid)}`
```
To:
```ts
resolveRouteHref(`/bi/public/card/${encodeURIComponent(shareUuid)}`)
```

- [ ] **Step 3: Commit**

```bash
git add source/dts-platform-webapp/src/analytics/pages/DashboardDetailPage.tsx \
       source/dts-platform-webapp/src/analytics/pages/CardDetailPage.tsx
git commit -m "fix(analytics): fix dashboard/card detail pages hash routing for share URLs"
```

---

### Task 9: Fix analyticsApi.ts auth redirects (2 URLs)

**Files:**
- Modify: `src/analytics/api/analyticsApi.ts`

The hardcoded `/#/auth/login` works in hash mode but is fragile. Use `resolveLoginHref()` from `@/routes/constants`.

- [ ] **Step 1: Add import**

```ts
import { resolveLoginHref } from '@/routes/constants';
```

- [ ] **Step 2: Fix first redirect (line ~1214)**

Change:
```ts
window.location.href = `/#/auth/login?redirect=${returnUrl}`;
```
To:
```ts
window.location.href = `${resolveLoginHref()}?redirect=${returnUrl}`;
```

- [ ] **Step 3: Fix second redirect (line ~1225)**

Same change as step 2.

- [ ] **Step 4: Commit**

```bash
git add source/dts-platform-webapp/src/analytics/api/analyticsApi.ts
git commit -m "fix(analytics): use resolveLoginHref for auth redirects instead of hardcoded hash"
```

---

### Task 10: Fix workbench/index.tsx (1 URL)

**Files:**
- Modify: `src/pages/workbench/index.tsx`

- [ ] **Step 1: Add import and fix (line ~390)**

Add import:
```ts
import { resolveRouteForOpen } from '@/analytics/helpers/resolveAnalyticsUrl';
```

Change:
```ts
window.open(`/bi/screens/${screen.id}/preview`, "_blank");
```
To:
```ts
window.open(resolveRouteForOpen(`/bi/screens/${screen.id}/preview`), "_blank");
```

- [ ] **Step 2: Commit**

```bash
git add source/dts-platform-webapp/src/pages/workbench/index.tsx
git commit -m "fix(workbench): fix screen preview hash routing"
```

---

### Task 11: Verify ExploreSessionsPage.tsx (no change needed)

**Files:**
- Review: `src/analytics/pages/ExploreSessionsPage.tsx:274`

- [ ] **Step 1: Verify this is an API URL, not a route**

The URL at line 274 is:
```ts
const url = `${window.location.origin}/bi/api/explore-session/public/${encodeURIComponent(uuid)}`;
```

This is a **backend API endpoint** (`/bi/api/...`), not a client-side route. The nginx config proxies `/bi/api/*` to the analytics backend. **No change needed** — this URL correctly bypasses the hash router.

---

### Task 12: Smoke test all fixes

- [ ] **Step 1: Run TypeScript compilation check**

Run: `cd source/dts-platform-webapp && npx tsc --noEmit`
Expected: No errors

- [ ] **Step 2: Run unit tests**

Run: `cd source/dts-platform-webapp && npx vitest run src/analytics/helpers/resolveAnalyticsUrl.test.ts`
Expected: All tests pass

- [ ] **Step 3: Verify no remaining direct URL patterns**

Run:
```bash
cd source/dts-platform-webapp
grep -rn "window\.open(\`/bi/" src/analytics/ src/pages/workbench/ || echo "CLEAN"
grep -rn "window\.location\.href = \`/bi/" src/analytics/ || echo "CLEAN"
grep -rn "location\.origin}/bi/" src/analytics/ || echo "CLEAN"
grep -rn '/#/auth/login' src/analytics/ || echo "CLEAN"
```
Expected: All four return "CLEAN"

- [ ] **Step 4: Final commit if any stragglers found**

If grep reveals any remaining hardcoded URLs, fix them following the same pattern. Otherwise, done.
