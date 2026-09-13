# T02：建立本地 BI 基线样本与 S0 静态盘点

- **优先级**：P0
- **状态**：READY
- **依赖**：无；若现有 API 无法建立样本而需要新增 seed 工具，则先等待 T01 完成 fresh impact 基线

## 可测试目标

在不修改现有 PJM/客户业务数据的前提下，复验 Sprint-94 当前会话的登录与受保护 API 通道，使用现有能力建立 QueryDataset 与 legacy Card 基线输入，并完成 32 条前端路由、动态菜单和后端旧写面的静态盘点。不得预造尚未实现的 Analysis/Dashboard/Screen 目标 fixture。

## 输入、输出与安全契约

| 项目 | 契约 |
|---|---|
| 输入 | Sprint-93 登录步骤、当前 Sprint 运行实例、现有 25 个 DWS/ADS 关系、QueryDataset/Card 现有 API、静态路由与菜单种子 |
| 输出 | 当前会话/API 复验证据、DS-PUBLISHED/STALE/DENIED 输入、legacy Card 三分类输入、动态菜单差异、后端旧写 surface 清单、清理 dry-run |
| 写入边界 | 只创建 `E2E_BI_202608_*` 隔离资产；保留匹配既有行；禁止 truncate/reset/drop |
| 敏感边界 | 只记录计数、ID、状态和 hash；不导出业务行值、SQL、凭据或成员名单 |
| 失败 | 登录/API 复验失败则登记 G0 blocker；无法以现有 API 建立的 fixture 移交对应 Feature，不直接写库伪造目标状态 |

## 实施步骤

1. 按 Sprint-93 的步骤重新登录当前 Sprint 实例，记录 `/api/session/status` 和至少一个受保护 Platform/Analytics 请求；不复用旧 PASS 结论。
2. 用真实账号走查四个菜单入口，并核对动态菜单是否暴露 `route-inventory.md` 中标记“菜单 ✗”的路由。
3. 盘点后端旧 surface：`/api/card` write、MBQL execute、public/embed、VDS create/update；记录 method/path/owner/当前开关，不统计不存在的历史调用。
4. 通过现有 QueryDataset API 在 DWS/ADS 上建立 DS-PUBLISHED、DS-STALE 和 DS-DENIED 输入；不得复制源表。
5. 通过当前 Card/MBQL 兼容能力建立 convertible、legacy-read-only、invalid 三类输入；不写 `dts.analysis/v1`。
6. 对每个样本记录 ID、版本、当前可证明状态和后续 owner；尚未实现的 contract checksum 标记 `NOT_AVAILABLE_BEFORE_F1`，不得伪造。
7. 执行清理 dry-run，证明只命中隔离前缀；本 Task 不执行 redirect、flag 改动或迁移 apply。

## RED → GREEN

- RED：Sprint-94 当前登录/API 未复验；QueryDataset/Card 为空；动态菜单与后端旧写 surface 没有独立清单。
- GREEN：当前会话和受保护 API 可重现；数据集与 legacy 输入可由现有 API 创建/清理；32 条静态路由、动态菜单差异和后端旧写 surface 可逐项对账。

## 影响范围

- 平台/Analytics 隔离 fixture 与测试证据；默认不修改业务源码。
- 若确需补 seed 工具，必须先完成 T01、逐符号 impact，并另行把本 Task 改回 DRAFT 直至工具契约钉定。

## Definition of Done

- [ ] Sprint-94 当前登录、session 与受保护 API 复验有命令/响应证据。
- [ ] DS-PUBLISHED/STALE/DENIED 和 legacy 三分类输入均由现有 API 建立，结果经人工复核。
- [ ] Analysis/Dashboard/Screen 目标 fixture 未被 F0 伪造，ownership 与建立阶段已回写 `domain-profile.md`。
- [ ] 32 条静态路由、动态菜单与后端旧写 surface 清单完整。
- [ ] 无现有业务行被更新/删除；清理 dry-run 只命中隔离前缀。
- [ ] 本 Task 未产生用户行为变更：无重定向、无 flag 改动、无迁移 apply、无删除。
