# 2026-09-12 Jira 开放工单继续处理

查询：`status = Open ORDER BY priority DESC, updated DESC`，本轮开始共 16 张，全部为 S10DC。使用 xiezm 登录 Jira；每单追加本轮根因证据、复现方法、排查路径和依赖条件。未完成真实验收的工单保留开放。

## 本轮范围和结论

| 工单 | 本轮证据与处理 | 尚需条件 |
|---|---|---|
| [67](https://jira.yuzhicloud.com/browse/S10DC-67) | 菜单迁移链核对；评论 10782 | 55/65/67 一致的最终菜单树与角色可见范围 |
| [65](https://jira.yuzhicloud.com/browse/S10DC-65) | 治理/资产目录拆分的迁移与授权依赖；评论 10783 | 最终父子菜单、旧路由和授权继承规则 |
| [63](https://jira.yuzhicloud.com/browse/S10DC-63) | 更正“系统没有权限模型”；已有资产授权与 ADS 编辑授权不是同一权限；评论 10784 | 集市/卡片对象、动作矩阵及分角色账号 |
| [61](https://jira.yuzhicloud.com/browse/S10DC-61) | F9 已确认部分规则；现有 canMaintain 缺少部门/ADS资源校验；评论 10785 | F9-T01 现场基线与 Q4–Q7、正式权限链实施和验收 |
| [60](https://jira.yuzhicloud.com/browse/S10DC-60) | 新建接入目标与绑定已有模型的归属来源待统一；评论 10786 | 业务域必填/继承规则和历史资产补录方案 |
| [59](https://jira.yuzhicloud.com/browse/S10DC-59) | 执行成功与资产登记分阶段，assetProjection 只读；评论 10787 | 原任务、采集日志、自动登记时机和重试规则 |
| [57](https://jira.yuzhicloud.com/browse/S10DC-57) | 现有入湖服务端已有部门/对象/密级准入，不能用目录读授权替代执行授权；评论 10788 | 按人授权的对象、动作、有效期、撤销/审批矩阵 |
| [56](https://jira.yuzhicloud.com/browse/S10DC-56) | 更正旧评论：当前禁止字段低于文件密级，与 F9 D4 一致；评论 10789 | 确认是否仅简化逐字段填写，保留只升不降封存 |
| [55](https://jira.yuzhicloud.com/browse/S10DC-55) | 历史升根菜单后又被后续迁移重挂 consumption；评论 10790 | 与65/67统一前向迁移及角色授权规则 |
| [54](https://jira.yuzhicloud.com/browse/S10DC-54) | 已有岗位识别；待办中心统一读取并按类型过滤，待核后端范围；评论 10791 | 各岗位可见/办理口径、待办样本与账号 |
| [32](https://jira.yuzhicloud.com/browse/S10DC-32) | 两条资产质量读取路径仍只读规则运行，缺少工作流聚合；评论 10792 | 聚合/回退口径、两条规则的真实工作流及资产验收 |
| [87](https://jira.yuzhicloud.com/browse/S10DC-87) | 匿名部门/密级门槛，另有快照过期拒绝；更正有效期检查顺序；评论 10793 | 原分享链接、拒绝审计、登录分享/匿名范围规则 |
| [51](https://jira.yuzhicloud.com/browse/S10DC-51) | 导入硬编码禁用；关联依赖持久化 base，非简单权限问题；评论 10794 | 来源字段固定版本/密级/映射及关联语义契约 |
| [66](https://jira.yuzhicloud.com/browse/S10DC-66) | 提供隔离恢复、升级、逐对象核对与回退演练步骤；评论 10795 | 现场版本、数据库/资源/配置备份、对象清单和窗口 |
| [64](https://jira.yuzhicloud.com/browse/S10DC-64) | 确认数据库接入 TableInfo/TableMeta 均无表别名字段；评论 10796 | 别名来源、需覆盖的选择器、带注释源表 |
| [58](https://jira.yuzhicloud.com/browse/S10DC-58) | 已实现大屏管理名称点击右侧预览；评论 10797 | 正式镜像/交付、部署、真实账号与 Chrome 95 验收 |

## S10DC-58 源码和验证

- 修复提交：`2e42daa8e76bc8e2fe4fdf5990d0b42e0d5d736e`，已 push；`/data/dts-stack` 已 `git pull --ff-only` 并核对同 SHA。
- 根因：`ScreensPage` 名称列为 `target="_blank"` 链接，没有页内预览状态。新增一个共享右侧抽屉和名称选择组件，复用既有 `/bi/screens/{id}/preview?embed=1&scaleMode=fit`；切换名称替换预览，关闭即释放 iframe。后端权限、密级与预览默认模式保持既有语义。
- GitNexus：编辑前 `ScreensPage` upstream impact 为 LOW，直接调用者/流程均为 0；路由入口仍属人工核对范围。目标页面与旧索引提交 `8c7023572` 的差异为空。提交前 detect_changes：4 文件、`screenColumns`/`ScreensPage`、risk low。
- 全仓索引刷新遇到多次解析超时并回退串行，持续运行后仍未完成，已停止本轮刷新；不宣称获得最新全仓索引。目标页面无索引基线差异的核对和上述影响分析均已执行，不能把旧图的“0流程”解释为没有页面影响。
- 源码契约：17/17；组件交互：1/1，覆盖首次点击、切换、禁止读取、关闭释放、不打开新页签。
- 依赖安装：原 npm 连接超时，使用 registry.npmmirror.com 与锁文件完整性校验完成安装；未修改依赖清单/锁文件。
- 正式 `pnpm build`：通过，含 TypeScript 与 `LEGACY_BROWSER_BUILD=1`，耗时 4m51s；保留既有 Browserslist 数据过期和大 chunk 告警。构建产物位于 `/data/dts-stack/source/dts-platform-webapp/dist`。
- 桌面/窄屏组件浏览器检查：Chrome 152.0.7977.82，1366×768 与 390×844 通过；右侧面板宽度分别为760/351px，保持一个标签页、切换更新、关闭释放，截图已检查。隔离样例替换 iframe 内容；本地默认 session 探测有 DNS 错误，不作为真实会话或真实大屏验收证据。
- Chrome 95：本轮未找到可用程序，未执行真实 Chrome 95 验收。
- 镜像/离线包、容器部署：本轮未执行。
- DTS 真实业务页面：打开 `https://bi.yuzhicloud.com` 后停在登录页；仅用户提供的 Jira 凭据已用于 Jira，业务账号尚未提供。

## 现场与工作区边界

开发目录本轮开始干净、领先远端一个既有 F9 文档提交；本次推送包含该既有文档历史和 S10DC-58 提交，不代表 F9 已编码。后续出现其他并行前端修改，本轮不提交、不覆盖这些修改。

构建目录已有 5 个 dbt 文件修改，均保留；本轮构建输入的 `source/dts-platform-webapp`、`source/dts-session-core` 与 Git 一致。未从开发目录复制未提交源文件或产物。

运行前端容器只读核对为 `dts-stack-dts-platform-webapp-1`，镜像 `sha256:5403778edc4675eeb8dfa9796384619e0766e3fdbea4eee94f2d2f0b9fc75019`。其 Compose 标签仍指向 `/data/dts-stack/docker-compose-app.yml`；本轮未更改容器或迁移管理路径。

本机原始日志：`/tmp/jira58-source-test.log`、`/tmp/jira58-component-test.log`、`/tmp/jira58-install-retry.log`、`/tmp/jira58-web-build.log`、`/tmp/jira58-browser.log`、`/tmp/jira-gitnexus-analyze-20260912.log`。截图 `/tmp/jira58-1366.png`、`/tmp/jira58-390.png`；结果 `/tmp/jira58-browser-result.json`。不在工单或仓库记录口令。
