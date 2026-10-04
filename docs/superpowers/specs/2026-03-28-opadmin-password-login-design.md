# OPAdmin Password Login for PKI-Only Environments

> Status: Draft
> Date: 2026-03-28
> Scope: dts-platform-webapp, dts-platform

## 1. Problem

In field deployments where PKI (certificate-based) login is the only authentication method, the `opadmin` (ROLE_OP_ADMIN) superuser account has no PKI certificate and therefore cannot login to the platform.

## 2. Solution

Add a hidden password login form to the platform-webapp login page, accessible via URL parameter `?mode=admin`. Only ROLE_OP_ADMIN is permitted to use this password login; all other roles are rejected with a prompt to use PKI.

## 3. Design

### 3.1 Frontend — Login Page

**Trigger**: `GET /login?mode=admin`

- Normal `/login` → existing PKI login flow (unchanged)
- `/login?mode=admin` → renders username + password form
- Form submits to existing Keycloak password auth endpoint
- On success: validate role is OP_ADMIN, proceed to dashboard
- On success but non-OP_ADMIN role: reject with "请使用证书登录"

### 3.2 Backend — Role Restriction

No new backend endpoint needed. The existing `/api/keycloak/auth/login` handles password authentication. The role restriction is enforced **client-side** after login succeeds (same pattern as admin-webapp's three-admin role check).

Optionally, a backend environment variable `DTS_OPADMIN_PASSWORD_LOGIN_ENABLED` (default: `true`) can gate the feature.

### 3.3 Security

- URL `?mode=admin` is not linked from any UI navigation
- Password login restricted to OP_ADMIN role only
- Feature can be disabled via environment variable
- Audit: standard Keycloak login audit applies

## 4. Changes

| File | Change |
|------|--------|
| `dts-platform-webapp/src/pages/sys/login/login-form.tsx` | Add `mode=admin` conditional rendering |
| `dts-platform-webapp/src/pages/sys/login/` | Add `PasswordLoginForm` component |

## 5. Menu Completeness

No change needed. opadmin already has full menu visibility in platform via `DEFAULT_MENU_ROLES = ["ROLE_OP_ADMIN"]`. Platform and admin-webapp have independent menu systems.
