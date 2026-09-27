package com.github.orcas.orchestrator.core;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "com.github.orcas.orchestrator.core")
class ArchitectureTest {
    @ArchTest
    static final ArchRule core_does_not_depend_on_spring_or_reactive_libraries = noClasses()
            .that().resideOutsideOfPackage("..test..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..", "reactor..", "io.github.resilience4j..", "org.slf4j..", "jakarta.persistence..");
}
