package com.yuzhi.dts.mdm.demo.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties("mdm.demo")
public class MdmDemoProperties {

    private boolean enabled = true;
    private String storagePath = "data/mdm";
    private Required required = new Required();
    private Callback callback = new Callback();
    private Upstream upstream = new Upstream();
    private Registry registry = new Registry();

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

    public Required getRequired() {
        return required;
    }

    public void setRequired(Required required) {
        this.required = required;
    }

    public Callback getCallback() {
        return callback;
    }

    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    public Upstream getUpstream() {
        return upstream;
    }

    public void setUpstream(Upstream upstream) {
        this.upstream = upstream;
    }

    public Registry getRegistry() {
        return registry;
    }

    public void setRegistry(Registry registry) {
        this.registry = registry;
    }

    public static class Required {

        private String users = "userCode,userName,deptCode";
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

    public static class Callback {

        private String url = "http://10.10.10.135:8080/api/mdm/callback";
        private String authToken;
        private String signatureHeader = "X-Signature";

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getAuthToken() {
            return authToken;
        }

        public void setAuthToken(String authToken) {
            this.authToken = authToken;
        }

        public String getSignatureHeader() {
            return signatureHeader;
        }

        public void setSignatureHeader(String signatureHeader) {
            this.signatureHeader = signatureHeader;
        }
    }

    public static class Upstream {

        private String handshakeUrl = "http://example.com/api/mdm/handshake";
        private String authToken;
        private Duration connectTimeout = Duration.ofSeconds(5);
        private Duration readTimeout = Duration.ofSeconds(15);

        public String getHandshakeUrl() {
            return handshakeUrl;
        }

        public void setHandshakeUrl(String handshakeUrl) {
            this.handshakeUrl = handshakeUrl;
        }

        public String getAuthToken() {
            return authToken;
        }

        public void setAuthToken(String authToken) {
            this.authToken = authToken;
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

    public static class Registry {

        private String systemCode = "DTS-DEMO";
        private String securityDomain = "SECRET";
        private String pushMode = "FULL";
        private List<String> dataTypes = List.of("users", "depts");

        public String getSystemCode() {
            return systemCode;
        }

        public void setSystemCode(String systemCode) {
            this.systemCode = systemCode;
        }

        public String getSecurityDomain() {
            return securityDomain;
        }

        public void setSecurityDomain(String securityDomain) {
            this.securityDomain = securityDomain;
        }

        public String getPushMode() {
            return pushMode;
        }

        public void setPushMode(String pushMode) {
            this.pushMode = pushMode;
        }

        public List<String> getDataTypes() {
            return dataTypes;
        }

        public void setDataTypes(List<String> dataTypes) {
            this.dataTypes = dataTypes;
        }
    }
}
