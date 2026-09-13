# Sprint-89 G0 交付基线

**探测时间**: 2026-08-10
**结论**: **PASS_WITH_GAPS**。服务、数据库、门户和未登录保护边界可证明；真实登录、成功 API 调用、Chrome 95 点击链及元数据→规划绑定样本缺失。

## Probe 结果

| Probe | 状态 | 当前证据 | 处置 |
|---|---|---|---|
| P1 可运行实例/健康 | PASS | `v223-dts-platform-1` health=healthy；`GET 127.0.0.1:8081/management/health` 返回 `UP` | 可开展后端契约工作 |
| P2 登录 | BLOCKED_INPUT | SSO 入口经代理返回 302；本轮没有授权账号/密码，不尝试默认凭据 | F0/T01 补输入 |
| P3 数据库/schema | PASS | `dts_platform` 可读；四张 catalog/modeling 关键表均存在 | 见 `assets/domain-profile.md` |
| P4 代表数据 | GAP | catalog 有 83/78/1510 数据，但 `modeling_warehouse_plan_source=0` | F0/T01 建立非敏感真实绑定样本 |
| P5 API harness | PARTIAL | dts-platform 内部访问保护端点返回 `401 Unauthorized`；没有授权成功响应 | F3/F4 阻塞 |
| P6 UI harness | PARTIAL | `https://bi.yuzhicloud.com/` HTTP 200；未完成登录和菜单点击 | F3/F4 阻塞 |
| P7 构建/测试 | GAP | 本轮为 Sprint 建档，按“一次集中验证”约束未提前跑全量构建 | F4/T01 统一执行 |
| P8 外部依赖 | PASS_SCOPE | PostgreSQL 与平台容器运行；首个 P0 切片不依赖 OpenMetadata | 后续若引入需重新登记 |

## 域名与运行边界

- `BASE_DOMAIN=yuzhicloud.com`
- 平台 UI：`bi.yuzhicloud.com`
- API：`api.yuzhicloud.com`（主机当前 DNS 未解析，容器内直连可探测；验收需补正确 host/proxy 路径）
- SSO：`sso.yuzhicloud.com`
- 平台保护端点：未登录返回 401，符合 fail-closed 预期

## 阻塞输入

| # | 输入 | 最小要求 | 解锁 |
|---|---|---|---|
| B01 | 授权账号 | 可读取 Catalog、进入数据建模并维护当前 WarehousePlan 来源；不得输出 token/身份详情 | F3、F4/T02 |
| B02 | 代表来源 | 一个可重复采集、允许临时增删字段的非敏感 PostgreSQL/MySQL schema | IT-02/03/08 |
| B03 | 生产脱敏画像 | 量级、最大字段数、drift 分布、同步窗口 | NFR 最终阈值 |
| B04 | Chrome 95 环境 | 客户站或等效容器/浏览器 | G4 浏览器验收 |

## 准入结论

- F1 后端身份契约可进入 READY：本地 schema、单测入口和代码 seam 均存在，不依赖授权 UI。
- F2 在 F1/T01 冻结测试后开始。
- F3 和 F4/T02 保持 BLOCKED，直到 B01/B02/B04 到位。
- 不使用 `modeling_warehouse_plan_source=0` 的空结果证明端到端功能正常。
