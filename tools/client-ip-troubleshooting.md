# DTS 现场客户端 IP 排错指南

本文用于排查 DTS legacy 部署中审计日志、platform 登录信息或 `ClientIpTrace` 显示
`172.18.0.1` 这类 Docker 网桥地址，而不是浏览器所在局域网真实 IP 的问题。

范围限定：

- 现场为公司局域网环境。
- DTS 使用 `docker-compose.legacy.yml`。
- 现场访问域名为 `https://bi.iae.caep`。
- DTS 宿主机服务器 IP 为 `10.10.10.134`。
- 客户端局域网主要为 `10.10.10.0/24` 和 `10.10.11.0/24`。
- DTS 入口只运行 Traefik，中间没有其它 nginx、LB、Ingress 或代理服务器。
- 用户从其他客户端机器正常访问 `https://bi.iae.caep` 的业务页面。
- 不把 WireGuard 地址作为当前问题证据。

重要原则：

- 不要在服务器本机执行 curl 作为客户端验证；本机访问可能走 loopback、hairpin NAT 或 docker-proxy 路径。
- `TRUSTED_PROXY_CIDRS` 只能解决“Traefik 已收到真实 `X-Forwarded-For`/`Forwarded`，但因为不信任上一跳而丢弃”的问题。
- 本现场没有其它前置代理时，不需要把 `10.10.10.0/24`、`10.10.11.0/24` 当成 `TRUSTED_PROXY_CIDRS` 主动配置；这些是客户端网段，不是可信代理网段。
- 如果 Traefik 自己看到的客户端也是 `172.18.0.1`，并且请求头里没有真实 `X-Forwarded-For`/`Forwarded`，优先查 Docker NAT、docker-proxy、iptables/nft 或宿主机入口网络。

## 0. 记录现场变量

在真实客户端机器上确认客户端 IP，记为：

```text
CLIENT_IP=<客户端真实局域网IP>
```

在 DTS 宿主机上确认部署目录，本文默认：

```text
/opt/prod/s10/v2.2.3
```

现场 DTS 入口信息固定为：

```text
DTS_SITE_URL=https://bi.iae.caep
DTS_SERVER_IP=10.10.10.134
CLIENT_LAN_CIDRS=10.10.10.0/24,10.10.11.0/24
```

在客户端机器上确认域名解析，记为：

```text
bi.iae.caep -> 10.10.10.134
```

如果客户端域名解析不是 `10.10.10.134`，先修复 DNS 或客户端 hosts，再继续后续步骤。

现场拓扑按以下链路理解：

```text
客户端 10.10.10.x / 10.10.11.x
  -> DTS宿主机 10.10.10.134:443
  -> dts-proxy / Traefik
  -> dts-platform
```

该链路中没有其它前置代理，因此正常情况下 Traefik 的 `ClientAddr` 或 `ClientHost` 应直接出现客户端局域网 IP。

已确认现场现象：

```text
dts-platform-webapp 右上角显示的登录 IP 是容器 IP。
```

该 IP 来自浏览器请求 `https://bi.iae.caep/api/session/status` 后，platform 返回的 `loginIp` / `clientIp` 字段。legacy 模式下 `docker-compose.legacy.yml` 已配置：

```text
https://bi.iae.caep/api/* -> Traefik -> dts-platform:8081
```

因此右上角显示容器 IP，说明 `dts-platform` 在 `/api/session/status` 这条请求上解析到的就是容器地址。后续排查只需要确认这个容器地址是在 Traefik 入口前已经形成，还是 Traefik 转发到 platform 时形成。

后续命令除特别说明外，都在 DTS 宿主机的部署目录执行。

在真实客户端正常访问 DTS 业务页面，并记录访问时间点，记为：

```text
BUSINESS_ACCESS_TIME=<客户端正常访问业务的时间点>
```

只要业务页面可以正常访问，就继续第 1 步。

如果业务页面无法访问：

- 先修复客户端到 `https://bi.iae.caep` 的网络连通性、DNS、TLS 或入口端口问题。
- 修复后重新正常访问业务页面，再继续第 1 步。

## 1. 判定 Traefik 入口是否已经拿到真实客户端 IP

查看 `BUSINESS_ACCESS_TIME` 前后的 Traefik JSON access log，找到客户端正常访问业务时对应的请求日志。

命令，在 DTS 宿主机执行：

```bash
tail -n 200 logs/traefik/access.log
```

预期结果之一：

