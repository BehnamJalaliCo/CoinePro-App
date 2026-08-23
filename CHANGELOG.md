# Changelog

All notable user-visible CoinePro Android changes are recorded here.

The format follows Keep a Changelog conventions. Release names use semantic `MAJOR.MINOR.PATCH`; Android `versionCode` must satisfy the repository positive/range validator and Play enforces cross-release monotonicity against existing release history.

## [Unreleased]

### Added
- Phase 16 protected release-engineering pipeline.
- Dedicated staging build identity and staging service configuration boundary.
- Reproducible signed Android App Bundle path with signing material supplied outside the repository.
- Manual Play Console internal-track publishing workflow for the staging package.
- Phase 17 Launch & Safety education surface.
- Notification permission education and denial-recovery path before platform prompting.
- Connection setup education that distinguishes configured credentials from provider-confirmed state.
- Safe system feedback/share path with app version and environment metadata only.
- Phase 17 incident and rollback runbook.
- Protected GET-only production read-only smoke workflow with sanitized evidence artifact.
- Permanent Phase 1–17 repository consistency gate.
- Full Phase 1–17 cross-phase reconciliation audit.

### Changed
- Persisted Signal navigation/actionability now consistently requires a positive server Signal ID across Signals, notifications, deep links, AI and execution.
- Deep-link parsing is restricted to the CoinePro scheme and supported route shapes.
- Execution request/domain validation rejects invalid Signal identity and invalid request primitives without hidden write retries.
- Room market cache now independently rejects out-of-product-scope rows on write and restore.
- Production market smoke applies the same source freshness thresholds as the Android client.
- Baseline Profile now targets the current `CoineProThemeKt` class.
- Release/version documentation now distinguishes repository syntax/range checks from Play-enforced cross-release `versionCode` monotonicity.
- Staging CI uses supported `lintStaging` and `assembleStaging` gates; cumulative unit tests remain on the supported debug test variants instead of claiming a nonexistent `testStagingUnitTest` task.

### Security
- Release keystores and service-account credentials remain external secrets and are never committed.
- Benchmark builds use non-routable benchmark configuration instead of inheriting production endpoints.
- Production smoke remains read-only and never creates, closes, retries or mutates a trade/provider connection.
- External legal/provider/production evidence is never synthesized from Android CI, mocks or cached data.

## [0.1.0]

Initial native Android product milestone covering the Phase 0–15 application surface and quality gates.
