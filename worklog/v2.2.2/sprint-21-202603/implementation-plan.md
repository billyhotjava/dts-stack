# Sprint-21: 统一产品入口与UI框架 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Transform DTS from "professional-tool-first" to "BI-analysis-first" by adding a portal selection page, unifying the UI framework (antd), and standardizing the shell (Header + Sidebar) across both webapps.

**Architecture:** Three-phase approach: (F1) Add portal page in platform-webapp + install antd in analytics-webapp + unify Headers with app-switch button; (F2) Replace all 12 custom UI components in analytics-webapp with antd equivalents across ~60 files; (F3) Replace analytics-webapp's custom SidebarNav with antd Layout.Sider + Menu.

**Tech Stack:** React 18/19, TypeScript, antd v5.22.x, React Router v7, Vite

---

## File Structure

### New Files
| File | Responsibility |
|------|---------------|
| `dts-platform-webapp/src/pages/portal/PortalPage.tsx` | Portal selection page with dual cards |
| `dts-platform-webapp/src/layouts/components/app-switcher.tsx` | "Switch app" button for platform Header |

### Modified Files (F1)
| File | Changes |
|------|---------|
| `dts-platform-webapp/src/constants/portal-navigation.ts` | `DEFAULT_PORTAL_ROUTE` → `/portal` |
| `dts-platform-webapp/src/routes/sections/dashboard/static-routes.tsx` | Add `/portal` route |
| `dts-platform-webapp/src/routes/sections/dashboard/index.tsx:53` | Fallback → `/portal` |
| `dts-platform-webapp/src/routes/sections/dashboard/dynamic-resolver.tsx:61` | Fallback → `/portal` |
| `dts-platform-webapp/src/pages/sys/error/Page404.tsx:14` | Fallback → `/portal` |
| `dts-platform-webapp/src/pages/sys/login/login-form.tsx:206,389` | Fallback → `/portal` |
| `dts-platform-webapp/src/pages/workbench/index.tsx:266` | Fallback → `/portal` |
| `dts-platform-webapp/src/layouts/dashboard/header.tsx:36` | Add AppSwitcher |
| `dts-analytics-webapp/modern/package.json` | Add antd + @ant-design/icons |
| `dts-analytics-webapp/modern/src/main.tsx` | Add ConfigProvider |
| `dts-analytics-webapp/modern/src/layouts/AppLayout.tsx` | Restructure Header with antd + add switch button |

### Modified Files (F2) — ~60 files
All files importing from `ui/Button`, `ui/Card`, `ui/Badge`, `ui/Modal`, `ui/Loading`, `ui/Input`, `ui/Tabs`, `ui/Dropdown`, `ui/Drawer`.

### Deleted Files (F2 final)
| Directory | Reason |
|-----------|--------|
| `dts-analytics-webapp/modern/src/ui/` | Entire directory removed after migration |

### Modified Files (F3)
| File | Changes |
|------|---------|
| `dts-analytics-webapp/modern/src/layouts/AppLayout.tsx` | Replace SidebarNav with antd Sider + Menu |
| `dts-analytics-webapp/modern/src/components/SidebarNav/` | Delete after replacement |

---

## Feature 1: 门户与外壳统一

### Task 1: PortalPage — 门户选择页

**Files:**
- Create: `dts-platform-webapp/src/pages/portal/PortalPage.tsx`
- Modify: `dts-platform-webapp/src/routes/sections/dashboard/static-routes.tsx`

- [ ] **Step 1: Create PortalPage component**

```tsx
// dts-platform-webapp/src/pages/portal/PortalPage.tsx
import { useEffect, useState } from "react";
import { Card, Checkbox, Typography, Space, Avatar, theme } from "antd";
import {
	BarChartOutlined,
	SettingOutlined,
} from "@ant-design/icons";
import { useUserStore } from "@/store/userStore";

const { Title, Text, Paragraph } = Typography;

const PORTAL_PREF_KEY = "dts.portal.preferredApp";

type AppChoice = "analytics" | "platform";

export default function PortalPage() {
	const [remember, setRemember] = useState(false);
	const displayName = useUserStore((s) => s.userInfo?.displayName ?? s.userInfo?.login ?? "");

	// Auto-redirect if user has a remembered preference
	useEffect(() => {
		const preferred = localStorage.getItem(PORTAL_PREF_KEY) as AppChoice | null;
		if (preferred === "analytics") {
			window.location.href = "/analytics";
		} else if (preferred === "platform") {
			// SPA internal navigation — just replace location within platform-webapp
			window.location.replace("/dashboard/workbench");
		}
	}, []);

	const handleSelect = (choice: AppChoice) => {
		if (remember) {
			localStorage.setItem(PORTAL_PREF_KEY, choice);
		}
		if (choice === "analytics") {
			window.location.href = "/analytics";
		} else {
			window.location.replace("/dashboard/workbench");
		}
	};

	// If auto-redirecting, show nothing
	const preferred = localStorage.getItem(PORTAL_PREF_KEY);
	if (preferred === "analytics" || preferred === "platform") {
		return null;
	}

	return (
		<div
			style={{
				display: "flex",
				flexDirection: "column",
				alignItems: "center",
				justifyContent: "center",
				minHeight: "100vh",
				background: "linear-gradient(135deg, #f5f7fa 0%, #c3cfe2 100%)",
				padding: 24,
			}}
		>
			<Space direction="vertical" align="center" size={32}>
				<Title level={2} style={{ margin: 0 }}>
					DTS 数据平台
				</Title>

				<Space size={24}>
					<Card
						hoverable
						style={{ width: 260, textAlign: "center", cursor: "pointer" }}
						onClick={() => handleSelect("analytics")}
					>
						<Space direction="vertical" size={12}>
							<Avatar
								size={64}
								icon={<BarChartOutlined />}
								style={{ backgroundColor: "#1677ff" }}
							/>
							<Title level={4} style={{ margin: 0 }}>
								BI 分析
							</Title>
							<Paragraph type="secondary" style={{ margin: 0 }}>
								智能分析，轻松洞察数据价值
							</Paragraph>
						</Space>
					</Card>

					<Card
						hoverable
						style={{ width: 260, textAlign: "center", cursor: "pointer" }}
						onClick={() => handleSelect("platform")}
					>
						<Space direction="vertical" size={12}>
							<Avatar
								size={64}
								icon={<SettingOutlined />}
								style={{ backgroundColor: "#52c41a" }}
							/>
							<Title level={4} style={{ margin: 0 }}>
								大数据平台
							</Title>
							<Paragraph type="secondary" style={{ margin: 0 }}>
								数据全链路专业管控（专业版）
							</Paragraph>
						</Space>
					</Card>
				</Space>

				<Checkbox checked={remember} onChange={(e) => setRemember(e.target.checked)}>
					记住我的选择，下次直接进入
				</Checkbox>

				{displayName && (
					<Text type="secondary">
						欢迎，{displayName}
					</Text>
				)}
			</Space>
		</div>
	);
}
```

