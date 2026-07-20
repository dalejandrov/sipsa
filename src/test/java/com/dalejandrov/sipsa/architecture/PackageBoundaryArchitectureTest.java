package com.dalejandrov.sipsa.architecture;

import com.dalejandrov.sipsa.application.service.AuditTrailService;
import com.dalejandrov.sipsa.application.service.IngestionAuditService;
import com.dalejandrov.sipsa.application.service.IngestionRunQueryService;
import com.dalejandrov.sipsa.application.service.IngestionTriggerService;
import com.dalejandrov.sipsa.application.service.SipsaReadService;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * TECH-093 (ADR-007 §F5): the exact 3 package-boundary rules ADR-007 authorized, no more.
 * <p>
 * These protect boundaries the codebase already satisfies (TECH-090, TECH-091, and TECH-095
 * landed first, specifically so these rules assert the post-move state rather than failing
 * on day one) — they exist to catch <b>future</b> regressions, not to enforce a target
 * architecture the code doesn't have yet. No rule beyond these 3 was added, per TECH-093's
 * explicit scope limit.
 * <p>
 * Scans only {@code com.dalejandrov.sipsa} (production sources — {@link ImportOption
 * .DoNotIncludeTests} excludes {@code target/test-classes}, including this class and every
 * other test). Generated SOAP code (CXF, under {@code infrastructure.soap.client}, see
 * ADR-007 §F3/TECH-094) needed no exclusion for any of the 3 rules below: none of them
 * restrict {@code infrastructure} internally, and {@code domain} has zero dependency on
 * {@code infrastructure} of any kind (verified separately, not just assumed) — so nothing
 * about the generated package interacts with these rules. TECH-094 (still pending) may
 * relocate that package later; these rules do not depend on or block that outcome.
 * <p>
 * Full findings and rationale: {@code docs/adr/ADR-007-package-boundaries-and-internal-models.md}.
 */
@AnalyzeClasses(packages = "com.dalejandrov.sipsa", importOptions = ImportOption.DoNotIncludeTests.class)
class PackageBoundaryArchitectureTest {

    /**
     * ADR-007 explicitly does NOT prohibit {@code application} from depending on {@code api}
     * in general — it only accepts 5 specific, already-reviewed application services doing so
     * (to consume HTTP request/response DTOs, API mappers, and {@code TimezoneUtil} — a
     * deliberate, existing pattern across every read/query service, not an isolated mistake).
     * Any <em>other</em> application class reaching into {@code api} (including its
     * {@code controller} and {@code filter} sub-packages) is a new, unreviewed coupling this
     * rule catches. Writing this as "application must never depend on api" would fail
     * immediately against an ADR-007-accepted decision — TECH-093 explicitly forbids that.
     */
    @ArchTest
    static final ArchRule application_must_not_depend_on_api_except_the_five_accepted_services =
            noClasses()
                    .that().resideInAPackage("..application..")
                    .and().areNotAssignableTo(IngestionTriggerService.class)
                    .and().areNotAssignableTo(IngestionRunQueryService.class)
                    .and().areNotAssignableTo(SipsaReadService.class)
                    .and().areNotAssignableTo(AuditTrailService.class)
                    .and().areNotAssignableTo(IngestionAuditService.class)
                    .should().dependOnClassesThat().resideInAPackage("..api..")
                    .because("ADR-007 accepts exactly these five application services depending "
                            + "on api.dto.request/response, api.mapper, and api.util (TimezoneUtil) "
                            + "as a deliberate, already-reviewed pattern (TECH-090/091/095); any "
                            + "other application class reaching into api is a new, unreviewed coupling");

    /**
     * TECH-095 removed the only prior {@code domain -> infrastructure} reference (a
     * Javadoc-only {@code @see SoapGatewayImpl} import in {@code SoapGateway}). {@code domain}
     * defines contracts; {@code infrastructure} implements them — never the reverse.
     */
    @ArchTest
    static final ArchRule domain_must_not_depend_on_infrastructure =
            noClasses()
                    .that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat().resideInAPackage("..infrastructure..")
                    .because("domain defines contracts that infrastructure implements, never the "
                            + "reverse - ADR-007 F4/TECH-095 removed the only prior violation, a "
                            + "Javadoc-only @see import in SoapGateway");

    /**
     * Already true today with zero exceptions — this rule only prevents a future controller
     * from bypassing the application layer to touch persistence directly.
     */
    @ArchTest
    static final ArchRule controllers_must_not_depend_on_repositories =
            noClasses()
                    .that().resideInAPackage("..api.controller..")
                    .should().dependOnClassesThat().resideInAPackage("..infrastructure.persistence.repository..")
                    .because("controllers must go through application services, never touch "
                            + "persistence repositories directly");
}
