# Unified Frontend Refactor Design

**Date:** 2026-03-09
**Branch:** `customer/2.2.1`
**Scope:** `source/dts-platform-webapp`, `source/dts-admin-webapp`, `source/dts-analytics-webapp/modern`

## Context

The current `2.2.1` customer line has three active web applications, but they no longer feel like one product:

- `platform-webapp` and `admin-webapp` share a dashboard shell, yet still contain old placeholder pages, fake notices, and inconsistent page chrome.
- `analytics-webapp/modern` uses a separate token system and layout contract, with a different visual language and several demo-oriented placeholders.
- customer-visible pages still expose reserved, dummy, or incomplete materials that should not survive into the refactor branch.

The goal of this sprint is not another polish pass. It is a structural front-end refactor for the `2.2.1` customer line.

## Goals

- unify the three webapps under one product-grade visual language
- make the refactor `light-first`, with dark mode retained as a secondary theme
- fully apply the unified style to `analytics-webapp/modern`, including:
  - navigation shell
  - editor/workspace pages
  - preview/export/public screen pages
- remove production `faker`, `demo`, `placeholder`, `dummy`, and `mock` materials
- keep the existing `2.2.1` architecture and release topology intact

## Non-Goals

- no backend capability expansion
- no new product information architecture
- no package-level monorepo redesign or shared npm package extraction in this sprint
- no attempt to backport `v2.2.2+` product concepts

## Current Findings

### Shared shell lineage

`platform-webapp` and `admin-webapp` already share the same dashboard shell pattern:

- `source/dts-platform-webapp/src/layouts/dashboard/main.tsx`
- `source/dts-platform-webapp/src/layouts/dashboard/header.tsx`
- `source/dts-admin-webapp/src/layouts/dashboard/main.tsx`
- `source/dts-admin-webapp/src/layouts/dashboard/header.tsx`

This means their shell should be refactored once as one design family, not redesigned separately.

### Analytics has a full parallel shell

`analytics-webapp/modern` is not just a page bundle. It owns a full console shell, workspace shell, and runtime shell:

- `source/dts-analytics-webapp/modern/src/layouts/AppLayout.tsx`
- `source/dts-analytics-webapp/modern/src/layouts/layout.css`
- `source/dts-analytics-webapp/modern/src/pages/screens/ScreenDesignerPage.tsx`
- `source/dts-analytics-webapp/modern/src/pages/screens/PublicScreenPage.tsx`

Any partial token alignment would leave the real inconsistency untouched.

### Fake and placeholder material still exists

Production cleanup must include removal or replacement of:

- fake notices driven by `faker`
- demo services and devtools-only surfaces
- explicit placeholder pages and reserved copy
- analytics demo plugin adapters and screen placeholder logic

The detailed inventory is tracked under:

- `worklog/v2.2.1/frontend-refactor-sprint-01/fake-material-inventory.md`

## Design Decisions

### 1. Light-first product language

The new baseline is `light-first`, not dark-first.

Core traits:

- light content canvas with layered surfaces instead of plain white slabs
- dark sidebar as a strong product anchor, paired with a light working area
- large-radius panels, thick section cards, and deliberate whitespace
- a single product-grade dashboard language for overview pages
- uniform empty, loading, error, and read-only states

The provided dashboard prototype defines the target tone:

- strong left navigation
- dense but calm enterprise control center
- large cards and clear visual hierarchy
- shallow color count, with emphasis on shape and spacing rather than decorative gradients

### 2. Three shells, one design contract

The refactor will use one design contract across three shells:

#### Console Shell

Applies to normal admin/console pages:

- `platform-webapp`
- `admin-webapp`
- analytics list/detail pages under the normal app shell

Shared behaviors:

- dark sidebar + light main content
- unified `PageHeader`, breadcrumb, global actions, search, account entry
- same page spacing, card geometry, filter bars, status badges, and tables

#### Workspace Shell

Applies to high-density editing surfaces:

- `ScreenDesignerPage`
- screen preview/export work areas

Shared behaviors:

- persistent work toolbar
- structured side panels for properties, layers, and page management
- lighter, product-aligned controls without collapsing into generic admin tables

