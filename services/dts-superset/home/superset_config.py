import os
from datetime import timedelta
from flask_appbuilder.security.manager import AUTH_OID
from superset_ext.security import CustomSsm

# 反向代理子路径（同域名挂载）支持：
# - Traefik 会 StripPrefix(/dashboards) 并注入 X-Forwarded-Prefix: /dashboards
# - 这里通过中间件把 SCRIPT_NAME 设置为前缀，确保 Superset 生成的链接/静态资源路径正确
class ReverseProxied:  # noqa: D101
    def __init__(self, app):
        self.app = app

    def __call__(self, environ, start_response):
        forwarded_prefix = environ.get("HTTP_X_FORWARDED_PREFIX", "").rstrip("/")
        if forwarded_prefix:
            environ["SCRIPT_NAME"] = forwarded_prefix
        forwarded_proto = environ.get("HTTP_X_FORWARDED_PROTO")
        if forwarded_proto:
            environ["wsgi.url_scheme"] = forwarded_proto
        return self.app(environ, start_response)

# 基础配置
SECRET_KEY = os.environ.get("SUPERSET_SECRET_KEY", "change-me")
SQLALCHEMY_DATABASE_URI = os.environ.get(
    "SUPERSET_DATABASE_URI",
    "postgresql+psycopg2://superset:superset@dts-pg:5432/superset",
)
SQLALCHEMY_TRACK_MODIFICATIONS = False
WTF_CSRF_ENABLED = True
SESSION_COOKIE_SECURE = True
SESSION_COOKIE_SAMESITE = "Lax"
SESSION_COOKIE_PATH = os.environ.get("SUPERSET_SESSION_COOKIE_PATH", "/dashboards")
PERMANENT_SESSION_LIFETIME = timedelta(hours=8)
APP_NAME = os.environ.get("SUPERSET_APP_NAME", "数据驾驶舱")

# 认证与 SSO（Keycloak OIDC）
ENABLE_PROXY_FIX = True
AUTH_TYPE = AUTH_OID
CUSTOM_SECURITY_MANAGER = CustomSsm
OIDC_CLIENT_SECRETS = os.environ.get("OIDC_CLIENT_SECRETS", "/app/pythonpath/client_secrets.json")
OIDC_OPENID_REALM = os.environ.get("OIDC_OPENID_REALM", "superset")
OIDC_INTROSPECTION_AUTH_METHOD = "client_secret_post"
OIDC_ID_TOKEN_COOKIE_SECURE = True
OIDC_TOKEN_COOKIE_SECURE = True
AUTH_ROLE_PUBLIC = None
AUTH_USER_REGISTRATION = True
AUTH_USER_REGISTRATION_ROLE = "Gamma"

# SQL Lab / 安全
ENABLE_CORS = True
CORS_OPTIONS = {"supports_credentials": True}
FEATURE_FLAGS = {
    "ENABLE_TEMPLATE_PROCESSING": True,
}
SQLLAB_CTAS_NO_LIMIT = False
SQLLAB_ASYNC_TIME_LIMIT_SEC = 600
SQL_MAX_ROW = 50000
SQLLAB_DISABLE_SQLALCHEMY_MSG = True
ALLOW_DML = False  # 禁止 DML

# 日志与轮转（100MB 轮转可用 logrotate，以下为简单文件路径示例）
LOG_FORMAT = "%(asctime)s:%(levelname)s:%(name)s:%(message)s"
LOG_FILE = os.environ.get("SUPERSET_LOG_FILE", "/app/superset_home/logs/superset.log")

# DM / Inceptor 等 JDBC 驱动路径示例
JAVA_HOME = os.environ.get("JAVA_HOME", "/usr/lib/jvm/java-17-openjdk")
EXTRA_JAVA_CLASSPATH = os.environ.get("EXTRA_JAVA_CLASSPATH", "/app/superset_home/drivers/*")

# DM 连接 URI 模板（示例）
# dm+pyodbc://user:pass@host:port/DB?driver=DM%20ODBC%20DRIVER

# RLS 示例（数据集过滤条件可使用 current_user.extra_attributes）
#   dept_code = '{{ current_user.extra_attributes.get("dept_code") }}'
#   max_data_security_level = '{{ current_user.extra_attributes.get("max_data_security_level") }}'

# 安全 HTTP 头
TALISMAN_CONFIG = {
    "force_https": True,
    "session_cookie_secure": True,
    "content_security_policy": None,
}

def FLASK_APP_MUTATOR(app):  # noqa: D103
    app.wsgi_app = ReverseProxied(app.wsgi_app)
    return app
