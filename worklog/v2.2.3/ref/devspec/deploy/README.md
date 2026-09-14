# devspec 发布与访问（nginx）

本目录内容为纯静态文件（HTML/Markdown/JSON/PNG），无需后端。两种访问方式：

## 方案 A（已部署）：静态同步到阿里云，nginx 直接托管

- 入口：**https://dev.yuzhicloud.com/**（未登录 302 到 `/login.html`；HTTP 301 到 HTTPS；旧 `/devspec/*` 301 到根路径）
- 证书：Let's Encrypt 生产证书，certbot.timer 自动续期
- 访问控制：只有登录页，无浏览器 Basic Auth 弹窗。登录页 fetch `/api/login`，nginx 按
  `.htpasswd-devspec` 校验（失败返回 403，限流 10 次/分钟），成功下发 HttpOnly 会话 Cookie；
  其余路径校验 Cookie，否则 302 到登录页；`/api/logout` 清除 Cookie。页面源码不含口令。
  改口令：`htpasswd /etc/nginx/.htpasswd-devspec admin`；强制全部下线：更换配置中的会话令牌并 reload
- 阿里云新增文件（未修改任何既有配置）：
  - `/etc/nginx/conf.d/dev.yuzhicloud.com.conf`（80 重定向 + 443 TLS + 备用 8099）
  - `/etc/nginx/.htpasswd-devspec`
  - `/var/www/dts-ref/devspec`、`/var/www/dts-ref/intro`（rsync 同步）
  - `/etc/letsencrypt/live/dev.yuzhicloud.com/`（certbot 签发）
- 验证（2026-09-14）：未登录各页 302 到登录页；错误口令 403、正确口令 204 + Cookie；带 Cookie
  门户/图/md/intro 200；伪造 Cookie 302；退出后受保护页回到登录页；Chrome 全流程 0 个弹窗；bi/jira 不受影响

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

阿里云侧：拷入 `nginx-devspec.conf.example`，把 `__DEVSPEC_SESSION_TOKEN__` 替换为
`openssl rand -hex 32` 结果并 `chmod 600`，用 `htpasswd` 创建账号，`nginx -t` 后 reload；
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