- [ ] **Step 2: Add /portal route as a top-level route (OUTSIDE DashboardLayout)**

The PortalPage is a full-screen selection page and must NOT be wrapped in `DashboardLayout` (no sidebar/header). Add it to `dts-platform-webapp/src/routes/sections/index.tsx` as a sibling of `dashboardRoutes`, wrapped only in `LoginAuthGuard`:

```tsx
// dts-platform-webapp/src/routes/sections/index.tsx
import { Suspense, lazy } from "react";
import { LoginAuthGuard } from "../components/login-auth-guard";

const PortalPage = lazy(() => import("@/pages/portal/PortalPage"));

export const makeRoutesSection = (): RouteObject[] => [
  { index: true, element: <Navigate to={LOGIN_ROUTE} replace /> },
  // Auth
  ...authRoutes,
  // Portal (full-screen, no DashboardLayout)
  {
    path: "portal",
    element: (
      <LoginAuthGuard>
        <Suspense fallback={null}>
          <PortalPage />
        </Suspense>
      </LoginAuthGuard>
    ),
  },
  // Dashboard
  ...dashboardRoutes,
  // Main
  ...mainRoutes,
  // No Match
  { path: "*", element: <Navigate to="/404" replace /> },
];
```

- [ ] **Step 3: Also update PortalPage to use React Router navigate() for platform choice**

In the PortalPage component, change the platform navigation from `window.location.replace` to React Router `navigate()` for smoother SPA transition:

```tsx
import { useNavigate } from "react-router";

// Inside the component:
const navigate = useNavigate();

const handleSelect = (choice: AppChoice) => {
  if (remember) {
    localStorage.setItem(PORTAL_PREF_KEY, choice);
  }
  if (choice === "analytics") {
    window.location.href = "/analytics"; // Cross-app: full page load required
  } else {
    navigate("/dashboard/workbench", { replace: true }); // Same app: SPA navigation
  }
};

// Also update the auto-redirect useEffect:
useEffect(() => {
  const preferred = localStorage.getItem(PORTAL_PREF_KEY) as AppChoice | null;
  if (preferred === "analytics") {
    window.location.href = "/analytics";
  } else if (preferred === "platform") {
    navigate("/dashboard/workbench", { replace: true });
  }
}, [navigate]);
```

- [ ] **Step 4: Verify TypeScript compiles**

Run: `cd /opt/prod/s10/s10-stack/source/dts-platform-webapp && npx tsc --noEmit`
Expected: No TypeScript errors

- [ ] **Step 5: Commit**

```bash
git add source/dts-platform-webapp/src/pages/portal/PortalPage.tsx source/dts-platform-webapp/src/routes/sections/index.tsx
git commit -m "feat(F1/T01): add PortalPage with dual card selection and remember preference"
```

---

### Task 2: 默认路由改为 /portal

**Files:**
- Modify: `dts-platform-webapp/src/constants/portal-navigation.ts:1`
- Modify: `dts-platform-webapp/src/routes/sections/dashboard/index.tsx:53`
- Modify: `dts-platform-webapp/src/routes/sections/dashboard/dynamic-resolver.tsx:61`
- Modify: `dts-platform-webapp/src/pages/sys/error/Page404.tsx:14`
- Modify: `dts-platform-webapp/src/pages/sys/login/login-form.tsx:206,389`
- Modify: `dts-platform-webapp/src/pages/workbench/index.tsx:266`

- [ ] **Step 1: Change DEFAULT_PORTAL_ROUTE constant**

In `dts-platform-webapp/src/constants/portal-navigation.ts` line 1:
```typescript
// Before:
export const DEFAULT_PORTAL_ROUTE = "/dashboard/workbench";
// After:
export const DEFAULT_PORTAL_ROUTE = "/portal";
```

- [ ] **Step 2: Update all hardcoded fallbacks**

In each file below, change `"/dashboard/workbench"` to `"/portal"`:

**`routes/sections/dashboard/index.tsx` line 53:**
```typescript
// Before:
const fallbackPath = GLOBAL_CONFIG.defaultRoute || "/dashboard/workbench";
// After:
const fallbackPath = GLOBAL_CONFIG.defaultRoute || "/portal";
```

