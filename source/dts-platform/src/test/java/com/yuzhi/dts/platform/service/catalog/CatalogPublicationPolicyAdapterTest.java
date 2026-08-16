package com.yuzhi.dts.platform.service.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.security.policy.AssetAction;
import com.yuzhi.dts.platform.service.security.AccessChecker;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CatalogPublicationPolicyAdapterTest {

    private static final UUID ASSET_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID SOURCE_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");

    @Mock
    private CatalogDatasetRepository catalogs;

    @Mock
    private AccessChecker accessChecker;

    private CatalogPublicationPolicyAdapter policy;

    @BeforeEach
    void setUp() {
        policy = new CatalogPublicationPolicyAdapter(catalogs, accessChecker);
    }

    @Test
    void authorizesArchiveForAnActivePublishedAsset() {
        CatalogDataset dataset = dataset(ASSET_ID);
        dataset.setEnabled(true);
        dataset.setLifecycleStatus("ACTIVE");
        when(catalogs.findById(ASSET_ID)).thenReturn(Optional.of(dataset));
        when(accessChecker.canPerform(dataset, AssetAction.ARCHIVE)).thenReturn(true);

        CatalogPublicationPolicyPort.Decision decision = policy.authorizeArchive(ASSET_ID);

        assertThat(decision.status()).isEqualTo(CatalogPublicationPolicyPort.Status.ALLOWED);
        verify(accessChecker).canPerform(dataset, AssetAction.ARCHIVE);
    }

    @Test
    void presentsAProspectivePublicationAsAnActiveCatalogAsset() {
        when(catalogs.findByHiveDatabaseIgnoreCaseAndHiveTableIgnoreCase("finance", "fct_payment"))
            .thenReturn(List.of());
        when(accessChecker.canPerform(any(CatalogDataset.class), org.mockito.ArgumentMatchers.eq(AssetAction.CREATE)))
            .thenReturn(true);

        CatalogPublicationPolicyPort.Decision decision = policy.authorizeUpsert(SOURCE_ID, "finance", "fct_payment");

        assertThat(decision.status()).isEqualTo(CatalogPublicationPolicyPort.Status.ALLOWED);
        ArgumentCaptor<CatalogDataset> subject = ArgumentCaptor.forClass(CatalogDataset.class);
        verify(accessChecker).canPerform(subject.capture(), org.mockito.ArgumentMatchers.eq(AssetAction.CREATE));
        assertThat(subject.getValue().getLifecycleStatus()).isEqualTo("ACTIVE");
    }

    private static CatalogDataset dataset(UUID id) {
        CatalogDataset dataset = new CatalogDataset();
        dataset.setId(id);
        dataset.setName("fct_payment");
        dataset.setSourceId(SOURCE_ID);
        dataset.setHiveDatabase("finance");
        dataset.setHiveTable("fct_payment");
        return dataset;
    }
}
