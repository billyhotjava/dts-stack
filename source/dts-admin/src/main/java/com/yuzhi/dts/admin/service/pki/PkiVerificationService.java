package com.yuzhi.dts.admin.service.pki;

import com.yuzhi.dts.admin.config.PkiAuthProperties;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class PkiVerificationService {

    private static final Logger log = LoggerFactory.getLogger(PkiVerificationService.class);

    public record VerifyResult(boolean ok, String message, Map<String, Object> identity) {}

    private final PkiAuthProperties props;

    public PkiVerificationService(PkiAuthProperties props) {
        this.props = props;
    }

    public VerifyResult verifyPkcs7(String originDataBase64, String p7SignatureBase64, String certBase64Optional) {
        if (originDataBase64 == null || p7SignatureBase64 == null) {
            return new VerifyResult(false, "缺少签名参数", null);
        }

        byte[] originBytes;
        String originText;
        String normalizedOrigin = originDataBase64.trim();
        try {
            originBytes = Base64.getDecoder().decode(normalizedOrigin.replaceAll("\\s+", ""));
            originText = new String(originBytes, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            originBytes = originDataBase64.getBytes(StandardCharsets.UTF_8);
            originText = originDataBase64;
        }

        String normalizedSignature = p7SignatureBase64.replaceAll("\\s+", "");

        // Try vendor JAR if configured
        if (props != null && props.getVendorJarPath() != null && !props.getVendorJarPath().isBlank()) {
            try {
                boolean verified = verifyWithVendor(originBytes, originText, normalizedSignature, certBase64Optional);
                if (!verified) {
                    return new VerifyResult(false, "签名验签失败", null);
                }
            } catch (Exception ex) {
                log.warn("Vendor PKI verification failed: {}", ex.toString());
                return new VerifyResult(false, "厂商网关验签失败: " + ex.getMessage(), null);
            }
        } else {
            // Without vendor JAR we cannot reliably verify P7 here
            return new VerifyResult(false, "未配置厂商JAR，无法完成服务端验签", null);
        }

        Map<String, Object> id = new HashMap<>();
        // Parse provided cert to extract identity if present
        if (certBase64Optional != null && !certBase64Optional.isBlank()) {
            try {
                byte[] certBytes = Base64.getDecoder().decode(certBase64Optional.replaceAll("\\s+", ""));
                CertificateFactory cf = CertificateFactory.getInstance("X.509");
                X509Certificate cert = (X509Certificate) cf.generateCertificate(new java.io.ByteArrayInputStream(certBytes));
                id.put("subjectDn", cert.getSubjectX500Principal().getName());
                id.put("issuerDn", cert.getIssuerX500Principal().getName());
                id.put("notBefore", cert.getNotBefore());
                id.put("notAfter", cert.getNotAfter());
                id.put("serialNumber", cert.getSerialNumber().toString(16));
                id.put("certB64", certBase64Optional);
            } catch (Exception ex) {
                log.debug("Failed to parse provided certificate: {}", ex.toString());
            }
        }

        id.put("signedAt", Instant.now());

        return new VerifyResult(true, "verified", id);
    }

    private boolean verifyWithVendor(byte[] originBytes, String originText, String p7SignatureBase64, String certBase64Optional)
        throws Exception {
            String jarPath = props.getVendorJarPath();
            String gatewayHost = props.getGatewayHost();
            int gatewayPort = props.getGatewayPort();
            if (gatewayHost == null || gatewayHost.isBlank()) {
                throw new IllegalStateException("未配置厂商网关地址 (dts.pki.gateway-host)");
            }
            if (gatewayPort <= 0) {
                throw new IllegalStateException("未配置厂商网关端口 (dts.pki.gateway-port)");
            }
            Path path = Path.of(jarPath);
            if (!Files.exists(path)) {
                throw new IllegalStateException("vendor-jar-path 不存在: " + jarPath);
            }
            // Build classloader with vendor jar and sibling jars to satisfy dependencies
            URL[] urls;
            if (Files.isDirectory(path)) {
                java.util.List<URL> list = new java.util.ArrayList<>();
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(path, "*.jar")) {
                    for (Path p : stream) {
                        try { list.add(p.toUri().toURL()); } catch (Exception e) { throw new RuntimeException(e); }
                    }
                }
                if (list.isEmpty()) {
                    throw new IllegalStateException("vendor 目录下未发现 JAR 文件: " + path.toAbsolutePath());
                }
                urls = list.toArray(new URL[0]);
            } else {
                // include sibling jars in the same dir to load transitive deps
                java.util.List<URL> list = new java.util.ArrayList<>();
                list.add(path.toUri().toURL());
                Path dir = path.getParent();
                if (dir != null && Files.isDirectory(dir)) {
                    try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.jar")) {
                        for (Path p : stream) {
                            if (!p.equals(path)) {
                                try { list.add(p.toUri().toURL()); } catch (Exception ignore) {}
                            }
                        }
                    }
                }
                urls = list.toArray(new URL[0]);
            }
            try (URLClassLoader loader = new URLClassLoader(urls, this.getClass().getClassLoader())) {
                Class<?> helperClz = Class.forName("com.koal.svs.client.SvsClientHelper", true, loader);
                Object helper = helperClz.getMethod("getInstance").invoke(null);

                // Build THostInfoSt
                Class<?> hostClz = Class.forName("com.koal.svs.client.st.THostInfoSt", true, loader);
                Object host = hostClz.getConstructor().newInstance();
                Method setIp = hostClz.getMethod("setSvrIP", String.class);
                setIp.invoke(host, gatewayHost);
                Method setPort = hostClz.getMethod("setPort", int.class);
                setPort.invoke(host, gatewayPort);

                // Try to initialize helper like demo: initialize(gwIP, gwPort, maxWaitMs, bCipher, socketTimeout)
                try {
                    Method init = helperClz.getMethod("initialize", String.class, int.class, int.class, boolean.class, int.class);
                    int maxWait = Math.max(1000, props.getApiTimeoutMs());
                    int socketTimeout = Math.max(500, props.getApiTimeoutMs());
                    boolean bCipher = false;
                    Object initResult = init.invoke(helper, gatewayHost, gatewayPort, maxWait, bCipher, socketTimeout);
                    if (!(initResult instanceof Boolean bool) || !bool) {
                        throw new IllegalStateException("厂商客户端初始化失败，请检查网关服务连通性");
                    }
                } catch (NoSuchMethodException ignore) {
                    // Some vendor builds may not expose initialize; safe to skip.
                }

                // Resolve digest constant reflectively; default SHA1
                int digestAlgo = 0;
                try {
                    String d = props.getDigest() == null ? "SHA1" : props.getDigest().toUpperCase();
                    String fieldName = switch (d) {
                        case "MD5" -> "DIGEST_ALGO_MD5";
                        case "NONE" -> "DIGEST_ALGO_NONE";
                        default -> "DIGEST_ALGO_SHA1";
                    };
                    digestAlgo = helperClz.getField(fieldName).getInt(null);
                } catch (Throwable ignore) {}

                // Attempt preferred method order:
                // 1) verifySign(-1,-1, byte[] origin, int len, String certB64, String signB64, THostInfoSt)
                // 2) PKCS7DataVerify(String plain, byte[] p7, int digestAlgo, THostInfoSt)
                Integer code = null;
                try {
                    Method verifySign = helperClz.getMethod(
                        "verifySign",
                        int.class,
                        int.class,
                        byte[].class,
                        int.class,
                        String.class,
                        String.class,
                        hostClz
                    );
                    String certB64 = certBase64Optional == null ? "" : certBase64Optional;
                    String signB64 = p7SignatureBase64 == null ? "" : p7SignatureBase64;
                    Object ret = verifySign.invoke(helper, -1, -1, originBytes, originBytes.length, certB64, signB64, host);
                    if (ret instanceof Integer) code = (Integer) ret;
                } catch (NoSuchMethodException nsme) {
                    // Fallback to PKCS7DataVerify
                }
                if (code == null) {
                    byte[] p7 = Base64.getDecoder().decode(String.valueOf(p7SignatureBase64).replaceAll("\\s+", "").getBytes(StandardCharsets.US_ASCII));
                    Method pkcs7 = helperClz.getMethod("PKCS7DataVerify", String.class, byte[].class, int.class, hostClz);
                    Object ret = pkcs7.invoke(helper, originText, p7, digestAlgo, host);
                    if (ret instanceof Integer) code = (Integer) ret; else code = 0;
                }
                // 0 = success per vendor convention
                return code == 0;
            } catch (InvocationTargetException ite) {
                Throwable cause = ite.getTargetException() != null ? ite.getTargetException() : ite;
                throw new Exception(cause);
            }
    }
}