**`routes/sections/dashboard/dynamic-resolver.tsx` line 61:**
```typescript
// Before:
const defaultRoute = GLOBAL_CONFIG.defaultRoute || "/dashboard/workbench";
// After:
const defaultRoute = GLOBAL_CONFIG.defaultRoute || "/portal";
```

**`pages/sys/error/Page404.tsx` line 14:**
```typescript
// Before:
const homePath = GLOBAL_CONFIG.defaultRoute || "/dashboard/workbench";
// After:
const homePath = GLOBAL_CONFIG.defaultRoute || "/portal";
```

**`pages/sys/login/login-form.tsx` line 206:**
```typescript
// Before:
navigate(safeRedirect || GLOBAL_CONFIG.defaultRoute || "/dashboard/workbench", { replace: true });
// After:
navigate(safeRedirect || GLOBAL_CONFIG.defaultRoute || "/portal", { replace: true });
```

**`pages/sys/login/login-form.tsx` line 389 (PKI login):**
```typescript
// Before:
navigate(GLOBAL_CONFIG.defaultRoute || "/dashboard/workbench", { replace: true });
// After:
navigate(GLOBAL_CONFIG.defaultRoute || "/portal", { replace: true });
```

**`pages/workbench/index.tsx` line 266:**
```typescript
// Before:
|| "/dashboard/workbench";
// After:
|| "/portal";
```

- [ ] **Step 3: Do NOT change these intentional hardcoded links** (they navigate within the workbench, not to the default route):
- `pages/workbench/WorkflowCenterPage.tsx:149` — `push("/dashboard/workbench")` — this navigates to workbench from workflow center
- `pages/workbench/index.tsx:373` — `push("/dashboard/workbench/workflow-center")` — this navigates to workflow center
- `layouts/dashboard/nav/nav-data/index.tsx:211` — path comparison for workbench detection

- [ ] **Step 4: Verify TypeScript compiles**

Run: `cd /opt/prod/s10/s10-stack/source/dts-platform-webapp && npx tsc --noEmit`
Expected: No errors

- [ ] **Step 5: Commit**

```bash
git add source/dts-platform-webapp/src/constants/portal-navigation.ts source/dts-platform-webapp/src/routes/sections/dashboard/index.tsx source/dts-platform-webapp/src/routes/sections/dashboard/dynamic-resolver.tsx source/dts-platform-webapp/src/pages/sys/error/Page404.tsx source/dts-platform-webapp/src/pages/sys/login/login-form.tsx source/dts-platform-webapp/src/pages/workbench/index.tsx
git commit -m "feat(F1/T02): change default route from /dashboard/workbench to /portal"
```

---

### Task 3: analytics-webapp 引入 antd + ConfigProvider

**Files:**
- Modify: `dts-analytics-webapp/modern/package.json`
- Modify: `dts-analytics-webapp/modern/src/main.tsx`

- [ ] **Step 1: Install antd and icons**

```bash
cd /opt/prod/s10/s10-stack/source/dts-analytics-webapp/modern
npm install antd@^5.22.1 @ant-design/icons
```

Note: Use `^5.22.1` to match platform-webapp's version.

- [ ] **Step 2: Add ConfigProvider to main.tsx**

In `dts-analytics-webapp/modern/src/main.tsx`, wrap the app with antd ConfigProvider:

```tsx
// Add imports at top:
import { ConfigProvider } from "antd";
import zhCN from "antd/locale/zh_CN";

// Wrap the router/app with ConfigProvider:
// Find the root render and wrap:
<ConfigProvider
  locale={zhCN}
  theme={{
    token: {
      colorPrimary: "#1677ff",
      borderRadius: 6,
    },
  }}
>
  {/* existing RouterProvider or app content */}
</ConfigProvider>
```

The exact wrapping location depends on the current main.tsx structure. Find where `RouterProvider` or the root component is rendered and wrap it.

- [ ] **Step 3: Verify build**

```bash
cd /opt/prod/s10/s10-stack/source/dts-analytics-webapp/modern
npx tsc -p tsconfig.json --noEmit
```

- [ ] **Step 4: Commit**

```bash
git add source/dts-analytics-webapp/modern/package.json source/dts-analytics-webapp/modern/package-lock.json source/dts-analytics-webapp/modern/src/main.tsx
git commit -m "feat(F1/T03): install antd v5 and add ConfigProvider with zh_CN locale"
```

---

### Task 4: analytics-webapp Header 重构（antd Layout.Header）

**Files:**
- Modify: `dts-analytics-webapp/modern/src/layouts/AppLayout.tsx`

- [ ] **Step 1: Restructure Header section of AppLayout.tsx**

The current AppLayout uses a custom header with inline SVGs, ThemeToggle, and a Dropdown for user menu. Replace the header section with antd `Layout.Header`:

```tsx
// Add imports:
import { Layout, Button, Dropdown, Space, Tooltip, theme } from "antd";
import {
  AppstoreOutlined,
  SearchOutlined,
  UserOutlined,
  LogoutOutlined,
} from "@ant-design/icons";

const { Header } = Layout;
```

Replace the header JSX (the `<header>` element inside `<Main>`) with:

