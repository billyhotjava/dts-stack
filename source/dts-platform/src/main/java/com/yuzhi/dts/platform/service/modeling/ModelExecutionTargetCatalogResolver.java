package com.yuzhi.dts.platform.service.modeling;

import com.yuzhi.dts.platform.config.ModelMaterializationProperties;
import com.yuzhi.dts.platform.service.etl.DbtConfigService;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CandidateView;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateException.Kind;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Resolves the server-owned execution target to its credential-free Catalog identity.
 */
@Service
public class ModelExecutionTargetCatalogResolver {

    private final ModelMaterializationProperties properties;
    private final DbtConfigService configService;

    public ModelExecutionTargetCatalogResolver(
        ModelMaterializationProperties properties,
        DbtConfigService configService
    ) {
        this.properties = properties;
        this.configService = configService;
    }

    public ResolvedCatalogTarget resolve(CandidateView candidate) {
        if (candidate == null) {
            throw new IllegalArgumentException("candidate is required");
        }
        String configuredKey = required(
            properties.getExecutionTargetKey(),
            "configured execution target key"
        );
        String candidateKey = required(
            candidate.executionTargetKey(),
            "Candidate execution target key"
        );
        String configuredAdapter = required(
            properties.getAdapter(),
            "configured adapter"
        ).toLowerCase(Locale.ROOT);
        String candidateAdapter = required(
            candidate.adapter(),
            "Candidate adapter"
        ).toLowerCase(Locale.ROOT);
        if (
            !Objects.equals(configuredKey, candidateKey) ||
            !Objects.equals(configuredAdapter, candidateAdapter)
        ) {
            throw targetFailure(
                candidate,
                configuredKey,
                configuredAdapter
            );
        }
        DbtConfigService.DbtWorkspaceConfig config;
        try {
            config = configService.loadRuntimeConfig();
        } catch (RuntimeException failure) {
            throw new ModelReleaseCandidateException(
                "MODEL_RELEASE_CATALOG_SOURCE_UNAVAILABLE",
                "The current execution target has no credential-free Catalog source identity",
                Kind.UNPROCESSABLE,
                Map.of(
                    "candidateId",
                    candidate.id(),
                    "executionTargetKey",
                    candidateKey
                )
            );
        }
        if (config == null || config.targetDataSourceId() == null) {
            throw new ModelReleaseCandidateException(
                "MODEL_RELEASE_CATALOG_SOURCE_UNAVAILABLE",
                "The current execution target has no credential-free Catalog source identity",
                Kind.UNPROCESSABLE,
                Map.of(
                    "candidateId",
                    candidate.id(),
                    "executionTargetKey",
                    candidateKey
                )
            );
        }
        return new ResolvedCatalogTarget(
            candidateKey,
            config.targetDataSourceId(),
            candidateAdapter
        );
    }

    private static ModelReleaseCandidateException targetFailure(
        CandidateView candidate,
        String configuredKey,
        String configuredAdapter
    ) {
        return new ModelReleaseCandidateException(
            "MODEL_RELEASE_EXECUTION_TARGET_NOT_CURRENT",
            "Candidate execution target no longer matches the server-owned target",
            Kind.CONFLICT,
            Map.of(
                "candidateId",
                candidate.id(),
                "candidateExecutionTargetKey",
                candidate.executionTargetKey(),
                "configuredExecutionTargetKey",
                configuredKey,
                "candidateAdapter",
                candidate.adapter(),
                "configuredAdapter",
                configuredAdapter
            )
        );
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }

    public record ResolvedCatalogTarget(
        String executionTargetKey,
        UUID sourceId,
        String adapter
    ) {}
}
