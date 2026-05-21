# DTS OpManager 交接说明

日期：2026-05-19

## 1. 背景和目标

`dts-opmanager` 是 DTS 产品的独立运维与离线升级控制台。它不再沿用 `dts-upgrade-lite` 的轻量脚本思路，而是作为一个单独服务交付，便于现场通过 Web UI 完成升级前检查、配置差异处理、镜像加载和容器重建。

当前设计需要和 DTS 现有技术栈保持一致：

- 后端采用 Spring Boot + JHipster 体系，保留 Maven 工程结构和 JHipster 依赖习惯。
- 前端采用 React + Vite，并打包进同一个 Spring Boot Jar，不做前后端分离部署。
- UI 必须兼容 Chrome 95，避免依赖复杂新特性和重渲染组件。
- 目标现场包含离线环境、鲲鹏 ARM64 服务器、麒麟 OS；可以假设 Java 和 Docker 可用，不强依赖 systemd。
- 现场升级包可能很大，优先支持提前把升级材料放到服务器目录，浏览器上传只作为补充能力。

## 2. 当前进度

当前工程根目录为 `opmanager/`，已完成独立项目骨架和第一轮核心能力。当前分支 HEAD 为 `9f3402f07 fix:update`，已同步到 `origin/v2.2.3`。注意：主仓库里有大量非 `opmanager` 的未提交改动，后续提交时需要继续保持范围收敛。

已完成内容：

- 独立 Maven 工程：`opmanager/pom.xml`
  - Artifact：`dts-opmanager`
  - Spring Boot：`3.4.5`
  - Java：`21`
  - JHipster framework：`8.11.0`
  - 使用 Undertow、Actuator、Validation、Springdoc 等基础依赖。
- 后端入口与配置：
  - `OpManagerApp`
  - `application.yml`
  - `OpManagerProperties`
  - 默认数据目录：`/var/lib/dts-opmanager`
  - 默认升级工作区：`/var/lib/dts-opmanager/packages`
- 前端工程：
  - `src/main/webapp`
  - React 18 + Vite + TypeScript
  - `@vitejs/plugin-legacy` 兼容 Chrome 95
  - 前端资源由同一个 Spring Boot 应用托管。
- Docker 部署：
  - `opmanager/Dockerfile`
  - `opmanager/build-image.sh`
  - `opmanager/deploy/docker-compose.yml`
  - `opmanager/deploy/env.example`
  - 默认容器内端口 `18090`，宿主机端口 `18095`。
- 固定升级工作区约定：
  - `$OPMANAGER_PACKAGE_ROOTS/images`
  - `$OPMANAGER_PACKAGE_ROOTS/dts-stack`
  - `$OPMANAGER_PACKAGE_ROOTS/misc`
  - 这三个目录是当前约定的唯一顶层业务目录。
- `dts-build.sh` 已支持导出 opmanager 升级工作区：
  - `--opmanager-package`
  - `--opmanager-output <dir>`
  - 推荐只使用镜像列表驱动的固定流程：
    `./builds/dts-build.sh --image dts-admin dts-admin-webapp dts-ingestion dts-platform dts-platform-webapp dts-analytics dts-metrics --legacy --opmanager-output /var/lib/dts-opmanager/packages`
- REST API 已有第一版：
  - `/api/opmanager/runtime`
  - `/api/opmanager/packages`
  - `/api/opmanager/config`
  - `/api/opmanager/workspace`
  - `/api/opmanager/jobs`
  - `/api/opmanager/containers`
- 后端核心服务已具备第一版：
  - `UpgradePackageService`：升级包目录注册、上传、校验。
  - `WorkspaceService`：识别固定工作区、加载 `images/*.tar`、调用 Docker Compose 重建容器。
  - `ProtectedConfigService`：对 `.env`、Compose 文件、MDM 相关目录做配置预检和保护性应用。
  - `RuntimeInspector` / `DockerService`：运行环境和容器状态检查。
  - `FileJobStore` / `UpgradePlanningService`：基于文件的任务和事件记录。
