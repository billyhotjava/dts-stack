package com.yuzhi.dts.platform.service.modeling.imports.converter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.yuzhi.dts.platform.service.modeling.imports.classifier.ModelConversionClassifier;
import com.yuzhi.dts.platform.service.modeling.imports.contract.ModelPackageContract.Defaults;
import com.yuzhi.dts.platform.service.modeling.imports.validator.ModelPackageValidator;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** Thin repo-native entry point that writes exactly one {@code dts-model-package.json}. */
public final class DtsModelPackageGenerator {

    private final ObjectMapper objectMapper;

    public DtsModelPackageGenerator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper
            .copy()
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
    }

    public static void main(String[] args) {
        int result = new DtsModelPackageGenerator(new ObjectMapper()).run(args, System.out, System.err);
        if (result != 0) {
            System.exit(result);
        }
    }

    public int run(String[] args, PrintStream out, PrintStream err) {
        try {
            Options options = Options.parse(args);
            JsonNode manifest = objectMapper.readTree(options.manifest().toFile());
            JsonNode catalog = options.catalog() == null ? null : objectMapper.readTree(options.catalog().toFile());
            var overrides = new DtsSemanticOverrideReader(objectMapper).read(options.overrides());
            var converter = new DbtModelPackageConverter(objectMapper, new ModelConversionClassifier());
            var modelPackage = converter.convert(
                new DbtModelPackageConverter.ConversionRequest(
                    options.packageId(),
                    manifest,
                    catalog,
                    options.projectRoot(),
                    overrides,
                    options.selectedUniqueIds(),
                    new Defaults(null, null)
                )
            );
            byte[] json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(modelPackage);
            new ModelPackageValidator(objectMapper).parseAndValidate(json);
            if (!options.validateOnly()) {
                Path parent = options.output().toAbsolutePath().normalize().getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                Files.write(options.output(), json);
                out.println(options.output().toAbsolutePath().normalize());
            }
            out.println(modelPackage.packageChecksum());
            return 0;
        } catch (Exception exception) {
            err.println(exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage());
            return 2;
        }
    }

    private record Options(
        Path manifest,
        Path catalog,
        Path overrides,
        Path projectRoot,
        Path output,
        String packageId,
        Set<String> selectedUniqueIds,
        boolean validateOnly
    ) {
        private static Options parse(String[] args) {
            Map<String, String> values = new LinkedHashMap<>();
            boolean validateOnly = false;
            for (int index = 0; index < args.length; index++) {
                String argument = args[index];
                if ("--validate-only".equals(argument)) {
                    validateOnly = true;
                    continue;
                }
                if (!argument.startsWith("--") || index + 1 >= args.length) {
                    throw new IllegalArgumentException("参数无效：" + argument);
                }
                values.put(argument, args[++index]);
            }
            String manifest = required(values, "--manifest");
            String packageId = required(values, "--package-id");
            Set<String> selected = new TreeSet<>();
            String select = values.get("--select");
            if (select != null) {
                for (String uniqueId : select.split(",")) {
                    if (!uniqueId.isBlank()) {
                        selected.add(uniqueId.trim());
                    }
                }
            }
            return new Options(
                Path.of(manifest),
                optionalPath(values.get("--catalog")),
                optionalPath(values.get("--overrides")),
                optionalPath(values.get("--project-root")),
                Path.of(values.getOrDefault("--output", "dts-model-package.json")),
                packageId,
                Set.copyOf(selected),
                validateOnly
            );
        }

        private static String required(Map<String, String> values, String name) {
            String value = values.get(name);
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("缺少参数 " + name);
            }
            return value;
        }

        private static Path optionalPath(String value) {
            return value == null || value.isBlank() ? null : Path.of(value);
        }
    }
}
