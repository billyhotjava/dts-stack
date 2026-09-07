package com.yuzhi.dts.analytics.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.analytics.domain.AnalyticsDatabase;
import com.yuzhi.dts.analytics.repository.AnalyticsDatabaseRepository;
import jakarta.persistence.EntityManagerFactory;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

/** Real ORM/transaction regression: the losing inner transaction must not dirty the outer legacy row. */
@SpringJUnitConfig(PlatformAnalyticsLegacyBindingTransactionTest.Config.class)
class PlatformAnalyticsLegacyBindingTransactionTest {
    @Autowired AnalyticsDatabaseRepository databases;
    @Autowired PlatformAnalyticsDatabaseRegistrationService registration;
    @Autowired PlatformTransactionManager transactions;
    @Autowired DataSource dataSource;

    @Test
    void uniqueBindingCollisionRollsBackOnlyTheAdoptionAndOuterTransactionCommitsWinner() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("alter table analytics_database add constraint uk_test_tenant_source unique(tenant_id, platform_data_source_id)");
        UUID source = UUID.randomUUID();
        var transaction = new TransactionTemplate(transactions);
        Long legacyId = transaction.execute(status -> databases.saveAndFlush(database(source, false)).getId());
        Long winnerId = transaction.execute(status -> databases.saveAndFlush(database(source, true)).getId());
        AnalyticsDatabase result = registration.ensureDataLakeDatabase(source);
        assertThat(result.getId()).isEqualTo(winnerId);
        assertThat(jdbc.queryForObject("select tenant_id from analytics_database where id = ?", String.class, legacyId)).isNull();
        assertThat(jdbc.queryForObject("select count(*) from analytics_database where tenant_id = 'default' and platform_data_source_id = ?", Integer.class, source)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select name from analytics_database where id = ?", String.class, winnerId)).isEqualTo(DataLakeDatabaseInitializer.DATA_LAKE_NAME);
    }

    private static AnalyticsDatabase database(UUID source, boolean bound) {
        AnalyticsDatabase database = new AnalyticsDatabase();
        database.setName(bound ? "winner" : "legacy");
        database.setEngine("postgres");
        database.setDetailsJson("{\"source\":\"data-lake\",\"system\":true,\"platformDataSourceId\":\"" + source + "\"}");
        if (bound) { database.setTenantId("default"); database.setPlatformDataSourceId(source); }
        return database;
    }

    @Configuration
    @EnableTransactionManagement
    @EnableJpaRepositories(basePackageClasses = AnalyticsDatabaseRepository.class,
        includeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = AnalyticsDatabaseRepository.class))
    static class Config {
        @Bean DataSource dataSource() {
            return new DriverManagerDataSource("jdbc:h2:mem:legacy_binding;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        }
        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
            var factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(dataSource);
            factory.setManagedTypes(PersistenceManagedTypes.of(AnalyticsDatabase.class.getName()));
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "create-drop"));
            return factory;
        }
        @Bean PlatformTransactionManager transactionManager(EntityManagerFactory factory) { return new JpaTransactionManager(factory); }
        @Bean PlatformAnalyticsDatabaseBindingWriter bindingWriter(AnalyticsDatabaseRepository databases) { return new PlatformAnalyticsDatabaseBindingWriter(databases); }
        @Bean PlatformAnalyticsDatabaseRegistrationService registration(AnalyticsDatabaseRepository databases, PlatformAnalyticsDatabaseBindingWriter writer) {
            return new PlatformAnalyticsDatabaseRegistrationService(databases, mock(PlatformInfraClient.class), mock(JdbcDetailsResolver.class),
                mock(MetadataSyncService.class), writer, new ObjectMapper(), "default");
        }
    }
}
