package com.yuzhi.dts.platform.service.etl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.config.DbtProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DbtFileServiceOwnershipTest {

    @Mock
    private DbtConfigService configService;

    @TempDir
    Path projectDir;

    @Test
    void saveFileOwnsOnlyWorkspaceContent() throws Exception {
        Path modelFile = projectDir.resolve("models/customer.sql");
        Files.createDirectories(modelFile.getParent());
        Files.writeString(modelFile, "select 1");

        DbtProperties properties = new DbtProperties();
        properties.setProjectDir(projectDir.toString());
        when(configService.loadConfig()).thenReturn(null);

        DbtFileService service = new DbtFileService(properties, configService);
        service.saveFile("models/customer.sql", "select 2 as customer_id");

        assertThat(Files.readString(modelFile)).isEqualTo("select 2 as customer_id");
    }
}
