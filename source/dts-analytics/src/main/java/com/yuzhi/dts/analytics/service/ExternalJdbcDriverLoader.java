package com.yuzhi.dts.analytics.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.ServiceLoader;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ExternalJdbcDriverLoader {

    private static final Logger log = LoggerFactory.getLogger(ExternalJdbcDriverLoader.class);

    private final String driversDir;
    private final List<URLClassLoader> classLoaders = new CopyOnWriteArrayList<>();

    public ExternalJdbcDriverLoader(@Value("${dts.analytics.jdbc.drivers-dir:}") String driversDir) {
        this.driversDir = driversDir == null ? "" : driversDir.trim();
    }

    @PostConstruct
    public void load() {
        if (driversDir.isBlank()) {
            return;
        }

        Path dir = Path.of(driversDir);
        if (!Files.exists(dir)) {
            log.info("External JDBC drivers dir not found: {}", dir);
            return;
        }
        if (!Files.isDirectory(dir)) {
            log.warn("External JDBC drivers path is not a directory: {}", dir);
            return;
        }

        List<Path> jars = new ArrayList<>();
        try (var stream = Files.list(dir)) {
            stream.filter(p -> p.getFileName().toString().toLowerCase().endsWith(".jar"))
                    .sorted()
                    .forEach(jars::add);
        } catch (IOException e) {
            log.warn("Failed to list external JDBC drivers in {}: {}", dir, e.getMessage());
            return;
        }

        if (jars.isEmpty()) {
            log.info("No external JDBC driver jars found in {}", dir);
            return;
        }

        for (Path jar : jars) {
            loadJar(jar);
        }
    }

    private void loadJar(Path jar) {
        URL url;
        try {
            url = jar.toUri().toURL();
        } catch (Exception e) {
            log.warn("Skip JDBC driver jar (invalid URL): {} ({})", jar, e.getMessage());
            return;
        }

        URLClassLoader cl = new URLClassLoader(new URL[] {url}, ExternalJdbcDriverLoader.class.getClassLoader());
        classLoaders.add(cl);

        boolean registeredAny = false;
        try {
            ServiceLoader<Driver> loader = ServiceLoader.load(Driver.class, cl);
            for (Driver driver : loader) {
                registerDriver(driver);
                registeredAny = true;
            }
        } catch (Throwable t) {
            log.warn("Failed to load drivers from {}: {}", jar.getFileName(), t.getMessage());
        }

        if (!registeredAny) {
            log.info("No java.sql.Driver providers found in {}", jar.getFileName());
        } else {
            log.info("Loaded external JDBC drivers from {}", jar.getFileName());
        }
    }

    private static void registerDriver(Driver driver) throws SQLException {
        if (driver == null) {
            return;
        }
        if (isDriverRegistered(driver.getClass().getName())) {
            return;
        }
        DriverManager.registerDriver(new DriverShim(driver));
    }

    private static boolean isDriverRegistered(String className) {
        if (className == null || className.isBlank()) {
            return false;
        }
        Enumeration<Driver> drivers = DriverManager.getDrivers();
        while (drivers.hasMoreElements()) {
            Driver existing = drivers.nextElement();
            if (existing != null && className.equals(existing.getClass().getName())) {
                return true;
            }
        }
        return false;
    }

    @PreDestroy
    public void close() {
        for (URLClassLoader cl : classLoaders) {
            try {
                cl.close();
            } catch (IOException ignore) {
                // ignore
            }
        }
        classLoaders.clear();
    }

    private static final class DriverShim implements Driver {
        private final Driver delegate;

        private DriverShim(Driver delegate) {
            this.delegate = delegate;
        }

        @Override
        public boolean acceptsURL(String url) throws SQLException {
            return delegate.acceptsURL(url);
        }

        @Override
        public java.sql.Connection connect(String url, java.util.Properties info) throws SQLException {
            return delegate.connect(url, info);
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
        public DriverPropertyInfo[] getPropertyInfo(String url, java.util.Properties info) throws SQLException {
            return delegate.getPropertyInfo(url, info);
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
}

