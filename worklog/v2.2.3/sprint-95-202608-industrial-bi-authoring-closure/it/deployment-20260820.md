# 2026-08-20 部署与运行验收

## 构建来源

- Git commit：`ed836636b`（GREEN）；RED checkpoint：`aa7956d5a`。
- 构建方式：从 GREEN commit 创建隔离 detached worktree；未包含主工作区未提交的数据建模改动。
- Analytics：host Maven `-DskipTests package` 成功，Docker 使用校验后的 prebuilt JAR。
- Platform webapp：Docker 内完整 `pnpm build` 成功，含 TypeScript 与 `LEGACY_BROWSER_BUILD=1`，10593 modules。

## 镜像与回滚点

| 服务 | 新镜像 ID | 回滚镜像 ID | 运行状态 |
|---|---|---|---|
| dts-analytics | `sha256:37ac772032f1...` | `sha256:6ecad5b9d451...` | running / healthy |
| dts-platform-webapp | `sha256:eb451b5dcd13...` | `sha256:e165bbe92dcc...` | running；`nginx -t` PASS |

回滚标签：`dts-analytics:rollback-sprint95-20260820`、`dts-platform-webapp:rollback-sprint95-20260820`。

## 运行验收

| 检查 | 结果 |
|---|---|
| Analytics `/api/health` | `UP`；appDatabase `UP` |
| `https://bi.yuzhicloud.com/` | HTTP 200 |
| 未登录 `/bi/api/analysis/11` | HTTP 401，fail-closed |
| 部署静态包 | 包含 Analysis 拖拽提示和 targeted cross-filter key |
| 最近 15 分钟 Analytics/Webapp 错误日志 | 0 / 0 |
| 部署页面完整 Mock 旅程（Chrome 150） | 1/1 PASS，9.3s；console/page/HTTP failure 0 |

## 未关闭缺口

- 当前环境没有 Chrome 95 executable，不能以 Chrome 150 替代客户兼容门禁。
- 当前 shell 没有真实验收账号；未执行真实 QueryDataset、真实导出文件密级头/审计、真实 revision 数据库对账。
- 为避免再次中断在线用户，未执行生产镜像切回演练；回滚标签和命令已验证存在。

## 已提交全量代码重建复验（2026-08-20 08:24）

- 构建来源更新为 `94cbe7cf5 fix(platform): select authoring migration constructor`，已推送至 `origin/v2.2.3`，本地与远端一致。
- 首次切换 `272e03155` 的 platform 镜像时，Spring 因多构造器未选定生产构造器而报 `No default constructor found`；立即回滚到预留镜像后恢复 healthy、0 次重启。新增 Spring BeanFactory 回归测试先复现同一异常，再以 `@Autowired` 明确生产构造器，聚焦测试 5/5 通过。
- 最终镜像：`dts-platform sha256:a9e8a7943547...`、`dts-analytics sha256:03ecd258a2c2...`、`dts-platform-webapp sha256:7a978454842f...`。三个容器均运行，两个后端 healthy，重启次数均为 0，webapp `nginx -t` 通过。
- Platform `/management/health` 与 Analytics `/api/health` 均为 `UP`，外部 `/`、`/data-modeling`、`/bi` 均返回 HTTP 200；三容器启动后关键错误匹配均为 0。
- 两个 Liquibase changeSet 均为 `EXECUTED`；3 个 authoring 增量列和 2 张 migration ledger 表已存在。
- 部署静态包 Sprint-95 旅程 1/1 通过（Chrome 150，9.3 秒），console/page/network failure 为 0；截图为 `analysis-authoring-published-1366x768.png` 与 `dashboard-targeted-linkage-768x900.png`。
- 回滚标签保留为 `rollback-pre-272e03155-20260820-075006`；失败镜像另保留为 `dts-platform:failed-272e03155-20260820-075006` 供诊断。
