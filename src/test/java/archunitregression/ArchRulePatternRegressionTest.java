package archunitregression;

import archunitregression.baddomain.FakeDomainClass;
import archunitregression.badinfra.FakeInfraClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.EvaluationResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * TECH-093: a negative-control fixture proving the "{@code noClasses that reside in X should
 * dependOnClassesThat reside in Y}" rule pattern — the same pattern
 * {@code PackageBoundaryArchitectureTest} uses for all 3 real rules — genuinely detects a
 * violation when one exists, rather than trivially passing regardless of input.
 * <p>
 * {@link FakeDomainClass} and {@link FakeInfraClass} exist solely for this test and live in
 * the {@code archunitregression} package tree, entirely outside
 * {@code com.dalejandrov.sipsa}: {@code PackageBoundaryArchitectureTest} scans only
 * {@code com.dalejandrov.sipsa}, so this fixture is never part of that scan and never
 * touches, weakens, or risks contaminating production code — no invalid production class
 * was added anywhere to make this assertion possible.
 */
class ArchRulePatternRegressionTest {

    @Test
    @DisplayName("the 'noClasses in X should dependOnClassesThat in Y' pattern flags a genuine violation")
    void rulePattern_detectsAGenuineViolation() {
        JavaClasses fixtureClasses = new ClassFileImporter()
                .importPackages("archunitregression.baddomain", "archunitregression.badinfra");

        ArchRule rule = noClasses()
                .that().resideInAPackage("..baddomain..")
                .should().dependOnClassesThat().resideInAPackage("..badinfra..");

        EvaluationResult result = rule.evaluate(fixtureClasses);

        assertThat(result.hasViolation())
                .as("FakeDomainClass deliberately depends on FakeInfraClass; the rule must catch it")
                .isTrue();
        assertThat(result.getFailureReport().toString())
                .as("the failure report names the actual offending class")
                .contains("FakeDomainClass");
    }

    @Test
    @DisplayName("the same pattern does not report a false positive against an innocent class in scope")
    void rulePattern_noFalsePositiveWhenNoViolationExists() {
        JavaClasses innocentAndInfra = new ClassFileImporter()
                .importPackages("archunitregression.gooddomain", "archunitregression.badinfra");

        ArchRule rule = noClasses()
                .that().resideInAPackage("..gooddomain..")
                .should().dependOnClassesThat().resideInAPackage("..badinfra..");

        EvaluationResult result = rule.evaluate(innocentAndInfra);

        assertThat(result.hasViolation())
                .as("InnocentDomainClass does not depend on FakeInfraClass; no violation should be reported")
                .isFalse();
    }
}
