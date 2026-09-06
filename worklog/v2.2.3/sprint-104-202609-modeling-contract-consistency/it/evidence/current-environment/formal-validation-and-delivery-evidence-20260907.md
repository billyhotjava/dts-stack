# Sprint 104 正式验证与离线交付证据（2026-09-07）

**状态：IN_PROGRESS。** 本记录归档部署目录的正式测试、构建、交付包和隔离迁移结果；不证明当前容器已经完成替换，也不证明 Chrome 95 或真实租户的页面旅程通过。

## 证据版本与边界

- 正式构建和离线交付包源码：`e83b51076e216a2464d5b8703186a7cb93ac723c`。
- 隔离迁移验证脚本提交：`3aa077d0c49d48aa0600705a11c3676fb5bf95ce`。
- 执行目录：`/opt/prod/s10/deploy`；开发目录未用于编译、构建或测试。
- 当前状态：三个镜像已由发布流程部署。Chrome 95 验收及 IT-08 至 IT-18 未执行，不能由本记录替代。

## 已执行的专项验证

| 范围 | 结果 | 原始证据 | 说明 |
|---|---:|---|---|
| 后端目标组首轮 | 73 项通过；整组未通过 | `/tmp/s104-backend-targeted-final.log` | 该次总计 96 项，其中 `ModelMaterializationRunArtifactServiceTest` 的 23 项因测试夹具 `UnfinishedStubbing` 出错；不得将 73 项写为该次 Maven 成功。 |
| 后端物化恢复组 | 29/29 通过，Maven `BUILD SUCCESS` | `/tmp/s104-recovery-final.log` | `ModelMaterializationRunArtifactServiceTest` 23 项和 `PlanOperationalRunServiceTest` 6 项。 |
| analytics 专项 | 15/15 通过，Maven `BUILD SUCCESS` | `/tmp/s104-analytics-final.log` | 含语义发布、平台源注册、启动有限重试和 Data Lake 初始化。日志中的重试耗尽为受控测试分支，不是测试失败。 |
| Node 原生质量前端契约 | 21/21 通过 | `/tmp/s104-formal-node-contracts.log` | `node --test` 执行，非 Vitest。 |
| 最终 source-contract | 9/9 通过 | `/tmp/s104-source-contract-final.log` | 单个 Vitest 文件通过。 |
| 前端正式构建 | 通过 | `/tmp/s104-formal-frontend-rebuild.log`、`/tmp/s104-release-build.log` | `pnpm build` 成功；存在既有大 chunk 警告，未导致失败。 |
| catalog 多输出组件 | 5/5 通过 | `/tmp/s104-catalog-multi-output.log` | 同一命令中 helper 因缺少 jsdom 失败，未将该 helper 写为通过。 |
| catalog 输出 helper 复测 | 3/3 通过 | `/tmp/s104-catalog-output-contract-retest.log` | 在 `7c502e0d3` 上的定向复测。 |
| catalog 多输出 typecheck | 退出码 0 | `/tmp/s104-multi-output-typecheck.log` | 在 `480e3f8b0` 上执行。 |

上述是不同命令和范围的证据，**73、29、15、21、9 不相加为统一测试总数**。

## 隔离 Liquibase 升级、回退与重放

脚本 `it/verify-delivery-migrations.sh` 在提交 `3aa077d0` 的部署目录执行，日志为 `/tmp/s104-delivery-migrations-3aa077d0.log`，退出码为 0。

- 只创建并清理 `s104_platform_*`、`s104_analytics_*` 临时数据库；未使用业务模块 POM，未连接业务库。
- 平台 changeSet 验证旧 `catalog_dataset` 行保留、`version` 非空且默认 `0`、回退删除列、重放恢复默认值。
- analytics changeSet 验证旧行保留、可空扩展、`tenant_id + platform_data_source_id` 重复写入被唯一约束拒绝、回退删除两列、重放恢复约束。
- 脚本完成后的 `pg_database` 查询显示两个 `s104_*` 前缀临时数据库数量为 `0`。
- 在线数据库执行记录：platform `20260906-01-catalog-dataset-version` 于 `00:49:54` 执行；analytics `0054-01` 于 `00:49:09` 执行。隔离回退验证不回退线上 changeSet。

## 正式镜像与离线包

构建日志 `/tmp/s104-release-build.log` 显示三服务镜像构建和 OpManager 离线包生成完成。交付元数据位于 `/opt/prod/s10/deploy/data/sprint104-release/s104-e83b51076e21/`。