```tsx
<Header
  style={{
    display: "flex",
    alignItems: "center",
    justifyContent: "space-between",
    padding: "0 16px",
    background: "#fff",
    borderBottom: "1px solid #f0f0f0",
    height: 48,
    lineHeight: "48px",
  }}
>
  {/* Left: breadcrumb / page title */}
  <div style={{ flex: 1 }}>
    <HeaderIntro />
  </div>

  {/* Right: actions */}
  <Space size={8}>
    <Tooltip title={t("nav.search")}>
      <Button
        type="text"
        icon={<SearchOutlined />}
        onClick={() => navigate("/search")}
      />
    </Tooltip>

    <Tooltip title="切换应用">
      <Button
        type="text"
        icon={<AppstoreOutlined />}
        onClick={() => {
          localStorage.removeItem("dts.portal.preferredApp");
          window.location.href = "/portal";
        }}
      />
    </Tooltip>

    <Dropdown
      menu={{
        items: [
          {
            key: "user-info",
            label: displayName || login,
            disabled: true,
          },
          { type: "divider" },
          {
            key: "logout",
            icon: <LogoutOutlined />,
            label: t("nav.logout"),
            onClick: handleLogout,
          },
        ],
      }}
      placement="bottomRight"
    >
      <Button type="text" icon={<UserOutlined />} />
    </Dropdown>
  </Space>
</Header>
```

- [ ] **Step 2: Remove ThemeToggle import and usage**

Remove the `ThemeToggle` import and its JSX usage from AppLayout.tsx. The theme toggle is being removed in favor of a unified light theme.

- [ ] **Step 3: Verify TypeScript compiles**

```bash
cd /opt/prod/s10/s10-stack/source/dts-analytics-webapp/modern
npx tsc -p tsconfig.json --noEmit
```

- [ ] **Step 4: Commit**

```bash
git add source/dts-analytics-webapp/modern/src/layouts/AppLayout.tsx
git commit -m "feat(F1/T04): restructure analytics Header with antd Layout.Header and app switcher"
```

---

### Task 5: platform-webapp Header 添加切换按钮

**Files:**
- Create: `dts-platform-webapp/src/layouts/components/app-switcher.tsx`
- Modify: `dts-platform-webapp/src/layouts/dashboard/header.tsx`

- [ ] **Step 1: Create AppSwitcher component**

```tsx
// dts-platform-webapp/src/layouts/components/app-switcher.tsx
import { Button, Tooltip } from "antd";
import { AppstoreOutlined } from "@ant-design/icons";

export function AppSwitcher() {
	const handleSwitch = () => {
		localStorage.removeItem("dts.portal.preferredApp");
		window.location.href = "/portal";
	};

	return (
		<Tooltip title="切换应用">
			<Button type="text" icon={<AppstoreOutlined />} onClick={handleSwitch} />
		</Tooltip>
	);
}
```

- [ ] **Step 2: Add AppSwitcher to Header**

In `dts-platform-webapp/src/layouts/dashboard/header.tsx`, add the AppSwitcher next to SearchBar (around line 36):

```tsx
import { AppSwitcher } from "../components/app-switcher";

// In the right section (around line 36):
<div className="flex items-center gap-2">
  <SearchBar />
  <AppSwitcher />
  <AccountDropdown />
</div>
```

- [ ] **Step 3: Verify TypeScript compiles**

```bash
cd /opt/prod/s10/s10-stack/source/dts-platform-webapp && npx tsc --noEmit
```

- [ ] **Step 4: Commit**

```bash
git add source/dts-platform-webapp/src/layouts/components/app-switcher.tsx source/dts-platform-webapp/src/layouts/dashboard/header.tsx
git commit -m "feat(F1/T05): add app switcher button to platform-webapp Header"
```

---

## Feature 2: 业务组件 antd 迁移

### Task 6: 迁移 Card 组件（~32 引用）

**Files:**
- Modify: All files importing from `ui/Card/Card`

- [ ] **Step 1: Identify all Card imports**

```bash
cd /opt/prod/s10/s10-stack/source/dts-analytics-webapp/modern
grep -rl "ui/Card/Card" src/ --include="*.tsx" --include="*.ts"
```

- [ ] **Step 2: Replace imports and adapt Props**

For each file, replace:
```tsx
// Before:
import { Card, CardBody, CardHeader, CardFooter } from "../ui/Card/Card";
// After:
import { Card } from "antd";
```

**Props mapping:**
| Custom | antd | Notes |
|--------|------|-------|
| `variant="hoverable"` | `hoverable` | Boolean prop |
| `padding="md"` | `style={{ padding: 16 }}` or `styles={{ body: { padding: 16 } }}` |
| `shadow="sm"` | Remove (antd Card has default shadow) |
| `<CardHeader title="x" />` | `<Card title="x">` | Use Card's `title` prop |
| `<CardBody>` | Remove wrapper (Card body is default) |
| `<CardFooter>` | `<Card actions={[...]}>` or custom div |
| `<CollapsibleCard>` | Custom wrapper with antd Card + collapse state |
| `<StatCard>` | Custom wrapper with antd Card + Statistic |

**For CollapsibleCard:** Create a thin wrapper if still used:
```tsx
// If CollapsibleCard is used, add to the consuming file or a shared util:
import { Card, Typography } from "antd";
import { useState } from "react";
import { DownOutlined, RightOutlined } from "@ant-design/icons";

function CollapsibleCard({ title, defaultOpen = true, children, ...rest }) {
  const [open, setOpen] = useState(defaultOpen);
  return (
    <Card
      title={<span onClick={() => setOpen(!open)} style={{ cursor: "pointer" }}>{open ? <DownOutlined /> : <RightOutlined />} {title}</span>}
      {...rest}
    >
      {open && children}
    </Card>
  );
}
```

**For StatCard:** Replace with antd `<Card><Statistic /></Card>`.

- [ ] **Step 3: Verify TypeScript compiles**

```bash
npx tsc -p tsconfig.json --noEmit
```

