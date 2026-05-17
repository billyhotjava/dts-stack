package com.yuzhi.dts.platform.web.rest.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.domain.modeling.ModelingGlossaryTerm;
import com.yuzhi.dts.platform.repository.modeling.ModelingGlossaryTermRepository;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class GlossaryInternalResourceTest {

    @Test
    void resolvesGlossaryTermsAndReportsMissingOrInactiveRefs() {
        ModelingGlossaryTermRepository repository = mock(ModelingGlossaryTermRepository.class);
        ModelingGlossaryTerm active = term("glossary.contract_amount", "合同金额", "ACTIVE");
        ModelingGlossaryTerm draft = term("glossary.draft_rate", "草稿指标", "DRAFT");
        when(repository.findByCodeLowerIn(anyCollection())).thenReturn(List.of(active, draft));

        GlossaryInternalResource resource = new GlossaryInternalResource(repository);
        GlossaryInternalResource.ResolveResponse response = resource
            .resolve(new GlossaryInternalResource.ResolveRequest(List.of(
                "glossary.contract_amount",
                "glossary.draft_rate",
                "glossary.missing"
            )))
            .getBody();

        assertThat(response).isNotNull();
        assertThat(response.terms()).extracting(GlossaryInternalResource.TermContract::ref)
            .containsExactly("glossary.contract_amount", "glossary.draft_rate");
        assertThat(response.terms()).extracting(GlossaryInternalResource.TermContract::active)
            .containsExactly(true, false);
        assertThat(response.missing()).containsExactly("glossary.missing");
        assertThat(response.inactive()).containsExactly("glossary.draft_rate");

        ArgumentCaptor<Collection<String>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(repository).findByCodeLowerIn(captor.capture());
        assertThat(captor.getValue())
            .contains("glossary.contract_amount", "contract_amount", "glossary.draft_rate", "draft_rate", "glossary.missing", "missing");
    }

    private static ModelingGlossaryTerm term(String code, String name, String status) {
        ModelingGlossaryTerm term = new ModelingGlossaryTerm();
        term.setId(UUID.randomUUID());
        term.setCode(code);
        term.setName(name);
        term.setStatus(status);
        return term;
    }
}
