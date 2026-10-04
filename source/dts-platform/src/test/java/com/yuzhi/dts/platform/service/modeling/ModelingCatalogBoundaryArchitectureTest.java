package com.yuzhi.dts.platform.service.modeling;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption.DoNotIncludeTests;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "com.yuzhi.dts.platform", importOptions = DoNotIncludeTests.class)
class ModelingCatalogBoundaryArchitectureTest {

    @ArchTest
    static final ArchRule modelingUsesCatalogOnlyThroughPublicServiceContracts = noClasses()
        .that()
        .resideInAPackage("..service.modeling..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("..domain.catalog..", "..repository.catalog..")
        .because("Catalog persistence entities and repositories are owned by Catalog public adapters");

    @ArchTest
    static final ArchRule catalogAndGovernanceUseStandardOwnerPorts = noClasses()
        .that()
        .resideInAnyPackage("..service.catalog..", "..service.governance..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("..domain.modeling..", "..repository.modeling..")
        .because("governed standard persistence is exposed through owner-side immutable ports");

    @ArchTest
    static final ArchRule catalogIdentityUsesForeignDomainsOnlyThroughPorts = noClasses()
        .that()
        .haveSimpleName("CatalogAssetIdentityResolver")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "..domain.governance..",
            "..repository.governance..",
            "..domain.modeling..",
            "..repository.modeling..",
            "..domain.service..",
            "..repository.service.."
        )
        .because("Catalog identity resolution is an orchestrator, not a second persistence owner");
}