- [ ] **Step 4: Commit**

```bash
git add -u source/dts-analytics-webapp/modern/src/
git commit -m "feat(F2/T06): migrate Card component from custom UI to antd"
```

---

### Task 7: 迁移 Badge 组件（~29 引用）

**Files:**
- Modify: All files importing from `ui/Badge/Badge`

- [ ] **Step 1: Identify all Badge imports**

```bash
grep -rl "ui/Badge/Badge" src/ --include="*.tsx" --include="*.ts"
```

- [ ] **Step 2: Replace imports and adapt Props**

```tsx
// Before:
import { Badge, StatusDot, CountBadge } from "../ui/Badge/Badge";
// After:
import { Badge, Tag } from "antd";
```

**Props mapping:**
| Custom | antd | Notes |
|--------|------|-------|
| `<Badge variant="success">text</Badge>` | `<Tag color="success">text</Tag>` | Badge is used as Tag in most cases |
| `<Badge removable onRemove={fn}>` | `<Tag closable onClose={fn}>` |
| `<StatusDot variant="success" label="x" />` | `<Badge status="success" text="x" />` |
| `<CountBadge count={5} />` | `<Badge count={5} />` |

**Important:** The custom `Badge` is used more like antd's `Tag` (text label with color). Map accordingly based on actual usage in each file.

- [ ] **Step 3: Verify TypeScript compiles**

```bash
npx tsc -p tsconfig.json --noEmit
```

- [ ] **Step 4: Commit**

```bash
git add -u source/dts-analytics-webapp/modern/src/
git commit -m "feat(F2/T07): migrate Badge component from custom UI to antd Tag/Badge"
```

---

### Task 8: 迁移 Button 组件（~26 引用）

**Files:**
- Modify: All files importing from `ui/Button/Button`

- [ ] **Step 1: Identify all Button imports**

```bash
grep -rl "ui/Button/Button" src/ --include="*.tsx" --include="*.ts"
```

- [ ] **Step 2: Replace imports and adapt Props**

```tsx
// Before:
import { Button, IconButton, ButtonGroup } from "../ui/Button/Button";
// After:
import { Button, Space } from "antd";
```

**Props mapping:**
| Custom | antd | Notes |
|--------|------|-------|
| `variant="primary"` | `type="primary"` |
| `variant="secondary"` | `type="default"` |
| `variant="tertiary"` | `type="text"` |
| `variant="danger"` | `danger` | Boolean prop |
| `variant="success"` | `type="primary"` + `style={{ backgroundColor: "#52c41a" }}` |
| `size="sm"` | `size="small"` |
| `size="md"` | `size="middle"` (default) |
| `size="lg"` | `size="large"` |
| `loading` | `loading` | Same |
| `icon` | `icon` | Same |
| `iconPosition="right"` | `iconPosition="end"` | antd v5.17+ |
| `fullWidth` | `block` |
| `<IconButton>` | `<Button type="text" icon={...} />` |
| `<ButtonGroup>` | `<Space>` or `<Button.Group>` |

- [ ] **Step 3: Verify TypeScript compiles**

```bash
npx tsc -p tsconfig.json --noEmit
```

- [ ] **Step 4: Commit**

```bash
git add -u source/dts-analytics-webapp/modern/src/
git commit -m "feat(F2/T08): migrate Button component from custom UI to antd"
```

---

### Task 9: 迁移 Spinner → Spin（~31 引用）

**Files:**
- Modify: All files importing from `ui/Loading/Spinner`

- [ ] **Step 1: Identify all Spinner imports**

```bash
grep -rl "ui/Loading/Spinner" src/ --include="*.tsx" --include="*.ts"
```

- [ ] **Step 2: Replace imports and adapt Props**

```tsx
// Before:
import { Spinner, LoadingOverlay, InlineLoading } from "../ui/Loading/Spinner";
// After:
import { Spin } from "antd";
import { LoadingOutlined } from "@ant-design/icons";
```

**Props mapping:**
| Custom | antd | Notes |
|--------|------|-------|
| `<Spinner size="md" />` | `<Spin />` | Default size |
| `<Spinner size="lg" />` | `<Spin size="large" />` |
| `<Spinner label="Loading..." />` | `<Spin tip="Loading..." />` |
| `<LoadingOverlay visible={x} />` | See below |
| `<InlineLoading />` | `<Spin size="small" />` |

**LoadingOverlay replacement (absolute-positioned to avoid CSS height chain issue):**
```tsx
// Replace LoadingOverlay with:
{visible && (
  <div style={{ position: "absolute", inset: 0, display: "flex", alignItems: "center", justifyContent: "center", background: "rgba(255,255,255,0.6)", zIndex: 10 }}>
    <Spin tip={label} />
  </div>
)}
```

The parent container needs `position: relative` for absolute positioning to work.

- [ ] **Step 3: Verify TypeScript compiles**

```bash
npx tsc -p tsconfig.json --noEmit
```

- [ ] **Step 4: Commit**

```bash
git add -u source/dts-analytics-webapp/modern/src/
git commit -m "feat(F2/T09): migrate Spinner/LoadingOverlay to antd Spin with absolute overlay"
```

---

### Task 10: 迁移 Modal 组件（~17 引用）

**Files:**
- Modify: All files importing from `ui/Modal/Modal`

- [ ] **Step 1: Identify all Modal imports**

```bash
grep -rl "ui/Modal/Modal" src/ --include="*.tsx" --include="*.ts"
```

- [ ] **Step 2: Replace imports and adapt Props**

```tsx
// Before:
import { Modal, ConfirmDialog } from "../../../ui/Modal/Modal";
// After:
import { Modal } from "antd";
```

