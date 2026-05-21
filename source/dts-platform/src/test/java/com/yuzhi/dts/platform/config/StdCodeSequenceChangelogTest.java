package com.yuzhi.dts.platform.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class StdCodeSequenceChangelogTest {

    private static final Path MASTER = Path.of("src/main/resources/config/liquibase/master.xml");
    private static final Path CHANGELOG = Path.of(
        "src/main/resources/config/liquibase/changelog/20260521_01_std_code_sequence_defaults.xml"
    );

    @Test
    void stdCodeValueAndMappingPrimaryKeysKeepPostgresSequenceDefaults() throws Exception {
        String master = Files.readString(MASTER);

        assertThat(master).contains("20260521_01_std_code_sequence_defaults.xml");

        String changelog = Files.readString(CHANGELOG);
        assertThat(changelog)
            .contains("CREATE SEQUENCE IF NOT EXISTS std_code_value_item_id_seq")
            .contains("ALTER TABLE std_code_value ALTER COLUMN item_id SET DEFAULT nextval('std_code_value_item_id_seq'::regclass)")
            .contains("CREATE SEQUENCE IF NOT EXISTS std_code_mapping_map_id_seq")
            .contains("ALTER TABLE std_code_mapping ALTER COLUMN map_id SET DEFAULT nextval('std_code_mapping_map_id_seq'::regclass)");
    }
}
