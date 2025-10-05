package com.yuzhi.dts.platform.service.infra;

import com.yuzhi.dts.platform.web.rest.infra.HiveConnectionTestRequest;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Base64;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import javax.security.auth.Subject;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.auth.login.AppConfigurationEntry;
import javax.security.auth.login.AppConfigurationEntry.LoginModuleControlFlag;
import javax.security.auth.login.Configuration;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import java.net.URL;
import java.net.URLClassLoader;

@Service
public class HiveConnectionService {

    private static final Logger log = LoggerFactory.getLogger(HiveConnectionService.class);
    private static final String HIVE_DRIVER = "org.apache.hive.jdbc.HiveDriver";
    private static final String[] FALLBACK_DRIVERS = new String[] {
        // Common alternative driver class names for TDS/Inceptor distributions
        "com.transwarp.inceptor.Driver",
        "com.inceptor.jdbc.Driver",
    };
    private static final String KRB5_CONF_KEY = "java.security.krb5.conf";
    private static final String USE_SUBJECT_CREDS_ONLY_KEY = "javax.security.auth.useSubjectCredsOnly";

    private final ReentrantLock kerberosLock = new ReentrantLock();

    @Value("${dts.platform.jdbc.drivers-dir:/opt/dts/drivers}")
    private String externalDriversDir;

    @PostConstruct
    void ensureDriverPresent() {
        try {
            Class.forName(HIVE_DRIVER);
        } catch (ClassNotFoundException e) {
            log.warn("Hive JDBC driver not found on classpath: {}. Trying to load from {}", e.getMessage(), externalDriversDir);
            // Try to load external drivers placed under the configured directory
            tryLoadExternalDrivers();
        }
    }

    private void tryLoadExternalDrivers() {
        if (externalDriversDir == null || externalDriversDir.isBlank()) return;
        try {
            Path dir = Path.of(externalDriversDir);
            if (!Files.isDirectory(dir)) {
                log.debug("External drivers dir not found: {}", dir);
                return;
            }
            List<URL> jars = new ArrayList<>();
            try (var stream = Files.list(dir)) {
                stream.filter(p -> p.toString().endsWith(".jar")).forEach(p -> {
                    try {
                        jars.add(p.toUri().toURL());
                    } catch (Exception ignored) {}
                });
            }
            if (jars.isEmpty()) {
                log.debug("No JARs found under {}", dir);
                return;
            }
            URL[] urls = jars.toArray(new URL[0]);
            URLClassLoader loader = new URLClassLoader(urls, Thread.currentThread().getContextClassLoader());
            boolean loaded = false;
            try {
                Class.forName(HIVE_DRIVER, true, loader);
                loaded = true;
                log.info("Loaded Hive driver {} from {}", HIVE_DRIVER, externalDriversDir);
            } catch (ClassNotFoundException ignored) {
                for (String alt : FALLBACK_DRIVERS) {
                    try {
                        Class.forName(alt, true, loader);
                        loaded = true;
                        log.info("Loaded alternative JDBC driver {} from {}", alt, externalDriversDir);
                        break;
                    } catch (ClassNotFoundException e) {
                        // keep trying
                    }
                }
            }
            if (loaded) {
                Thread.currentThread().setContextClassLoader(loader);
            } else {
                log.warn("Failed to load any JDBC driver from {}", externalDriversDir);
            }
        } catch (Exception ex) {
            log.warn("Error while loading external JDBC drivers: {}", ex.getMessage());
            log.debug("External driver load stacktrace", ex);
        }
    }

