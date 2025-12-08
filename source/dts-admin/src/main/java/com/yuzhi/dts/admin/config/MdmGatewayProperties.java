package com.yuzhi.dts.admin.config;

import jakarta.validation.constraints.NotBlank;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "dts.mdm.gateway")
public class MdmGatewayProperties {

    private boolean enabled = true;
    /**
     * 本地落盘目录，用于保存院端推送的全量 JSON。
     */
    @NotBlank
    private String storagePath = "../../data/mdm";

    /**
     * 网关专用日志文件路径，配合 logback 滚动 100MB。
     */
    @NotBlank
    private String logPath = "../../logs/dts-admin/mdm-gateway.log";

    private final Upstream upstream = new Upstream();
    private final Callback callback = new Callback();
    private final Registry registry = new Registry();
    private final Required required = new Required();
    /**
     * 本地组织根节点的代码，用于缺失 parentCode 时兜底挂载。
     */
    private String rootCode = "90";
    /**
     * 是否在 MDM 导入时自动为人员创建 Keycloak 用户（若不存在）。
     */
    private boolean autoProvisionUsers = true;
    /**
     * 自动创建用户时分配的基础角色（realm 角色），逗号分隔。
     */
    private String autoProvisionRoles = "EMPLOYEE";
    /**
     * 自动创建用户时是否启用登录（PKI 环境可禁用口令登录）。
     */
    private boolean autoProvisionEnableLogin = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getStoragePath() {
        return storagePath;
    }

    public void setStoragePath(String storagePath) {
        this.storagePath = storagePath;
    }

    public String getLogPath() {
        return logPath;
    }

    public void setLogPath(String logPath) {
        this.logPath = logPath;
    }

    public Upstream getUpstream() {
        return upstream;
    }

    public Callback getCallback() {
        return callback;
    }

    public Registry getRegistry() {
        return registry;
    }

    public Required getRequired() {
        return required;
    }

    public String getRootCode() {
        return rootCode;
    }

    public void setRootCode(String rootCode) {
        this.rootCode = rootCode;
    }

    public boolean isAutoProvisionUsers() {
        return autoProvisionUsers;
    }

    public void setAutoProvisionUsers(boolean autoProvisionUsers) {
        this.autoProvisionUsers = autoProvisionUsers;
    }

    public String getAutoProvisionRoles() {
        return autoProvisionRoles;
    }

    public void setAutoProvisionRoles(String autoProvisionRoles) {
        this.autoProvisionRoles = autoProvisionRoles;
    }

    public boolean isAutoProvisionEnableLogin() {
        return autoProvisionEnableLogin;
    }

    public void setAutoProvisionEnableLogin(boolean autoProvisionEnableLogin) {
        this.autoProvisionEnableLogin = autoProvisionEnableLogin;
    }

    public static class Upstream {

        /**
         * 院平台握手/拉取的完整地址 = baseUrl + pullPath。
         */
        private String baseUrl = "http://example-mdm-upstream";

        /**
         * 院平台的拉取/握手请求路径。
         */
        private String pullPath = "/api/mdm/pull";

        /**
         * 可选的 Bearer Token 或其他鉴权字段。
         */
        private String authToken;

        /**
         * 是否以 multipart 方式发送拉取请求（院方 demo：文件名 orgItDemand<ts>.txt，内容为 JSON 字符串）。
         */
        private boolean useMultipart = false;

        /**
         * multipart 的文件字段名。
         */
        private String filePartName = "file";

        /**
         * multipart 的文件名前缀与后缀。
         */
        private String filePrefix = "orgItDemand";
        private String fileSuffix = ".txt";

        /**
         * multipart 附带的表单参数（例如 targetNode 等），与文件内容的 JSON 分离。
         */
        private Map<String, String> formParams = new HashMap<>();

        /**
         * 发送给院方的 JSON payload 默认字段（作为文件内容），可被请求体覆盖。
         */
        private Map<String, String> payloadTemplate = new HashMap<>();

        private Duration connectTimeout = Duration.ofSeconds(5);
        private Duration readTimeout = Duration.ofSeconds(30);

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getPullPath() {
            return pullPath;
        }

        public void setPullPath(String pullPath) {
            this.pullPath = pullPath;
        }

        public String getAuthToken() {
            return authToken;
        }

        public void setAuthToken(String authToken) {
            this.authToken = authToken;
        }

        public boolean isUseMultipart() {
            return useMultipart;
        }

        public void setUseMultipart(boolean useMultipart) {
            this.useMultipart = useMultipart;
        }

        public String getFilePartName() {
            return filePartName;
        }

        public void setFilePartName(String filePartName) {
            this.filePartName = filePartName;
        }

