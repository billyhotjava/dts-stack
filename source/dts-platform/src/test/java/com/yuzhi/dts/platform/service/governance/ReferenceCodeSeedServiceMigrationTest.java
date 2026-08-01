package com.yuzhi.dts.platform.service.governance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.config.DbtProperties;
import com.yuzhi.dts.platform.repository.governance.StdCodeDirectoryRepository;
import com.yuzhi.dts.platform.repository.governance.StdCodeValueRepository;
import com.yuzhi.dts.platform.service.etl.DbtConfigService;
import com.yuzhi.dts.platform.service.modeling.LegacyCodeSetMigrationPort;
import com.yuzhi.dts.platform.service.modeling.LegacyCodeSetMigrationPort.LegacyCodeSetCandidate;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InOrder;

class ReferenceCodeSeedServiceMigrationTest {

    private final StdCodeDirectoryRepository directories = mock(StdCodeDirectoryRepository.class);
    private final StdCodeValueRepository values = mock(StdCodeValueRepository.class);
    private final LegacyCodeSetMigrationPort legacyCodeSets = mock(LegacyCodeSetMigrationPort.class);
    private final DbtConfigService dbtConfig = mock(DbtConfigService.class);
    private final ReferenceCodeSecurity security = mock(ReferenceCodeSecurity.class);

    @TempDir
    Path projectDir;

    private ReferenceCodeSeedService service;

    @BeforeEach
    void setUp() {
        DbtProperties properties = new DbtProperties();
        properties.setProjectDir(projectDir.toString());
        when(dbtConfig.loadConfig()).thenReturn(DbtConfigService.DbtConfigView.disabled("test"));
        when(directories.findAll()).thenReturn(List.of());
        service = new ReferenceCodeSeedService(directories, values, legacyCodeSets, dbtConfig, properties, security);
    }

    @Test
    void staleCandidateCannotCreateOrphanGovernanceRecords() {
        LegacyCodeSetCandidate candidate = candidate();
        when(legacyCodeSets.findCandidates()).thenReturn(List.of(candidate));
        when(
            legacyCodeSets.replaceInlineCodeSet(candidate.id(), candidate.inlineCodeSet(), candidate.code())
        ).thenReturn(false);

        Map<String, Object> result = service.syncSeeds(null);

        assertThat(result).containsEntry("legacyDirectories", 0).containsEntry("legacyItems", 0).containsEntry("legacyStandardsUpdated", 0);
        verify(directories, never()).findByCodeTypeCodeIgnoreCase(candidate.code());
        verify(directories, never()).save(org.mockito.ArgumentMatchers.any());
        verify(values, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void conditionalOwnershipUpdateHappensBeforeGovernanceObjectsAreCreated() {
        LegacyCodeSetCandidate candidate = candidate();
        when(legacyCodeSets.findCandidates()).thenReturn(List.of(candidate));
        when(
            legacyCodeSets.replaceInlineCodeSet(candidate.id(), candidate.inlineCodeSet(), candidate.code())
        ).thenReturn(true);
        when(directories.findByCodeTypeCodeIgnoreCase(candidate.code())).thenReturn(Optional.empty());
        when(values.existsByCodeTypeIdAndCodeValue(candidate.code(), "01")).thenReturn(false);

        Map<String, Object> result = service.syncSeeds(null);

        assertThat(result).containsEntry("legacyDirectories", 1).containsEntry("legacyItems", 1).containsEntry("legacyStandardsUpdated", 1);
        InOrder order = inOrder(legacyCodeSets, directories, values);
        order.verify(legacyCodeSets).replaceInlineCodeSet(candidate.id(), candidate.inlineCodeSet(), candidate.code());
        order.verify(directories).findByCodeTypeCodeIgnoreCase(candidate.code());
        order.verify(directories).save(org.mockito.ArgumentMatchers.any());
        order.verify(values).existsByCodeTypeIdAndCodeValue(candidate.code(), "01");
        order.verify(values).save(org.mockito.ArgumentMatchers.any());
    }

    private LegacyCodeSetCandidate candidate() {
        return new LegacyCodeSetCandidate(
            UUID.randomUUID(),
            "PAYMENT_STATUS",
            "支付状态",
            "finance",
            "string",
            "v1",
            "01:已支付"
        );
    }
}
