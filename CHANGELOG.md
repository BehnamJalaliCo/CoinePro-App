# Changelog

All notable user-visible CoinePro Android changes are recorded here.

The format follows Keep a Changelog conventions and release versions use semantic `MAJOR.MINOR.PATCH` names with a monotonically increasing Android `versionCode`.

## [Unreleased]

### Added
- Phase 16 protected release-engineering pipeline.
- Dedicated staging build identity and staging service configuration boundary.
- Reproducible signed Android App Bundle path with signing material supplied outside the repository.
- Manual Play Console internal-track publishing workflow for the staging package.

### Security
- Release keystores and service-account credentials remain external secrets and are never committed.
- Benchmark builds use non-routable benchmark configuration instead of inheriting production endpoints.

## [0.1.0]

Initial native Android product milestone covering the Phase 0–15 application surface and quality gates.
