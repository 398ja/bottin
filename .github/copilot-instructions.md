# GitHub Copilot Instructions

This repository implements Bottin, a NIP-05 registry service for Nostr. When using GitHub Copilot, keep the following guidelines in mind:

## Commit Messages

- Use Conventional Commits as defined in [.commitlintrc.yml](../.commitlintrc.yml).
- Allowed types: `build`, `ci`, `chore`, `docs`, `feat`, `fix`, `perf`, `refactor`, `revert`, `style`, `test`.
- Header max 100 characters, lowercase type and scope.
- Example: `feat(api): add domain verification endpoint`

## Code Guidelines

- Maintain Java 21 compatibility and update `pom.xml` for new dependencies.
- Use Spring Boot conventions for controllers, services, and repositories.
- Remove unused imports.
- Run `mvn -q verify` before committing code.

## Protocol

- Follow the [NIP-05 specification](https://github.com/nostr-protocol/nips/blob/master/05.md) for identity verification endpoints.
- The `/.well-known/nostr.json` endpoint must return valid NIP-05 responses.

## Pull Requests

- Ensure pull requests include a clear description and test results.
- Reference related issues using `Closes #123` when applicable.
- Document new features in the README or related docs.

These instructions help Copilot produce code that respects the repository's conventions and Nostr protocol requirements.