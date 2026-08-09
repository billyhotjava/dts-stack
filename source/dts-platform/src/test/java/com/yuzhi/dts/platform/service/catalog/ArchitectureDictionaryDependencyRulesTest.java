package com.yuzhi.dts.platform.service.catalog;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption.DoNotIncludeTests;
import com.tngtech.archunit.lang.ArchRule;
import com.yuzhi.dts.platform.repository.catalog.CatalogDomainRepository;
import com.yuzhi.dts.platform.repository.modeling.DataMartRepository;
import com.yuzhi.dts.platform.repository.modeling.SubjectDomainRepository;
import com.yuzhi.dts.platform.repository.modeling.WarehouseLayerRepository;
import org.junit.jupiter.api.Test;

class ArchitectureDictionaryDependencyRulesTest {

    @Test
    void controllersCannotBypassArchitectureDictionaryApplicationBoundaries() {
        ArchRule rule = noClasses()
            .that()
            .resideInAPackage("..web.rest..")
            .should()
            .dependOnClassesThat()
            .areAssignableTo(CatalogDomainRepository.class)
            .orShould()
            .dependOnClassesThat()
            .areAssignableTo(DataMartRepository.class)
            .orShould()
            .dependOnClassesThat()
            .areAssignableTo(SubjectDomainRepository.class)
            .orShould()
            .dependOnClassesThat()
            .areAssignableTo(WarehouseLayerRepository.class)
            .because("REST and compatibility adapters must delegate to canonical application boundaries");

        rule.check(new ClassFileImporter().withImportOption(new DoNotIncludeTests()).importPackages("com.yuzhi.dts.platform"));
    }

    @Test
    void governanceConsumersCannotReadCatalogDomainJpaRepositoryDirectly() {
        ArchRule rule = noClasses()
            .that()
            .resideInAPackage("..service.governance..")
            .should()
            .dependOnClassesThat()
            .areAssignableTo(CatalogDomainRepository.class)
            .because("indicators and governance consume the immutable catalog domain read port");

        rule.check(new ClassFileImporter().withImportOption(new DoNotIncludeTests()).importPackages("com.yuzhi.dts.platform"));
    }
}