```text
ClientAddr 或 ClientHost 包含 CLIENT_IP
```

如果符合：

- Traefik 入口已经看到了真实客户端 IP。
- 继续第 2 步。

如果不符合，并且看到：

```text
ClientAddr 或 ClientHost 是 172.18.0.1
```

继续检查同一条 JSON 日志中的请求头字段。

如果同一条日志里 `X-Forwarded-For` 或 `Forwarded` 包含 `CLIENT_IP`：

- 进入第 6 步，验证 `TRUSTED_PROXY_CIDRS`。

如果同一条日志里 `X-Forwarded-For` 和 `Forwarded` 都没有 `CLIENT_IP`：

- 真实客户端 IP 在到达 Traefik 前已经丢失。
- 跳到第 4 步，排查 Docker/NAT/宿主机入口。

现场已经确认右上角登录 IP 是容器 IP，因此第 1 步是当前最关键分界点：

- 如果 Traefik access log 的 `ClientAddr` / `ClientHost` 也是容器 IP，问题在宿主机入口到 Traefik 之前，优先查 Docker NAT、docker-proxy、iptables/nft。
- 如果 Traefik access log 的 `ClientAddr` / `ClientHost` 是 `10.10.10.x` 或 `10.10.11.x`，但 platform 仍显示容器 IP，问题在 Traefik 到 platform 的转发头链路。

## 2. 查看 platform 是否收到真实客户端 IP

命令，在 DTS 宿主机执行：

```bash
grep "platform-session-status-client-ip" logs/dts-platform/*.log
```

预期结果：

- 最近的日志行中 `resolved=<CLIENT_IP>`。
- 这说明从 Traefik 到 platform 的 IP 链路正常。继续第 3 步做审计确认。

如果不是预期结果：

- 如果 Traefik 第 1 步已经看到 `CLIENT_IP`，但 platform 仍显示 `172.18.0.1`，说明 Traefik 到 platform 的转发头没有传递或被清理。
- 继续第 6 步。

## 3. 查看审计日志是否写入真实客户端 IP

命令，在 DTS 宿主机执行：

```bash
grep "clientIp" logs/dts-platform/*.log
```

预期结果：

- 对应登录、会话或审计日志中的 `clientIp` 为 `CLIENT_IP`。
- 排查结束。

如果不是预期结果：

- 如果第 2 步 `resolved=<CLIENT_IP>`，但审计仍写其他值，继续查具体审计写入路径。
- 如果第 2 步没有 `resolved=<CLIENT_IP>`，回到第 1 步按入口链路继续排查。

## 4. 确认当前 dts-proxy 端口发布

命令：

```bash
docker port dts-proxy
```

预期结果：

```text
80/tcp -> 0.0.0.0:80
443/tcp -> 0.0.0.0:443
```

如果符合：

- 继续第 5 步。

如果不是预期结果：

- `dts-proxy` 没有按预期发布入口端口。
- 执行修复路径 R1。

## 5. 确认 dts-proxy 容器 IP

命令：

```bash
docker inspect dts-proxy --format '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}'
```

预期结果：

```text
172.18.0.x
```

如果符合：

- 记录输出为：

```text
DTS_PROXY_IP=<上一步输出>
```

- 继续第 8 步。

如果不是预期结果：

- `dts-proxy` 不在预期 Docker 网络，或容器未正常运行。
- 执行修复路径 R1。

## 6. 验证 Traefik trustedIPs 配置是否覆盖上一跳

本现场拓扑为客户端局域网直连 DTS 宿主机 Traefik，中间没有其它代理。正常情况下不需要进入本步骤，也不需要把 `10.10.10.0/24`、`10.10.11.0/24` 写入 `TRUSTED_PROXY_CIDRS`。

只有在第 1 步确认同一条 Traefik access log 中已经存在 `X-Forwarded-For` 或 `Forwarded`，并且其中包含 `CLIENT_IP` 时，才继续检查本步骤。

命令：

```bash
docker inspect dts-proxy --format '{{json .Args}}'
```

预期结果：

- 输出包含：

```text
--entrypoints.web.forwardedHeaders.trustedIPs=<包含上一跳IP或网段>
--entrypoints.websecure.forwardedHeaders.trustedIPs=<包含上一跳IP或网段>
```

如果符合：

- `trustedIPs` 已覆盖上一跳。
- 如果第 2 步仍失败，继续第 7 步。

如果不是预期结果：