    public HiveConnectionTestResult testConnection(HiveConnectionTestRequest request) {
        Objects.requireNonNull(request, "request");
        long start = System.nanoTime();
        kerberosLock.lock();
        try {
            Path tempDir = Files.createTempDirectory("dts-hive-test-");
            Path keytabPath = null;
            Path krb5Path = null;
            var previousKrb5 = System.getProperty(KRB5_CONF_KEY);
            var previousUseSubject = System.getProperty(USE_SUBJECT_CREDS_ONLY_KEY);
            try {
                if (request.getAuthMethod() == HiveConnectionTestRequest.AuthMethod.KEYTAB) {
                    keytabPath = writeKeytab(tempDir, request);
                }
                krb5Path = prepareKrb5Conf(tempDir, request);
                System.setProperty(KRB5_CONF_KEY, krb5Path.toAbsolutePath().toString());
                System.setProperty(USE_SUBJECT_CREDS_ONLY_KEY, "false");

                var loginContext = buildLoginContext(request, keytabPath);
                loginContext.login();
                HiveConnectionTestResult result;
                try {
                    result = Subject.doAs(loginContext.getSubject(), (PrivilegedExceptionAction<HiveConnectionTestResult>) () -> connectAndProbe(request));
                } catch (PrivilegedActionException pae) {
                    throw pae.getException();
                } finally {
                    try {
                        loginContext.logout();
                    } catch (LoginException e) {
                        log.debug("Kerberos logout failure: {}", e.getMessage());
                    }
                }
                return result;
            } catch (Exception ex) {
                long elapsed = elapsedMillis(start);
                log.warn("Hive connection test failed: {}", ex.getMessage());
                log.debug("Hive connection test stacktrace", ex);
                return HiveConnectionTestResult.failure(sanitizeMessage(ex), elapsed);
            } finally {
                restoreProperty(KRB5_CONF_KEY, previousKrb5);
                restoreProperty(USE_SUBJECT_CREDS_ONLY_KEY, previousUseSubject);
                deleteQuietly(keytabPath);
                deleteQuietly(krb5Path);
                deleteQuietly(tempDir);
            }
        } catch (IOException ioException) {
            long elapsed = elapsedMillis(start);
            log.warn("Failed to prepare Kerberos artifacts: {}", ioException.getMessage());
            log.debug("Kerberos artifacts preparation stacktrace", ioException);
            return HiveConnectionTestResult.failure(sanitizeMessage(ioException), elapsed);
        } finally {
            kerberosLock.unlock();
        }
    }

    private HiveConnectionTestResult connectAndProbe(HiveConnectionTestRequest request) throws SQLException, ClassNotFoundException {
        try {
            Class.forName(HIVE_DRIVER);
        } catch (ClassNotFoundException ignored) {
            // Driver may have been registered via ServiceLoader from external JARs (alternative vendor driver)
        }
        long connectStart = System.nanoTime();
        var url = request.getJdbcUrl();
        var props = new java.util.Properties();
        if (request.getLoginUser() != null) {
            props.setProperty("user", request.getLoginUser());
        }
        if (request.getAuthMethod() == HiveConnectionTestRequest.AuthMethod.PASSWORD && request.getPassword() != null) {
            props.setProperty("password", request.getPassword());
        }
        if (request.getProxyUser() != null) {
            props.setProperty("hive.server2.proxy.user", request.getProxyUser());
        }
        request.getJdbcProperties().forEach(props::setProperty);

        try (Connection connection = props.isEmpty() ? DriverManager.getConnection(url) : DriverManager.getConnection(url, props)) {
            var elapsed = elapsedMillis(connectStart);
            DatabaseMetaData metaData = connection.getMetaData();
            runValidationQuery(connection, request.getTestQuery());
            return HiveConnectionTestResult.success(
                "连接成功",
                elapsed,
                safe(metaData::getDatabaseProductVersion),
                safe(metaData::getDriverVersion),
                collectWarnings(connection.getWarnings())
            );
        }
    }

    private void runValidationQuery(Connection connection, String query) {
        String sql = (query == null || query.isBlank()) ? "SELECT 1" : query;
        try (Statement statement = connection.createStatement()) {
            statement.setMaxRows(1);
            statement.execute(sql);
        } catch (SQLException e) {
            log.warn("Validation query failed: {}", e.getMessage());
        }
    }

    private static List<String> collectWarnings(SQLWarning warning) {
        if (warning == null) {
            return List.of();
        }
        var warnings = new ArrayList<String>();
        SQLWarning current = warning;
        while (current != null) {
            warnings.add(Optional.ofNullable(current.getMessage()).orElse("SQLWarning"));
            current = current.getNextWarning();
        }
        return warnings;
    }

    private LoginContext buildLoginContext(HiveConnectionTestRequest request, Path keytabPath) {
        CallbackHandler handler = null;
        if (request.getAuthMethod() == HiveConnectionTestRequest.AuthMethod.PASSWORD) {
            handler = new KerberosPasswordCallbackHandler(request.getLoginPrincipal(), request.getPassword());
        }
        Configuration configuration = new KerberosConfiguration(request, keytabPath);
        try {
            return new LoginContext("DTS-Hive", null, handler, configuration);
        } catch (LoginException e) {
            throw new IllegalStateException("无法创建 Kerberos 登录上下文: " + sanitizeMessage(e), e);
        }
    }

