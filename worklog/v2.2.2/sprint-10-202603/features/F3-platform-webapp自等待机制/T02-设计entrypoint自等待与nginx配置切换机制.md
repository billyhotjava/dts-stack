# T02: 设计 entrypoint 自等待与 Nginx 配置切换机制

**优先级**: P0
**状态**: READY
**依赖**: T01

## 目标
实现 `platform-webapp` 容器先存活、再等待后端、后切换正式配置的启动行为。

## 技术设计
- 启动时先渲染一个可稳定运行的占位 Nginx 配置
- 后台循环检查 `dts-platform`、`dts-admin`、`dts-analytics` 的可解析/可访问状态
- 就绪后重新渲染正式代理配置并执行 `nginx -s reload`
- 控制等待日志和超时策略，避免无限静默

## 影响范围
- `builds/dts-platform-webapp/docker-entrypoint.sh`
- `builds/dts-platform-webapp/nginx.conf.template`
- 相关测试

## 验证
- [ ] 后端延迟可用时 webapp 容器仍保持 `Up`
- [ ] 后端就绪后代理配置切换成功

## 完成标准
- [ ] 不再需要人工二次启动 `dts-platform-webapp`