**Props mapping:**
| Custom | antd | Notes |
|--------|------|-------|
| `isOpen` | `open` | Rename |
| `onClose` | `onCancel` | Rename |
| `title` | `title` | Same |
| `description` | Remove or use subtitle pattern |
| `size="sm"` | `width={400}` |
| `size="md"` | `width={520}` (default) |
| `size="lg"` | `width={720}` |
| `size="xl"` | `width={1000}` |
| `size="full"` | `width="100%"` + `style={{ top: 0 }}` |
| `closeOnOverlayClick` | `maskClosable` |
| `closeOnEscape` | `keyboard` |
| `showCloseButton` | `closable` |
| `footer` | `footer` | Same |

**ConfirmDialog replacement:**
```tsx
// Before:
<ConfirmDialog isOpen={x} onClose={fn} onConfirm={fn2} title="x" message="y" variant="danger" />
// After:
Modal.confirm({
  title: "x",
  content: "y",
  okButtonProps: { danger: true },
  onOk: fn2,
  onCancel: fn,
});
// Or use inline Modal with custom footer
```

- [ ] **Step 3: Verify TypeScript compiles**

```bash
npx tsc -p tsconfig.json --noEmit
```

- [ ] **Step 4: Commit**

```bash
git add -u source/dts-analytics-webapp/modern/src/
git commit -m "feat(F2/T10): migrate Modal/ConfirmDialog from custom UI to antd Modal"
```

---

### Task 11: 迁移 Input 组件（~17 引用）

**Files:**
- Modify: All files importing from `ui/Input/Input`

- [ ] **Step 1: Identify all Input imports**

```bash
grep -rl "ui/Input/Input" src/ --include="*.tsx" --include="*.ts"
```

- [ ] **Step 2: Replace imports and adapt Props**

```tsx
// Before:
import { Input, SearchInput, TextArea } from "../ui/Input/Input";
// After:
import { Input, Form } from "antd";
import { SearchOutlined } from "@ant-design/icons";
const { TextArea } = Input;
```

**Props mapping:**
| Custom | antd | Notes |
|--------|------|-------|
| `label` | Wrap with `<Form.Item label="x">` or add label manually |
| `helperText` | `<Form.Item help="x">` |
| `error` | `status="error"` + `<Form.Item help="x" validateStatus="error">` |
| `size="sm"` | `size="small"` |
| `icon` | `prefix` (left) or `suffix` (right) |
| `fullWidth` | `style={{ width: "100%" }}` (default in antd) |
| `<SearchInput>` | `<Input.Search />` |
| `<TextArea rows={4}>` | `<Input.TextArea rows={4}>` |

**Note:** If the custom Input uses `label`/`error` heavily, you may need to wrap with a simple label+error div rather than introducing `Form.Item` (which requires Form context):
```tsx
<div>
  {label && <label style={{ display: "block", marginBottom: 4, fontWeight: 500 }}>{label}</label>}
  <Input status={error ? "error" : undefined} {...rest} />
  {error && <div style={{ color: "#ff4d4f", fontSize: 12, marginTop: 2 }}>{error}</div>}
  {helperText && !error && <div style={{ color: "#999", fontSize: 12, marginTop: 2 }}>{helperText}</div>}
</div>
```

- [ ] **Step 3: Verify TypeScript compiles**

```bash
npx tsc -p tsconfig.json --noEmit
```

- [ ] **Step 4: Commit**

```bash
git add -u source/dts-analytics-webapp/modern/src/
git commit -m "feat(F2/T11): migrate Input/SearchInput/TextArea from custom UI to antd"
```

---

### Task 12: 迁移 Select 组件（~9 引用）

**Files:**
- Modify: All files importing from `ui/Input/Select`

- [ ] **Step 1: Identify all Select imports**

```bash
grep -rl "ui/Input/Select" src/ --include="*.tsx" --include="*.ts"
```

- [ ] **Step 2: Replace imports and adapt Props**

```tsx
// Before:
import { Select, NativeSelect } from "../ui/Input/Select";
// After:
import { Select } from "antd";
```

**Props mapping:**
| Custom | antd | Notes |
|--------|------|-------|
| `options: SelectOption[]` | `options: { value, label }[]` | Format may differ |
| `value` | `value` | Same |
| `onChange(value)` | `onChange(value)` | Same |
| `searchable` | `showSearch` |
| `searchPlaceholder` | Not directly available, use `filterOption` |
| `placeholder` | `placeholder` | Same |
| `disabled` | `disabled` | Same |
| `fullWidth` | `style={{ width: "100%" }}` |
| `label` / `error` | Same wrapper pattern as Input (Task 11) |
| `NativeSelect` | `<Select>` | Replace with antd Select |

**Custom options format adaptation:**
```tsx
// If custom SelectOption is { value: string; label: string }
// antd uses the same format — no change needed
// If custom uses { id, name } or similar, map:
options={items.map(i => ({ value: i.id, label: i.name }))}
```

- [ ] **Step 3: Verify TypeScript compiles**

```bash
npx tsc -p tsconfig.json --noEmit
```

- [ ] **Step 4: Commit**

```bash
git add -u source/dts-analytics-webapp/modern/src/
git commit -m "feat(F2/T12): migrate Select/NativeSelect from custom UI to antd Select"
```

---

### Task 13: 迁移剩余组件（Skeleton/Tabs/Dropdown/Drawer/Checkbox）

**Files:**
- Modify: All files importing from `ui/Loading/Skeleton`, `ui/Tabs/Tabs`, `ui/Dropdown/Dropdown`, `ui/Drawer/Drawer`, `ui/Input/Checkbox`

