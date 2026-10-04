# F0：评审与可验收基线

**优先级**：P0
**状态**：DRAFT

## 目标

在编码前锁定三件事：统计口径能被验证、按钮数量能被约束、现状缺陷有可复现的证据。避免本 Sprint 变成"改了界面但数字仍然对不上"。

## 契约定义

| 类型 | 契约 | 关键签名 |
|---|---|---|
| 一致性判据 | `domainStats` vs `listAssets` | 同一 `activeDept` + 同一 `domainId` 下，`byDomain[d].total` 必须等于 `listAssets({domainId: d}).total` |
| 控件约束 | 地图页 chrome | 契约测试统计 `AssetOverviewPage.tsx` 中 `<Button` 出现次数 ≤1 |
| 缺陷证据 | truncated 误报 | 用 360 条全未归域的数据集复现现状警告，作为修复前后的对照 |

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|---|---|---|---|---|
| T01 | 建立统计口径一致性判据与测试夹具 | P0 | DRAFT | — |
| T02 | 复现并记录 truncated 误报证据 | P0 | DRAFT | — |
| T03 | 建立控件数量与菜单唯一性门禁 | P0 | DRAFT | — |

## Definition of Ready

- [x] service 层测试脚手架**已存在**，复用不新建：`CatalogAssetPortalServicePermissionParityTest`（`@ExtendWith(MockitoExtension)` + 全 repository mock + `AccessChecker` mock）与 `CatalogAssetPortalTagFilterTest`；聚合器侧有 `CatalogAssetOverviewAggregatorTest`
- [ ] 已确认测试数据集能构造"OM 资产 + legacy 资产共存"场景（L07 的触发条件）
- [ ] 已确认 `role-menu-defaults.json` 中台账条目的角色范围，作为 G-75-04 的断言基线

## Definition of Done

- [ ] G-75-01 判据可执行且当前为 FAIL（证明它有区分力）
- [ ] G-75-02 的误报证据已记入 `assets/`
- [ ] G-75-03、G-75-04 门禁已落成测试且当前 G-75-03 为 FAIL（现状 3 个按钮）