#### Runtime Shell

Applies to:

- public screen view
- preview runtime controls

Shared behaviors:

- unified loading/error/empty states
- unified control bar style
- keep business screen content themeable inside the canvas

The shell is unified; the visualized business screen content stays configurable.

### 3. Shared token contract, not a shared package

This sprint will not start by extracting a shared frontend package.

Instead, it will define and enforce a single contract for:

- color roles
- typography scale
- spacing scale
- radius scale
- border and shadow rules
- semantic statuses
- chart palette
- shell sizing

Why this approach:

- current repo has three independent frontend projects
- build/release topology should remain ops-friendly
- token contract can be implemented immediately without changing packaging or deployment

This choice accepts limited future consolidation work, but avoids turning the sprint into build-system surgery.

### 4. Canonical component primitives

The refactor will standardize these primitives across all three apps:

- `PageHeader`
- `SectionCard`
- `MetricCard`
- `FilterBar`
- `StatusBadge`
- `EmptyState`
- `InsightPanel`
- `DataTableShell`
- `SidePanel`
- `WorkspaceToolbar`

The target is not literal source sharing in this sprint. The target is visual and interaction consistency.

### 5. Delete fake materials from production code

The sprint will directly remove production fake/demo material rather than merely hiding it.

Rules:

- delete `faker`-backed notices and demo-only route content
- remove `demoService` and placeholder page surfaces from production flows
- remove analytics demo plugin adapters and placeholder panel logic
- if a fake component is still referenced by a real route, replace it with a real empty state or an intentionally scoped unavailable state

No customer-visible route should ship with `预留`, `demo`, `dummy`, or fake operational data.

## Design Contract

### Surface hierarchy

- `surface-page`: page background
- `surface-card`: normal card and form container
- `surface-float`: dropdown, drawer, modal, and popover
- `surface-workspace`: designer or canvas surround

### Text hierarchy

- `text-primary`
- `text-secondary`
- `text-muted`
- `text-disabled`

No module-specific gray ladders should remain after the refactor.

### Geometry

- buttons and inputs keep moderate radii
- cards and section panels use larger radii
- workspace panels use visually heavier, padded containers
- spacing follows a unified 8px rhythm

### Density modes

- `console-density` for list/configuration pages
- `workspace-density` for designer and runtime control areas

### Theme behavior

- light theme is default and primary
- dark theme remains supported but secondary
- chart palette, states, and semantic colors map to the same roles in all three apps

## Migration Strategy

### Phase A: Remove fake surfaces and invalid references

- clean `faker`, `demo`, `placeholder`, `dummy`, and `mock` assets from production
- remove or replace associated routes, menus, imports, and empty fake services
- keep all three apps buildable during cleanup

### Phase B: Unify shell layers

- refactor shared console shell for `platform` and `admin`
- refactor analytics `AppLayout`
- align page headers, sidebars, and surface hierarchy

### Phase C: Refactor high-value page families

- customer-visible admin configuration pages
- customer-visible platform workbench and governance pages
- analytics screens list, designer, preview, export, and runtime pages

### Phase D: Verification and visual audit

- all three frontends must build successfully
- route smoke for customer-visible pages
- manual visual audit checklist for shell, cards, empty states, and runtime control surfaces

## Verification

Minimum build verification:

- `pnpm -C source/dts-platform-webapp build`
- `pnpm -C source/dts-admin-webapp build`
- `pnpm -C source/dts-analytics-webapp/modern build`

Manual audit focus:

- no fake notices
- no customer-visible placeholder copy
- console shell alignment across all three apps
- analytics designer and runtime surfaces visibly belong to the same product family

## Risks

- deleting fake materials can break routes or shared layout imports if removal is too literal
- analytics workspace pages have a different density profile and should not be flattened into generic admin pages
- platform/admin share shell code, so shell changes must be treated as cross-app changes by default

## Rollback Strategy

- keep the refactor split into small commits by shell and task
- maintain per-task route/build verification
- if a task destabilizes one app, revert only the task commit instead of rolling back the full sprint