- 执行修复路径 R2。

## 7. 验证 Traefik 到 platform 的路由是否直连 dts-platform

命令：

```bash
docker inspect dts-platform --format '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}'
```

预期结果：

```text
172.18.0.x
```

如果符合：

- 继续查看 Traefik access log 和 platform trace 的同一次请求。
- 如果 Traefik 有真实 `X-Forwarded-For`，platform 没有，保留第 1 步和第 2 步日志作为问题证据，检查 Traefik 中间件或动态配置是否清理请求头。

如果不是预期结果：

- `dts-platform` 不在预期 Docker 网络，或容器异常。
- 执行修复路径 R1。

## 8. 检查 Docker DNAT 规则是否存在

命令：

```bash
sudo iptables -t nat -S DOCKER
```

预期结果：

- 输出包含将宿主机 `80` 和 `443` 转发到 `DTS_PROXY_IP` 的 DNAT 规则，例如：

```text
-A DOCKER ... -p tcp ... --dport 443 -j DNAT --to-destination <DTS_PROXY_IP>:443
-A DOCKER ... -p tcp ... --dport 80 -j DNAT --to-destination <DTS_PROXY_IP>:80
```

如果符合：

- 继续第 9 步。

如果不是预期结果：

- Docker 发布端口的 DNAT 规则缺失。
- 执行修复路径 R3。

## 9. 检查 PREROUTING 是否跳转到 DOCKER chain

命令：

```bash
sudo iptables -t nat -S PREROUTING
```

预期结果：

- 输出包含：

```text
-A PREROUTING -m addrtype --dst-type LOCAL -j DOCKER
```

如果符合：

- 继续第 10 步。

如果不是预期结果：

- 入站流量不会进入 Docker DNAT 链。
- 执行修复路径 R3。

## 10. 检查 OUTPUT 是否跳转到 DOCKER chain

命令：

```bash
sudo iptables -t nat -S OUTPUT
```

预期结果：

- 输出包含：

```text
-A OUTPUT ! -d 127.0.0.0/8 -m addrtype --dst-type LOCAL -j DOCKER
```

如果符合：

- 继续第 11 步。

如果不是预期结果：

- 本机访问发布端口路径异常，但远程客户端访问不一定受影响。
- 继续第 11 步。

## 11. 检查 POSTROUTING 是否存在错误 SNAT/MASQUERADE

命令：

```bash
sudo iptables -t nat -S POSTROUTING
```

预期结果：

- 可以有 Docker 出站 MASQUERADE。
- 不应存在把入站到 `DTS_PROXY_IP:80/443` 或 `172.18.0.0/16` 的外部客户端流量改写为 `172.18.0.1` 的额外 SNAT/MASQUERADE 规则。

如果符合：

- 继续第 12 步。

如果不是预期结果：

- 存在错误 SNAT/MASQUERADE。
- 执行修复路径 R4。

## 12. 检查是否由 docker-proxy 接管 80/443

命令：

```bash
pgrep -af docker-proxy
```

预期结果：

- 不存在监听 `0.0.0.0:80` 或 `0.0.0.0:443` 并转发到 `dts-proxy` 的 docker-proxy 进程。

如果符合：

- 继续第 13 步。

如果不是预期结果：

- docker-proxy 可能导致容器内看到的 peer 变成 Docker 网桥地址。
- 执行修复路径 R5。

## 13. 确认 iptables 后端

命令：

```bash
iptables -V
```

预期结果：

- 输出明确显示 `nf_tables` 或 `legacy`。
- 继续第 14 步。

如果不是预期结果：

- 现场 iptables 工具异常。
- 先修复系统 iptables 工具，再重新从第 8 步开始。

## 14. 检查 nftables 是否存在另一套 Docker/NAT 规则

命令：

```bash
sudo nft list ruleset
```

预期结果：

- nftables 与第 8 到 11 步观察到的 iptables 后端一致。
- 不应出现另一套互相冲突的 80/443 DNAT、SNAT、MASQUERADE 规则。

如果符合：

- 继续第 15 步。

如果不是预期结果：

- 可能存在 iptables legacy/nft 后端不一致，或 nft 规则覆盖 Docker NAT 行为。
- 执行修复路径 R6。

## 15. 抓包确认真实客户端 IP 在哪一层丢失

命令：

```bash
sudo tcpdump -ni any "host CLIENT_IP and tcp port 443" -c 20
```

预期结果：

