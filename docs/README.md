# Bottin Documentation

Bottin is a production-ready NIP-05 registry service with persistent storage, REST API, admin dashboard, and domain verification capabilities.

This documentation is organized following the [Diátaxis framework](https://diataxis.fr/), which classifies documentation into four categories based on user needs.

## Tutorials

Learning-oriented guides that take you through a series of steps to complete a project.

*Coming soon*

## How-To Guides

Problem-oriented guides that show you how to achieve a specific goal.

- [Deploy with Docker](how-to/docker-deployment.md) - Deploy Bottin services using Docker Compose
- [Integrate NIP-05 Validation](how-to/integrate-nip05-validation.md) - Serve `.well-known/nostr.json` for your domain, and validate identities from a client
- [Configure Deployment Settings](how-to/configure-deployment-settings.md) - Set the media server, relays, and rate limit from the admin UI
- [Configure Admin Access](how-to/configure-admin-access.md) - Set who may administer the deployment, and sign in with a Nostr key
- [Running E2E Tests](how-to/running-e2e-tests.md) - Run end-to-end tests with Testcontainers
- [Verify the /apps Nav and Avatar Dropdown](how-to/verify-apps-nav-and-avatar-dropdown.md) - Browser-level check of the authenticated nav and logout flow
- [Verify Profile and Relay Publishing](how-to/verify-profile-and-relay-publishing.md) - Edit and publish kind-0 profile and kind-10002 relay list from the browser
- [Upload a Profile Avatar and Banner](how-to/upload-profile-images.md) - Set profile images from a local file

## Reference

Information-oriented technical descriptions of the system.

- [Docker Compose Configuration](reference/docker-compose-configuration.md) - Environment variables and service configuration
- [REST API](reference/rest-api.md) - API endpoints documentation

## Explanation

Understanding-oriented discussions that clarify and illuminate particular topics.

- [Architecture Overview](explanation/architecture.md) - System architecture and module structure
- [Follow and Block Lists](explanation/follow-and-block-lists.md) - Why these lists are published to the user's own relays rather than stored here, why blocks are encrypted, and why an unreadable list is never overwritten
