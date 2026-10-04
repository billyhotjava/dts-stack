# T04: 清理 git 中的日志文件

**模块**: Analytics 后端
**文件**: `source/dts-analytics/log.root_IS_UNDEFINED/`

## 问题

应用日志文件被提交到 git。目录名 `log.root_IS_UNDEFINED` 暗示日志配置问题（log base path 未正确解析）。

## 修复方案

1. `git rm -r source/dts-analytics/log.root_IS_UNDEFINED/`
2. 在 `.gitignore` 添加 `**/log.root_IS_UNDEFINED/`
3. 检查 `logback-spring.xml` 确认 `LOG_PATH` 变量是否正确设置