- 能看到 `CLIENT_IP` 访问宿主机 `443` 的 TCP 包。

如果符合：

- 宿主机入口能看到真实客户端 IP。
- 继续第 16 步。

如果不是预期结果：

- 真实客户端 IP 在到达宿主机前已经丢失。
- 检查现场前置网络、K8s 节点、LB、网关或访问路径。
- DTS 侧无法通过代码或 `TRUSTED_PROXY_CIDRS` 恢复这个 IP。

## 16. 找到 Docker bridge 名称

命令：

```bash
docker network inspect dts-core -f '{{ index .Options "com.docker.network.bridge.name" }}'
```

预期结果：

```text
br-xxxxxxxxxxxx
```

如果符合：

- 记录输出为：

```text
DTS_BRIDGE_IF=<上一步输出>
```

- 继续第 17 步。

如果不是预期结果：

- `dts-core` 网络异常。
- 执行修复路径 R1。

## 17. 在 Docker bridge 上抓包

命令：

```bash
sudo tcpdump -ni DTS_BRIDGE_IF "tcp port 443" -c 20
```

预期结果之一：

```text
CLIENT_IP -> DTS_PROXY_IP.443
```

如果符合：

- Docker bridge 上仍保留真实客户端 IP。
- 如果 Traefik/platform 日志仍显示 `172.18.0.1`，继续查 Traefik 容器内监听和转发。

如果看到：

```text
172.18.0.1 -> DTS_PROXY_IP.443
```

- 真实客户端 IP 在宿主机入口到 Docker bridge 之间被改写。
- 执行修复路径 R3、R4、R5、R6 中与第 8 到 14 步不符合项对应的修复。

如果没有相关包：

- 客户端请求没有进入 `dts-core` bridge。
- 回到第 4 步确认端口发布和容器网络。

## 修复路径 R1：重建 DTS 入口容器

使用条件：

- `dts-proxy` 端口发布异常。
- `dts-proxy` 或 `dts-platform` 不在预期 Docker 网络。
- `dts-core` 网络状态异常。

命令：

```bash
docker compose -f docker-compose.legacy.yml up -d --force-recreate dts-proxy
```

预期结果：

- `dts-proxy` 重建成功。
- 重建后正常访问业务页面，再从第 1 步重新验证。

如果不是预期结果：

- 保留 compose 输出。
- 检查镜像、证书、端口占用和 Docker daemon 状态。

## 修复路径 R2：配置 TRUSTED_PROXY_CIDRS

使用条件：

- 第 1 步 Traefik access log 中 `X-Forwarded-For` 或 `Forwarded` 已包含 `CLIENT_IP`。
- 第 6 步显示 `trustedIPs` 未覆盖上一跳来源。
- 本现场声明没有前置代理，因此通常不应满足这两个条件；如果满足，说明现场链路中仍有设备或组件注入了代理头，需要先确认这个上一跳来源。

编辑 `.env` 或现场环境文件，设置：

```text
TRUSTED_PROXY_CIDRS=127.0.0.1/32,<上一跳代理IP或CIDR>
```

隔离内网临时验证可使用：

```text
TRUSTED_PROXY_CIDRS=127.0.0.1/32,10.0.0.0/8,172.16.0.0/12,192.168.0.0/16
```

风险说明：

- `TRUSTED_PROXY_CIDRS` 配的是可信上一跳代理，不是客户端网段白名单。
- 对本现场来说，不建议因为客户端在 `10.10.10.0/24`、`10.10.11.0/24` 就直接配置这两个网段。
- CIDR 放得越宽，越多内网来源可以伪造 `X-Forwarded-For`。
- 长期配置应只写真实上一跳代理、网关或负载均衡器的 IP/CIDR；如果确认没有这类上一跳代理，可以不配置。

命令：

```bash
docker compose -f docker-compose.legacy.yml up -d --force-recreate dts-proxy
```

预期结果：

- `dts-proxy` 重建成功。
- 重建后正常访问业务页面，再从第 1 步重新验证。

## 修复路径 R3：恢复 Docker DNAT 规则

使用条件：

- 第 8 步缺少 `80/443 -> DTS_PROXY_IP` 的 DNAT 规则。
- 第 9 步缺少 PREROUTING 到 DOCKER chain 的跳转。

命令：

```bash
sudo systemctl restart docker
```

预期结果：

- Docker daemon 重启成功。
- 注意：该命令会影响所有 Docker 容器。

