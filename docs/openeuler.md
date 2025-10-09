openEuler 部署与兼容性说明
================================

目标：在保持 Ubuntu 开发体验不变的情况下，保证在 openEuler（RHEL/CentOS 家族）生产环境可直接运行。

- 支持方式：已在 `docker-compose.yml`、`docker-compose-app.yml`、`docker-compose.dev.yml` 中为所有本地目录/文件挂载加入 SELinux 友好标签（z/Z），无需额外手工 `chcon`。
- 不影响 Ubuntu：Ubuntu 默认无 SELinux，上述挂载选项会被安全忽略，开发流程不变。

1. 依赖安装（Docker 推荐）
- 建议使用 Docker Engine 20.10+ 与 Compose v2 插件：
- 安装（示例，按你环境选择官方或镜像源）：
  - `dnf install -y docker docker-compose-plugin`（或 `yum`）
  - 启动并开机自启：`systemctl enable --now docker`，`systemctl status docker`
- 验证：`docker version`、`docker compose version`

提示：若你更偏好 Podman：当前清单以 Docker/Compose 为主，Podman Compose 兼容性存在差异；若需 Podman，请与我们确认目标版本后再评估迁移。

2. SELinux 与挂载策略
- openEuler 默认可能启用 SELinux（Enforcing）。我们已将本地目录挂载改为：
  - 只读共享（证书、配置等）：`:ro,z`
  - 读写独占（数据目录，如 Postgres、MinIO）：`Z`
- 这样可以避免常见的 `permission denied` / `operation not permitted` 问题。
- 如果仍遇到拒绝日志：
  - 确认已安装 `container-selinux` 包：`dnf install -y container-selinux`
  - 保持 Enforcing 模式，重启服务后再次验证。

3. host-gateway 解析（容器访问宿主）
- 我们在应用容器中使用了 `extra_hosts: "<domain>:host-gateway"` 以便容器访问宿主上的服务（例如 SSO/反向代理）。
- 这需要 Docker Engine 20.10+ 支持。如果你的版本较旧或出现解析失败，可在 `/etc/docker/daemon.json` 中指定网关 IP：

```
{
  "host-gateway-ip": "172.17.0.1"
}
```

- 然后 `systemctl restart docker` 生效。不同环境网关 IP 可能不是 `172.17.0.1`，可通过 `ip addr` 或 `docker network inspect bridge` 确认。

4. 运行与验证
- 首次初始化（生成 `.env`、证书并拉起基础服务）：
  - `./init.sh single 'Strong@2025!' dts.local`
- 部署（打包镜像方式）：
  - `docker compose -f docker-compose.yml -f docker-compose-app.yml up -d`
- 运行状况：
  - `docker compose ps`
  - 反向代理面板（如启用 dashboard）：`http://localhost:${TRAEFIK_DASHBOARD_PORT}`
  - Trino：`docker compose exec dts-trino wget -qO- http://localhost:8080/v1/info`
  - SSO：`curl -k https://sso.${BASE_DOMAIN}`

5. 常见问题
- EACCES/权限问题：
  - 确认 `container-selinux` 已安装；
  - 确认使用了我们更新后的 Compose 文件（带 z/Z）；
  - 不要对 `/var/run/docker.sock` 加 SELinux relabel；保持 `:ro` 即可。
- 文件路径大小写/权限：
  - 证书、驱动、上传目录等均位于 `services/` 下，请保持目录存在且宿主具备可读/写权限。
- Podman 环境：
  - 如需兼容，请提供目标 Podman/Compose 版本，我们再给出最小修改建议或单独的 `docker-compose.podman.yml`。

6. 变更摘要（与 Ubuntu 相比）
- Compose 变更：
  - 基础与应用、开发清单均采用长语法挂载，并加入 `bind.selinux`（z/Z）。
  - 保留 `docker.sock` 只读挂载，不做 relabel。
- 文档新增：
  - 本文件 `docs/openeuler.md`，覆盖安装、SELinux、host-gateway 与排障要点。

如需我们帮你在目标 openEuler 服务器上做一次实际验证，告诉我 Docker/内核版本与是否启用 SELinux，我可以给出更具体的检查清单。

