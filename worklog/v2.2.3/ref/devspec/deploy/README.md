# devspec 发布与访问（nginx）

本目录内容为纯静态文件（HTML/Markdown/JSON/PNG），无需后端。两种访问方式：

## 方案 A（已部署）：静态同步到阿里云，nginx 直接托管

- 入口：**https://dev.yuzhicloud.com/**（未登录 302 到 `/login.html`；HTTP 301 到 HTTPS；旧 `/devspec/*` 301 到根路径）
- 证书：Let's Encrypt 生产证书，certbot.timer 自动续期
- 访问控制：只有登录页，无浏览器 Basic Auth 弹窗。登录页 fetch `/api/login`，nginx 按
  `.htpasswd-devspec` 校验（失败返回 403，限流 10 次/分钟），成功下发 HttpOnly 会话 Cookie。
  会话令牌 = `过期时间.HMAC-SHA256(过期时间, 密钥)`，由 nginx 内嵌 perl（`DevspecSession.pm`）签发与校验：
  - **闲置 30 分钟自动退出**：每次访问受保护页面滑动续期；Cookie 同步设 `Max-Age=1800`，浏览器恢复会话也会失效；
  - 门户与文档页每分钟查询 `/api/session`（不续期），失效即跳转 `login.html?reason=timeout`；在门户或图内操作时经 `/api/session/touch` 续期；
  - `/api/logout` 清除 Cookie。页面源码不含口令。
  改口令：`htpasswd /etc/nginx/.htpasswd-devspec admin`；改超时：`DevspecSession.pm` 的 `$IDLE_SECONDS`；
  强制全部下线：更换 `DevspecSession.pm` 的 `$SECRET` 后 `nginx -t && systemctl reload nginx`
- 阿里云新增内容（未修改任何既有站点配置）：
  - `/etc/nginx/conf.d/dev.yuzhicloud.com.conf`（80 重定向 + 443 TLS + 备用 8099；chmod 600）
  - `/etc/nginx/perl/DevspecSession.pm`（会话签名密钥与超时；chmod 600）
  - 软件包 `libnginx-mod-http-perl`（与 nginx 同源同版本 1.24.0-2ubuntu7.17）
  - `/etc/nginx/.htpasswd-devspec`
  - `/var/www/dts-ref/devspec`、`/var/www/dts-ref/intro`（rsync 同步）
  - `/etc/letsencrypt/live/dev.yuzhicloud.com/`（certbot 签发）
- 验证（2026-09-15）：未登录各页 302 到登录页；错误口令 403、正确口令 204 + 30 分钟令牌；旧固定令牌、
  伪造令牌、签名正确但已过期的令牌均 302/401；访问页面与 touch 续期、`/api/session` 不续期；Chrome 实测
  会话过期后门户自动跳转 `login.html?reason=timeout` 并提示；bi/jira 不受影响

```bash
# 开发机 -> 阿里云（需要 SSH 权限）
rsync -avz --delete \
  /opt/prod/s10/v2.2.3/worklog/v2.2.3/ref/devspec/ \
  root@39.106.43.56:/var/www/dts-ref/devspec/

# 保留门户中的 ../intro/index.html 链接时一并同步
rsync -avz --delete \
  /opt/prod/s10/v2.2.3/worklog/v2.2.3/ref/intro/ \
  root@39.106.43.56:/var/www/dts-ref/intro/
```

阿里云侧：`apt install libnginx-mod-http-perl`；由 `DevspecSession.pm.example` 生成
`/etc/nginx/perl/DevspecSession.pm`（`__DEVSPEC_SESSION_SECRET__` 替换为 `openssl rand -hex 32`，`chmod 600`）；
拷入 `nginx-devspec.conf.example`，用 `htpasswd` 创建账号，`nginx -t` 后 reload；
安全组只放行 80/443。内容更新只需重复 rsync。

## 方案 B（临时）：开发机起静态服务，阿里云反代

适用：临时演示、内网/VPN 环境。开发机为 NAT 后的动态地址，链路稳定性依赖隧道。

```bash
# 开发机（已按此启动，端口 8090）
python3 -m http.server 8090 --bind 0.0.0.0 \
  --directory /opt/prod/s10/v2.2.3/worklog/v2.2.3/ref/devspec
# 日志：/tmp/opencode/devspec-http.log；停止：kill <pid>
```

阿里云 nginx 使用 `nginx-devspec.conf.example` 中被注释的 proxy 段，`proxy_pass` 指向
开发机可达地址（推荐 frp/Tailscale 隧道，而不是路由器端口映射）。同样套用 `/api/login` 会话门禁。

## 安全说明

- 登录依赖 nginx 服务端校验；方案 B 或直接打开本地文件时没有 `/api/login`，登录页无法使用，
  此时直接打开 `index.html` 阅读即可。
- 只发布 `devspec`（与 `intro`）目录，不要暴露仓库其他内容。
- `/api/mdm/**` 等运行时接口与本静态站点无关。
