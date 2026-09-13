# Professional Metrics Service Evidence

## Scope

验证 Sprint-32 第一阶段 `dts-metrics` 独立服务壳、镜像构建、默认 Compose 挂载和最小运行态健康检查。

## Commands

```bash
./mvnw -q -Dmaven.repo.local=/tmp/codex-m2 -pl dts-metrics test
./mvnw -q -Dmaven.repo.local=/tmp/codex-m2 -pl dts-platform -DskipTests compile
bash -n init.sh
bash -n builds/dts-build.sh
docker compose -f docker-compose-app.yml config --services
MAVEN_UNRESTRICTED=1 SAVE_IMAGE_TARS=false builds/dts-build.sh --image dts-metrics --no-save
docker run --rm -d --name dts-metrics-smoke -p 18084:8084 dts-metrics:1.0.0
curl -fsS http://127.0.0.1:18084/management/health
curl -fsS http://127.0.0.1:18084/api/metrics/health
curl -fsS http://127.0.0.1:18084/api/metrics/capabilities
docker rm -f dts-metrics-smoke
```

## Results

- `dts-metrics` 单模块测试通过。
- `dts-platform` 编译通过，验证 `/api/capabilities` 相关新增类可编译。
- `init.sh` 和 `builds/dts-build.sh` shell 语法检查通过。
- 默认 Compose 服务列表包含 `dts-platform`、`dts-analytics` 和 `dts-metrics`，不依赖 `professional` / `enterprise` profile。
- `builds/dts-build.sh --image dts-metrics --no-save` 成功构建 `dts-metrics:1.0.0`。
- 容器运行态健康检查通过：`/management/health` 返回 `UP`。
- 业务健康检查通过：`/api/metrics/health` 返回 `status=UP`、`enabled=true`、`edition=foundation`。
- 能力接口通过：`/api/metrics/capabilities` 返回平台契约、服务名和 MVP 能力清单。

## Notes

- 本阶段只完成 Metrics 服务可独立构建、默认部署、可暴露最小能力契约。
- Metrics 到 Platform 的真实回调和 UI 能力开关消费属于后续任务。
