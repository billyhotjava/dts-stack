# T01: 复盘 platform-webapp 当前启动失败链路

**优先级**: P0
**状态**: READY
**依赖**: 无

## 目标
明确 `dts-platform-webapp` 为何在 `dts-platform` 已定义网络别名的情况下仍会因为 upstream 解析失败退出。

## 技术设计
- 复盘 `docker-entrypoint.sh`
- 复盘 Nginx 模板对 upstream 解析的时机
- 结合远程现场日志确认失败发生在 Nginx 启动前还是 reload 时

## 影响范围
- `builds/dts-platform-webapp/docker-entrypoint.sh`
- `builds/dts-platform-webapp/nginx.conf.template`
- 相关测试与文档

## 验证
- [ ] 失败链路有明确结论
- [ ] 根因定位到具体时机和脚本行为

## 完成标准
- [ ] 形成可执行的修复假设
