# Admin Views Migration (A -> B)

This change removes legacy pages under `src/pages/management/system/*` (version A)
and migrates remaining functionality to the unified admin views under `src/admin/views/*` (version B).

## What Changed

- Navigation now points to `/admin/*` routes:
  - Users → `/admin/users`
  - Roles → `/admin/roles`
  - Orgs → `/admin/orgs`
  - Approval → `/admin/approval`
- Drafts: feature removed
  - Audit → `/admin/audit`
  - Portal Menus → `/admin/portal-menus`
- Frontend dashboard routes under `/management/system/*` now render B views directly.
- All A pages and related modals were removed.

## Files and Routes Updated

- `src/layouts/dashboard/nav/nav-data/nav-data-frontend.tsx`: nav items updated to `/admin/*` paths.
- `src/routes/sections/dashboard/frontend.tsx`: render B views for system routes.
- `src/routes/admin-routes.tsx`: `/admin/portal-menus` renders `PortalMenusView` directly.
- `src/routes/admin-routes.tsx`: added `/admin/users/:id` for user detail.
- `src/pages/management/system/*`: legacy files deleted; empty dirs may remain.

Note: The admin user view has been stabilized by migrating the legacy user modal
implementation into `src/admin/views/user-management.modal.tsx`, and a new
admin user detail view `src/admin/views/user-detail.tsx` has been added.
The old directory `src/pages/management/system/user` has been removed.

## Manual Verification

1) Start services
- Local dev: `./dev-up.sh --mode local`
- Packaged: `docker compose -f docker-compose.yml -f docker-compose-app.yml up -d`

2) UI checks (as a sysadmin role)
- Open `https://admin.${BASE_DOMAIN}`
- Nav → 管理：点击 用户/角色/组织/审批/草稿/审计/菜单 管理项
- Confirm URLs are `/admin/users`, `/admin/roles`, `/admin/orgs`, `/admin/approval`, `/admin/audit`, `/admin/portal-menus`

3) Legacy route compatibility
- Visit `/management/system/user`, `/management/system/role`, `/management/system/group` etc.
- Confirm the new B views render.

4) Core flows
- Users: search/filter; open change request form to create/adjust users
- Roles: create/edit/delete via change requests
- Orgs: browse and adjust via change requests
- Approval/Audit: lists load; actions behave as expected
- Portal menus: enable/disable leaf items; restore defaults

5) Health & Logs
- `docker compose ps`
- `docker compose logs -f dts-admin-webapp`

## Notes

- If you still need any A-only behavior, add it inside `src/admin/views/*` and keep `/management/system/*` routes as wrappers to the B views for compatibility.
- Empty directories under `src/pages/management/system/*` can be removed in a subsequent cleanup commit.
