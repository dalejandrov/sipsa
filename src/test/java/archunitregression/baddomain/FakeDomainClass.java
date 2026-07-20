package archunitregression.baddomain;

import archunitregression.badinfra.FakeInfraClass;

/**
 * Fixture-only class for {@code archunitregression.ArchRulePatternRegressionTest}: a
 * deliberate "domain depends on infrastructure"-shaped violation, entirely outside
 * {@code com.dalejandrov.sipsa} so it is never part of the real
 * {@code PackageBoundaryArchitectureTest} scan.
 */
public class FakeDomainClass {
    private final FakeInfraClass infra = new FakeInfraClass();
}
