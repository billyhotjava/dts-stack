# Sprint-104 建模契约交付记录

## 交付范围

本项交付涉及两项正式服务范围：

1. `dts-platform`：随 JAR 的 dbt 工作区引导内容交付组合唯一性宏。
2. `dts-platform-webapp`：随正式前端交付提供建模功能入口。

`dts-dbt:1.10.0` 仅作为既有正式镜像运行 IT-04 专项测试，不属于本项构建或拟部署服务。不在本计划中创建额外常驻服务，也不以本地 workspace 或临时容器文件替代正式交付内容。

## 发布前记录

当前已部署修复：`66fcd49dd8c1db3484e2358eecf1a3ae5f08df34`，正式构建并部署完成。`bd0670acc` 为前一次验收基线。最终交付记录以实际镜像及校验和为准。

## 执行顺序

1. 在部署目录确认分支、工作区和提交 SHA，执行正式构建与交付包检查。
2. 核对 `dts-platform` 交付物包含工作区引导所需的宏，再按部署配置更新该服务。
3. 按部署配置更新 `dts-platform-webapp`，并记录实际镜像标识。
4. 使用既有 `dts-dbt:1.10.0` 运行时，按 [runbook.md](runbook.md) 运行一次性 IT-04 验证并保存 JSON 证据。
5. 分别记录正式构建/交付包、容器部署和真实模型或页面验收结果；后两项必须由对应负责人实际执行后填写。

下述构建和部署已执行；另一台离线环境安装、Chrome 95 与全部模型端到端验收仍未完成。

## 实际构建与部署记录（2026-09-06）

- 正式源码提交：`bd0670acc89e7dd1be82e0d12961ba7744f63ca2`。开发区 commit/push 后，部署区 ff-only 拉取并核对。
- 入口：`builds/dts-build.sh --image dts-platform dts-platform-webapp --opmanager-output data/sprint104-acceptance/release-bd0670acc`。TypeScript 和 legacy production bundle 均成功。
- `dts-platform:1.0.0`：`sha256:8351d96cab4ad7563cd1e404e2c7c6529740588d0126d5a179a59e4b3fe86895`。
- `dts-platform-webapp:1.0.0`：`sha256:06754db0262dd46a0d8fab8a8a7fa45bfb5fe1126c835af3252cd71c5203af7c`。
- 交付包：`/opt/prod/s10/deploy/data/sprint104-acceptance/release-bd0670acc/dts-opmanager-upgrade-20260906-161102.tar.gz`，SHA-256：`353c7f78bb409eff02f73fbc5239b9358afbd9b913c8ce1283b811005da5d65b`。包内 images 仅包含上述两个镜像 tar。
- 在 `/opt/prod/s10/deploy` 执行 `docker compose -f docker-compose-app.yml up -d --no-deps dts-platform dts-platform-webapp`，退出0。后端 healthy；前端 running。
- 核对部署前后的全部其他容器 ID/StartedAt：无变化。保留 deploy 项目、正式挂载及持久化数据。
- 未将交付包发送到另一台离线机器；未以本记录宣称离线安装和 Chrome 95/模型页面全部验收通过。证据日志位于 `/tmp/sprint104-formal-build.log`，部署目录 `data/sprint104-acceptance/pre-deployment.json` 和 `release-evidence.json`。

## 修复版正式交付（3fa21140e）

- 编译器回归19/19，前端presentation回归6/6。正式双镜像构建退出0。
- package：`release-3fa21140e/dts-opmanager-upgrade-20260906-163631.tar.gz`；SHA-256 `67df70a9f8e9c7980227c2cff4b74f8556bd8bbe130f2e1bf8363d4050782e9e`。
- platform镜像：`sha256:30cace1f8e75ce777cac1a4985e337ddbaf5f8a55add1b7fcf4aa2dccb55ba3e`。
- webapp镜像：`sha256:bea293e514212386baab42e825e3deef6c025fdffe0450c580a7af4314511456`。
- 同一正式Compose范围更新成功，后端healthy、前端running，其他全部容器ID/StartedAt不变。证据见 `it/evidence/current-environment/release-3fa21140e.json`。

## 最新兼容性修复交付（66fcd49dd）

- 源码提交：`66fcd49dd8c1db3484e2358eecf1a3ae5f08df34`；草稿安全回归27/27，正式双镜像构建退出0。
- 包：`release-66fcd49dd/dts-opmanager-upgrade-20260906-165424.tar.gz`，SHA-256 `855c1efdb059730ebda1b80818a57b437fbfd4b2ea5116dd9560fc42317e0e13`。
- platform镜像：`sha256:7b2a3f6f739c4e6459f9f1d6414b6d44e8f2546d8407db901121ca2239849846`；webapp同3fa版本内容，正式构建复用同一镜像ID。
- Compose更新退出0，platform healthy、webapp running，其他容器未变化。完整记录见 `it/evidence/current-environment/release-66fcd49dd.json`。
- 部署后浏览器控制两次超时，未完成旧模型校验/提交及两次物化复测，不将测试与部署结果作为页面通过证据。
