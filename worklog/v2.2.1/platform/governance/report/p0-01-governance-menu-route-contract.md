# P0-01 治理中心菜单-路由-页面契约

更新时间：2026-02-22

## 契约范围

- 菜单种子：`source/dts-admin/src/main/resources/config/data/portal-menu-seed.json`
- 角色默认授权：`source/dts-admin/src/main/resources/config/data/role-menu-defaults.json`
- 前端动态路由：`source/dts-platform-webapp/src/routes/sections/dashboard/dynamic-resolver.tsx`

## 治理中心映射表

| 菜单 code | 路由 route | 页面组件 |
|---|---|---|
| `sys.nav.portal.governanceSubjects` | `/governance/subjects` | `/pages/governance/SubjectAreasPage` |
| `sys.nav.portal.governanceGlossary` | `/governance/standards/glossary` | `/pages/governance/GlossaryPage` |
| `sys.nav.portal.governanceElements` | `/governance/standards/elements` | `/pages/governance/ElementsPage` |
| `sys.nav.portal.governanceReference` | `/governance/standards/reference` | `/pages/governance/ReferenceCodesPage` |
| `sys.nav.portal.governanceTemplates` | `/governance/templates` | `/pages/governance/TemplatesPage` |
| `sys.nav.portal.governanceIndicators` | `/governance/indicators/dictionary` | `/pages/governance/IndicatorsPage` |
| `sys.nav.portal.governanceQualityRules` | `/governance/rules` | `/pages/governance/QualityRulesPage` |
| `sys.nav.portal.governanceQualityReport` | `/governance/quality` | `/pages/governance/QualityReportPage` |

## 说明

- `质量报告`入口已固定归属治理中心，并由 `QualityReportPage` 承接，避免落到目录中心页面语义。
- 角色默认授权仍按原角色矩阵生效，本次未扩大授权范围。
- 回归检查项：菜单可见性、路由匹配、页面落点一致性。
