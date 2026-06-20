# Sprint-1.5 集成验证（IT）

**状态**: PASS（2026-06-20，Playwright + 构建）

| 用例 | 验证点 | 结果 | 证据 |
|------|--------|------|------|
| IT-1 | 顶栏「工作区 ▸ 项目」两级渲染 | PASS | 无障碍快照：`team 销售处` ▸ `folder-open 销售准备项目` |
| IT-2 | 切换工作区→项目级联切换 | PASS | 切「质量处」→ 顶栏变「质量处 ▸ 质量月报项目」 |
| IT-3 | 切换工作区→阶段状态重新派生 | PASS | 销售处=①完成/②进行中；质量处=四阶段全完成、门户"主线已贯通" |
| IT-4 | 数据源 scope 归属 + 工作区过滤 | PASS | 连接页：平台共享源(PLM/ERP/QMIS)=3 + 本部门本地源·质量处=1（不串销售处本地源） |
| IT-5 | 连接阶段"选源绑定"语义 | PASS | 每源「绑定到项目」按钮；文案"物理接入与密钥在平台/工作区层统一管控" |
| IT-6 | 重置样例数据复位两级 | PASS | DevReset 重载 workspaces + projects |
| IT-7 | tsc + Chrome 95 legacy 构建 | PASS | `tsc --noEmit` 通过；`vite build` 通过；产物零 oklch/:has/容器查询/subgrid |

## 证据位置
- 截图：`../../sprint-1-地基/it/evidence/s15-connect-ownership.png`
- 无障碍快照：`/opt/prod/s10/v2.2.3/.playwright-mcp/page-2026-06-20T03-*.yml`

## 退出标准
- [x] 三层模型在外壳与数据层贯通，S2「连接」可在正确的"选源绑定"语义上开建
