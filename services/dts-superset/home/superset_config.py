import os
from datetime import timedelta
from flask_appbuilder.security.manager import AUTH_OID
from superset_ext.security import CustomSsm

# 基础配置
SECRET_KEY = os.environ.get("SUPERSET_SECRET_KEY", "change-me")
SQLALCHEMY_DATABASE_URI = os.environ.get(
    "SUPERSET_DATABASE_URI",
    "postgresql+psycopg2://superset:superset@dts-pg:5432/superset",
)
SQLALCHEMY_TRACK_MODIFICATIONS = False
WTF_CSRF_ENABLED = True
SESSION_COOKIE_SECURE = True
PERMANENT_SESSION_LIFETIME = timedelta(hours=8)

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
#   person_security_level >= '{{ current_user.extra_attributes.get("person_security_level") }}'

# 安全 HTTP 头
TALISMAN_CONFIG = {
    "force_https": True,
    "session_cookie_secure": True,
    "content_security_policy": None,
}