- 前端已有第一版页面：
  - 概览
  - 升级包注册和上传
  - 配置预检
  - 计划任务
  - 容器列表
  - 概览页已放置加载镜像、重建容器入口。
- 已有单元测试覆盖：
  - `UpgradePackageServiceTest`
  - `ProtectedConfigServiceTest`
  - `WorkspaceServiceTest`
  - `RuntimeInspectorTest`
  - `ProcessCommandRunnerTest`
  - `FileJobStoreTest`
  - `UpgradePlanningServiceTest`
  - `tests/test_dts_build_pack_contents.sh`

## 3. 当前升级包工作流

联网构建环境生成现场材料：

```bash
cd /opt/prod/s10/v2.2.3
./builds/dts-build.sh \
  --image dts-admin dts-admin-webapp dts-ingestion dts-platform dts-platform-webapp dts-analytics dts-metrics \
  --legacy \
  --opmanager-output /var/lib/dts-opmanager/packages
```

生成后的目录约定：

```text
/var/lib/dts-opmanager/packages/
  images/      # docker save 生成的镜像 tar 文件
  dts-stack/   # 去除源代码后的 DTS stack 工程目录
  misc/        # 其他元数据、杂项文件
```

现场部署 opmanager：

```bash
cd opmanager
docker load -i dts-opmanager-2.2.3-linux-arm64.tar
docker compose -f deploy/docker-compose.yml --env-file deploy/.env up -d
```

访问地址：

```text
http://<server-ip>:18095/
```

现场 UI 操作主线：

1. 在“升级包”页面注册服务器目录，例如 `/var/lib/dts-opmanager/packages`。
2. 在“配置预检”页面选择升级包，比较 `packages/dts-stack` 和现场 `OPMANAGER_TARGET_STACK_DIR`。
3. 对 `.env`、Compose 文件、MDM 目录等高风险配置逐项确认。
4. 在“概览”页面加载 `images/*.tar`。
5. 在“概览”页面重建容器。
6. 在“容器”页面查看 Docker 容器状态。

## 4. 关键设计决策

- `dts-opmanager` 是独立服务，不挂在 `dts-admin-webapp` 内，也不依赖 DTS 主库、Keycloak、Traefik 或 systemd。
- 升级包不再设计复杂的版本化包格式，当前聚焦固定目录约定：`images`、`dts-stack`、`misc`。
- `images` 只放 Docker 镜像 tar 文件，现场执行 `docker load` 后再重建容器。
- `dts-stack` 是通过 `dts-build.sh` 打出来并剔除源代码后的工程目录，用于配置对比和 Compose 重建。
- `misc` 只放补充材料，不参与当前核心升级流程。
- 配置文件不能被自动覆盖：
  - `.env` 默认只支持追加缺失 key。
  - Compose 和 MDM 相关文件默认支持写一份 `*.opmanager-<timestamp>` 副本给现场确认。
  - “直接使用升级包文件覆盖现场文件”能力已存在，但需要在 UI 上明确风险。
- Portainer 不做深度合并，当前只保留 `OPMANAGER_PORTAINER_URL` 作为外部入口链接；真正的升级操作仍由 opmanager 自己完成。
- Docker Compose 调用优先使用 `docker compose` 插件，找不到时退回 `docker-compose`。

## 5. 待完成任务

### P0 必须完成

- 增加访问控制。
  当前 API 第一版没有完整认证授权。生产部署前需要确定边界：仅内网访问、反向代理 Basic Auth、接入 DTS 统一认证，或单独的本地管理员口令。
- 完成真正的升级任务状态机。
  目前“计划任务”偏 dry-run 和事件记录，后续需要串起：运行环境检查、升级包校验、配置预检、备份、配置处理、镜像加载、容器重建、健康检查、结果归档。
- 增加备份与回滚策略。
  配置应用、Compose 重建前必须生成可追溯备份；失败后至少能给出恢复命令和备份路径。
- 强化容器重建策略。
  当前会选一个 Compose 文件执行 `up -d --force-recreate`。后续需要支持现场模式选择、服务白名单、`--no-deps`、只重建变更服务等策略。
