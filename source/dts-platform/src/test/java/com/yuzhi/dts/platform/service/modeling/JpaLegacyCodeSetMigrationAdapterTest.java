package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.domain.modeling.DataStandard;
import com.yuzhi.dts.platform.repository.modeling.DataStandardRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JpaLegacyCodeSetMigrationAdapterTest {

    @Mock
    private DataStandardRepository standards;

    @Test
    void exposesOnlyInlineCodeSetCandidatesAndUpdatesTheCurrentSnapshot() {
        UUID id = UUID.randomUUID();
        DataStandard candidate = standard(id, "PAYMENT_STATUS", "01:已支付,02:未支付");
        DataStandard migrated = standard(UUID.randomUUID(), "CURRENCY", "CURRENCY");
        when(standards.findAll()).thenReturn(List.of(candidate, migrated));
        when(standards.replaceCodeSetIfCurrent(id, "01:已支付,02:未支付", "PAYMENT_STATUS")).thenReturn(1);
        JpaLegacyCodeSetMigrationAdapter adapter = new JpaLegacyCodeSetMigrationAdapter(standards);

        assertThat(adapter.findCandidates()).singleElement().satisfies(view -> {
            assertThat(view.id()).isEqualTo(id);
            assertThat(view.inlineCodeSet()).isEqualTo("01:已支付,02:未支付");
        });
        assertThat(adapter.replaceInlineCodeSet(id, "01:已支付,02:未支付", "PAYMENT_STATUS")).isTrue();

        verify(standards).replaceCodeSetIfCurrent(id, "01:已支付,02:未支付", "PAYMENT_STATUS");
    }

    @Test
    void refusesToOverwriteAChangedInlineCodeSet() {
        UUID id = UUID.randomUUID();
        when(standards.replaceCodeSetIfCurrent(id, "01:已支付", "PAYMENT_STATUS")).thenReturn(0);
        JpaLegacyCodeSetMigrationAdapter adapter = new JpaLegacyCodeSetMigrationAdapter(standards);

        assertThat(adapter.replaceInlineCodeSet(id, "01:已支付", "PAYMENT_STATUS")).isFalse();

        verify(standards).replaceCodeSetIfCurrent(id, "01:已支付", "PAYMENT_STATUS");
    }

    private static DataStandard standard(UUID id, String code, String codeSet) {
        DataStandard standard = new DataStandard();
        standard.setId(id);
        standard.setCode(code);
        standard.setName(code);
        standard.setCodeSet(codeSet);
        return standard;
    }
}
