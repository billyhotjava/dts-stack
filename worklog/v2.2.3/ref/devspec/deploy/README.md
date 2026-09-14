# devspec 发布与访问（nginx）

本目录内容为纯静态文件（HTML/Markdown/JSON/PNG），无需后端。两种访问方式：

## 方案 A（已部署）：静态同步到阿里云，nginx 直接托管

- 入口：`http://dev.yuzhicloud.com/devspec/login.html`（DNS 已解析到 39.106.43.56）
- 访问控制：nginx Basic Auth `admin` + 页面登录 `admin`
- 阿里云新增文件（未修改任何既有配置）：
  - `/etc/nginx/conf.d/dev.yuzhicloud.com.conf`（80 + 备用 8099）
  - `/etc/nginx/.htpasswd-devspec`
  - `/var/www/dts-ref/devspec`、`/var/www/dts-ref/intro`（rsync 同步）

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

阿里云侧：拷入 `nginx-devspec.conf.example` 的 server 段、创建 Basic Auth、reload；建议
`certbot --nginx` 配 HTTPS，安全组只放行 80/443。内容更新只需重复 rsync。

## 方案 B（临时）：开发机起静态服务，阿里云反代

适用：临时演示、内网/VPN 环境。开发机为 NAT 后的动态地址，链路稳定性依赖隧道。

```bash
# 开发机（已按此启动，端口 8090）
python3 -m http.server 8090 --bind 0.0.0.0 \
  --directory /opt/prod/s10/v2.2.3/worklog/v2.2.3/ref/devspec
# 日志：/tmp/opencode/devspec-http.log；停止：kill <pid>
```

阿里云 nginx 使用 `nginx-devspec.conf.example` 中被注释的 proxy 段，`proxy_pass` 指向
开发机可达地址（推荐 frp/Tailscale 隧道，而不是路由器端口映射）。同样叠加 Basic Auth。

## 安全说明

- 页面内置登录（admin/Devops123@）只是静态门禁，口令在页面中可见；**真正对外访问必须加
  nginx Basic Auth 或接入统一认证**。
- 只发布 `devspec`（与 `intro`）目录，不要暴露仓库其他内容。
- `/api/mdm/**` 等运行时接口与本静态站点无关。
