package com.yuzhi.dts.ingestion.service.etl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.ingestion.domain.IngestionConnectorCapability;
import com.yuzhi.dts.ingestion.repository.IngestionConnectorCapabilityRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ConnectorCapabilityServiceTest {

    @Test
    void ensureDefaults_seedsApiAuthProvidersWithEnabledContract() {
        IngestionConnectorCapabilityRepository repository = mock(IngestionConnectorCapabilityRepository.class);
        ObjectMapper objectMapper = new ObjectMapper();
        when(repository.findByConnectorTypeIgnoreCase(anyString())).thenReturn(Optional.empty());

        ConnectorCapabilityService service = new ConnectorCapabilityService(repository, objectMapper);

        service.ensureDefaults();

        ArgumentCaptor<IngestionConnectorCapability> captor = ArgumentCaptor.forClass(IngestionConnectorCapability.class);
        verify(repository, times(4)).save(captor.capture());
        IngestionConnectorCapability apiCapability = captor
            .getAllValues()
            .stream()
            .filter(entity -> "api".equals(entity.getConnectorType()))
            .findFirst()
            .orElseThrow();

        Map<String, Object> constraints = objectMapper.convertValue(
            apiCapability.getConstraintsJson(),
            new TypeReference<Map<String, Object>>() {}
        );
        assertThat(constraints).containsEntry("contractVersion", "1.2.0");
        assertThat((List<?>) constraints.get("authProviders"))
            .anySatisfy(provider -> assertAuthProvider(provider, "jwtLogin", true))
            .anySatisfy(provider -> assertAuthProvider(provider, "customSignature", false))
            .anySatisfy(provider -> assertAuthProvider(provider, "mtls", false));
    }

    @SuppressWarnings("unchecked")
    private static void assertAuthProvider(Object provider, String id, boolean enabled) {
        assertThat(provider).isInstanceOf(Map.class);
        assertThat((Map<String, Object>) provider).containsEntry("id", id).containsEntry("enabled", enabled);
    }
}