| 服务 | 镜像标签 | 镜像 ID | 镜像归档 SHA-256 |
|---|---|---|---|
| dts-platform | `dts-platform:s104-e83b51076e21` | `sha256:13c721e4eafd90b8caed743dc93846eaf5c06e1512224a31ff98a307d6fa4447` | `328a1a89467aed33838b6dcc45847b7c5498f8edf9bfc51cc7af2acee6f97ee6` |
| dts-analytics | `dts-analytics:s104-e83b51076e21` | `sha256:29ed6c9f2c5be11b48b77daeb6216cb5741480bc85d9ed5d613e85123394cd21` | `91ae99be7dd7be5e673b4dbb99bd4ceb07e9ab3a2ef665113e456bfe5775d12e` |
| dts-platform-webapp | `dts-platform-webapp:s104-e83b51076e21` | `sha256:9dbe565ce3e0eb31768ce594206d00fba896e94dbe567544badc7a65422a526c` | `edd1e17749b852b091f66f900107dbd67756b474a3716d229e46c3675b8a43ca` |

- OpManager 包：`dts-opmanager-upgrade-20260907-003926.tar.gz`，SHA-256 `586342cd0fbfa3e7495bf7dab523b8b0a4ce841d522b545e862e18050e7e8473`（`archive.sha256`）。
- `package-verification.log` 对已解包内容逐项校验为 `OK`，其中包含 `misc/release-manifest.json` 与 `misc/rollback-manifest.json`。
- `source.json`、`unpacked/misc/release-manifest.json` 将三镜像和归档校验和绑定到上述 `e83b51076e21` 源码版本。

## 已部署容器与离线只读验证

`pre-deployment.json` 与 `deployed-containers.json` 记录此次只替换了以下三个容器；前者为 `1.0.0` 标签和旧 image ID，后者为本包中的 e83 镜像 ID。

| 容器 | 运行结果 | 实际 image ID |
|---|---|---|
| `/deploy-dts-platform-1` | `running`、`healthy` | `sha256:13c721e4eafd90b8caed743dc93846eaf5c06e1512224a31ff98a307d6fa4447` |
| `/deploy-dts-analytics-1` | `running`、`healthy` | `sha256:29ed6c9f2c5be11b48b77daeb6216cb5741480bc85d9ed5d613e85123394cd21` |
| `/deploy-dts-platform-webapp-1` | `running`、无容器 healthcheck | `sha256:9dbe565ce3e0eb31768ce594206d00fba896e94dbe567544badc7a65422a526c` |

已对解包目录执行只读/可逆的离线预检：

```sh
bin/dts-upgrade-lite plan \
  --source <unpacked/dts-stack> --images-dir <unpacked/images> \
  --extra-dir <unpacked/misc> --target <独立临时副本>
```

- `plan` 退出码为 0，报告保留于 `/tmp/s104-offline-plan-e83b51076e21-azu99Y/logs/upgrade-lite-20260907-005218/`；没有使用正式部署目录作为 target，也没有执行 `apply` 或 `rollback`。
- 三份归档均由本地 `docker load -i` 加载，标签和 image ID 与 manifest 相同；加载后上述三个运行容器的 image ID 未变化。该命令没有拉取网络镜像的步骤或输出。
- 解包包中存在 `dbt_project.yml`，其 `model-paths` 声明 `models` 与 `dbt_model/models`；后者含 46 个文件，另有 7 个 macro 和 1 个 profile 示例。`models` 目录当前为空，因此本次只证明包内容和预检路径，未执行 dbt，也不声称离线安装或模型运行已验收。

## 未执行或待确认

- 仅三服务容器的替换与上述运行状态已有记录；未执行离线包 `apply`、回滚演练、dbt 运行或真实业务流程验证。
- 新 W4 前端未打入 e83 镜像；本次构建、容器和浏览器证据不覆盖该新增界面。
- 当前物化 dispatch 已精确返回 `MATERIALIZATION_SOURCE_MISSING`；通用根因仍在修复中，本文不把错误定位表述为恢复通过。
- Chrome 95、真实登录租户、四步 W1–W4 和 F2 IT-08 至 IT-18 的浏览器/运行时验收未执行。
- 本文不把历史失败日志、构建成功或离线包校验表述为 Sprint DONE。
