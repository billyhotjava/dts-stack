package com.yuzhi.dts.mdm.upstream.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties("mdm.mock")
public class UpstreamMockProperties {

    /**
     * 本地服务端口，默认 28080（可通过运行参数覆盖）
     */
    private int port = 28080;

    /**
     * 回调目标 URL，缺省指向 dts-admin 的 /api/mdm/receive。
     */
    private String callbackUrl = "http://localhost:38012/api/mdm/receive";

    /**
     * 回调时的 dataType（院方示例 sync-demand 表示准备数据；其他值为推送）
     */
    private String dataType = "full";

    /**
     * 回调时带的签名头名称（与 dts-admin 配置一致）
     */
    private String signatureHeader = "X-Signature";

    /**
     * 回调时带的签名值（可为空）
     */
    private String callbackToken;

    /**
     * 文件字段名（院方示例 file）
     */
    private String filePartName = "file";

    /**
     * 回调文件名前缀/后缀
     */
    private String filePrefix = "mdm-full-";
    private String fileSuffix = ".json";

    /**
     * 用于回调的样例 JSON（类路径或文件路径）
     */
    private String sampleLocation = "classpath:sample/orgs-users-large.json";

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getCallbackUrl() {
        return callbackUrl;
    }

    public void setCallbackUrl(String callbackUrl) {
        this.callbackUrl = callbackUrl;
    }

    public String getDataType() {
        return dataType;
    }

    public void setDataType(String dataType) {
        this.dataType = dataType;
    }

    public String getSignatureHeader() {
        return signatureHeader;
    }

    public void setSignatureHeader(String signatureHeader) {
        this.signatureHeader = signatureHeader;
    }

    public String getCallbackToken() {
        return callbackToken;
    }

    public void setCallbackToken(String callbackToken) {
        this.callbackToken = callbackToken;
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

    public String getSampleLocation() {
        return sampleLocation;
    }

    public void setSampleLocation(String sampleLocation) {
        this.sampleLocation = sampleLocation;
    }
}
