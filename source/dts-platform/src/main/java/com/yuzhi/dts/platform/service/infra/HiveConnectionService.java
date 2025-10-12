package com.yuzhi.dts.platform.service.infra;

import com.yuzhi.dts.platform.web.rest.infra.HiveConnectionTestRequest;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
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
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Base64;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.ConcurrentHashMap;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    private static final Set<String> REGISTERED_EXTERNAL_DRIVERS = ConcurrentHashMap.newKeySet();

    @FunctionalInterface
    public interface HiveConnectionCallback<T> {
        T doWithConnection(Connection connection, long connectStart) throws Exception;
    }

    private final ReentrantLock kerberosLock = new ReentrantLock();
    private volatile ClassLoader jdbcDriverLoader;

    @Value("${dts.jdbc.drivers-dir:/opt/dts/drivers}")
    private String externalDriversDir;

    @Value("${dts.jdbc.load-external-drivers:false}")
    private boolean loadExternalDrivers;

    @Value("${dts.jdbc.driver-class:}")
    private String explicitDriverClass;

    @Value("${dts.jdbc.driver-jar:}")
    private String explicitDriverJar;

    @Value("${dts.platform.hive.test.preserve-artifacts:false}")
    private boolean preserveArtifacts;

    @PostConstruct
    void ensureDriverPresent() {
        try {
            // Prefer external driver when enabled to avoid classpath Hadoop clashes
            if (loadExternalDrivers) {
                log.info("External JDBC driver loading enabled; dir={}, explicitClass={}", externalDriversDir, (explicitDriverClass == null || explicitDriverClass.isBlank()) ? "(none)" : explicitDriverClass);
                tryLoadExternalDrivers();
                return;
            }
            try {
                Class.forName(HIVE_DRIVER);
            } catch (ClassNotFoundException e) {
                log.warn("Hive JDBC driver not found on classpath and external loading disabled: {}", e.getMessage());
            }
        } catch (Throwable t) {
            // Never fail startup due to driver presence checks
            log.warn("Suppressing driver check failure during startup: {}", t.toString());
            log.debug("Driver check failure stacktrace", t);
        }
    }

    /** Exposes the classloader that loaded external JDBC drivers, if any. */
    public ClassLoader getJdbcDriverLoader() {
        return jdbcDriverLoader;
    }

    private <T> T executeWithinDriver(HiveConnectionTestRequest request, HiveConnectionCallback<T> callback) throws Exception {
        ClassLoader previousCl = Thread.currentThread().getContextClassLoader();
        if (jdbcDriverLoader != null) {
            Thread.currentThread().setContextClassLoader(jdbcDriverLoader);
        }
        try {
            prepareDriverClasses();
            String url = resolveJdbcUrl(request);
            java.util.Properties props = buildConnectionProperties(request);
            long connectStart = System.nanoTime();
            log.info(
                "Attempting JDBC connect. url={}, authMethod={}, propsKeys={}",
                url,
                request.getAuthMethod(),
                props.keySet()
            );
            try (Connection connection = openConnection(url, props)) {
                return callback.doWithConnection(connection, connectStart);
            }
        } finally {
            Thread.currentThread().setContextClassLoader(previousCl);
        }
    }

    public <T> T executeWithConnection(HiveConnectionTestRequest request, HiveConnectionCallback<T> callback) throws Exception {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(callback, "callback");

        kerberosLock.lock();
        Path tempDir = null;
        Path keytabPath = null;
        Path krb5Path = null;
        String previousKrb5 = System.getProperty(KRB5_CONF_KEY);
        String previousUseSubject = System.getProperty(USE_SUBJECT_CREDS_ONLY_KEY);
        try {
            tempDir = Files.createTempDirectory("dts-hive-exec-");
            if (request.getAuthMethod() == HiveConnectionTestRequest.AuthMethod.KEYTAB) {
                keytabPath = writeKeytab(tempDir, request);
            }
            krb5Path = prepareKrb5Conf(tempDir, request);
            System.setProperty(KRB5_CONF_KEY, krb5Path.toAbsolutePath().toString());
            System.setProperty(USE_SUBJECT_CREDS_ONLY_KEY, "false");
            log.info(
                "Kerberos system properties set: {}={}, {}={}",
                KRB5_CONF_KEY,
                krb5Path.toAbsolutePath(),
                USE_SUBJECT_CREDS_ONLY_KEY,
                System.getProperty(USE_SUBJECT_CREDS_ONLY_KEY)
            );

            PrivilegedExceptionAction<T> action = () -> executeWithinDriver(request, callback);

            // Use pure JAAS for both KEYTAB and PASSWORD to avoid Hadoop Shell native process checks
            // that may fail in restricted/containerized environments.
            LoginContext loginContext = buildLoginContext(request, keytabPath);
            loginContext.login();
            try {
                Subject subject = loginContext.getSubject();
                // Register Subject to vendor UserGroupInformation (e.g., Transwarp), if available
                bridgeKerberosSubjectToVendor(subject);
                return Subject.doAs(subject, action);
            } finally {
                try {
                    loginContext.logout();
                } catch (LoginException e) {
                    log.debug("Kerberos logout failure: {}", e.getMessage());
                }
            }
        } catch (PrivilegedActionException pae) {
            Throwable cause = pae.getException();
            if (cause instanceof Exception ex) {
                throw ex;
            }
            throw new IOException(cause);
        } finally {
            restoreProperty(KRB5_CONF_KEY, previousKrb5);
            restoreProperty(USE_SUBJECT_CREDS_ONLY_KEY, previousUseSubject);
            if (preserveArtifacts) {
                if (tempDir != null) {
                    log.warn("Preserving Kerberos artifacts for debugging under {}. Remember to clean up manually.", tempDir.toAbsolutePath());
                }
            } else {
                deleteQuietly(keytabPath);
                deleteQuietly(krb5Path);
                deleteQuietly(tempDir);
            }
            kerberosLock.unlock();
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
            // Build URL set with optional explicit jar first, then all jars from directory
            java.util.LinkedHashSet<URL> jarSet = new java.util.LinkedHashSet<>();
            if (explicitDriverJar != null && !explicitDriverJar.isBlank()) {
                Path jarPath = Path.of(explicitDriverJar);
                if (!jarPath.isAbsolute()) jarPath = dir.resolve(explicitDriverJar);
                if (Files.isRegularFile(jarPath)) {
                    try { jarSet.add(jarPath.toUri().toURL()); } catch (Exception ignored) {}
                } else {
                    log.warn("Configured driver jar not found: {}", jarPath);
                }
            }
            try (var stream = Files.list(dir)) {
                stream.filter(p -> p.toString().endsWith(".jar")).forEach(p -> {
                    try { jarSet.add(p.toUri().toURL()); } catch (Exception ignored) {}
                });
            }
            List<URL> jars = new ArrayList<>(jarSet);
            if (jars.isEmpty()) {
                log.debug("No JARs found under {}", dir);
                return;
            }
            if (jars.size() > 1) {
                log.warn("Multiple JDBC driver JARs detected under {} ({} files). To avoid conflicts, keep only one vendor driver jar.", dir, jars.size());
            }
            URL[] urls = jars.toArray(new URL[0]);
            try {
                log.info("Preparing external JDBC loader with {} jar(s); first={} dir={}", urls.length, urls[0].toString(), dir);
            } catch (Throwable ignored) {}
            // Use platform classloader as parent so standard Java modules like java.sql are visible
            ClassLoader parent = getPlatformOrSystemClassLoader();
            URLClassLoader loader = new URLClassLoader(urls, parent);
            boolean loaded = false;
            // 1) If explicit driver class is provided, try that first
            if (explicitDriverClass != null && !explicitDriverClass.isBlank()) {
                String cn = explicitDriverClass.trim();
                try {
                    Class<?> cls = Class.forName(cn, true, loader);
                    Object inst = cls.getDeclaredConstructor().newInstance();
                    if (inst instanceof java.sql.Driver driver) {
                        registerExternalDriver(driver);
                        loaded = true;
                        log.info("Loaded and registered explicit JDBC driver {} from {}", cn, externalDriversDir);
                    } else {
                        log.warn("Explicit class {} is not a java.sql.Driver", cn);
                    }
                } catch (Throwable e) {
                    log.warn("Failed to load explicit driver {}: {}", cn, e.toString());
                }
            }
            // 2) Discover via ServiceLoader (JDBC 4)
            if (!loaded) {
                try {
                    java.util.ServiceLoader<java.sql.Driver> drivers = java.util.ServiceLoader.load(java.sql.Driver.class, loader);
                    for (java.sql.Driver drv : drivers) {
                        try {
                            registerExternalDriver(drv);
                            log.info("Discovered and registered JDBC driver via ServiceLoader: {} (loader={})", drv.getClass().getName(), loader);
                            loaded = true;
                        } catch (Throwable t) {
                            log.debug("Ignoring ServiceLoader driver load failure: {}", t.toString());
                        }
                    }
                } catch (Throwable t) {
                    log.debug("ServiceLoader discovery failed: {}", t.toString());
                }
            }
            try {
                Class<?> cls = Class.forName(HIVE_DRIVER, true, loader);
                Object inst = cls.getDeclaredConstructor().newInstance();
                if (inst instanceof java.sql.Driver driver) {
                    registerExternalDriver(driver);
                }
                loaded = true;
                log.info("Loaded Hive driver {} from {}", HIVE_DRIVER, externalDriversDir);
            } catch (Throwable ignored) {
                for (String alt : FALLBACK_DRIVERS) {
                    try {
                        Class<?> cls = Class.forName(alt, true, loader);
                        Object inst = cls.getDeclaredConstructor().newInstance();
                        if (inst instanceof java.sql.Driver driver) {
                            registerExternalDriver(driver);
                        }
                        loaded = true;
                        log.info("Loaded alternative JDBC driver {} from {}", alt, externalDriversDir);
                        break;
                    } catch (Throwable e) {
                        // keep trying
            }
    }
            }
            if (loaded) {
                this.jdbcDriverLoader = loader;
                // Log available drivers for debugging (do not alter global TCCL here)
                try {
                    var e = java.sql.DriverManager.getDrivers();
                    while (e.hasMoreElements()) {
                        var d = e.nextElement();
                        log.info("DriverManager registered: {} via {}", d.getClass().getName(), d.getClass().getClassLoader());
                    }
                } catch (Throwable t) {
                    log.debug("Failed to list DriverManager drivers: {}", t.toString());
                }
            } else {
                log.warn("Failed to load any JDBC driver from {}", externalDriversDir);
            }
        } catch (Throwable ex) {
            log.warn("Error while loading external JDBC drivers (suppressed, will not fail startup): {}", ex.toString());
            log.debug("External driver load stacktrace", ex);
        }
    }

    private void prepareDriverClasses() {
        try {
            var e = DriverManager.getDrivers();
            while (e.hasMoreElements()) {
                var d = e.nextElement();
                log.info("Driver available before connect: {} via {}", d.getClass().getName(), d.getClass().getClassLoader());
            }
        } catch (Throwable ignored) {}
        try {
            if (jdbcDriverLoader != null) {
                Class.forName(HIVE_DRIVER, true, jdbcDriverLoader);
            } else {
                Class.forName(HIVE_DRIVER);
            }
        } catch (ClassNotFoundException ignored) {
            // Driver may have been registered via ServiceLoader from external JARs
        }
    }

    private String resolveJdbcUrl(HiveConnectionTestRequest request) {
        String url = request.getJdbcUrl();
        if (request.getAuthMethod() == HiveConnectionTestRequest.AuthMethod.KEYTAB && StringUtils.hasText(url)) {
            String lower = url.toLowerCase(Locale.ROOT);
            if (lower.startsWith("jdbc:hive2:") && !lower.contains(";authentication=")) {
                url = url + (url.endsWith(";") ? "" : ";") + "authentication=kerberos";
                log.info("Kerberos auth detected; appended authentication=kerberos to JDBC URL");
            }
        }
        return url;
    }

    private java.util.Properties buildConnectionProperties(HiveConnectionTestRequest request) {
        java.util.Properties props = new java.util.Properties();
        if (request.getAuthMethod() == HiveConnectionTestRequest.AuthMethod.PASSWORD && StringUtils.hasText(request.getPassword())) {
            props.setProperty("password", request.getPassword());
        }
        if (StringUtils.hasText(request.getProxyUser())) {
            props.setProperty("hive.server2.proxy.user", request.getProxyUser());
        }
        request.getJdbcProperties().forEach(props::setProperty);
        return props;
    }

    private static ClassLoader getPlatformOrSystemClassLoader() {
        try {
            return ClassLoader.getPlatformClassLoader();
        } catch (Throwable ignored) {
            return ClassLoader.getSystemClassLoader();
        }
    }

    public HiveConnectionTestResult testConnection(HiveConnectionTestRequest request) {
        Objects.requireNonNull(request, "request");
        long invocationStart = System.nanoTime();
        try {
            return executeWithConnection(request, (connection, connectStart) -> {
                long elapsed = elapsedMillis(connectStart);
                DatabaseMetaData metaData = connection.getMetaData();
                try {
                    log.info(
                        "JDBC connected in {} ms. driver={}, dbProduct={} {}",
                        elapsed,
                        safe(metaData::getDriverVersion),
                        safe(metaData::getDatabaseProductName),
                        safe(metaData::getDatabaseProductVersion)
                    );
                } catch (Exception ignored) {}
                runValidationQuery(connection, request.getTestQuery());
                return HiveConnectionTestResult.success(
                    "连接成功",
                    elapsed,
                    safe(metaData::getDatabaseProductVersion),
                    safe(metaData::getDriverVersion),
                    collectWarnings(connection.getWarnings())
                );
            });
        } catch (Exception ex) {
            long elapsed = elapsedMillis(invocationStart);
            log.warn("Hive connection test failed: {}", ex.getMessage());
            log.debug("Hive connection test stacktrace", ex);
            return HiveConnectionTestResult.failure(sanitizeMessage(ex), elapsed);
        }
    }

    private void bridgeKerberosSubjectToVendor(Subject subject) {
        if (subject == null || subject.getPrincipals().isEmpty()) {
            return;
        }
        try {
            ClassLoader loader = jdbcDriverLoader != null ? jdbcDriverLoader : Thread.currentThread().getContextClassLoader();
            Class<?> vendorUgiClass;
            try {
                vendorUgiClass = Class.forName("io.transwarp.jdbc.UserGroupInformation", true, loader);
            } catch (ClassNotFoundException e) {
                vendorUgiClass = Class.forName("io.transwarp.jdbc.UserGroupInformation");
            }
            Method loginUserFromSubject = vendorUgiClass.getMethod("loginUserFromSubject", Subject.class);
            loginUserFromSubject.invoke(null, subject);
            log.info("Registered Kerberos Subject with vendor UserGroupInformation for Transwarp driver");
        } catch (ClassNotFoundException e) {
            log.debug("Vendor UserGroupInformation not found on classpath; skipping vendor bridge");
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            log.warn("Vendor UserGroupInformation bridge failed: {}", sanitizeMessage(cause));
            log.debug("Vendor bridge failure stacktrace", cause);
        } catch (ReflectiveOperationException | SecurityException e) {
            log.debug("Unable to bridge Kerberos Subject into vendor UGI: {}", e.toString());
        }
    }

    private Connection openConnection(String url, java.util.Properties props) throws SQLException {
        try {
            return props == null || props.isEmpty() ? DriverManager.getConnection(url) : DriverManager.getConnection(url, props);
        } catch (SQLException ex) {
            String msg = Optional.ofNullable(ex.getMessage()).orElse("");
            if (!msg.contains("No suitable driver")) {
                throw ex;
            }
            log.warn("No suitable driver via DriverManager for {}. Trying driver enumeration and vendor URL fallbacks...", url);
            // Attempt to load external drivers on-demand (even if disabled), then retry
            try {
                tryLoadExternalDrivers();
            } catch (Throwable ignored) {}
            ClassLoader driverLoader = jdbcDriverLoader;
            ClassLoader previousLoader = Thread.currentThread().getContextClassLoader();
            boolean switchedLoader = false;
            if (driverLoader != null && previousLoader != driverLoader) {
                try {
                    Thread.currentThread().setContextClassLoader(driverLoader);
                    switchedLoader = true;
                } catch (SecurityException ignored) {}
            }
            try {
                if (driverLoader != null) {
                    try {
                        Connection retry = props == null || props.isEmpty()
                            ? DriverManager.getConnection(url)
                            : DriverManager.getConnection(url, props);
                        if (retry != null) {
                            return retry;
                        }
                    } catch (SQLException retryEx) {
                        log.debug("Retry with explicit driver classloader failed: {}", sanitizeMessage(retryEx));
                    }
                }
                // Re-list drivers after on-demand load (with the driver classloader if available)
                try {
                    var e = DriverManager.getDrivers();
                    while (e.hasMoreElements()) {
                        var d = e.nextElement();
                        log.info("Driver available after lazy load: {} via {}", d.getClass().getName(), d.getClass().getClassLoader());
                    }
                } catch (Throwable ignored) {}
                // Try explicitly with any registered drivers
                var drivers = DriverManager.getDrivers();
                while (drivers.hasMoreElements()) {
                    var d = drivers.nextElement();
                    try {
                        if (d.acceptsURL(url)) {
                            log.info("Using driver {} to connect", d.getClass().getName());
                            Connection c = d.connect(url, props);
                            if (c != null) return c;
                        }
                    } catch (Throwable ignored) {}
                }
                // Vendor-specific alias rewrites (e.g., Inceptor)
                String[] candidates = rewriteJdbcUrlCandidates(url);
                for (String candidate : candidates) {
                    if (candidate.equals(url)) continue;
                    try {
                        var ds = DriverManager.getDrivers();
                        while (ds.hasMoreElements()) {
                            var d = ds.nextElement();
                            try {
                                if (d.acceptsURL(candidate)) {
                                    log.info("Using driver {} with rewritten URL {}", d.getClass().getName(), candidate);
                                    Connection c = d.connect(candidate, props);
                                    if (c != null) return c;
                                }
                            } catch (Throwable ignored) {}
                        }
                        // direct try via DriverManager
                        Connection c = props == null || props.isEmpty()
                            ? DriverManager.getConnection(candidate)
                            : DriverManager.getConnection(candidate, props);
                        if (c != null) return c;
                    } catch (SQLException ignored) {
                        // keep trying
                    }
                }
            } finally {
                if (switchedLoader) {
                    try {
                        Thread.currentThread().setContextClassLoader(previousLoader);
                    } catch (SecurityException ignored) {}
                }
            }
            throw ex;
        }
    }

    private void registerExternalDriver(java.sql.Driver driver) throws SQLException {
        String driverName = driver.getClass().getName();
        ClassLoader loader = driver.getClass().getClassLoader();
        String key = driverName + "@" + Integer.toHexString(System.identityHashCode(loader));
        if (!REGISTERED_EXTERNAL_DRIVERS.add(key)) {
            log.debug("External JDBC driver already registered: {} (loader={})", driverName, loader);
            return;
        }
        Driver shim = new DriverShim(driver);
        try {
            DriverManager.registerDriver(shim);
            log.info("Registered JDBC driver via shim: {} (delegate loader={})",
                driverName,
                loader);
        } catch (SQLException e) {
            REGISTERED_EXTERNAL_DRIVERS.remove(key);
            throw e;
        }
    }

    private String[] rewriteJdbcUrlCandidates(String url) {
        List<String> out = new ArrayList<>();
        out.add(url);
        try {
            if (url != null && url.startsWith("jdbc:hive2:")) {
                out.add(url.replaceFirst("^jdbc:hive2", "jdbc:inceptor2"));
                out.add(url.replaceFirst("^jdbc:hive2", "jdbc:inceptor"));
            }
        } catch (Throwable ignored) {}
        return out.toArray(new String[0]);
    }

    private void runValidationQuery(Connection connection, String query) {
        String sql = (query == null || query.isBlank()) ? "SELECT 1" : query;
        try (Statement statement = connection.createStatement()) {
            statement.setMaxRows(1);
            log.info("Running validation query: {}", sql);
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
            try {
                long size = Files.size(keytabPath);
                log.info("Keytab written to {} ({} bytes), perms set to owner-read only", keytabPath.toAbsolutePath(), size);
            } catch (Exception ignored) {}
            return keytabPath;
        } catch (IllegalArgumentException ex) {
            throw new IOException("无法解析 keytab Base64 内容", ex);
        }
    }

    private static Path prepareKrb5Conf(Path tempDir, HiveConnectionTestRequest request) throws IOException {
        String content = request.getKrb5Conf();
        if (content == null || content.isBlank()) {
            throw new IOException("必须上传 krb5.conf 文件");
        }
        Path krb5 = tempDir.resolve("krb5.conf");
        Files.writeString(krb5, content, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        try {
            long size = Files.size(krb5);
            String summary = summarizeKrb5(content);
            log.info("krb5.conf written to {} ({} bytes). Summary: {}", krb5.toAbsolutePath(), size, summary);
        } catch (Exception e) {
            log.debug("Failed to summarize krb5.conf: {}", e.getMessage());
        }
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

    private static String summarizeKrb5(String content) {
        // Best-effort extract default_realm and realm->kdc counts; avoid verbose dumps
        String defaultRealm = null;
        Map<String, Integer> realmKdcCount = new HashMap<>();
        String currentRealm = null;
        boolean inRealms = false;
        Pattern defaultRealmPattern = Pattern.compile("^\\s*default_realm\\s*=\\s*([^#;]+)", Pattern.CASE_INSENSITIVE);
        Pattern realmHeaderPattern = Pattern.compile("^\\s*([A-Z0-9_.-]+)\\s*=\\s*\\{\\s*$");
        Pattern kdcPattern = Pattern.compile("^\\s*kdc\\s*=\\s*([^#;]+)", Pattern.CASE_INSENSITIVE);
        for (String rawLine : content.split("\\r?\\n")) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("#") || line.startsWith(";")) continue;
            if (defaultRealm == null) {
                Matcher m = defaultRealmPattern.matcher(line);
                if (m.find()) {
                    defaultRealm = m.group(1).trim();
                }
            }
            if (line.equalsIgnoreCase("[realms]")) {
                inRealms = true;
                currentRealm = null;
                continue;
            }
            if (line.startsWith("[")) {
                // new section
                inRealms = false;
                currentRealm = null;
                continue;
            }
            if (inRealms) {
                Matcher header = realmHeaderPattern.matcher(line);
                if (header.find()) {
                    currentRealm = header.group(1).trim();
                    realmKdcCount.putIfAbsent(currentRealm, 0);
                    continue;
                }
                if (line.startsWith("}")) {
                    currentRealm = null;
                    continue;
                }
                if (currentRealm != null) {
                    Matcher kdc = kdcPattern.matcher(line);
                    if (kdc.find()) {
                        realmKdcCount.put(currentRealm, realmKdcCount.getOrDefault(currentRealm, 0) + 1);
                    }
                }
            }
        }
        StringBuilder sb = new StringBuilder();
        sb.append("default_realm=").append(defaultRealm == null ? "(unknown)" : defaultRealm);
        if (!realmKdcCount.isEmpty()) {
            sb.append(", realms=");
            boolean first = true;
            for (Map.Entry<String, Integer> e : realmKdcCount.entrySet()) {
                if (!first) sb.append("; ");
                first = false;
                sb.append(e.getKey()).append("(kdc:").append(e.getValue()).append(")");
            }
        }
        return sb.toString();
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
            log.info("Kerberos login config: principal={}, method={}, useKeyTab={}, keyTabPath={}",
                request.getLoginPrincipal(),
                request.getAuthMethod(),
                options.get("useKeyTab"),
                (keytabPath == null ? "(none)" : keytabPath.toAbsolutePath().toString()));
            return new AppConfigurationEntry[] {
                new AppConfigurationEntry(
                    "com.sun.security.auth.module.Krb5LoginModule",
                    LoginModuleControlFlag.REQUIRED,
                    options)
            };
        }
    }

    private static final class DriverShim implements Driver {

        private final java.sql.Driver delegate;

        private DriverShim(java.sql.Driver delegate) {
            this.delegate = delegate;
        }

        @Override
        public Connection connect(String url, java.util.Properties info) throws SQLException {
            return delegate.connect(url, info);
        }

        @Override
        public boolean acceptsURL(String url) throws SQLException {
            return delegate.acceptsURL(url);
        }

        @Override
        public java.sql.DriverPropertyInfo[] getPropertyInfo(String url, java.util.Properties info) throws SQLException {
            return delegate.getPropertyInfo(url, info);
        }

        @Override
        public int getMajorVersion() {
            return delegate.getMajorVersion();
        }

        @Override
        public int getMinorVersion() {
            return delegate.getMinorVersion();
        }

        @Override
        public boolean jdbcCompliant() {
            return delegate.jdbcCompliant();
        }

        @Override
        public java.util.logging.Logger getParentLogger() throws java.sql.SQLFeatureNotSupportedException {
            return delegate.getParentLogger();
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
