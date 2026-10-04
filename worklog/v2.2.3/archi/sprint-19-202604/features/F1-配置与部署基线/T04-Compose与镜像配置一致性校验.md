# T04: Compose 与镜像配置一致性校验

**优先级**: P1
**状态**: DONE
**依赖**: T01, T03

## 目标

确认 compose、镜像版本、服务名、端口和环境变量在 OpenMetadata 链路中一致。

## 范围

- 检查 `imgversion.conf`、`docker-compose-app.yml` 和相关 `.env` 变量。
- 确认 `dts-openmetadata`、`dts-openmetadata-ingestion`、`dts-platform`、`dts-ingestion` 间地址互通。
- 固化 `docker compose ps`、OpenMetadata API health 和日志检查命令。
- 记录 legacy/dev compose 的差异或不支持项。

## 完成标准

- [ ] OpenMetadata server 和 ingestion 镜像版本一致或差异有解释。
- [ ] compose 服务名、端口和应用配置能互相解析。
- [ ] 验收命令写入 `it/README.md`。
- [ ] 发现的不一致项有 follow-up 或修复提交。
