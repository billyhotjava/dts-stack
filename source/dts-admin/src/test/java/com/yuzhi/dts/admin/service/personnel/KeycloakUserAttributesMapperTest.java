package com.yuzhi.dts.admin.service.personnel;

import static org.assertj.core.api.Assertions.assertThat;

import com.yuzhi.dts.admin.service.dto.personnel.PersonnelPayload;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class KeycloakUserAttributesMapperTest {

    @Test
    void mapsCoreFieldsToSnakeCaseAttributes() {
        PersonnelPayload payload = new PersonnelPayload(
            "P001", "EXT-001", "alice", "Alice Zhang", "110101199001011234",
            "FIN-001", "财务部", "/集团/财务部", "分析师", "G5",
            "alice@example.com", "13800000000", "ACTIVE", null, null,
            Map.of("securityLevel", "IMPORTANT")
        );

        Map<String, List<String>> attrs = KeycloakUserAttributesMapper.toAttributes(payload);

        assertThat(attrs).containsEntry("person_code", List.of("P001"));
        assertThat(attrs).containsEntry("national_id", List.of("110101199001011234"));
        assertThat(attrs).containsEntry("full_name", List.of("Alice Zhang"));
        assertThat(attrs).containsEntry("dept_code", List.of("FIN-001"));
        assertThat(attrs).containsEntry("dept_name", List.of("财务部"));
        assertThat(attrs).containsEntry("title", List.of("分析师"));
        assertThat(attrs).containsEntry("grade", List.of("G5"));
        assertThat(attrs).containsEntry("person_security_level", List.of("IMPORTANT"));
    }

    @Test
    void normalizesNumericSecurityLevelToSemanticCode() {
        PersonnelPayload payload = samplePayload(Map.of("securityLevel", "1"));
        Map<String, List<String>> attrs = KeycloakUserAttributesMapper.toAttributes(payload);
        assertThat(attrs).containsEntry("person_security_level", List.of("IMPORTANT"));
    }

    @Test
    void defaultsToGeneralWhenSecurityLevelMissing() {
        PersonnelPayload payload = samplePayload(Map.of());
        Map<String, List<String>> attrs = KeycloakUserAttributesMapper.toAttributes(payload);
        assertThat(attrs).containsEntry("person_security_level", List.of("GENERAL"));
    }

    @Test
    void omitsBlankFields() {
        PersonnelPayload payload = new PersonnelPayload(
            "P002", null, "bob", "Bob", null,
            null, null, null, null, null, null, null, null, null, null, Map.of()
        );
        Map<String, List<String>> attrs = KeycloakUserAttributesMapper.toAttributes(payload);
        assertThat(attrs).doesNotContainKeys("national_id", "dept_code", "dept_name", "title", "grade", "email", "phone");
        assertThat(attrs).containsEntry("person_code", List.of("P002"));
        assertThat(attrs).containsEntry("full_name", List.of("Bob"));
    }

    @Test
    void passesThroughExtraAttributesWithoutOverridingMappedKeys() {
        PersonnelPayload payload = new PersonnelPayload(
            "P003", null, "carol", "Carol", null,
            "DEPT-X", null, null, null, null, null, null, null, null, null,
            Map.of("custom_tag", "foo", "dept_code", "SHOULD-NOT-OVERRIDE")
        );
        Map<String, List<String>> attrs = KeycloakUserAttributesMapper.toAttributes(payload);
        assertThat(attrs).containsEntry("dept_code", List.of("DEPT-X"));
        assertThat(attrs).containsEntry("custom_tag", List.of("foo"));
    }

    private PersonnelPayload samplePayload(Map<String, Object> attributes) {
        return new PersonnelPayload(
            "P001", null, "alice", "Alice", null,
            "FIN-001", null, null, null, null, null, null, null, null, null, attributes
        );
    }
}
