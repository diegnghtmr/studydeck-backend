package com.studydeck.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * ArchUnit tests enforcing hexagonal architecture boundaries.
 *
 * <p>Dependency rule: bootstrap → infrastructure → application → domain
 *
 * <p>The domain layer must remain pure Java — no Spring, no Jakarta EE annotations. The application
 * layer must remain framework-free — no Spring, no Jakarta EE.
 *
 * <p>Note: {@code allowEmptyShould(true)} is set on all rules because in P0 the domain and
 * application packages only contain {@code package-info.java} placeholders. ArchUnit's default
 * behavior fails when no classes match the {@code that()} clause. These rules will enforce real
 * constraints as domain/application classes are added in P1+.
 */
@AnalyzeClasses(
    packages = "com.studydeck",
    importOptions = {ImportOption.DoNotIncludeTests.class})
class HexagonalArchitectureTest {

  /** Domain classes must not depend on Spring Framework. */
  @ArchTest
  static final ArchRule domainMustNotDependOnSpring =
      noClasses()
          .that()
          .resideInAPackage("com.studydeck.domain..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("org.springframework..", "jakarta..", "javax..")
          .allowEmptyShould(true);

  /** Application service classes must not depend on Spring Framework. */
  @ArchTest
  static final ArchRule applicationMustNotDependOnSpring =
      noClasses()
          .that()
          .resideInAPackage("com.studydeck.application..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("org.springframework..", "jakarta..", "javax..")
          .allowEmptyShould(true);

  /** Domain classes must not access adapter implementations directly. */
  @ArchTest
  static final ArchRule domainMustNotAccessAdapters =
      noClasses()
          .that()
          .resideInAPackage("com.studydeck.domain..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("com.studydeck.infrastructure.adapter..")
          .allowEmptyShould(true);

  /** Inbound adapters must not directly depend on outbound adapters. */
  @ArchTest
  static final ArchRule inboundAdaptersMustNotDependOnOutboundAdapters =
      noClasses()
          .that()
          .resideInAPackage("com.studydeck.infrastructure.adapter.in..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("com.studydeck.infrastructure.adapter.out..")
          .allowEmptyShould(true);
}
