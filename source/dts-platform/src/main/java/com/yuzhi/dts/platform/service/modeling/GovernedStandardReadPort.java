package com.yuzhi.dts.platform.service.modeling;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Owner-side read contract for governed standards referenced by other DTS domains.
 *
 * <p>The projections intentionally exclude JPA entities so catalog and governance
 * consumers cannot take ownership of modeling persistence.</p>
 */
public interface GovernedStandardReadPort {

    Optional<StandardAsset> findDataStandardById(UUID id);

    Optional<StandardAsset> findDataStandardByCode(String code);

    Map<String, UUID> findDataStandardIdsByLowerCode(Collection<String> lowerCodes);

    Optional<GlossaryAsset> findGlossaryTermById(UUID id);

    Optional<GlossaryAsset> findFirstGlossaryTermByLowerCodes(Collection<String> lowerCodes);

    Optional<Integer> findDataElementVersion(UUID id);

    record StandardAsset(UUID id, String code) {}

    record GlossaryAsset(UUID id, String code) {}
}