下一条命令：

```bash
docker compose -f docker-compose.legacy.yml up -d
```

预期结果：

- DTS 容器重新拉起。
- 完成后正常访问业务页面，再从第 1 步重新验证。

如果不是预期结果：

- 保留 `systemctl status docker` 和 compose 输出。
- 继续第 13、14 步确认 iptables/nft 后端一致性。

## 修复路径 R4：删除错误 SNAT/MASQUERADE

使用条件：

- 第 11 步发现明确匹配入站 80/443 或 `DTS_PROXY_IP` 的错误 SNAT/MASQUERADE。

先列出规则行号：

```bash
sudo iptables -t nat -L POSTROUTING --line-numbers -n -v
```

预期结果：

- 能定位到错误 SNAT/MASQUERADE 的规则编号，记为：

```text
BAD_RULE_NO=<规则编号>
```

删除该规则：

```bash
sudo iptables -t nat -D POSTROUTING BAD_RULE_NO
```

预期结果：

- 删除成功。
- 删除后正常访问业务页面，再从第 1 步重新验证。

如果不是预期结果：

- 不要继续删除其他规则。
- 保留第 11 步和本步骤输出，确认规则来源后再处理。

## 修复路径 R5：关闭 docker userland-proxy

使用条件：

- 第 12 步发现 docker-proxy 接管 `80` 或 `443`。
- 第 8、9 步 Docker DNAT 不完整或不生效。

查看 Docker daemon 配置：

```bash
cat /etc/docker/daemon.json
```

预期结果：

- 配置中包含：

```json
"userland-proxy": false
```

如果不是预期结果：

- 在 `/etc/docker/daemon.json` 中加入或调整该配置。
- 修改后执行修复路径 R3。

如果符合：

- docker-proxy 不应继续接管 80/443。
- 如果仍存在 docker-proxy，执行修复路径 R3 后重新验证。

## 修复路径 R6：统一 iptables/nft 后端

使用条件：

- 第 13、14 步显示 iptables 和 nftables 规则后端不一致。
- Docker 规则写入一个后端，但系统实际生效规则在另一个后端。

命令：

```bash
sudo update-alternatives --display iptables
```

预期结果：

- 能看到当前 iptables 指向 `iptables-nft` 或 `iptables-legacy`。

如果不是预期结果：

- 现场系统未通过 alternatives 管理 iptables。
- 保留输出并按操作系统厂商文档统一后端。

如果符合：

- 选择现场系统推荐后端。
- Kylin V10 常见选择是 `iptables-nft`。
- 统一后执行修复路径 R3，再正常访问业务页面，并从第 1 步重新验证。

## 最终判定表

| 证据 | 根因层级 | 修复路径 |
| --- | --- | --- |
| Traefik access log 已有 `CLIENT_IP`，platform 没有 | Traefik 到 platform 转发头链路 | R2，必要时查中间件 |
| Traefik `ClientAddr=172.18.0.1`，但 access log 头里有真实 `X-Forwarded-For` | Traefik 不信任上一跳代理头 | R2 |
| Traefik `ClientAddr=172.18.0.1`，且没有真实 `X-Forwarded-For/Forwarded` | Traefik 入口之前已经丢 IP | 第 4 到 17 步 |
| Docker DNAT 缺失 | Docker NAT 规则损坏 | R3 |
| POSTROUTING 有错误 SNAT/MASQUERADE | 宿主机 NAT 误改源地址 | R4 |
| docker-proxy 接管 80/443 | userland-proxy 路径丢源地址 | R5 |
| iptables/nft 后端不一致 | 系统防火墙后端不一致 | R6 |
| 宿主机 `tcpdump any` 都看不到 `CLIENT_IP` | DTS 宿主机前置网络已丢 IP | 查客户网络/K8s/LB/网关 |

## 现场回传证据清单

如果排查后仍无法定位，请回传以下命令输出：

```bash
tail -n 200 logs/traefik/access.log
```

```bash
grep "platform-session-status-client-ip" logs/dts-platform/*.log
```

```bash
docker port dts-proxy
```

```bash
docker inspect dts-proxy --format '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}'
```

```bash
sudo iptables -t nat -S DOCKER
```

```bash
sudo iptables -t nat -S PREROUTING
```

```bash
sudo iptables -t nat -S POSTROUTING
```

```bash
pgrep -af docker-proxy
```

```bash
iptables -V
```

```bash
sudo nft list ruleset
```
