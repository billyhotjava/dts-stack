package com.yuzhi.dts.platform.service.modeling;

import com.yuzhi.dts.platform.domain.modeling.DataStandard;
import com.yuzhi.dts.platform.domain.modeling.ModelingGlossaryTerm;
import com.yuzhi.dts.platform.repository.modeling.DataStandardRepository;
import com.yuzhi.dts.platform.repository.modeling.MetadataStandardRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelingGlossaryTermRepository;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Transactional(readOnly = true)
public class JpaGovernedStandardReadAdapter implements GovernedStandardReadPort {

    private final DataStandardRepository dataStandards;
    private final MetadataStandardRepository dataElements;
    private final ModelingGlossaryTermRepository glossaryTerms;

    public JpaGovernedStandardReadAdapter(
        DataStandardRepository dataStandards,
        MetadataStandardRepository dataElements,
        ModelingGlossaryTermRepository glossaryTerms
    ) {
        this.dataStandards = dataStandards;
        this.dataElements = dataElements;
        this.glossaryTerms = glossaryTerms;
    }

    @Override
    public Optional<StandardAsset> findDataStandardById(UUID id) {
        return id == null ? Optional.empty() : dataStandards.findById(id).map(this::standardAsset);
    }

    @Override
    public Optional<StandardAsset> findDataStandardByCode(String code) {
        return !StringUtils.hasText(code)
            ? Optional.empty()
            : dataStandards.findByCodeIgnoreCase(code.trim()).map(this::standardAsset);
    }

    @Override
    public Map<String, UUID> findDataStandardIdsByLowerCode(Collection<String> lowerCodes) {
        if (lowerCodes == null || lowerCodes.isEmpty()) {
            return Map.of();
        }
        Map<String, UUID> result = new LinkedHashMap<>();
        for (DataStandard standard : dataStandards.findByCodeLowerIn(lowerCodes)) {
            if (standard == null || standard.getId() == null || !StringUtils.hasText(standard.getCode())) {
                continue;
            }
            result.put(standard.getCode().trim().toLowerCase(Locale.ROOT), standard.getId());
        }
        return Map.copyOf(result);
    }

    @Override
    public Optional<GlossaryAsset> findGlossaryTermById(UUID id) {
        return id == null ? Optional.empty() : glossaryTerms.findById(id).map(this::glossaryAsset);
    }

    @Override
    public Optional<GlossaryAsset> findFirstGlossaryTermByLowerCodes(Collection<String> lowerCodes) {
        if (lowerCodes == null || lowerCodes.isEmpty()) {
            return Optional.empty();
        }
        List<ModelingGlossaryTerm> terms = glossaryTerms.findByCodeLowerIn(lowerCodes);
        return terms == null ? Optional.empty() : terms.stream().findFirst().map(this::glossaryAsset);
    }

    @Override
    public Optional<Integer> findDataElementVersion(UUID id) {
        return id == null ? Optional.empty() : dataElements.findById(id).map(element -> element.getVersion());
    }

    private StandardAsset standardAsset(DataStandard standard) {
        return new StandardAsset(standard.getId(), standard.getCode());
    }

    private GlossaryAsset glossaryAsset(ModelingGlossaryTerm term) {
        return new GlossaryAsset(term.getId(), term.getCode());
    }
}
