package com.yuzhi.dts.platform.service.catalog;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption.DoNotIncludeTests;
import com.tngtech.archunit.lang.ArchRule;
import com.yuzhi.dts.platform.repository.catalog.CatalogDomainRepository;
import org.junit.jupiter.api.Test;

class CatalogDomainCommandBoundaryArchitectureTest {

    @Test
    void catalogDomainControllerDoesNotOwnPersistenceWrites() {
        ArchRule rule = noClasses()
            .that()
            .haveSimpleName("CatalogDomainResource")
            .should()
            .dependOnClassesThat()
            .areAssignableTo(CatalogDomainRepository.class)
            .because("platform architecture dictionaries must be written through one canonical command boundary");

        rule.check(new ClassFileImporter().withImportOption(new DoNotIncludeTests()).importPackages("com.yuzhi.dts.platform"));
    }
}
