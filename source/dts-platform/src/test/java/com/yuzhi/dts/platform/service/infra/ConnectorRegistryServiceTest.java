package com.yuzhi.dts.platform.service.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.domain.infra.InfraConnector;
import com.yuzhi.dts.platform.repository.infra.InfraConnectorRepository;
import com.yuzhi.dts.platform.service.infra.dto.ConnectorDriverBindingDto;
import com.yuzhi.dts.platform.service.infra.dto.InfraConnectorDto;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConnectorRegistryServiceTest {

    @Mock
    private InfraConnectorRepository connectorRepository;

    @Mock
    private ConnectorDriverBindingService driverBindingService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private ConnectorRegistryService service;

    @BeforeEach
    void setUp() {
        service = new ConnectorRegistryService(connectorRepository, objectMapper, driverBindingService);
    }

    @Test
    void listIncludesDriverReadinessFromInstalledInventory() {
        InfraConnector connector = connector("mysql", "MySQL");
        ConnectorDriverBindingDto driver = new ConnectorDriverBindingDto(
            "BUNDLED",
            "READY",
            "com.mysql.cj.jdbc.Driver",
            "mysql-connector-j-9.7.0.jar",
            "9.7.0",
            "17",
            "连接器驱动已就绪"
        );
        when(connectorRepository.findByStatusIgnoreCaseOrderByDisplayOrderAscConnectorKeyAsc("ACTIVE")).thenReturn(
            List.of(connector)
        );
        when(driverBindingService.installedDrivers()).thenReturn(List.of());
        when(driverBindingService.resolve(connector, List.of())).thenReturn(driver);

        List<InfraConnectorDto> result = service.list(null, false);

        assertThat(result).singleElement().extracting(InfraConnectorDto::driver).isEqualTo(driver);
        verify(driverBindingService).installedDrivers();
    }

    @Test
    void seedBindsInceptorToPackagedQuarkDriver() throws Exception {
        when(connectorRepository.findByConnectorKeyIgnoreCase(any())).thenReturn(Optional.empty());
        when(connectorRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.seedBuiltInConnectors();

        ArgumentCaptor<InfraConnector> captor = ArgumentCaptor.forClass(InfraConnector.class);
        verify(connectorRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        InfraConnector inceptor = captor
            .getAllValues()
            .stream()
            .filter(item -> "inceptor".equals(item.getConnectorKey()))
            .findFirst()
            .orElseThrow();
        Map<String, Object> compatibility = objectMapper.readValue(
            inceptor.getCompatibilityPayload(),
            new TypeReference<Map<String, Object>>() {}
        );
        assertThat(compatibility)
            .containsEntry("driverPolicy", "BUNDLED")
            .containsEntry("driverClass", "io.transwarp.jdbc.QuarkDriver");
    }

    private InfraConnector connector(String key, String name) {
        InfraConnector connector = new InfraConnector();
        connector.setConnectorKey(key);
        connector.setName(name);
        connector.setCategory("DATABASE");
        connector.setSourceType(key);
        connector.setDefaultEngine("ADDAX");
        connector.setStatus("ACTIVE");
        connector.setDisplayOrder(10);
        return connector;
    }
}