- 在真实离线 ARM64/Kylin 环境验证。
  需要确认 opmanager 镜像自身、DTS 业务镜像 tar、Docker Compose 插件、文件权限、Java 运行时在鲲鹏和麒麟 OS 上可用。

### P1 应该完成

- 配置差异 UI 继续完善。
  当前已有左右两侧文本预览和动作按钮，后续需要更接近 Beyond Compare 的体验：差异高亮、按块定位、只展示变更、超大文件降级提示。
- 配置决策持久化。
  高风险文件没有处理前，应阻止进入“加载镜像/重建容器”；用户的 KEEP_LOCAL、MERGE_ENV_ADD_KEYS、WRITE_PACKAGE_COPY 等决定需要保存到任务记录。
- 镜像加载状态检测。
  展示 tar 文件列表、镜像 tag、是否已加载、加载耗时和失败重试结果。
- 增加容器健康检查。
  重建后检查关键 DTS 容器状态、端口、健康接口和最近日志摘要。
- 完善浏览器上传链路。
  当前上传能力是补充入口，后续如仍保留，需要支持大文件断点/进度、压缩包解包、解包后的固定目录校验。
- 增加操作日志和报告导出。
  现场升级需要可交付证据，包括升级前版本、镜像清单、配置处理记录、执行命令、失败信息和最终状态。

### P2 可以后续迭代

- 编写现场运维 runbook。
  包括离线材料准备、导入镜像、启动 opmanager、升级流程、失败恢复、常见错误。
- 补齐 opmanager 自身多架构构建说明。
  明确 x86_64 和 ARM64 镜像构建、保存、导入方式。
- 评估 Portainer 集成深度。
  如果现场已强依赖 Portainer，可以考虑只做跳转和状态参考；不建议把升级编排依赖 Portainer API。
- 增加浏览器 E2E smoke test。
  用 Playwright 覆盖注册目录、配置预检、加载镜像按钮禁用态、任务列表等关键路径。
- 与 DTS 主产品菜单做链接。
  如果后续仍希望从 `dts-admin-webapp` 进入，可以只放外链入口，不把 opmanager 前端嵌回主系统。

## 6. 建议交接后的第一组验证命令

后续同事接手后，建议先只验证 `opmanager` 和打包脚本相关范围：

```bash
cd /opt/prod/s10/v2.2.3/opmanager
../source/dts-admin/mvnw -q -f pom.xml test

cd /opt/prod/s10/v2.2.3/opmanager/src/main/webapp
pnpm build

cd /opt/prod/s10/v2.2.3
bash tests/test_dts_build_pack_contents.sh
git diff --check -- opmanager builds/dts-build.sh tests/test_dts_build_pack_contents.sh
```

如果要验证容器镜像：

```bash
cd /opt/prod/s10/v2.2.3/opmanager
./build-image.sh --tag dts-opmanager:2.2.3 --output dts-opmanager-2.2.3-linux-arm64.tar
docker compose -f deploy/docker-compose.yml --env-file deploy/.env up -d
```

## 7. 接手注意事项

- 不要把 `OPMANAGER_PACKAGE_ROOTS` 顶层目录扩展成复杂结构；当前明确只保留 `images`、`dts-stack`、`misc`。
- 不要把 `dts-upgrade-lite` 重新作为主升级引擎；它可以作为历史脚本或参考，但 opmanager 的主线是 Web 控制台编排。
- 不要默认覆盖现场 `.env`、Compose、MDM 配置。现场配置通常已经按客户环境调整，必须通过预检和显式动作处理。
- 不要默认依赖 systemd。现场可依赖 Java 和 Docker，启动方式优先用 Docker Compose 的 restart policy。
- 不要把 Portainer 当成升级编排核心。Portainer 可以提供跳转或状态辅助，真正的配置保护和升级流程应保留在 opmanager。
- 提交时只 stage `opmanager/`、`builds/dts-build.sh`、`tests/test_dts_build_pack_contents.sh` 等相关文件，避免带入主仓库当前已有的其他脏改动。
