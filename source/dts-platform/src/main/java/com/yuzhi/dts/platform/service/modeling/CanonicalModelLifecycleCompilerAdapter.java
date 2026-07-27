package com.yuzhi.dts.platform.service.modeling;

import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.ArtifactWrite;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.ImplementationView;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelSpecView;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Reuses the existing deterministic dbt compiler without creating another ModelSpec. */
@Component
public class CanonicalModelLifecycleCompilerAdapter implements ModelLifecycleCompilerPort {

    private final ModelSpecApplicationService modelSpecs;
    private final ModelSpecSourceValidationPort sourceValidation;

    public CanonicalModelLifecycleCompilerAdapter(
        ModelSpecApplicationService modelSpecs,
        ModelSpecSourceValidationPort sourceValidation
    ) {
        this.modelSpecs = modelSpecs;
        this.sourceValidation = sourceValidation;
    }

    @Override
    public List<ArtifactWrite> compile(String tenantId, ModelSpecView model, ImplementationView implementation) {
        if (implementation.ownership() == ModelSpecContract.ImplementationMode.DBT_MANAGED) {
            throw new ModelSpecException(
                "MODEL_DBT_IMPORT_REQUIRED",
                "DBT-managed implementations must be verified through the dedicated import path",
                ModelSpecException.Kind.CONFLICT
            );
        }
        ModelSpecCompilerProjection.ImplementationProjection projection = ModelSpecCompilerProjection.project(
            model,
            implementation,
            reference -> modelSpecs.revision(tenantId, reference),
            tenantId,
            input -> {
                if (sourceValidation == null) {
                    return model.sourceRefs()
                        .stream()
                        .filter(source -> input.sourceBindingId().equals(source.sourceBindingId()))
                        .filter(source -> input.resolvedVersion().equals(source.resolvedVersion()))
                        .findFirst()
                        .orElse(null);
                }
                return sourceValidation
                    .resolveCurrentBindingForCompiler(tenantId, model.planId(), input.sourceBindingId(), input.resolvedVersion())
                    .orElse(null);
            }
        );
        ModelingDbtCompiler.CompiledArtifacts compiled = ModelingDbtCompiler.compile(projection);
        List<ArtifactWrite> result = new ArrayList<>();
        for (Map.Entry<String, String> file : compiled.files().entrySet()) {
            String path = compiled.outputDirectory() + "/" + file.getKey();
            String contentChecksum = checksum(file.getValue());
            for (String artifactType : artifactTypes(file.getKey())) {
                result.add(
                    new ArtifactWrite(
                        artifactType,
                        path,
                        contentChecksum,
                        file.getValue(),
                        nodeKind(file.getKey()),
                        materialization(file.getKey(), implementation),
                        null
                    )
                );
            }
        }
        return List.copyOf(result);
    }

    private static List<String> artifactTypes(String path) {
        if (path.startsWith("stg_") && path.endsWith(".sql")) return List.of("STG_SQL");
        if (path.endsWith(".yml") || path.endsWith(".yaml")) return List.of("SCHEMA", "TEST");
        if (path.endsWith(".sql")) return List.of("SQL");
        return List.of("DOC");
    }

    private static String nodeKind(String path) {
        return path.startsWith("stg_") && path.endsWith(".sql") ? "STG" : "MODEL";
    }

    private static String materialization(String path, ImplementationView implementation) {
        return path.startsWith("stg_") && path.endsWith(".sql") ? "ephemeral" : implementation.materialization();
    }

    private static String checksum(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte item : digest) result.append(String.format("%02x", item));
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
