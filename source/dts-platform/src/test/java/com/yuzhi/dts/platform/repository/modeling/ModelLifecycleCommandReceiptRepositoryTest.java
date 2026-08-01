package com.yuzhi.dts.platform.repository.modeling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.GeneratedInput;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.InputMode;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.SaveImplementationCommand;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ImplementationMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class ModelLifecycleCommandReceiptRepositoryTest {

    @Test
    void hashesEquivalentPayloadsCanonicallyAndDistinguishesChangedPayloads() {
        ModelLifecycleCommandReceiptRepository repository = new ModelLifecycleCommandReceiptRepository(
            mock(JdbcTemplate.class),
            new ObjectMapper()
        );
        LinkedHashMap<String, Object> reordered = new LinkedHashMap<>();
        reordered.put("revision", 7);
        reordered.put("action", "SAVE");

        String first = repository.payloadHash(Map.of("action", "SAVE", "revision", 7));
        String same = repository.payloadHash(reordered);
        String changed = repository.payloadHash(Map.of("action", "SAVE", "revision", 8));

        assertThat(first).matches("^[0-9a-f]{64}$").isEqualTo(same).isNotEqualTo(changed);
    }

    @Test
    void commandPayloadHashCannotDriftAfterNestedCallerCollectionsAreMutated() {
        ModelLifecycleCommandReceiptRepository repository = new ModelLifecycleCommandReceiptRepository(
            mock(JdbcTemplate.class),
            new ObjectMapper()
        );
        List<Object> nestedList = new ArrayList<>(List.of("original"));
        Set<Object> nestedSet = new LinkedHashSet<>(Set.of("alpha"));
        String[] nestedArray = { "first" };
        Map<String, Object> nestedMap = new LinkedHashMap<>();
        nestedMap.put("list", nestedList);
        nestedMap.put("set", nestedSet);
        nestedMap.put("array", nestedArray);
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("nested", nestedMap);
        Map<String, Object> casts = new LinkedHashMap<>();
        casts.put("calendar_date", "date");
        List<Object> partitions = new ArrayList<>(List.of("calendar_date"));
        Map<String, Object> settings = new LinkedHashMap<>();
        settings.put("casts", casts);
        settings.put("partitionFields", partitions);
        SaveImplementationCommand command = new SaveImplementationCommand(
            InputMode.GENERATED,
            List.of(new GeneratedInput("DATE_DIMENSION", config)),
            List.of(),
            settings,
            ImplementationMode.DESIGNER_GENERATED,
            "table",
            "freeze-1"
        );

        String before = repository.payloadHash(command);
        nestedList.add("mutated");
        nestedSet.add("beta");
        nestedArray[0] = "mutated";
        nestedMap.put("late", true);
        config.put("late", true);
        casts.put("late_field", "string");
        partitions.add("late_partition");
        settings.put("retentionDays", 30);

        assertThat(repository.payloadHash(command)).isEqualTo(before);
        assertThat(command.settings().get("casts")).isEqualTo(Map.of("calendar_date", "date"));
        assertThat(((GeneratedInput) command.inputs().getFirst()).config()).doesNotContainKey("late");
        assertThatThrownBy(() -> command.settings().put("retentionDays", 30))
            .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> ((Map<String, Object>) command.settings().get("casts")).put("late", "string"))
            .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> ((List<Object>) ((Map<?, ?>) ((GeneratedInput) command.inputs().getFirst()).config().get("nested")).get("list")).add("late"))
            .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> ((Set<Object>) ((Map<?, ?>) ((GeneratedInput) command.inputs().getFirst()).config().get("nested")).get("set")).add("late"))
            .isInstanceOf(UnsupportedOperationException.class);
    }
}
