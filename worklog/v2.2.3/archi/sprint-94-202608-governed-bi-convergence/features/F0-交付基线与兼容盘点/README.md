# F0：交付基线与兼容盘点

**优先级**：P0
**状态**：IN_PROGRESS（T01/T02 工程基线已推进；T03 缺目标角色；T04 已完成本地 dry-run，待目标安装预检）

## 目标

在触碰平台数据集、Analytics 安全配置和旧 Card 数据前，恢复可重复的索引/构建/测试路径，复验当前登录/API 验收通道，建立当前能力可支持的隔离基线，并把静态分母与目标环境观测缺口分开记录。

本 Feature 同时冻结升级历史边界：调用量不再阻断旧 BI 退役，唯一 durable 历史是大屏全链。F0 只交付预检和 dry-run，不在基线阶段执行生产 DELETE。

## 输入与输出契约

| 类型 | 输入 | 输出 |
|---|---|---|
| 工程基线 | 当前分支、普通工作用户、两个后端模块、webapp、GitNexus | fresh index、per-symbol impact 模板、可重复聚焦测试命令与权限修复记录 |
| 当前实例基线 | 当前会话、Query Dataset/Card/ReportLink 现有 API、25 个 DWS/ADS 关系 | Sprint-94 当前登录/API 复验、published/stale/denied 数据集输入、legacy Card 三分类输入、清理 dry-run |
| 旧 BI Contract 盘点 | 菜单、静态/动态 route、API prefix、public/embed 端点 | `../../assets/route-inventory.md` 32 行分母、后端旧写清单；F6/T03 据此形成删除清单 |
| 目标环境观测 | A1～A4、只读资产统计、代理/应用指标及其保留窗口 | 角色矩阵、容量/P95/并发分布、逐 surface 的 source/window/value 或 UNKNOWN 原因；不输出敏感值 |

## UI/UX 规格

- 本 Feature 不改变用户 UI；只通过真实菜单验证四个主入口、鉴权和当前四态。
- Chrome 95 未取得时，后端契约探索可继续，但所有用户可见 Task 仍为 DRAFT/BLOCKED。
- 兼容路由盘点必须区分“菜单可见”“直接 URL 可达”“生产调用仍存在”，不得根据静态 route 猜测使用量。

## Task

| ID | Task | 优先级 | 状态 | 依赖 |
|---|---|---|---|---|
| T01 | 恢复索引构建与测试基线 | P0 | READY | 无 |
| T02 | 建立本地 BI 基线样本与 S0 静态盘点 | P0 | READY | 无；若需新增 seed 工具则先等待 T01 |
| T03 | 取得目标环境角色、容量与调用观测 | P0 | BLOCKED | 目标环境只读权限、A1～A4、观测来源与保留窗口 |
| T04 | 固化大屏保留边界与旧 BI 清理预检 | P0 | IN_PROGRESS | T01、目标安装只读数据库访问 |

## Definition of Ready

- [x] 当前失败命令、权限 owner、索引刷新现象和安全边界已记录在 `it/baseline.md`。
- [x] 隔离样本命名、最少分布和禁止修改的业务数据边界已定义。
- [x] 本 Feature 只允许安全的权限修复、只读画像和隔离 fixture，不允许迁移 apply。
- [x] Sprint-93 `xiezm` 证据只作为复验步骤输入；Sprint-94 当前登录与受保护 API 结论由 T02 重新执行。
- [x] 旧路由静态分母已建（`../../assets/route-inventory.md` 32 行）；T02 补动态菜单和后端旧写面清单。
- [ ] 目标环境只读统计、A1～A4 与调用指标访问已授权（未满足时 T03 保持 BLOCKED，不混入 T02）。
- [ ] Chrome 95 可用（**不阻断 T02**；仅阻断 F6/T01）。

## 完成标准

- [ ] 普通工作用户可重复运行聚焦测试，GitNexus fresh，impact/detect_changes 路径可用。
- [ ] 当前 QueryDataset 与 legacy Card 基线输入可重复使用并有清理/回滚说明；Analysis/Dashboard/Screen 目标 fixture 由各能力 owner 后置建立。
- [ ] `route-inventory.md` 32 行静态分母完整；每个旧 surface 记录观测来源、可用窗口、实测值或 UNKNOWN 原因，未知调用不被写成零。
- [ ] 后端旧写面盘点完成，与前端路由表分开记录。
- [ ] T03 将目标环境结果回填 `domain-profile.md` 与 `nfr-budget.md`；历史调用缺失不阻断退役，但目标容量和角色仍阻断发布验收。
- [ ] 大屏全历史引用预检和默认 ROLLBACK 清理脚本可重复；生产 apply 仍由 F6/T03 独立发布。
