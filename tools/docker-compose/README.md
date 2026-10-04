把 Docker Compose 二进制放在这里（离线环境用）

离线部署（尤其是 legacy 模式 `./init.sh legacy ...`）需要宿主机有 Compose：
- Docker Compose v1：`docker-compose`（推荐，兼容 docker-compose 1.22/1.29）
- 或 Docker Compose v2：`docker compose` plugin

如果目标环境离线且未安装 Compose，可以提前在联网环境下载对应架构的二进制，拷贝到本目录并赋予可执行权限。

`init.sh` 会自动探测这些文件名：
- `tools/docker-compose/docker-compose`
- `tools/docker-compose/docker-compose-$(uname -m)`（例如 `docker-compose-aarch64`）
- `tools/docker-compose/docker-compose-Linux-$(uname -m)`（例如 `docker-compose-Linux-aarch64`）

示例（aarch64）：
- 拷贝文件到：`tools/docker-compose/docker-compose-Linux-aarch64`
- `chmod +x tools/docker-compose/docker-compose-Linux-aarch64`
- 运行：`./init.sh legacy ...`

