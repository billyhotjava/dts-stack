package com.yuzhi.dts.admin.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.yuzhi.dts.admin.domain.OrganizationNode;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.liquibase.LiquibaseAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 覆盖 {@link OrganizationRepository#findAncestorChainByDeptCode(String)} 的递归 CTE 查询。
 * 验证 N+1 修复：单次 SQL 即可返回整条祖先链（根 → 目标节点）。
 *
 * <p>使用 {@link DataJpaTest} + Testcontainers PostgreSQL 切片，避免加载完整 Spring
 * 应用上下文（该上下文依赖 Keycloak，测试环境无法解析）。Liquibase 在启动时自动执行
 * master.xml，确保 schema 与生产一致。
 */
@DataJpaTest(
    properties = {
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.liquibase.change-log=classpath:config/liquibase/master.xml",
        // AuditingEntityListener 在测试中拿不到登录用户，这里强制 created_by 非空
        "spring.jpa.properties.hibernate.default_schema=public"
    }
)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class, LiquibaseAutoConfiguration.class })
@Testcontainers
class OrganizationRepositoryAncestorChainIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.4").withReuse(true);

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.liquibase.url", POSTGRES::getJdbcUrl);
        registry.add("spring.liquibase.user", POSTGRES::getUsername);
        registry.add("spring.liquibase.password", POSTGRES::getPassword);
    }

    @Autowired
    private OrganizationRepository organizationRepository;

    @Test
    @DisplayName("findAncestorChainByDeptCode 返回 3 层祖先链，按 depth DESC（根先、叶最后）")
    void findAncestorChainByDeptCode_threeLevels_returnsOrderedChain() {
        OrganizationNode root = saveNode(null, "ROOT-DEPT-3L", "集团总部");
        OrganizationNode mid = saveNode(root, "MID-DEPT-3L", "信息技术部");
        OrganizationNode leaf = saveNode(mid, "LEAF-DEPT-3L", "数据平台组");

        List<Object[]> chain = organizationRepository.findAncestorChainByDeptCode("leaf-dept-3l");

        assertThat(chain).as("应返回 root → mid → leaf 三行").hasSize(3);

        // 每行: [id, dept_code, parent_id, name]
        assertRow(chain.get(0), root.getId(), "ROOT-DEPT-3L", null, "集团总部");
        assertRow(chain.get(1), mid.getId(), "MID-DEPT-3L", root.getId(), "信息技术部");
        assertRow(chain.get(2), leaf.getId(), "LEAF-DEPT-3L", mid.getId(), "数据平台组");
    }

    @Test
    @DisplayName("findAncestorChainByDeptCode 对不存在的 deptCode 返回空列表")
    void findAncestorChainByDeptCode_unknownDept_returnsEmpty() {
        List<Object[]> chain = organizationRepository.findAncestorChainByDeptCode("no-such-dept-" + System.nanoTime());
        assertThat(chain).isEmpty();
    }

    @Test
    @DisplayName("findAncestorChainByDeptCode 对单节点（无父）返回 1 行")
    void findAncestorChainByDeptCode_singleNode_returnsOne() {
        String deptCode = "SOLO-DEPT-" + System.nanoTime();
        OrganizationNode only = saveNode(null, deptCode, "独立节点");
        List<Object[]> chain = organizationRepository.findAncestorChainByDeptCode(deptCode);
        assertThat(chain).hasSize(1);
        assertRow(chain.get(0), only.getId(), deptCode, null, "独立节点");
    }

    private OrganizationNode saveNode(OrganizationNode parent, String deptCode, String name) {
        OrganizationNode node = new OrganizationNode();
        node.setName(name);
        node.setDataLevel("DATA_INTERNAL");
        node.setDeptCode(deptCode);
        node.setParent(parent);
        node.setRoot(parent == null);
        // @DataJpaTest 不启用 JPA Auditing（因为没有 SecurityContext），手动填充审计字段。
        node.setCreatedBy("test");
        node.setCreatedDate(Instant.now());
        node.setLastModifiedBy("test");
        node.setLastModifiedDate(Instant.now());
        return organizationRepository.saveAndFlush(node);
    }

    private static void assertRow(Object[] row, Long expectedId, String expectedDeptCode, Long expectedParentId, String expectedName) {
        assertThat(row).hasSizeGreaterThanOrEqualTo(4);
        assertThat(toLong(row[0])).isEqualTo(expectedId);
        assertThat(Objects.toString(row[1], null)).isEqualTo(expectedDeptCode);
        assertThat(toLong(row[2])).isEqualTo(expectedParentId);
        assertThat(Objects.toString(row[3], null)).isEqualTo(expectedName);
    }

    private static Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Long l) {
            return l;
        }
        if (value instanceof Number n) {
            return n.longValue();
        }
        return Long.parseLong(value.toString());
    }
}
