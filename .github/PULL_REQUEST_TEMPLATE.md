## Summary

<!-- One paragraph describing what this PR does and why -->

## Related Story / Issue

<!-- Backlog story: TECH-XXX  |  GitHub issue: #NNN -->

## Type of Change

- [ ] Bug fix (correctiva — non-breaking)
- [ ] Feature (new capability)
- [ ] Refactoring (behavior unchanged)
- [ ] Test (tests only, no production code change)
- [ ] Documentation
- [ ] Configuration / build / tooling
- [ ] Security

## Breaking Changes

- [ ] No breaking changes
- [ ] Breaking change — describe impact and migration path below:

<!-- Describe breaking changes here if applicable -->

## Checklist

### Build
- [ ] `./mvnw clean verify` passes locally
- [ ] No compilation warnings in changed files

### Tests
- [ ] Existing tests pass
- [ ] New tests added for changed logic
- [ ] Each new test has a clear `// given / when / then` structure

### Documentation
- [ ] CHANGELOG.md updated under `[Unreleased]`
- [ ] Backlog story marked `Done` in [technical-backlog.md](../docs/backlog/technical-backlog.md)
- [ ] ADR updated if this story has a linked ADR (`Proposed` → `Accepted` if applicable)
- [ ] Implementation roadmap updated if a phase is complete
- [ ] Other docs updated if affected (API docs, testing-strategy, etc.)

### Security
- [ ] No credentials, secrets, or sensitive data in the diff
- [ ] No stack traces exposed in error responses
- [ ] Input validation is in place for any new user-facing parameters

### ADR Required?
- [ ] No — not a significant architectural decision
- [ ] Yes — ADR created or updated: `docs/adr/ADR-XXX-...`

## Testing Evidence

<!-- How was this change tested? Paste the relevant test command output or a screenshot if applicable -->

```
./mvnw clean verify
...
Tests run: X, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## Notes for Reviewer

<!-- Anything the reviewer should pay special attention to, or that is not obvious from the code -->