    private static Path writeKeytab(Path tempDir, HiveConnectionTestRequest request) throws IOException {
        try {
            var decoded = Base64.getDecoder().decode(request.getKeytabBase64().replaceAll("\\s", ""));
            var fileName = Optional.ofNullable(request.getKeytabFileName()).map(KerberosUtils::safeFileName).orElse("client.keytab");
            Path keytabPath = tempDir.resolve(fileName);
            Files.write(keytabPath, decoded, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            try {
                var perms = EnumSet.of(PosixFilePermission.OWNER_READ);
                Files.setPosixFilePermissions(keytabPath, perms);
            } catch (UnsupportedOperationException ignored) {
                // Windows / non-posix file system
            }
            return keytabPath;
        } catch (IllegalArgumentException ex) {
            throw new IOException("无法解析 keytab Base64 内容", ex);
        }
    }

    private static Path prepareKrb5Conf(Path tempDir, HiveConnectionTestRequest request) throws IOException {
        String content;
        if (request.getKrb5Conf() != null) {
            content = request.getKrb5Conf();
        } else {
            if (request.getRealm() == null) {
                throw new IOException("缺少 Kerberos Realm 信息");
            }
            var sb = new StringBuilder();
            sb.append("[libdefaults]\n");
            sb.append("  default_realm = ").append(request.getRealm()).append('\n');
            sb.append("  dns_lookup_kdc = false\n");
            sb.append("  ticket_lifetime = 24h\n");
            sb.append("  renew_lifetime = 7d\n\n");
            sb.append("[realms]\n");
            sb.append("  ").append(request.getRealm()).append(" = {\n");
            if (request.getKdcs().isEmpty()) {
                throw new IOException("请至少配置一个 KDC 地址");
            }
            for (String kdc : request.getKdcs()) {
                sb.append("    kdc = ").append(kdc).append('\n');
            }
            sb.append("  }\n");
            content = sb.toString();
        }
        Path krb5 = tempDir.resolve("krb5.conf");
        Files.writeString(krb5, content, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        return krb5;
    }

    private static long elapsedMillis(long startNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
    }

    private static void restoreProperty(String key, String previousValue) {
        if (previousValue == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, previousValue);
        }
    }

    private static void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
        }
    }

    private static String sanitizeMessage(Throwable throwable) {
        if (throwable == null) {
            return "未知错误";
        }
        Throwable cause = throwable;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        String message = cause.getMessage();
        if (message == null || message.isBlank()) {
            message = cause.toString();
        }
        return message;
    }

    private static <T> T safe(SupplierWithException<T> supplier) {
        try {
            return supplier.get();
        } catch (Exception e) {
            return null;
        }
    }

    @FunctionalInterface
    private interface SupplierWithException<T> {
        T get() throws Exception;
    }

    private static final class KerberosConfiguration extends Configuration {

        private final HiveConnectionTestRequest request;
        private final Path keytabPath;

        private KerberosConfiguration(HiveConnectionTestRequest request, Path keytabPath) {
            this.request = request;
            this.keytabPath = keytabPath;
        }

        @Override
        public AppConfigurationEntry[] getAppConfigurationEntry(String name) {
            Map<String, Object> options = new HashMap<>();
            options.put("refreshKrb5Config", "true");
            options.put("storeKey", "true");
            options.put("isInitiator", "true");
            options.put("principal", request.getLoginPrincipal());
            options.put("doNotPrompt", String.valueOf(request.getAuthMethod() == HiveConnectionTestRequest.AuthMethod.KEYTAB));
            if (request.getAuthMethod() == HiveConnectionTestRequest.AuthMethod.KEYTAB && keytabPath != null) {
                options.put("useKeyTab", "true");
                options.put("keyTab", keytabPath.toAbsolutePath().toString());
            } else {
                options.put("useKeyTab", "false");
            }
            return new AppConfigurationEntry[] {
                new AppConfigurationEntry(
                    "com.sun.security.auth.module.Krb5LoginModule",
                    LoginModuleControlFlag.REQUIRED,
                    options)
            };
        }
    }

    private static final class KerberosPasswordCallbackHandler implements CallbackHandler {

        private final String principal;
        private final char[] password;

        private KerberosPasswordCallbackHandler(String principal, String password) {
            this.principal = principal;
            this.password = Optional.ofNullable(password).orElse("").toCharArray();
        }

        @Override
        public void handle(Callback[] callbacks) throws IOException, UnsupportedCallbackException {
            for (Callback callback : callbacks) {
                if (callback instanceof NameCallback nameCallback) {
                    nameCallback.setName(principal);
                } else if (callback instanceof PasswordCallback passwordCallback) {
                    passwordCallback.setPassword(password);
                } else {
                    throw new UnsupportedCallbackException(callback, "Unsupported Kerberos callback");
                }
            }
        }
    }

    private static final class KerberosUtils {
        private KerberosUtils() {}

        private static String safeFileName(String original) {
            var path = FileSystems.getDefault().getPath(original).getFileName();
            return path != null ? path.toString().replaceAll("[^A-Za-z0-9._-]", "_") : "client.keytab";
        }
    }
}