        public String getFilePrefix() {
            return filePrefix;
        }

        public void setFilePrefix(String filePrefix) {
            this.filePrefix = filePrefix;
        }

        public String getFileSuffix() {
            return fileSuffix;
        }

        public void setFileSuffix(String fileSuffix) {
            this.fileSuffix = fileSuffix;
        }

        public Map<String, String> getFormParams() {
            return formParams;
        }

        public void setFormParams(Map<String, String> formParams) {
            this.formParams = formParams;
        }

        public Map<String, String> getPayloadTemplate() {
            return payloadTemplate;
        }

        public void setPayloadTemplate(Map<String, String> payloadTemplate) {
            this.payloadTemplate = payloadTemplate;
        }

        public Duration getConnectTimeout() {
            return connectTimeout;
        }

        public void setConnectTimeout(Duration connectTimeout) {
            this.connectTimeout = connectTimeout;
        }

        public Duration getReadTimeout() {
            return readTimeout;
        }

        public void setReadTimeout(Duration readTimeout) {
            this.readTimeout = readTimeout;
        }
    }

    public static class Callback {

        /**
         * 院端调用回调接口时带的 Token，用于快速校验。
         */
        private String authToken;

        /**
         * 提供给院方的回调地址（IP 直连）。
         */
        private String url;

        /**
         * 回调接口期望接收的签名头名称，例如 X-Signature。
         */
        private String signatureHeader = "X-Signature";

        /**
         * 允许访问回调接口的来源 IP 列表（逗号分隔，裸 IP；为空则不做限制）。
         */
        private String allowedIps = "";

        public String getAuthToken() {
            return authToken;
        }

        public void setAuthToken(String authToken) {
            this.authToken = authToken;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getSignatureHeader() {
            return signatureHeader;
        }

        public void setSignatureHeader(String signatureHeader) {
            this.signatureHeader = signatureHeader;
        }

        public String getAllowedIps() {
            return allowedIps;
        }

        public void setAllowedIps(String allowedIps) {
            this.allowedIps = allowedIps;
        }
    }

    /**
     * 针对院方文档的组织/部门全量接口，定义最小必需字段，做基础校验用。
     */
    private String requiredFields = "orgCode,deptCode,status";

    public String getRequiredFields() {
        return requiredFields;
    }

    public void setRequiredFields(String requiredFields) {
        this.requiredFields = requiredFields;
    }

    /**
     * 按类型定义必填字段（兼容院方示例：用户/部门分开校验）。
     */
    public static class Required {

        /**
         * 必填的人员字段（逗号分隔）
         */
        private String users = "userCode,userName,deptCode";

        /**
         * 必填的部门/组织字段（逗号分隔）
         */
        private String depts = "deptCode,deptName";

        public String getUsers() {
            return users;
        }

        public void setUsers(String users) {
            this.users = users;
        }

        public String getDepts() {
            return depts;
        }

        public void setDepts(String depts) {
            this.depts = depts;
        }
    }

    public static class Registry {

        /**
         * 系统编码 / sysCode
         */
        private String systemCode = "10XT";

        /**
         * 兼容院方配置 key=sysCode。
         */
        private String sysCode;

        /**
         * 数据范围 dataRange（院方示例值：9010）
         */
        private String dataRange = "9010";

        /**
         * 安全域 areaSecurity（院方示例值：9001）
         */
        private String areaSecurity = "9001";

        /**
         * 业务域 areaBusiness（院方示例值：B）
         */
        private String areaBusiness = "B";

        /**
         * dataType，院方示例 sync_demand 表示准备数据。
         */
        private String dataType = "sync-demand";

        public String getSystemCode() {
            return StringUtils.defaultIfBlank(systemCode, sysCode);
        }

        public void setSystemCode(String systemCode) {
            this.systemCode = systemCode;
        }

        public String getSysCode() {
            return getSystemCode();
        }

        public void setSysCode(String sysCode) {
            this.sysCode = sysCode;
            if (StringUtils.isBlank(this.systemCode)) {
                this.systemCode = sysCode;
            }
        }

        public String getDataRange() {
            return dataRange;
        }

        public void setDataRange(String dataRange) {
            this.dataRange = dataRange;
        }

        public String getAreaSecurity() {
            return areaSecurity;
        }

        public void setAreaSecurity(String areaSecurity) {
            this.areaSecurity = areaSecurity;
        }

        public String getAreaBusiness() {
            return areaBusiness;
        }

        public void setAreaBusiness(String areaBusiness) {
            this.areaBusiness = areaBusiness;
        }

        public String getDataType() {
            return dataType;
        }

        public void setDataType(String dataType) {
            this.dataType = dataType;
        }
    }
}
