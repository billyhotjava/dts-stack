# 逆向建模规划归属控件修复

- 问题：规划归属列默认单行省略，应用表的数据集市、主题域并排渲染，后者被裁切；业务过程标签和下拉框也相互挤占空间。
- 修复：本列固定 280px、关闭自动省略；业务过程、数据集市、主题域带标签分行显示，下拉框宽度限制在单元格内；无主题域时明确提示。
- 交付源码：`f179d8099d2f8f8e4905e81a0926ab37d33e319c`。
- 测试：构建目录 `/data/dts-stack` 的 3 个针对性测试文件共 8 项通过；覆盖业务过程值保留、集市和主题域选择、预览阻断清空、换集市后清除旧主题域。
- 浏览器：真实组件生成的布局夹具在 1366、768 宽度下，3 个规划下拉框均为 264px 并完整位于单元格内。截图位于 `/data/dts-stack/.artifacts/reverse-planning-{1366,768}.png`。此项不是登录页面整包验收，也不是实际 Chrome 95 运行结果。
- 正式构建：`bash builds/dts-build.sh --image dts-platform-webapp` 成功，包含类型检查及 Chrome 95 兼容构建。
- 镜像：`dts-platform-webapp:1.0.0`，ID `sha256:47ec7be3f770582f591e63ae3f7cdc7fa4fdb9c8e328ce5329a441c8de6d2ab2`。
- 交付包：`/data/dts-stack/builds/dist/dts-platform-webapp_1.0.0-20260921-161319.tar`，SHA256 `f16c5b2b88e7075437fe22d54403de293592326fb692923e76383dab9b1fb556`。
- 部署：沿用容器标签确认的 `/data/dts-stack/docker-compose-app.yml`、`dts-stack` 项目，仅重建前端容器；镜像 ID 和 revision 一致，nginx 检查通过。
- 公网：页面及 `data-modeling-CBkY9HFT.css` 返回 200；公网 CSS 与容器字节一致，含新规划控件样式。
- 待验收：用户真实账号下 PJM ZIP 完整生成预览；当前可用浏览器未登录，未宣称整包导入成功。openEuler 未部署。