- [ ] **Step 1: Identify all remaining imports**

```bash
grep -rl "ui/Loading/Skeleton\|ui/Tabs/Tabs\|ui/Dropdown/Dropdown\|ui/Drawer/Drawer\|ui/Input/Checkbox" src/ --include="*.tsx" --include="*.ts"
```

- [ ] **Step 2: Migrate each component**

**Skeleton (~3 refs):**
```tsx
// Before:
import { Skeleton, CardSkeleton, TableSkeleton } from "../ui/Loading/Skeleton";
// After:
import { Skeleton, Card } from "antd";
// CardSkeleton → <Card loading={true}><Skeleton active /></Card>
// TableSkeleton → <Skeleton active paragraph={{ rows: 5 }} />
```

**Tabs (~2 refs):**
```tsx
// Before:
import { Tabs, TabList, Tab, TabPanels, TabPanel } from "../ui/Tabs/Tabs";
// Or: import { SimpleTabs } from "../ui/Tabs/Tabs";
// After:
import { Tabs } from "antd";

// SimpleTabs → <Tabs items={items.map(i => ({ key: i.value, label: i.label, children: i.content }))} />
// Context-based Tabs → convert to antd items API
```

**Dropdown (~1 ref):**
```tsx
// Before:
import { Dropdown, DropdownItem, DropdownSeparator } from "../ui/Dropdown/Dropdown";
// After:
import { Dropdown } from "antd";
// Convert children to menu items:
// <Dropdown menu={{ items: [...] }} trigger={["click"]}>
```

Note: The Dropdown in AppLayout.tsx was already replaced in Task 4. Check if there are other usages.

**Drawer (~1 ref):**
```tsx
// Before:
import { Drawer } from "../../../ui/Drawer/Drawer";
// After:
import { Drawer } from "antd";
// isOpen → open
// onClose → onClose (same)
// size → width (sm=320, md=480, lg=640, xl=800)
```

**Checkbox (~1 ref):**
```tsx
// Before:
import { Checkbox, Radio, Toggle } from "../../ui/Input/Checkbox";
// After:
import { Checkbox, Radio, Switch } from "antd";
// Toggle → Switch
// description prop → add description text manually
```

- [ ] **Step 3: Verify TypeScript compiles**

```bash
npx tsc -p tsconfig.json --noEmit
```

- [ ] **Step 4: Commit**

```bash
git add -u source/dts-analytics-webapp/modern/src/
git commit -m "feat(F2/T13): migrate Skeleton/Tabs/Dropdown/Drawer/Checkbox to antd"
```

---

### Task 14: 删除 ui/ 目录 + ThemeToggle 清理

**Files:**
- Delete: `dts-analytics-webapp/modern/src/ui/` (entire directory)
- Modify: `dts-analytics-webapp/modern/src/hooks/useTheme.tsx` (keep for now, remove usage later)

