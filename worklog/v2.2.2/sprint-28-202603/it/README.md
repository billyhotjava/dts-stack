# Sprint-28 IT

## 集成验证范围

- 平台登录后默认入口与 BI 菜单首跳
- 左侧菜单进入 BI 页面
- BI 页面收藏、最近访问、多标签行为
- `/analytics/*` 旧路径兼容跳转
- `/public/card/:uuid`、`/public/dashboard/:uuid`、`/public/screen/:uuid` 匿名访问
- GPMC 第三层 drill 页停留超过 refresh 周期后的稳定性
- 同一浏览器内 `platform` 多 tab 顶号广播
- 同一浏览器内 `platform` 与 `admin` 同时登录且互不影响
- idle timeout / session expired / refresh failed 后的 redirect 回跳
- `modern` 下线前后的 compose/build/e2e 切换验证

## 验证记录

- 待实现后补充
