# F0：交付基线与事实对账

**优先级**：P0
**状态**：IN_PROGRESS

## 目标

在任何生产 adapter、迁移 apply 或 UI 编码前，证明真实登录、受保护 API、代表性模型/质量/血缘样本和回滚路径可用，并把存量自动解析比例固化为后续任务输入。

## 契约定义

| 类型 | 契约 | 关键字段/输出 |
|---|---|---|
| 基线 | `it/baseline.md` | P1～P8 的实际命令、时间、环境、结果 |
| 数据画像 | `assets/domain-profile.md` | 资产/投影/血缘/质量数量、分布、空值、歧义率 |
| 迁移 preview | 既有 normalization preview owner | `previewHash,totalPending,rows,truncated`，不得 apply |
| 验收样本 | 隔离前缀 `E2E_GOV_202608_*` | ODS→DWD→DWS→ADS、pass/fail/expired 质量、字段血缘 manifest |

## UI/UX 规格

- 从真实菜单依次打开模型工作台、资产概览、资产目录、资产详情、元数据、血缘和质量页面。
- 本 Feature 不改 UI，只记录可达性、权限、空/错误态和当前网络请求。
- Chrome 95 未通过时，不阻塞纯后端契约任务，但 F5/F6 不得进入 READY。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|---|---|---|---|---|
| T01 | 验证真实登录、验收样本与迁移画像基线 | P0 | IN_PROGRESS | 无 |

## Definition of Ready

- [x] 探针、输出文件和失败语义已定义。
- [x] 使用隔离前缀且不修改既有 PJM/研究所业务数据。
- [x] 只读统计与 preview 可安全执行。
- [x] apply 明确不属于本 Task。

## 完成标准

- [ ] P1～P8 每项都有真实证据。
- [ ] producer/evidence 可解析、歧义、缺失比例已量化。
- [ ] 一条具备字段血缘的真实模型链及三类质量样本可重复使用。
- [ ] 受影响 Feature 状态按结果更新，未通过项不被写成 PASS。