- [ ] **Step 1: Verify no remaining imports from ui/**

```bash
grep -r "from.*ui/" src/ --include="*.tsx" --include="*.ts" | grep -v "node_modules" | grep -v "antd"
```

Expected: No matches (all custom UI imports should be gone)

- [ ] **Step 2: Remove ThemeToggle references**

Check if any file still imports ThemeToggle:
```bash
grep -r "ThemeToggle" src/ --include="*.tsx" --include="*.ts"
```

Remove any remaining references. The `useTheme` hook in `hooks/useTheme.tsx` can stay (it may be used for other purposes), but ThemeToggle UI component should be fully removed.

- [ ] **Step 3: Delete the entire ui/ directory**

```bash
rm -rf /opt/prod/s10/s10-stack/source/dts-analytics-webapp/modern/src/ui/
```

- [ ] **Step 4: Verify build**

```bash
cd /opt/prod/s10/s10-stack/source/dts-analytics-webapp/modern
npx tsc -p tsconfig.json --noEmit && npm run build
```

- [ ] **Step 5: Commit**

```bash
cd /opt/prod/s10/s10-stack
git add -u source/dts-analytics-webapp/modern/src/ui/
git add source/dts-analytics-webapp/modern/src/
git commit -m "feat(F2/T14): remove custom ui/ directory, complete antd migration"
```

---

## Feature 3: 侧边导航统一

### Task 15: AppLayout 侧边栏替换为 antd Sider + Menu

**Files:**
- Modify: `dts-analytics-webapp/modern/src/layouts/AppLayout.tsx`
- Delete: `dts-analytics-webapp/modern/src/components/SidebarNav/` (after replacement)

- [ ] **Step 1: Rewrite AppLayout with antd Layout**

Replace the entire layout structure in AppLayout.tsx:

```tsx
import { Layout, Menu, Button, Dropdown, Space, Tooltip, theme } from "antd";
import type { MenuProps } from "antd";
import {
  HomeOutlined,
  LineChartOutlined,
  QuestionCircleOutlined,
  DashboardOutlined,
  FolderOutlined,
  DatabaseOutlined,
  AppstoreOutlined,
  BarChartOutlined,
  DesktopOutlined,
  ProjectOutlined,
  SearchOutlined,
  UserOutlined,
  LogoutOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  DeleteOutlined,
} from "@ant-design/icons";

const { Header, Sider, Content } = Layout;

// Menu items configuration
const menuSections: MenuProps["items"] = [
  // Core section
  {
    key: "core-group",
    type: "group",
    label: t("nav.section.core"),
    children: [
      { key: "/", icon: <HomeOutlined />, label: t("nav.home") },
      { key: "/analyze", icon: <LineChartOutlined />, label: t("nav.analyze") },
      { key: "/questions", icon: <QuestionCircleOutlined />, label: t("nav.questions") },
      { key: "/dashboards", icon: <DashboardOutlined />, label: t("nav.dashboards") },
      { key: "/collections", icon: <FolderOutlined />, label: t("nav.collections") },
    ],
  },
  { type: "divider" },
  // Data section
  {
    key: "data-group",
    type: "group",
    label: t("nav.section.data"),
    children: [
      { key: "/data", icon: <DatabaseOutlined />, label: t("nav.data") },
      { key: "/models", icon: <AppstoreOutlined />, label: t("nav.models") },
      { key: "/metrics", icon: <BarChartOutlined />, label: t("nav.metrics") },
      { key: "/trash", icon: <DeleteOutlined />, label: t("nav.trash") },
    ],
  },
  { type: "divider" },
  // Tools section
  {
    key: "tools-group",
    type: "group",
    label: t("nav.section.tools"),
    children: [
      { key: "/screens", icon: <DesktopOutlined />, label: t("nav.screens") },
      { key: "/project-cockpit", icon: <ProjectOutlined />, label: "项目看板" },
      { key: "/search", icon: <SearchOutlined />, label: t("nav.search") },
    ],
  },
];
```

**Layout structure:**
```tsx
const [collapsed, setCollapsed] = useState(false);
const location = useLocation();
const navigate = useNavigate();

return (
  <Layout style={{ minHeight: "100vh" }}>
    <Sider
      collapsible
      collapsed={collapsed}
      onCollapse={setCollapsed}
      trigger={null}
      width={220}
      collapsedWidth={64}
      style={{
        overflow: "auto",
        height: "100vh",
        position: "sticky",
        top: 0,
        left: 0,
      }}
    >
      {/* Logo area */}
      <div style={{ height: 48, display: "flex", alignItems: "center", justifyContent: "center", padding: "0 16px" }}>
        {collapsed ? <img src={logoIcon} height={24} /> : <img src={logoFull} height={24} />}
      </div>

      <Menu
        mode="inline"
        selectedKeys={[location.pathname]}
        items={menuSections}
        onClick={({ key }) => navigate(key)}
        style={{ borderRight: 0 }}
      />

      {/* Collapse toggle at bottom */}
      <div style={{ position: "absolute", bottom: 0, width: "100%", borderTop: "1px solid #f0f0f0", padding: 8, textAlign: "center" }}>
        <Button
          type="text"
          icon={collapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
          onClick={() => setCollapsed(!collapsed)}
        />
      </div>
    </Sider>

    <Layout>
      <Header style={{ ... }}>
        {/* Header content from Task 4 */}
      </Header>
      <Content style={{ padding: 16, overflow: "auto" }}>
        <ErrorBoundary>
          <Outlet />
        </ErrorBoundary>
      </Content>
    </Layout>
  </Layout>
);
```

- [ ] **Step 2: Remove SidebarNav imports**

Remove all imports of `SidebarNav`, `SidebarProvider`, `useSidebar`, `SidebarSection`, `SidebarItem`, `SidebarButton`, `SidebarDivider`, `SidebarSearch` from AppLayout.tsx.

- [ ] **Step 3: Delete SidebarNav component directory**

```bash
rm -rf /opt/prod/s10/s10-stack/source/dts-analytics-webapp/modern/src/components/SidebarNav/
```

Also check if SidebarNav is imported anywhere else:
```bash
grep -r "SidebarNav" src/ --include="*.tsx" --include="*.ts"
```

- [ ] **Step 4: Delete SidebarNav CSS**

The CSS custom properties in `SidebarNav.css` are no longer needed. Verify no other component depends on them:
```bash
grep -r "sidebar-width\|sidebar-collapsed-width" src/ --include="*.css" --include="*.tsx"
```

- [ ] **Step 5: Verify build**

```bash
cd /opt/prod/s10/s10-stack/source/dts-analytics-webapp/modern
npx tsc -p tsconfig.json --noEmit && npm run build
```

- [ ] **Step 6: Commit**

```bash
cd /opt/prod/s10/s10-stack
git add -u source/dts-analytics-webapp/modern/src/components/SidebarNav/
git add source/dts-analytics-webapp/modern/src/layouts/AppLayout.tsx
git commit -m "feat(F3/T15): replace custom SidebarNav with antd Layout.Sider + Menu"
```

---

## Final Verification

### Task 16: 全量构建验证

- [ ] **Step 1: Build analytics-webapp**

```bash
cd /opt/prod/s10/s10-stack/source/dts-analytics-webapp/modern
npm run build
```

Expected: Build succeeds with no errors.

- [ ] **Step 2: Build platform-webapp**

```bash
cd /opt/prod/s10/s10-stack/source/dts-platform-webapp
npm run build
```

Expected: Build succeeds with no errors.

- [ ] **Step 3: Verify no custom UI imports remain**

```bash
cd /opt/prod/s10/s10-stack/source/dts-analytics-webapp/modern
grep -r "from.*['\"].*ui/" src/ --include="*.tsx" --include="*.ts" | grep -v "node_modules" | grep -v "antd"
```

Expected: No matches.

- [ ] **Step 4: Verify ui/ directory is deleted**

```bash
ls -la /opt/prod/s10/s10-stack/source/dts-analytics-webapp/modern/src/ui/ 2>&1
```

Expected: "No such file or directory"

- [ ] **Step 5: Commit final state**

```bash
git commit -m "chore(sprint-21): final build verification passed"
```
