package com.yuzhi.dts.common.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;

class CatalogTagAuditActionCatalogContractTest {

    private static final Set<String> EXPECTED_ACTION_CODES = Set.of(
        "CATALOG_TAG_CATEGORY_CREATE",
        "CATALOG_TAG_CATEGORY_UPDATE",
        "CATALOG_TAG_CATEGORY_DELETE",
        "CATALOG_TAG_CREATE",
        "CATALOG_TAG_UPDATE",
        "CATALOG_TAG_DELETE",
        "CATALOG_ASSET_TAG_CREATE",
        "CATALOG_ASSET_TAG_DELETE",
        "CATALOG_ASSET_TAG_BATCH_CREATE",
        "CATALOG_TAG_BUILTIN_INSTALL",
        "CATALOG_TAG_MIGRATION_DRY_RUN",
        "CATALOG_TAG_MIGRATION_EXECUTE",
        "CATALOG_TAG_MIGRATION_ROLLBACK"
    );

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void canonicalCatalogRegistersEveryCatalogTagActionForSuccessAndFailure() throws Exception {
        JsonNode catalog = readCatalog(canonicalCatalog());
        JsonNode tagEntry = findEntry(catalog, "catalog.tags");

        assertThat(tagEntry.path("actions").size()).isEqualTo(EXPECTED_ACTION_CODES.size());
        Set<String> actualCodes = StreamSupport
            .stream(tagEntry.path("actions").spliterator(), false)
            .map(action -> action.path("code").asText())
            .collect(Collectors.toSet());

        assertThat(actualCodes).hasSize(EXPECTED_ACTION_CODES.size());
        assertThat(actualCodes).containsExactlyInAnyOrderElementsOf(EXPECTED_ACTION_CODES);
        for (JsonNode action : tagEntry.path("actions")) {
            assertThat(action.path("display").asText()).isNotBlank();
            assertThat(textValues(action.path("phases"))).contains("SUCCESS", "FAIL");
        }
    }

    @Test
    void canonicalAndDockerFallbackCatalogsRemainByteForByteSynchronized() throws Exception {
        String canonical = Files.readString(canonicalCatalog());
        JsonNode expected = objectMapper.readTree(canonical);

        for (Path fallback : fallbackCatalogs()) {
            String mirrored = Files.readString(fallback);
            assertThat(objectMapper.readTree(mirrored)).as("semantic catalog at %s", fallback).isEqualTo(expected);
            assertThat(mirrored).as("byte-for-byte mirror at %s", fallback).isEqualTo(canonical);
        }
    }

    private JsonNode readCatalog(Path path) throws Exception {
        return objectMapper.readTree(Files.readString(path));
    }

    private JsonNode findEntry(JsonNode catalog, String key) {
        List<JsonNode> matches = StreamSupport
            .stream(catalog.path("sections").spliterator(), false)
            .flatMap(section -> StreamSupport.stream(section.path("entries").spliterator(), false))
            .filter(entry -> key.equals(entry.path("key").asText()))
            .toList();
        assertThat(matches).as("audit catalog entry %s", key).hasSize(1);
        return matches.getFirst();
    }

    private Set<String> textValues(JsonNode array) {
        return StreamSupport
            .stream(array.spliterator(), false)
            .map(JsonNode::asText)
            .collect(Collectors.toSet());
    }

    private Path canonicalCatalog() {
        return moduleRoot().resolve("src/main/resources/config/audit-action-catalog.json");
    }

    private List<Path> fallbackCatalogs() {
        Path sourceRoot = moduleRoot().getParent();
        return List.of(
            sourceRoot.resolve("dts-admin/src/main/docker/dts-common-fallback/src/main/resources/config/audit-action-catalog.json"),
            sourceRoot.resolve("dts-platform/src/main/docker/dts-common-fallback/src/main/resources/config/audit-action-catalog.json")
        );
    }

    private Path moduleRoot() {
        return Path.of(System.getProperty("basedir")).toAbsolutePath().normalize();
    }
}
