package com.yuzhi.dts.platform.service.catalog;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.security.modeling.ModelingIdentityException;
import com.yuzhi.dts.platform.service.modeling.ModelingPermissionAudit;
import jakarta.persistence.EntityManager;
import java.util.*;
import org.junit.jupiter.api.Test;

class CatalogClassificationEditServiceTest {
    private final CatalogClassificationService classification=mock(CatalogClassificationService.class);
    private final ModelingPermissionAudit audit=mock(ModelingPermissionAudit.class);
    private final CatalogClassificationEditService service=new CatalogClassificationEditService(classification,mock(CatalogClassificationWriteLock.class),mock(EntityManager.class),audit);
    private CatalogDataset dataset(){var value=new CatalogDataset();value.setId(UUID.randomUUID());value.setClassification("SECRET");return value;}
    @Test void omissionPreservesClassificationAndExplicitNullIsInvalid(){
        var dataset=dataset();service.apply(dataset,null,false);assertThat(dataset.getClassification()).isEqualTo("SECRET");
        assertThatThrownBy(()->service.apply(dataset,null,true)).isInstanceOfSatisfying(ModelingIdentityException.class,ex->assertThat(ex.status()).isEqualTo(400));
        verifyNoInteractions(classification);
    }
    @Test void lowerLevelProducesConflictAndAuditsBeforeReturning(){
        var dataset=dataset();when(classification.resolve(anyString(),anyString())).thenReturn(Optional.empty());
        assertThatThrownBy(()->service.apply(dataset,"PUBLIC",true)).isInstanceOfSatisfying(ModelingIdentityException.class,ex->{assertThat(ex.status()).isEqualTo(409);assertThat(ex.code()).isEqualTo("CLASSIFICATION_DOWNGRADE_FORBIDDEN");});
        assertThat(dataset.getClassification()).isEqualTo("SECRET");verify(classification,never()).sealOrRaise(any());
        verify(audit).denied(anyString(),eq("CATALOG_CLASSIFICATION_DOWNGRADE_REJECTED"),eq(dataset.getId().toString()),eq("CLASSIFICATION_DOWNGRADE_FORBIDDEN"));
    }
    @Test void equalLevelIsIdempotent(){var dataset=dataset();when(classification.resolve(anyString(),anyString())).thenReturn(Optional.empty());service.apply(dataset,"SECRET",true);assertThat(dataset.getClassification()).isEqualTo("SECRET");verify(classification,never()).sealOrRaise(any());verifyNoInteractions(audit);}
}
