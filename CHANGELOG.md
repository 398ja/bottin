# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.2.0] - 2026-01-30

### Added
- Docker image build bound to Maven deploy phase for streamlined CI/CD
- E2E testing documentation and architecture guide
- CI configuration with build and test jobs

### Changed
- Domain verification now uses `_nostr-verification` naming for better protocol alignment
- Admin UI shows both DNS and Well-Known verification options without method selection
- Verification attempts both DNS and Well-Known methods automatically
- Auto-generates verification token when viewing unverified domains
- Updated nostr-java dependency to 1.3.0
- Removed nsecbunker-account dependency from bottin-core

### Fixed
- SecurityFilterChain conflict in E2E tests
- Qodana security findings addressed
- CI workflow fixes for google-java-format and Maven wrapper
- Admin credentials now properly use environment variables
- Fixed 500 error when viewing domains list

### Security
- Removed credentials from dependabot registry configuration

## [0.1.0] - 2025-01-15

### Added
- Initial release of Bottin NIP-05 Registry Service
- REST API for NIP-05 record management
- Admin dashboard with domain and record management
- Domain verification via DNS TXT and Well-Known file methods
- PostgreSQL and H2 database support
- Spring Boot starter for easy integration
- Docker support with Jib

[Unreleased]: https://github.com/tcheeric/bottin/compare/v0.2.0...HEAD
[0.2.0]: https://github.com/tcheeric/bottin/compare/v0.1.0...v0.2.0
[0.1.0]: https://github.com/tcheeric/bottin/releases/tag/v0.1.0
