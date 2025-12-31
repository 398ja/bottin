# GitHub Copilot Instructions

This repository implements Bottin, a NIP-05 identity service for Nostr. When using GitHub Copilot, keep the following guidelines in mind:

- Use Conventional Commits for titles and commit messages (e.g., `feat(scope): message`).
- Ensure pull requests include a clear description and test results.
- Reference related issues using `Closes #123` when applicable.
- Run `mvn -q verify` before committing code.
- Document new features in the README or related docs.
- Maintain Java 21 compatibility and update `pom.xml` for new dependencies.
- Remove unused imports.

These instructions help Copilot produce code that respects the repository's conventions and NIP-05 protocol requirements.