package com.yuzhi.dts.platform.service.catalog;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Serializes even an absent classification snapshot, before taking any dataset row lock. */
@Component
public class CatalogClassificationWriteLock {
    private final JdbcTemplate jdbc;
    public CatalogClassificationWriteLock(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @Transactional(propagation = Propagation.MANDATORY)
    public void lock(String type, String key) {
        jdbc.query("select pg_advisory_xact_lock(hashtextextended(?, 0))", rs -> { }, "catalog-classification:" + type + ":" + key);
    }
}
